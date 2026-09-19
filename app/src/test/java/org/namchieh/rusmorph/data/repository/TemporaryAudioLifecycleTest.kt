package org.namchieh.rusmorph.data.repository

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test

class TemporaryAudioLifecycleTest {
    @Test fun deletes_recording_after_success() = runBlocking {
        val file = temporaryFile()
        consumeTemporaryAudio(file) { "ok" }
        assertFalse(file.exists())
    }

    @Test fun deletes_recording_after_http_failure() = runBlocking {
        val file = temporaryFile()
        runCatching { consumeTemporaryAudio<Unit>(file) { throw IllegalStateException("HTTP 500") } }
        assertFalse(file.exists())
    }

    @Test fun deletes_recording_after_cancellation() = runBlocking {
        val file = temporaryFile()
        runCatching { consumeTemporaryAudio<Unit>(file) { throw CancellationException("cancelled") } }
        assertFalse(file.exists())
    }

    private fun temporaryFile(): File = File.createTempFile("rusmorph-test-", ".m4a").apply { writeBytes(byteArrayOf(1)) }
}
