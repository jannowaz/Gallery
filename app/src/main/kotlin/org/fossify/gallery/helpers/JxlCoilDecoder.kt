package org.fossify.gallery.helpers

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Dimension
import com.awxkee.jxlcoder.JxlCoder
import com.awxkee.jxlcoder.ScaleMode
import java.io.IOException

/**
 * Coil [Decoder] for JPEG XL (`.jxl`) images backed by [JxlCoder].
 *
 * The bundled `jxl-integration` library only hooks into Glide, so without this decoder Coil's
 * Compose grid thumbnails and [org.fossify.gallery.activities.ComposeExternalViewerActivity] fail
 * to render `.jxl` files. Detects both naked JXL codestreams (`0xFF 0x0A`) and ISOBMFF containers
 * via a non-destructive [okio.BufferedSource.peek] so non-JXL images pass through untouched.
 */
class JxlCoilDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val bytes = source.source().use { it.readByteArray() }
        if (bytes.isEmpty()) throw IOException("Empty JXL image stream")

        val targetWidth = (options.size.width as? Dimension.Pixels)?.px ?: -1
        val targetHeight = (options.size.height as? Dimension.Pixels)?.px ?: -1

        val bitmap = if (targetWidth > 0 || targetHeight > 0) {
            val w = if (targetWidth > 0) targetWidth else targetHeight
            val h = if (targetHeight > 0) targetHeight else targetWidth
            JxlCoder.decodeSampled(
                byteArray = bytes,
                width = w,
                height = h,
                scaleMode = ScaleMode.FIT,
            )
        } else {
            JxlCoder.decode(bytes)
        }

        return DecodeResult(
            image = bitmap.asImage(),
            isSampled = targetWidth > 0 || targetHeight > 0,
        )
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            if (result.mimeType.equals("image/jxl", ignoreCase = true)) {
                return JxlCoilDecoder(result.source, options)
            }
            val peek = result.source.source().peek()
            val header = ByteArray(ISOBMFF_SIGNATURE.size)
            val read = peek.read(header)
            if (read < 2) return null
            val slice = if (read == header.size) header else header.copyOf(read)
            return if (isJxlMagicBytes(slice)) JxlCoilDecoder(result.source, options) else null
        }

        override fun equals(other: Any?): Boolean = other is Factory
        override fun hashCode(): Int = javaClass.hashCode()
    }

    companion object {
        // Naked JXL codestream signature: 0xFF 0x0A
        private val NAKED_SIGNATURE = byteArrayOf(0xFF.toByte(), 0x0A.toByte())

        // ISOBMFF JXL container box: 00 00 00 0C 4A 58 4C 20 0D 0A 87 0A
        private val ISOBMFF_SIGNATURE = byteArrayOf(
            0x00, 0x00, 0x00, 0x0C,
            0x4A, 0x58, 0x4C, 0x20,
            0x0D, 0x0A, 0x87.toByte(), 0x0A,
        )

        internal fun isJxlMagicBytes(header: ByteArray): Boolean {
            if (header.size >= NAKED_SIGNATURE.size &&
                header[0] == NAKED_SIGNATURE[0] &&
                header[1] == NAKED_SIGNATURE[1]
            ) {
                return true
            }
            if (header.size >= ISOBMFF_SIGNATURE.size) {
                for (i in ISOBMFF_SIGNATURE.indices) {
                    if (header[i] != ISOBMFF_SIGNATURE[i]) return false
                }
                return true
            }
            return false
        }
    }
}
