package org.namchieh.rusmorph.ui.screen.pronunciation

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordClipPlaybackDeviceTest {
    @Test
    fun recordedM4aCanSeekAndPlayFromWordOffset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recording = File.createTempFile("word-clip-device-test-", ".m4a", context.cacheDir)
        try {
            recordShortM4a(recording)
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(recording.absolutePath)
                prepare()
            }
            try {
                val targetMs = minOf(300, (player.duration / 3).coerceAtLeast(0))
                val seekCompleted = CountDownLatch(1)
                val playbackError = AtomicReference<String?>(null)
                player.setOnSeekCompleteListener { seekCompleted.countDown() }
                player.setOnErrorListener { _, what, extra ->
                    playbackError.set("$what/$extra")
                    true
                }
                player.seekTo(targetMs.toLong(), MediaPlayer.SEEK_CLOSEST)
                assertTrue("seek did not complete", seekCompleted.await(2, TimeUnit.SECONDS))
                player.start()
                SystemClock.sleep(300)
                assertNull("MediaPlayer reported an error", playbackError.get())
                assertTrue(
                    "playback position did not advance from the requested word offset",
                    player.currentPosition >= targetMs,
                )
            } finally {
                runCatching { if (player.isPlaying) player.stop() }
                player.release()
            }
        } finally {
            recording.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun recordShortM4a(output: File) {
        val recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(64_000)
            setOutputFile(output.absolutePath)
            prepare()
            start()
        }
        try {
            SystemClock.sleep(1_200)
        } finally {
            recorder.stop()
            recorder.release()
        }
    }
}
