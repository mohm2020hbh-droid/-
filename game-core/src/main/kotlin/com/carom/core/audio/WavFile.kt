package com.carom.core.audio

/**
 * Reads a WAV file: uncompressed PCM, 8, 16 or 24 bits, any number of channels (mixed down to one) and any
 * sample rate (kept as it is, so the sound plays exactly as it was recorded). Chunks other than `fmt ` and `data`
 * are skipped.
 */
object WavFile {

    /** Mono 16-bit samples and the rate they were recorded at. */
    class Pcm(val samples: ShortArray, val sampleRate: Int) {
        val seconds: Double get() = samples.size.toDouble() / sampleRate
    }

    class FormatException(message: String) : Exception(message)

    fun decode(bytes: ByteArray): Pcm {
        if (bytes.size < 12 || ascii(bytes, 0) != "RIFF" || ascii(bytes, 8) != "WAVE") throw FormatException("not a WAV file")
        var channels = 0
        var rate = 0
        var bits = 0
        var at = 12
        while (at + 8 <= bytes.size) {
            val id = ascii(bytes, at)
            val size = int32(bytes, at + 4)
            val body = at + 8
            if (size < 0 || body + size > bytes.size) throw FormatException("chunk '$id' runs past the end of the file")
            when (id) {
                "fmt " -> {
                    val format = int16(bytes, body)
                    channels = int16(bytes, body + 2)
                    rate = int32(bytes, body + 4)
                    bits = int16(bytes, body + 14)
                    // 0xFFFE (extensible) carries plain PCM too when its sub-format is PCM; only integer PCM is read.
                    if (format != 1 && format != 0xFFFE) throw FormatException("only PCM WAV files are supported (format $format)")
                    if (bits != 8 && bits != 16 && bits != 24) throw FormatException("$bits-bit samples are not supported")
                    if (channels < 1 || rate <= 0) throw FormatException("bad format chunk")
                }
                "data" -> {
                    if (channels == 0) throw FormatException("'data' comes before 'fmt '")
                    val width = bits / 8
                    val frames = size / (width * channels)
                    val out = ShortArray(frames) { f ->
                        var sum = 0
                        for (c in 0 until channels) sum += sample(bytes, body + (f * channels + c) * width, bits)
                        (sum / channels).toShort()
                    }
                    return Pcm(out, rate)
                }
            }
            at = body + size + (size and 1) // chunks are padded to an even length
        }
        throw FormatException("no 'data' chunk")
    }

    /** One sample as a 16-bit value. */
    private fun sample(b: ByteArray, at: Int, bits: Int): Int = when (bits) {
        8 -> ((b[at].toInt() and 0xFF) - 128) shl 8
        16 -> int16(b, at).toShort().toInt()
        else -> ((b[at + 2].toInt() shl 16) or ((b[at + 1].toInt() and 0xFF) shl 8) or (b[at].toInt() and 0xFF)) shr 8
    }

    private fun ascii(b: ByteArray, at: Int) = String(CharArray(4) { (b[at + it].toInt() and 0xFF).toChar() })
    private fun int16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
    private fun int32(b: ByteArray, at: Int) = int16(b, at) or (int16(b, at + 2) shl 16)
}
