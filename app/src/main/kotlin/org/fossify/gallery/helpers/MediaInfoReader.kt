package org.fossify.gallery.helpers

import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import com.drew.imaging.ImageMetadataReader
import com.drew.lang.KeyValuePair
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifDirectoryBase
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.iptc.IptcDirectory
import com.drew.metadata.jpeg.JpegCommentDirectory
import com.drew.metadata.mov.metadata.QuickTimeMetadataDirectory
import com.drew.metadata.png.PngDirectory
import com.drew.metadata.xmp.XmpDirectory
import com.drew.metadata.xmp.XmpReader
import org.fossify.gallery.extensions.getDecodedImageDimensions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

data class DescriptiveMetadata(
    val title: String? = null,
    val description: String? = null,
    val comment: String? = null,
) {
    val isEmpty: Boolean
        get() = title == null && description == null && comment == null
}

data class MediaInfoData(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val modifiedMillis: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationMillis: Long? = null,
    val dateTakenMillis: Long? = null,
    val camera: String? = null,
    val iso: String? = null,
    val focalLength: String? = null,
    val aperture: String? = null,
    val exposure: String? = null,
    val descriptive: DescriptiveMetadata = DescriptiveMetadata(),
    val metadataReadFailed: Boolean = false,
)

/**
 * Reads the small, curated subset of file metadata that is useful in the media Info dialog.
 *
 * Keep this outside Compose: metadata parsing and video probing are blocking file operations, while
 * the dialog should only render an immutable result. Besides embedded XMP/IPTC/EXIF/container
 * fields, the reader also honours the two XMP sidecar names already supported by [XmpWriter].
 */
object MediaInfoReader {
    private const val MAX_SIDECAR_BYTES = 8L * 1024L * 1024L

    fun read(path: String): MediaInfoData {
        require(path.isNotBlank()) { "Path is empty" }
        val file = File(path)
        require(file.exists()) { "File does not exist" }

        val metadataResult = runCatching { ImageMetadataReader.readMetadata(file) }
        val metadata = metadataResult.getOrNull() ?: Metadata()
        val sidecarRead = appendSidecarMetadata(file, metadata)

        val isVideo = file.extension.lowercase(Locale.ROOT) in VIDEO_EXTENSIONS
        val retrieverData = if (isVideo) readVideoMetadata(path) else VideoMetadata()
        val androidExifResult = if (isVideo) null else runCatching { ExifInterface(path) }
        val androidExif = androidExifResult?.getOrNull()

        val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
        val pngText = readPngText(metadata)
        val quickTime = metadata.getFirstDirectoryOfType(QuickTimeMetadataDirectory::class.java)

        val xmp = linkedMapOf<String, String>()
        metadata.getDirectoriesOfType(XmpDirectory::class.java).forEach { directory ->
            directory.xmpProperties.forEach { (key, value) -> xmp.putIfAbsent(key, value) }
        }

        val iptc = metadata.getFirstDirectoryOfType(IptcDirectory::class.java)
        val sources = DescriptiveMetadataSources(
            xmp = xmp,
            iptcTitle = iptc?.getString(IptcDirectory.TAG_OBJECT_NAME)
                ?: iptc?.getString(IptcDirectory.TAG_HEADLINE),
            iptcDescription = iptc?.getString(IptcDirectory.TAG_CAPTION)
                ?: iptc?.getString(IptcDirectory.TAG_LOCAL_CAPTION),
            iptcComment = iptc?.getString(IptcDirectory.TAG_SPECIAL_INSTRUCTIONS),
            exifTitle = ifd0?.readWindowsText(ExifDirectoryBase.TAG_WIN_TITLE),
            exifDescription = ifd0?.getString(ExifDirectoryBase.TAG_IMAGE_DESCRIPTION)
                ?: androidExif?.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION),
            exifComment = subIfd?.getDescription(ExifDirectoryBase.TAG_USER_COMMENT)
                ?: androidExif?.getAttribute(ExifInterface.TAG_USER_COMMENT)
                ?: ifd0?.readWindowsText(ExifDirectoryBase.TAG_WIN_COMMENT),
            jpegComment = metadata.getFirstDirectoryOfType(JpegCommentDirectory::class.java)
                ?.getString(JpegCommentDirectory.TAG_COMMENT),
            containerTitle = quickTime?.getString(QuickTimeMetadataDirectory.TAG_TITLE)
                ?: retrieverData.title
                ?: findGenericText(metadata, setOf("title", "object name", "headline")),
            containerDescription = quickTime?.getString(QuickTimeMetadataDirectory.TAG_DESCRIPTION)
                ?: findGenericText(metadata, setOf("description", "caption", "image description")),
            containerComment = quickTime?.getString(QuickTimeMetadataDirectory.TAG_COMMENT)
                ?: findGenericText(metadata, setOf("comment", "user comment", "instructions")),
            pngText = pngText,
        )

        val camera = listOfNotNull(
            cleanMetadataValue(ifd0?.getString(ExifDirectoryBase.TAG_MAKE)
                ?: androidExif?.getAttribute(ExifInterface.TAG_MAKE)),
            cleanMetadataValue(ifd0?.getString(ExifDirectoryBase.TAG_MODEL)
                ?: androidExif?.getAttribute(ExifInterface.TAG_MODEL)),
        ).distinct().joinToString(" ").ifBlank { null }

        val decodedDimensions = if (!isVideo) path.getDecodedImageDimensions() else null
        val dateTaken = subIfd?.dateOriginal?.time
            ?: parseExifDateTimeMillis(androidExif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))

        return MediaInfoData(
            name = file.name,
            path = file.absolutePath,
            sizeBytes = file.length(),
            modifiedMillis = file.lastModified(),
            width = retrieverData.width ?: decodedDimensions?.first,
            height = retrieverData.height ?: decodedDimensions?.second,
            durationMillis = retrieverData.durationMillis,
            dateTakenMillis = dateTaken,
            camera = camera,
            iso = cleanMetadataValue(
                subIfd?.getDescription(ExifDirectoryBase.TAG_ISO_EQUIVALENT)
                    ?: androidExif?.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
            ),
            focalLength = cleanMetadataValue(
                subIfd?.getDescription(ExifDirectoryBase.TAG_FOCAL_LENGTH)
                    ?: androidExif?.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
            ),
            aperture = cleanMetadataValue(
                subIfd?.getDescription(ExifDirectoryBase.TAG_FNUMBER)
                    ?: androidExif?.getAttribute(ExifInterface.TAG_F_NUMBER),
            ),
            exposure = cleanMetadataValue(
                subIfd?.getDescription(ExifDirectoryBase.TAG_EXPOSURE_TIME)
                    ?: androidExif?.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
            ),
            descriptive = resolveDescriptiveMetadata(sources),
            metadataReadFailed = metadataResult.isFailure && !sidecarRead && androidExifResult?.isSuccess != true,
        )
    }

    private fun appendSidecarMetadata(file: File, metadata: Metadata): Boolean {
        val candidates = listOf(
            File("${file.absolutePath}.xmp"),
            File(file.parentFile, "${file.nameWithoutExtension}.xmp"),
        ).distinctBy(File::getAbsolutePath)

        var readAny = false
        candidates.forEach { sidecar ->
            if (!sidecar.isFile || sidecar.length() > MAX_SIDECAR_BYTES) return@forEach
            val parsed = runCatching {
                XmpReader().extract(sidecar.readText(), metadata)
            }.isSuccess
            readAny = readAny || parsed
        }
        return readAny
    }

    private fun readVideoMetadata(path: String): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
            val swapDimensions = rotation % 180 != 0
            VideoMetadata(
                width = if (swapDimensions) rawHeight else rawWidth,
                height = if (swapDimensions) rawWidth else rawHeight,
                durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull(),
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
            )
        } catch (_: Exception) {
            VideoMetadata()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readPngText(metadata: Metadata): Map<String, String> {
        val values = linkedMapOf<String, String>()
        metadata.getDirectoriesOfType(PngDirectory::class.java).forEach { directory ->
            val entries = directory.getObject(PngDirectory.TAG_TEXTUAL_DATA) as? Iterable<*> ?: return@forEach
            entries.filterIsInstance<KeyValuePair>().forEach { entry ->
                values.putIfAbsent(entry.key, entry.value.toString())
            }
        }
        return values
    }

    private fun findGenericText(metadata: Metadata, names: Set<String>): String? {
        metadata.directories.forEach { directory ->
            directory.tags.forEach { tag ->
                if (tag.tagName.lowercase(Locale.ROOT) in names) {
                    cleanMetadataValue(tag.description)?.let { return it }
                }
            }
        }
        return null
    }

    private fun ExifIFD0Directory.readWindowsText(tag: Int): String? {
        getByteArray(tag)?.let { bytes ->
            cleanMetadataValue(runCatching { String(bytes, Charsets.UTF_16LE) }.getOrNull())?.let { return it }
        }
        return decodeNumericByteString(getString(tag))
    }

    private data class VideoMetadata(
        val width: Int? = null,
        val height: Int? = null,
        val durationMillis: Long? = null,
        val title: String? = null,
    )
}

/**
 * Parses the public EXIF DateTimeOriginal attribute.  AndroidX exposes a convenient
 * `dateTimeOriginal` property too, but marks it as restricted API, so using the raw attribute
 * avoids a future library update breaking the metadata sheet.
 */
internal fun parseExifDateTimeMillis(value: String?): Long? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val formats = listOf("yyyy:MM:dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss")
    return formats.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(raw)?.time
        }.getOrNull()
    }
}

internal data class DescriptiveMetadataSources(
    val xmp: Map<String, String> = emptyMap(),
    val iptcTitle: String? = null,
    val iptcDescription: String? = null,
    val iptcComment: String? = null,
    val exifTitle: String? = null,
    val exifDescription: String? = null,
    val exifComment: String? = null,
    val jpegComment: String? = null,
    val containerTitle: String? = null,
    val containerDescription: String? = null,
    val containerComment: String? = null,
    val pngText: Map<String, String> = emptyMap(),
)

internal fun resolveDescriptiveMetadata(sources: DescriptiveMetadataSources): DescriptiveMetadata {
    val title = firstMetadataValue(
        findXmpValue(sources.xmp, "dc:title", "photoshop:headline", "xmp:title"),
        sources.iptcTitle,
        sources.exifTitle,
        findMapValue(sources.pngText, "title", "headline"),
        sources.containerTitle,
    )
    val description = firstMetadataValue(
        findXmpValue(sources.xmp, "dc:description", "photoshop:caption", "xmp:description"),
        sources.iptcDescription,
        sources.exifDescription,
        findMapValue(sources.pngText, "description", "caption", "prompt", "parameters"),
        sources.containerDescription,
    )
    var comment = firstMetadataValue(
        findXmpValue(sources.xmp, "exif:usercomment", "photoshop:instructions", "xmp:comment"),
        sources.exifComment,
        sources.jpegComment,
        sources.iptcComment,
        findMapValue(sources.pngText, "comment", "usercomment", "instructions"),
        sources.containerComment,
    )

    // Exporters commonly mirror the same prompt into XMP, EXIF and a PNG text chunk. Showing the
    // identical wall of text twice makes the dialog look broken, so keep the first semantic field.
    if (comment != null && (metadataValuesEqual(comment, description) || metadataValuesEqual(comment, title))) {
        comment = null
    }
    return DescriptiveMetadata(title = title, description = description, comment = comment)
}

internal fun findXmpValue(properties: Map<String, String>, vararg aliases: String): String? {
    val normalizedAliases = aliases.map { normalizeMetadataKey(it) }
    properties.entries.forEach { (rawKey, rawValue) ->
        val key = normalizeMetadataKey(rawKey)
        if (key.endsWith("/?xml:lang") || key.endsWith("/?rdf:resource")) return@forEach
        if (normalizedAliases.any { alias -> key == alias || key.endsWith("/$alias") }) {
            cleanMetadataValue(rawValue)?.let { return it }
        }
    }
    return null
}

private fun findMapValue(values: Map<String, String>, vararg aliases: String): String? {
    aliases.forEach { alias ->
        values.entries.firstOrNull { normalizeMetadataKey(it.key) == normalizeMetadataKey(alias) }
            ?.value
            ?.let(::cleanMetadataValue)
            ?.let { return it }
    }
    return null
}

private fun normalizeMetadataKey(value: String): String = value
    .trim()
    .trimStart('/')
    .lowercase(Locale.ROOT)
    .replace(Regex("\\[\\d+]"), "")

private fun firstMetadataValue(vararg values: String?): String? =
    values.firstNotNullOfOrNull(::cleanMetadataValue)

internal fun cleanMetadataValue(raw: String?): String? {
    if (raw == null) return null
    var value = raw.trim()
    listOf("ASCII", "UNICODE", "JIS").firstOrNull { prefix ->
        value.startsWith(prefix, ignoreCase = true) &&
            value.drop(prefix.length).take(4).any { it == 0.toChar() }
    }?.let { prefix -> value = value.drop(prefix.length) }

    value = value.replace(0.toChar().toString(), "").trim()
    return value.takeIf(String::isNotBlank)
}

private fun decodeNumericByteString(raw: String?): String? {
    val value = raw ?: return null
    if (!Regex("^\\s*\\d+(\\s+\\d+)+\\s*$").matches(value)) return cleanMetadataValue(value)
    return runCatching {
        val bytes = value.trim().split(Regex("\\s+")).map { it.toInt().toByte() }.toByteArray()
        cleanMetadataValue(String(bytes, Charsets.UTF_16LE))
    }.getOrNull()
}

private fun metadataValuesEqual(first: String, second: String?): Boolean = second != null &&
    first.replace(Regex("\\s+"), " ").trim().equals(
        second.replace(Regex("\\s+"), " ").trim(),
        ignoreCase = true,
    )
