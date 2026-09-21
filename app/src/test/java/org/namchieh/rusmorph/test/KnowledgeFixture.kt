package org.namchieh.rusmorph.test

import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path

fun openKnowledgeFixture(path: String): Reader {
    val file = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .flatMap { root ->
            sequenceOf(
                root.resolve("src/test/resources").resolve(path),
                root.resolve("app/src/test/resources").resolve(path),
            )
        }
        .firstOrNull(Files::isRegularFile)
    return requireNotNull(file) { "Missing fixture asset $path" }.toFile().bufferedReader()
}
