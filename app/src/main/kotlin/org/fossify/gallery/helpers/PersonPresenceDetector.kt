package org.fossify.gallery.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import org.fossify.gallery.models.PersonPresence
import kotlin.math.max

/**
 * A deliberately conservative, completely local person-presence classifier.
 *
 * A face immediately counts as a person. When no face is visible, the bundled selfie segmenter
 * supplies a human silhouette signal for back/side views. Ambiguous masks deliberately stay
 * [PersonPresence.UNCERTAIN], so the cleanup inbox never turns a weak prediction into a deletion
 * suggestion.
 */
class PersonPresenceDetector : AutoCloseable {
    private val faceDetector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build(),
    )
    private val segmenter: Segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build(),
    )

    data class Result(val presence: PersonPresence, val coverage: Float)

    fun analyze(path: String): Result {
        val bitmap = decodeForAnalysis(path) ?: return Result(PersonPresence.UNCERTAIN, 0f)
        try {
            val image = InputImage.fromBitmap(bitmap, readRotation(path))
            if (Tasks.await(faceDetector.process(image)).isNotEmpty()) {
                return Result(PersonPresence.PERSON, 0f)
            }
            return classifyMask(Tasks.await(segmenter.process(image)))
        } catch (e: Exception) {
            android.util.Log.w("PersonPresence", "Could not classify $path", e)
            return Result(PersonPresence.UNCERTAIN, 0f)
        } finally {
            bitmap.recycle()
        }
    }

    private fun classifyMask(mask: SegmentationMask): Result {
        val values = mask.buffer.duplicate().order(java.nio.ByteOrder.nativeOrder())
        values.rewind()
        var samples = 0
        var foreground = 0
        var peak = 0f
        // The raw mask is normally 256². Looking at every fourth sample preserves a stable
        // coverage estimate while keeping the scan's CPU cost bounded on very large folders.
        var index = 0
        while (values.remaining() >= Float.SIZE_BYTES) {
            val confidence = values.float.coerceIn(0f, 1f)
            if (index % 4 == 0) {
                samples++
                if (confidence >= HUMAN_PIXEL_THRESHOLD) foreground++
                peak = max(peak, confidence)
            }
            index++
        }
        if (samples == 0) return Result(PersonPresence.UNCERTAIN, 0f)
        val coverage = foreground.toFloat() / samples
        return when {
            coverage >= PERSON_COVERAGE_THRESHOLD && peak >= PERSON_PEAK_THRESHOLD -> Result(PersonPresence.PERSON, coverage)
            coverage <= NO_PERSON_COVERAGE_THRESHOLD && peak <= NO_PERSON_PEAK_THRESHOLD -> Result(PersonPresence.NO_PERSON, coverage)
            else -> Result(PersonPresence.UNCERTAIN, coverage)
        }
    }

    /** Decodes a bounded bitmap instead of ever loading an original 48 MP photo into the heap. */
    private fun decodeForAnalysis(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > MAX_ANALYSIS_EDGE) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }

    private fun readRotation(path: String): Int = runCatching {
        when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    override fun close() {
        faceDetector.close()
        segmenter.close()
    }

    companion object {
        private const val MAX_ANALYSIS_EDGE = 1024
        private const val HUMAN_PIXEL_THRESHOLD = 0.70f
        private const val PERSON_COVERAGE_THRESHOLD = 0.012f
        private const val PERSON_PEAK_THRESHOLD = 0.86f
        private const val NO_PERSON_COVERAGE_THRESHOLD = 0.0008f
        private const val NO_PERSON_PEAK_THRESHOLD = 0.16f
    }
}

/** Stable rescan key that changes whenever the actual image bytes are likely to have changed. */
fun personScanSignature(path: String, modified: Long, size: Long): String = "$path|$modified|$size"
