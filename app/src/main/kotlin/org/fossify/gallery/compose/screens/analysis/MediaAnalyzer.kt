package org.fossify.gallery.compose.screens.analysis

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.fossify.gallery.helpers.MEDIA_EXTENSIONS
import java.io.File

class MediaAnalyzer(private val context: Context) {

    fun analyzeFolder(rootPath: String): Flow<AnalysisProgress> = flow {
        val allFiles = mutableListOf<String>()
        collectMediaFiles(rootPath, allFiles)
        val total = allFiles.size
        if (total == 0) {
            emit(AnalysisProgress.Done(emptyList(), rootPath, 0))
            return@flow
        }

        val results = mutableListOf<AnalysisResult>()
        for ((i, path) in allFiles.withIndex()) {
            val progress = ((i + 1) * 100) / total
            emit(AnalysisProgress.Scanning(progress, rootPath, i + 1, total))
            val result = AnalysisCriteria.analyze(path, context)
            if (result != null && result.score > 0) {
                results.add(result)
                emit(AnalysisProgress.Found(results.toList(), result))
            }
        }
        emit(AnalysisProgress.Done(results.sortedByDescending { it.wastedBytes }, rootPath, total))
    }.flowOn(Dispatchers.IO)

    private fun collectMediaFiles(rootPath: String, result: MutableList<String>) {
        if (rootPath.startsWith("content://")) {
            collectMediaFilesSaf(Uri.parse(rootPath), result)
        } else {
            collectMediaFilesDirect(rootPath, result)
        }
    }

    private fun collectMediaFilesDirect(rootPath: String, result: MutableList<String>) {
        // Resolve via MediaStore (filesystem directory listing is blocked under scoped storage).
        for (path in MediaStoreEnumerator.mediaPathsUnder(context, rootPath)) {
            if (path.substringAfterLast('/').startsWith(".")) continue
            val ext = path.substringAfterLast('.', "").lowercase()
            if (ext in MEDIA_EXTENSIONS || ext in AnalysisCriteria.VIDEO_EXTS || ext in AnalysisCriteria.IMAGE_EXTS) {
                result.add(path)
            }
        }
    }

    private fun collectMediaFilesSaf(rootUri: Uri, result: MutableList<String>) {
        try {
            val doc = DocumentFile.fromTreeUri(context, rootUri) ?: return
            for (child in doc.listFiles()) {
                if (child.name?.startsWith(".") == true) continue
                if (child.isDirectory) {
                    collectMediaFilesSaf(child.uri, result)
                } else {
                    val name = child.name ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in MEDIA_EXTENSIONS || ext in AnalysisCriteria.VIDEO_EXTS || ext in AnalysisCriteria.IMAGE_EXTS) {
                        result.add(child.uri.toString())
                    }
                }
            }
        } catch (_: Exception) { }
    }
}

sealed class AnalysisProgress {
    data class Scanning(val percent: Int, val folder: String, val scanned: Int, val total: Int) : AnalysisProgress()
    data class Found(val allResults: List<AnalysisResult>, val latest: AnalysisResult) : AnalysisProgress()
    data class Done(val results: List<AnalysisResult>, val folder: String, val totalScanned: Int) : AnalysisProgress()
}
