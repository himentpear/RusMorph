package org.namchieh.rusmorph.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.namchieh.rusmorph.update.model.AppUpdate
import org.namchieh.rusmorph.update.model.UpdateApkManifest
import org.namchieh.rusmorph.update.model.UpdateDecision
import org.namchieh.rusmorph.update.model.UpdateFlavorPolicy
import org.namchieh.rusmorph.update.model.UpdateManifest
import org.namchieh.rusmorph.update.model.UpdateRules
import org.namchieh.rusmorph.update.model.toValidatedUpdate

class UpdateRulesTest {
    @Test fun equalVersionHasNoUpdate() = assertEquals(UpdateDecision.None, decide(remote = 3))

    @Test fun lowerVersionHasNoUpdate() = assertEquals(UpdateDecision.None, decide(remote = 2))

    @Test fun higherVersionIsAvailable() {
        val result = decide(remote = 4) as UpdateDecision.Available
        assertFalse(result.mandatory)
    }

    @Test fun skippedVersionIsHiddenOnlyForAutomaticChecks() {
        assertEquals(UpdateDecision.None, decide(remote = 4, skipped = 4, automatic = true))
        assertFalse((decide(remote = 4, skipped = 4, automatic = false) as UpdateDecision.Available).mandatory)
    }

    @Test fun newerVersionThanSkippedIsAvailable() {
        assertTrue(decide(remote = 5, skipped = 4) is UpdateDecision.Available)
    }

    @Test fun forcedUpdateIsMandatory() {
        assertTrue((decide(remote = 4, force = true) as UpdateDecision.Available).mandatory)
    }

    @Test fun unsupportedLocalVersionIsMandatory() {
        assertTrue((decide(remote = 4, minimum = 4) as UpdateDecision.Available).mandatory)
    }

    @Test fun invalidManifestFieldsAreRejected() {
        assertNull(manifest(versionCode = 0).toValidatedUpdate())
        assertNull(manifest(url = "http://example.com/app.apk").toValidatedUpdate())
        assertNull(manifest(hash = "not-a-sha").toValidatedUpdate())
        assertNull(manifest(platform = "ios").toValidatedUpdate())
        assertNull(manifest(channel = "beta").toValidatedUpdate())
        assertNull(manifest(platform = null).toValidatedUpdate())
    }

    @Test fun flavorPolicyKeepsLocalBuildsOffTheProductionService() {
        assertFalse(UpdateFlavorPolicy.allowsAutomaticCheck("local", debug = true))
        assertFalse(UpdateFlavorPolicy.allowsAutomaticCheck("local", debug = false))
        assertFalse(UpdateFlavorPolicy.allowsManualCheck("local"))
        assertFalse(UpdateFlavorPolicy.allowsAutomaticCheck("production", debug = true))
        assertTrue(UpdateFlavorPolicy.allowsAutomaticCheck("production", debug = false))
        assertTrue(UpdateFlavorPolicy.allowsManualCheck("production"))
    }

    private fun decide(
        remote: Int,
        skipped: Int = 0,
        automatic: Boolean = true,
        force: Boolean = false,
        minimum: Int = 1,
    ) = UpdateRules.decide(update(remote, force, minimum), 3, skipped, automatic)

    private fun update(versionCode: Int, force: Boolean, minimum: Int) = AppUpdate(
        versionCode = versionCode,
        versionName = "0.004",
        minSupportedVersionCode = minimum,
        forceUpdate = force,
        title = "RusMorph 0.004",
        releaseNotes = emptyList(),
        apkUrl = "https://example.com/app.apk",
        apkSha256 = "a".repeat(64),
        apkSize = 1,
        releasePageUrl = null,
    )

    private fun manifest(
        versionCode: Int = 4,
        url: String = "https://example.com/app.apk",
        hash: String = "a".repeat(64),
        platform: String? = "android",
        channel: String = "stable",
    ) = UpdateManifest(
        platform = platform,
        channel = channel,
        versionCode = versionCode,
        versionName = "0.004",
        apk = UpdateApkManifest(url, hash, 1),
    )
}
