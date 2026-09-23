import org.gradle.api.tasks.testing.Test
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val configuredAgentProxyUrl = providers.gradleProperty("AGENT_PROXY_BASE_URL")
    .orElse(providers.environmentVariable("AGENT_PROXY_BASE_URL"))
val configuredDebugAgentProxyUrl = providers.gradleProperty("AGENT_PROXY_DEBUG_BASE_URL")
    .orElse(providers.environmentVariable("AGENT_PROXY_DEBUG_BASE_URL"))
val configuredSpeechBackendUrl = providers.gradleProperty("SPEECH_BACKEND_BASE_URL")
    .orElse(providers.environmentVariable("SPEECH_BACKEND_BASE_URL"))
val productionApiUrl = "https://api.namchieh.org/"
val releaseStoreFile = providers.gradleProperty("RUSMORPH_RELEASE_STORE_FILE")
    .orElse(providers.environmentVariable("RUSMORPH_RELEASE_STORE_FILE"))
val releaseStorePassword = providers.gradleProperty("RUSMORPH_RELEASE_STORE_PASSWORD")
    .orElse(providers.environmentVariable("RUSMORPH_RELEASE_STORE_PASSWORD"))
val releaseKeyAlias = providers.gradleProperty("RUSMORPH_RELEASE_KEY_ALIAS")
    .orElse(providers.environmentVariable("RUSMORPH_RELEASE_KEY_ALIAS"))
val releaseKeyPassword = providers.gradleProperty("RUSMORPH_RELEASE_KEY_PASSWORD")
    .orElse(providers.environmentVariable("RUSMORPH_RELEASE_KEY_PASSWORD"))
val hasReleaseSigning = listOf(
    releaseStoreFile.orNull, releaseStorePassword.orNull, releaseKeyAlias.orNull, releaseKeyPassword.orNull,
).all { !it.isNullOrBlank() }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
}

val dataAssetsDirectory = layout.projectDirectory.dir("src/main/assets/database")
val requiredDatabaseAssets = listOf(
    "lexicon.json",
    "declension_rules.json",
    "knowledge_chunks.json",
    "grammar_points.json",
    "tem4_questions.json",
    "grammar_question_links.json",
    "data_manifest.json",
)

val generatedCourseAssets = layout.buildDirectory.dir("generated/course-assets")
val generateCourseAssets by tasks.registering {
    group = "data build"
    description = "Package the existing dialogue corpus and verified textbook transcriptions as Android assets."
    val source = rootProject.layout.projectDirectory.file("agent-proxy/src/TextbookDialogueCorpus.ts")
    val output = generatedCourseAssets.map { it.file("database/textbook_dialogues.json") }
    val textOutput = generatedCourseAssets.map { it.file("database/textbook_texts.json") }
    val transcriptionSources = rootProject.fileTree("data-source/transcriptions") { include("urok_*.json") }
    inputs.file(source)
    inputs.files(transcriptionSources)
    outputs.files(output, textOutput)
    doLast {
        val windows1252 = Charset.forName("windows-1252")
        fun repairEncoding(value: String): String = if (value.contains('Ð') || value.contains('Ñ')) {
            String(value.toByteArray(windows1252), StandardCharsets.UTF_8)
        } else value
        val sourceText = source.asFile.readText()
        val record = Regex("""\{\s*lessonNumber:\s*(\d+),\s*lines:\s*(\[[^]]*])\s*}""")
        val parser = JsonSlurper()
        val records = record.findAll(sourceText).map { match ->
            val lesson = match.groupValues[1].toInt()
            val lines = (parser.parseText(match.groupValues[2]) as List<*>).map { repairEncoding(it.toString()) }
            mapOf("lessonNumber" to lesson, "lines" to lines)
        }.toList()
        check(records.isNotEmpty()) { "No textbook dialogues parsed from ${source.asFile}" }
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(records)))
        }

        val texts = transcriptionSources.files.sortedBy { it.name }.flatMap fileLoop@{ file ->
            val pages = parser.parse(file) as? List<*> ?: emptyList<Any>()
            pages.flatMap pageLoop@{ pageValue ->
                val page = pageValue as? Map<*, *> ?: return@pageLoop emptyList<Map<String, Any?>>()
                val lesson = (page["lesson"] as? Number)?.toInt() ?: return@pageLoop emptyList<Map<String, Any?>>()
                val sections = page["sections"] as? List<*> ?: emptyList<Any>()
                sections.mapNotNull { sectionValue ->
                    val section = sectionValue as? Map<*, *> ?: return@mapNotNull null
                    if (section["type"] != "TEXT" || section["status"] != "CONFIDENT") return@mapNotNull null
                    val paragraphs = (section["paragraphs"] as? List<*>)
                        ?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
                        .orEmpty()
                    if (paragraphs.isEmpty()) null else mapOf(
                        "lessonNumber" to lesson,
                        "title" to section["title"]?.toString(),
                        "paragraphs" to paragraphs,
                    )
                }
            }
        }.groupBy { it["lessonNumber"] }.map { (lesson, sections) ->
            mapOf(
                "lessonNumber" to lesson,
                "title" to sections.mapNotNull { it["title"] as? String }.firstOrNull(),
                "paragraphs" to sections.flatMap { it["paragraphs"] as List<*> },
            )
        }.sortedBy { (it["lessonNumber"] as Number).toInt() }
        check(texts.isNotEmpty()) { "No verified textbook TEXT sections found in data-source/transcriptions" }
        textOutput.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(texts)))
        }
    }
}

fun availablePythonCommand(): List<String>? {
    val configured = providers.environmentVariable("RUSMORPH_PYTHON").orNull
    val candidates = buildList {
        if (!configured.isNullOrBlank()) add(listOf(configured))
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            add(listOf("py", "-3"))
            add(listOf("python"))
        } else {
            add(listOf("python3"))
            add(listOf("python"))
        }
    }
    return candidates.firstOrNull { command ->
        try {
            ProcessBuilder(command + "--version")
                .directory(rootProject.projectDir)
                .redirectErrorStream(true)
                .start()
                .run { inputStream.use { it.readBytes() }; waitFor() == 0 }
        } catch (_: Exception) {
            false
        }
    }
}

val buildLexiconAssets by tasks.registering {
    group = "data build"
    description = "Convert read-only XLSX/CSV/DOCX sources into Room-compatible assets."
    inputs.files(rootProject.fileTree("data-source") {
        include("*.xlsx", "*.XLSX", "*.csv", "*.CSV", "*.docx", "*.DOCX", "knowledge_overrides.json")
    })
    inputs.files(rootProject.fileTree("tools/xlsx_builder") { include("**/*.py", "config/*.json") })
    inputs.files(rootProject.fileTree("tools/grammar_exam_importer") { include("**/*.py") })
    inputs.file(rootProject.layout.projectDirectory.file("data-source/grammar-tem4/grammar_tem4_source.xlsx"))
    inputs.file(rootProject.layout.projectDirectory.file("data-source/grammar-tem4/grammar_question_relation_overrides.json"))
    outputs.files(requiredDatabaseAssets.map(dataAssetsDirectory::file))
    outputs.file(rootProject.layout.projectDirectory.file("build/generated/agent/lexicon_agent.jsonl"))
    outputs.file(rootProject.layout.projectDirectory.file("build/reports/data/xlsx_audit_report.json"))
    outputs.file(rootProject.layout.projectDirectory.file("build/reports/data/pure_word_validation.json"))
    outputs.file(rootProject.layout.projectDirectory.file("build/reports/data/knowledge_association_report.json"))
    outputs.file(rootProject.layout.projectDirectory.file("build/reports/data/manual_review_candidates.json"))
    outputs.file(rootProject.layout.projectDirectory.file("build/reports/data/real_asset_test_fixtures.json"))
    outputs.file(rootProject.layout.projectDirectory.file("build/reports/data/grammar_tem4_audit_report.json"))
    doLast {
        val tabularSources = rootProject.fileTree("data-source") {
            include("*.xlsx", "*.XLSX", "*.csv", "*.CSV")
        }.files
        val existingAssets = requiredDatabaseAssets.all { dataAssetsDirectory.file(it).asFile.isFile }
        if (tabularSources.isEmpty()) {
            if (existingAssets) {
                logger.warn("No XLSX/CSV source found; preserving pre-generated database assets.")
                return@doLast
            }
            throw GradleException("No XLSX/CSV source and no complete pre-generated database assets.")
        }
        val python = availablePythonCommand()
        if (python == null) {
            if (existingAssets) {
                logger.warn("Python 3 was not found; preserving pre-generated database assets.")
                return@doLast
            }
            throw GradleException("Python 3 was not found and database assets are missing.")
        }
        providers.exec {
            workingDir(rootProject.projectDir)
            commandLine(
                python + listOf(
                    "tools/xlsx_builder/build_database_assets.py",
                    "--input", "data-source",
                    "--output", "app/src/main/assets/database",
                    "--agent-output", "build/generated/agent",
                    "--report-output", "build/reports/data",
                ),
            )
        }.result.get().assertNormalExitValue()
        providers.exec {
            workingDir(rootProject.projectDir)
            commandLine(
                python + listOf(
                    "tools/grammar_exam_importer/import_grammar_tem4.py",
                    "--source", "data-source/grammar-tem4/grammar_tem4_source.xlsx",
                    "--output", "app/src/main/assets/database",
                    "--report-output", "build/reports/data",
                ),
            )
        }.result.get().assertNormalExitValue()
    }
}

val testGrammarTem4Importer by tasks.registering {
    group = "verification"
    description = "Run normalized GrammarPoint/TEM4 importer tests."
    inputs.files(rootProject.fileTree("tools/grammar_exam_importer") { include("**/*.py") })
    inputs.file(rootProject.layout.projectDirectory.file("data-source/grammar-tem4/grammar_tem4_source.xlsx"))
    doLast {
        val python = availablePythonCommand()
            ?: throw GradleException("Python 3 is required for Grammar/TEM4 importer tests.")
        providers.exec {
            workingDir(rootProject.projectDir)
            commandLine(python + listOf("-m", "pytest", "tools/grammar_exam_importer/tests", "-q"))
        }.result.get().assertNormalExitValue()
    }
}

val verifyLexiconAssets by tasks.registering {
    group = "verification"
    description = "Verify pre-generated Room database assets without parsing source workbooks."
    dependsOn(buildLexiconAssets)
    inputs.files(requiredDatabaseAssets.map(dataAssetsDirectory::file))
    doLast {
        val files = requiredDatabaseAssets.associateWith { dataAssetsDirectory.file(it).asFile }
        val missing = files.filterValues { !it.isFile }.keys
        if (missing.isNotEmpty()) throw GradleException("Missing database assets: $missing")
        val parser = JsonSlurper()
        val lexicon = parser.parse(files.getValue("lexicon.json")) as? List<*>
            ?: throw GradleException("lexicon.json must contain a JSON array")
        val knowledge = parser.parse(files.getValue("knowledge_chunks.json")) as? List<*>
            ?: throw GradleException("knowledge_chunks.json must contain a JSON array")
        val grammar = parser.parse(files.getValue("grammar_points.json")) as? List<*>
            ?: throw GradleException("grammar_points.json must contain a JSON array")
        val questions = parser.parse(files.getValue("tem4_questions.json")) as? List<*>
            ?: throw GradleException("tem4_questions.json must contain a JSON array")
        val grammarLinks = parser.parse(files.getValue("grammar_question_links.json")) as? List<*>
            ?: throw GradleException("grammar_question_links.json must contain a JSON array")
        val declension = parser.parse(files.getValue("declension_rules.json")) as? Map<*, *>
            ?: throw GradleException("declension_rules.json must contain a JSON object")
        val manifest = parser.parse(files.getValue("data_manifest.json")) as? Map<*, *>
            ?: throw GradleException("data_manifest.json must contain a JSON object")
        if ((manifest["schemaVersion"] as? Number)?.toInt() != 2) {
            throw GradleException("data_manifest schemaVersion must be 2")
        }
        val entries = lexicon.map { it as? Map<*, *> ?: throw GradleException("Invalid lexicon entry") }
        val chunks = knowledge.map { it as? Map<*, *> ?: throw GradleException("Invalid knowledge chunk") }
        val entryIds = entries.map { it["id"] }
        val chunkIds = chunks.map { it["id"] }.toSet()
        if (entryIds.size != entryIds.toSet().size) throw GradleException("Duplicate lexicon IDs")
        if (chunks.size != chunkIds.size) throw GradleException("Duplicate knowledge chunk IDs")
        entries.forEach { entry ->
            val forms = entry["searchForms"] as? List<*>
            if (forms.isNullOrEmpty()) throw GradleException("Entry ${entry["id"]} has no searchForms")
            val missingRefs = (entry["relatedKnowledgeChunkIds"] as? List<*>).orEmpty().toSet() - chunkIds
            if (missingRefs.isNotEmpty()) throw GradleException("Entry ${entry["id"]} has invalid knowledge refs")
            listOf("gender", "declensionClass", "endingType", "pluralStressPattern", "aspect", "conjugationClass", "phoneticAlternation")
                .mapNotNull { entry[it] as? String }
                .forEach { value ->
                    if (value.trim().lowercase() in setOf("8", "null", "none", "-")) {
                        throw GradleException("Entry ${entry["id"]} contains a placeholder morphology value")
                    }
                }
        }
        val counts = manifest["counts"] as? Map<*, *> ?: throw GradleException("Manifest counts missing")
        if (grammar.size != 16 || questions.size != 48 || grammarLinks.size != 51) {
            throw GradleException("Grammar/TEM4 asset counts are invalid")
        }
        if ((counts["grammarPoints"] as? Number)?.toInt() != grammar.size ||
            (counts["questions"] as? Number)?.toInt() != questions.size ||
            (counts["grammarQuestionLinks"] as? Number)?.toInt() != grammarLinks.size
        ) throw GradleException("Manifest Grammar/TEM4 counts do not match generated assets")
        val rules = declension["rules"] as? List<*> ?: throw GradleException("Declension rules missing")
        val actualCounts = mapOf(
            "entries" to entries.size,
            "searchForms" to entries.sumOf { (it["searchForms"] as? List<*>)?.size ?: 0 },
            "partsOfSpeechRelations" to entries.sumOf { (it["partsOfSpeech"] as? List<*>)?.size ?: 0 },
            "annotationRecords" to entries.sumOf { (it["annotations"] as? List<*>)?.size ?: 0 },
            "provenanceRecords" to entries.sumOf { (it["provenance"] as? List<*>)?.size ?: 0 },
            "declensionRules" to rules.size,
            "knowledgeChunks" to chunks.size,
            "crossRefs" to entries.sumOf { (it["relatedKnowledgeChunkIds"] as? List<*>)?.size ?: 0 },
        )
        actualCounts.forEach { (name, actual) ->
            if ((counts[name] as? Number)?.toInt() != actual) {
                throw GradleException("Manifest $name does not match generated assets")
            }
        }
        logger.lifecycle("Verified lexicon assets: ${entries.size} entries, ${actualCounts["searchForms"]} search forms.")
    }
}

val buildDataAssets by tasks.registering {
    group = "data build"
    description = "Compatibility alias for buildLexiconAssets."
    dependsOn(buildLexiconAssets)
}

android {
    namespace = "org.namchieh.rusmorph"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.namchieh.rusmorph"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.006"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "AGENT_PROXY_BASE_URL", "".asBuildConfigString())
        buildConfigField("String", "AGENT_PROXY_DEVICE_BASE_URL", "".asBuildConfigString())
        buildConfigField("String", "SPEECH_BACKEND_BASE_URL", "".asBuildConfigString())
        buildConfigField("String", "SPEECH_BACKEND_DEVICE_BASE_URL", "".asBuildConfigString())
        buildConfigField("String", "REVIEW_WORKBENCH_URL", "https://api.namchieh.org/review/".asBuildConfigString())
    }

    flavorDimensions += "environment"
    productFlavors {
        create("local") {
            dimension = "environment"
            // Local builds may explicitly point at an emulator/LAN development gateway.
            buildConfigField("boolean", "ALLOW_CLEARTEXT_ENDPOINTS", "true")
            resValue("bool", "allow_cleartext", "true")
        }
        create("production") {
            dimension = "environment"
            buildConfigField("boolean", "ALLOW_CLEARTEXT_ENDPOINTS", "false")
            resValue("bool", "allow_cleartext", "false")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile.orNull))
                storePassword = requireNotNull(releaseStorePassword.orNull)
                keyAlias = requireNotNull(releaseKeyAlias.orNull)
                keyPassword = requireNotNull(releaseKeyPassword.orNull)
            }
        }
    }

    buildTypes {
        debug {
            val url = configuredDebugAgentProxyUrl.orNull
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: configuredAgentProxyUrl.orNull?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: productionApiUrl
            buildConfigField("String", "AGENT_PROXY_BASE_URL", url.asBuildConfigString())
            buildConfigField("String", "AGENT_PROXY_DEVICE_BASE_URL", "".asBuildConfigString())
            val speechUrl = configuredSpeechBackendUrl.orNull
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: productionApiUrl
            buildConfigField("String", "SPEECH_BACKEND_BASE_URL", speechUrl.asBuildConfigString())
            buildConfigField("String", "SPEECH_BACKEND_DEVICE_BASE_URL", "".asBuildConfigString())
        }
        release {
            val safeUrl = configuredAgentProxyUrl.orNull
                ?.takeIf { it.startsWith("https://") }
                ?: productionApiUrl
            buildConfigField("String", "AGENT_PROXY_BASE_URL", safeUrl.asBuildConfigString())
            buildConfigField("String", "AGENT_PROXY_DEVICE_BASE_URL", "".asBuildConfigString())
            val safeSpeechUrl = configuredSpeechBackendUrl.orNull
                ?.takeIf { it.startsWith("https://") }
                ?: productionApiUrl
            buildConfigField("String", "SPEECH_BACKEND_BASE_URL", safeSpeechUrl.asBuildConfigString())
            buildConfigField("String", "SPEECH_BACKEND_DEVICE_BASE_URL", "".asBuildConfigString())
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("main").assets.srcDir(generatedCourseAssets)
        getByName("debug").assets.srcDir("schemas")
        getByName("release").assets.srcDir("schemas")
        getByName("test").assets.srcDir("schemas")
        getByName("androidTest").assets.srcDir("schemas")
    }
}

// A release artifact is never silently signed with the debug keystore.  Developers can
// still build every debug variant without credentials, while CI/release explicitly fails.
tasks.configureEach {
    if (name.startsWith("assemble") && name.endsWith("Release")) {
        doFirst {
            check(hasReleaseSigning) {
                "Release signing is not configured. Set RUSMORPH_RELEASE_STORE_FILE, " +
                    "RUSMORPH_RELEASE_STORE_PASSWORD, RUSMORPH_RELEASE_KEY_ALIAS, and " +
                    "RUSMORPH_RELEASE_KEY_PASSWORD. Refusing to produce an unsigned or debug-signed release."
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", file("schemas").path)
    }
}


tasks.named("preBuild") {
    dependsOn(buildLexiconAssets, generateCourseAssets)
}

tasks.withType<Test>().configureEach {
    dependsOn(testGrammarTem4Importer)
    // Robolectric native SQLite can crash the Windows JVM when several Room suites share one process.
    forkEvery = 1
    maxParallelForks = 1
    val testHome = File(System.getProperty("java.io.tmpdir"), "rusmorph-test-home").apply { mkdirs() }
    val robolectricMavenRepository = File(testHome, ".m2/repository").apply { mkdirs() }
    systemProperty("user.home", testHome.absolutePath)
    // All Android-backed unit tests target API 34; pin the resolver as well so
    // manifest defaults cannot trigger a second 150 MB Android 15 download.
    systemProperty("robolectric.enabledSdks", "34")
    // Keep test process state isolated while reusing the already downloaded
    // Robolectric Android runtime across forked Room suites.
    systemProperty("maven.repo.local", robolectricMavenRepository.absolutePath)
}

val productionReleaseTaskNames = setOf(
    "assembleProductionRelease",
    ":app:assembleProductionRelease",
    "packageProductionRelease",
    ":app:packageProductionRelease",
)

gradle.taskGraph.whenReady {
    if (allTasks.any { it.name in productionReleaseTaskNames || it.path in productionReleaseTaskNames } && !hasReleaseSigning) {
        throw GradleException(
            "assembleProductionRelease requires release signing configuration (RUSMORPH_RELEASE_STORE_FILE, RUSMORPH_RELEASE_STORE_PASSWORD, RUSMORPH_RELEASE_KEY_ALIAS, RUSMORPH_RELEASE_KEY_PASSWORD)."
        )
    }
}

tasks.matching { it.name in setOf("assembleProductionRelease", "packageProductionRelease") }.configureEach {
    doFirst {
        if (!hasReleaseSigning) {
            throw GradleException(
                "assembleProductionRelease requires release signing configuration (RUSMORPH_RELEASE_STORE_FILE, RUSMORPH_RELEASE_STORE_PASSWORD, RUSMORPH_RELEASE_KEY_ALIAS, RUSMORPH_RELEASE_KEY_PASSWORD)."
            )
        }
    }
}
