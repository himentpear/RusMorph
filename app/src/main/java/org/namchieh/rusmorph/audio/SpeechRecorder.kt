package org.namchieh.rusmorph.audio

import android.content.Context
import android.media.MediaRecorder
import java.io.File

class SpeechRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startTimestampMs: Long = 0L

    fun start(): File {
        cancel()
        val file = File.createTempFile("speech-", ".m4a", context.cacheDir)
        @Suppress("DEPRECATION")
        val mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(64_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        startTimestampMs = System.currentTimeMillis()
        recorder = mediaRecorder
        output = file
        return file
    }

    fun stop(): File? {
        val file = output
        return try {
            val elapsed = System.currentTimeMillis() - startTimestampMs
            if (elapsed < 500) {
                try { Thread.sleep(500 - elapsed) } catch (_: Exception) {}
            }
            recorder?.stop()
            file
        } catch (_: RuntimeException) {
            file?.delete()
            null
        } finally {
            recorder?.release()
            recorder = null
            output = null
        }
    }

    fun cancel() {
        try { recorder?.stop() } catch (_: RuntimeException) { }
        recorder?.release()
        recorder = null
        output?.delete()
        output = null
    }
}
