package com.example.player

import com.example.data.model.Track
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Generates real playable stereo 16-bit PCM WAV files for tracks in the Music Form Open Catalog
 * so Media3 ExoPlayer streams genuine audio frames with accurate seeking, duration, and background playback.
 * Streams PCM chunks directly to disk without allocating large contiguous heap arrays.
 */
object SynthesizedAudioGenerator {

    const val SAMPLE_RATE = 22050
    const val NUM_CHANNELS = 2
    const val BITS_PER_SAMPLE = 16
    private const val CHUNK_SAMPLES = 1024 // 1024 stereo frames = 4096 bytes per chunk

    fun ensurePlayableAudioFile(cacheDir: File, track: Track): File {
        val audioDir = File(cacheDir, "music_form_streams").apply { mkdirs() }
        val outFile = File(audioDir, "${track.id}.wav")
        if (outFile.exists() && validateWavFile(outFile)) {
            return outFile
        }

        val durationSeconds = ((track.durationMs / 1000L).toInt()).coerceIn(8, 60)
        val totalSamplesPerChannel = SAMPLE_RATE * durationSeconds
        val dataSize = totalSamplesPerChannel * NUM_CHANNELS * (BITS_PER_SAMPLE / 8)

        val baseFreq = track.synthFreqHz.coerceIn(110f, 440f).toDouble()
        val bpm = track.bpm.coerceIn(60, 180)
        val beatDurationSec = 60.0 / bpm
        val barDurationSec = beatDurationSec * 4.0
        val chords = if (track.chordProgression.isNotEmpty()) track.chordProgression else listOf(0, 3, 7, 10)

        val tempFile = File(audioDir, "${track.id}.wav.tmp")
        val chunkBuffer = ByteArray(CHUNK_SAMPLES * NUM_CHANNELS * (BITS_PER_SAMPLE / 8))

        BufferedOutputStream(FileOutputStream(tempFile), 16 * 1024).use { bos ->
            bos.write(createWavHeader(dataSize))

            var sampleIndex = 0
            while (sampleIndex < totalSamplesPerChannel) {
                val framesInBatch = minOf(CHUNK_SAMPLES, totalSamplesPerChannel - sampleIndex)
                var byteIndex = 0

                for (f in 0 until framesInBatch) {
                    val i = sampleIndex + f
                    val t = i.toDouble() / SAMPLE_RATE
                    val barIndex = (t / barDurationSec).toInt()
                    val semitoneOffset = chords[barIndex % chords.size]
                    val rootFreq = baseFreq * 2.0.pow(semitoneOffset / 12.0)
                    val fifthFreq = rootFreq * 1.4983
                    val octaveFreq = rootFreq * 2.0

                    val beatPos = (t % beatDurationSec) / beatDurationSec
                    val sixteenthPos = ((t * 4.0) % beatDurationSec) / beatDurationSec
                    val arpNoteIdx = ((t / (beatDurationSec / 4.0)).toInt()) % chords.size
                    val arpFreq = rootFreq * 2.0.pow((chords[arpNoteIdx] + 12) / 12.0)

                    val arpEnv = exp(-4.5 * sixteenthPos)
                    val kickEnv = if (track.energy > 0.35f) exp(-18.0 * beatPos) else 0.0
                    val kickFreq = 52.0 + 95.0 * exp(-35.0 * beatPos)

                    val padLeft = 0.22 * sin(2.0 * PI * rootFreq * t) +
                        0.14 * sin(2.0 * PI * fifthFreq * t + 0.4)
                    val padRight = 0.22 * sin(2.0 * PI * rootFreq * t + 0.25) +
                        0.14 * sin(2.0 * PI * octaveFreq * t)

                    val subBass = 0.24 * sin(2.0 * PI * (rootFreq * 0.5) * t)
                    val arpVoice = 0.22 * arpEnv * sin(2.0 * PI * arpFreq * t)
                    val kickVoice = 0.26 * kickEnv * sin(2.0 * PI * kickFreq * t)

                    val edgeEnv = when {
                        t < 0.4 -> t / 0.4
                        t > durationSeconds - 0.8 -> ((durationSeconds - t) / 0.8).coerceAtLeast(0.0)
                        else -> 1.0
                    }

                    val leftSample = ((padLeft + subBass + arpVoice * 0.9 + kickVoice) * edgeEnv)
                        .coerceIn(-0.95, 0.95)
                    val rightSample = ((padRight + subBass + arpVoice * 1.1 + kickVoice) * edgeEnv)
                        .coerceIn(-0.95, 0.95)

                    val leftShort = (leftSample * Short.MAX_VALUE).toInt().toShort()
                    val rightShort = (rightSample * Short.MAX_VALUE).toInt().toShort()

                    chunkBuffer[byteIndex++] = (leftShort.toInt() and 0xFF).toByte()
                    chunkBuffer[byteIndex++] = ((leftShort.toInt() shr 8) and 0xFF).toByte()
                    chunkBuffer[byteIndex++] = (rightShort.toInt() and 0xFF).toByte()
                    chunkBuffer[byteIndex++] = ((rightShort.toInt() shr 8) and 0xFF).toByte()
                }

                bos.write(chunkBuffer, 0, byteIndex)
                sampleIndex += framesInBatch
            }
            bos.flush()
        }

        if (outFile.exists()) {
            outFile.delete()
        }
        if (!tempFile.renameTo(outFile)) {
            tempFile.copyTo(outFile, overwrite = true)
            tempFile.delete()
        }
        return outFile
    }

    /**
     * Validates that a WAV file has a structurally sound 44-byte RIFF/WAVE PCM header
     * and non-empty audio payload matching its declared subchunk size.
     */
    fun validateWavFile(file: File): Boolean {
        if (!file.exists() || file.length() <= 44L) return false
        return try {
            val headerBytes = ByteArray(44)
            FileInputStream(file).use { fis ->
                if (fis.read(headerBytes) != 44) return false
            }
            val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val riff = ByteArray(4).also { buf.get(it) }.toString(Charsets.US_ASCII)
            val chunkSize = buf.int
            val wave = ByteArray(4).also { buf.get(it) }.toString(Charsets.US_ASCII)
            val fmt = ByteArray(4).also { buf.get(it) }.toString(Charsets.US_ASCII)
            val subchunk1Size = buf.int
            val audioFormat = buf.short.toInt()
            val numChannels = buf.short.toInt()
            val sampleRate = buf.int
            val byteRate = buf.int
            val blockAlign = buf.short.toInt()
            val bitsPerSample = buf.short.toInt()
            val dataId = ByteArray(4).also { buf.get(it) }.toString(Charsets.US_ASCII)
            val dataSize = buf.int

            riff == "RIFF" &&
                wave == "WAVE" &&
                fmt == "fmt " &&
                subchunk1Size == 16 &&
                audioFormat == 1 &&
                numChannels == NUM_CHANNELS &&
                sampleRate == SAMPLE_RATE &&
                byteRate == SAMPLE_RATE * NUM_CHANNELS * (BITS_PER_SAMPLE / 8) &&
                blockAlign == NUM_CHANNELS * (BITS_PER_SAMPLE / 8) &&
                bitsPerSample == BITS_PER_SAMPLE &&
                dataId == "data" &&
                dataSize > 0 &&
                chunkSize == 36 + dataSize &&
                file.length() == (44L + dataSize.toLong())
        } catch (_: Exception) {
            false
        }
    }

    fun clearCachedStreams(cacheDir: File?): Int {
        if (cacheDir == null) return 0
        val audioDir = File(cacheDir, "music_form_streams")
        if (!audioDir.exists()) return 0
        var deletedCount = 0
        audioDir.listFiles()?.forEach { f ->
            if (f.delete()) deletedCount++
        }
        return deletedCount
    }

    internal fun createWavHeader(pcmDataSize: Int): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        val byteRate = SAMPLE_RATE * NUM_CHANNELS * (BITS_PER_SAMPLE / 8)
        val blockAlign = (NUM_CHANNELS * (BITS_PER_SAMPLE / 8)).toShort()

        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcmDataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // Subchunk1Size for PCM
        header.putShort(1) // AudioFormat = 1 (PCM)
        header.putShort(NUM_CHANNELS.toShort())
        header.putInt(SAMPLE_RATE)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(BITS_PER_SAMPLE.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmDataSize)
        return header.array()
    }
}
