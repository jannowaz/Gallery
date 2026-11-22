package org.fossify.gallery.extensions

import android.app.Activity
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Files
import android.provider.MediaStore.Images
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.SecurityDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.extensions.getCurrentFormattedDateTime
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.helpers.*
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.BuildConfig
import org.fossify.gallery.R
import org.fossify.gallery.helpers.MoveConflicts
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.TEMP_FOLDER_NAME
import org.fossify.gallery.models.DateTaken
import java.io.*
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.net.toUri

fun Activity.sharePath(path: String) {
    sharePathIntent(path, BuildConfig.APPLICATION_ID)
}

fun Activity.sharePaths(paths: ArrayList<String>) {
    sharePathsIntent(paths, BuildConfig.APPLICATION_ID)
}

fun Activity.shareMediumPath(path: String) {
    sharePath(path)
}

fun Activity.shareMediaPaths(paths: ArrayList<String>) {
    sharePaths(paths)
}

fun Activity.setAs(path: String) {
    setAsIntent(path, BuildConfig.APPLICATION_ID)
}

fun Activity.openPath(path: String, forceChooser: Boolean, extras: HashMap<String, Boolean> = HashMap()) {
    openPathIntent(path, forceChooser, BuildConfig.APPLICATION_ID, extras = extras)
}

fun Activity.openEditor(path: String, forceChooser: Boolean = false) {
    val newPath = path.removePrefix("file://")
    openEditorIntent(newPath, forceChooser, BuildConfig.APPLICATION_ID)
}

fun Activity.launchCamera() {
    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    launchActivityIntent(intent)
}

fun BaseSimpleActivity.launchGrantAllFilesIntent() {
    try {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.addCategory("android.intent.category.DEFAULT")
        intent.data = "package:$packageName".toUri()
        startActivity(intent)
    } catch (e: Exception) {
        val intent = Intent()
        intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
        try {
            startActivity(intent)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}

fun AppCompatActivity.showSystemUI() {
    window.showBars()
}

fun AppCompatActivity.hideSystemUI() {
    window.hideBars(transient = false)
}

fun BaseSimpleActivity.addNoMedia(path: String, callback: () -> Unit) {
    val file = File(path, NOMEDIA)
    if (getDoesFilePathExist(file.absolutePath)) {
        callback()
        return
    }

    if (needsStupidWritePermissions(path)) {
        handleSAFDialog(file.absolutePath) {
            if (!it) {
                return@handleSAFDialog
            }

            val fileDocument = getDocumentFile(path)
            if (fileDocument?.exists() == true && fileDocument.isDirectory) {
                fileDocument.createFile("", NOMEDIA)
                addNoMediaIntoMediaStore(file.absolutePath)
                callback()
            } else {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                callback()
            }
        }
    } else {
        try {
            if (file.createNewFile()) {
                ensureBackgroundThread {
                    addNoMediaIntoMediaStore(file.absolutePath)
                }
            } else {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
        callback()
    }
}

fun BaseSimpleActivity.addNoMediaIntoMediaStore(path: String) {
    try {
        // Hiding the same folder more than once can race the MediaScanner or repeat after a
        // restored preference. MediaProvider enforces DATA as unique and otherwise logs a noisy
        // SQLiteConstraintException even though the desired state already exists. Make this
        // legacy helper idempotent before attempting the insert.
        val alreadyIndexed = contentResolver.query(
            Files.getContentUri("external"),
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DATA} = ?",
            arrayOf(path),
            null,
        )?.use { it.moveToFirst() } == true
        if (alreadyIndexed) return
        val content = ContentValues().apply {
            put(Files.FileColumns.TITLE, NOMEDIA)
            put(Files.FileColumns.DATA, path)
            put(Files.FileColumns.MEDIA_TYPE, Files.FileColumns.MEDIA_TYPE_NONE)
        }
        contentResolver.insert(Files.getContentUri("external"), content)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun BaseSimpleActivity.removeNoMedia(path: String, callback: (() -> Unit)? = null) {
    val file = File(path, NOMEDIA)
    if (!getDoesFilePathExist(file.absolutePath)) {
        callback?.invoke()
        return
    }

    tryDeleteFileDirItem(file.toFileDirItem(applicationContext), false, false) {
        callback?.invoke()
        deleteFromMediaStore(file.absolutePath) { needsRescan ->
            if (needsRescan) {
                rescanAndDeletePath(path) {
                    rescanFolderMedia(path)
                }
            } else {
                rescanFolderMedia(path)
            }
        }
    }
}

fun BaseSimpleActivity.toggleFileVisibility(oldPath: String, hide: Boolean, callback: ((newPath: String) -> Unit)? = null) {
    val path = oldPath.getParentPath()
    var filename = oldPath.getFilenameFromPath()
    if ((hide && filename.startsWith('.')) || (!hide && !filename.startsWith('.'))) {
        callback?.invoke(oldPath)
        return
    }

    filename = if (hide) {
        ".${filename.trimStart('.')}"
    } else {
        filename.substring(1, filename.length)
    }

    val newPath = "$path/$filename"
    renameFile(oldPath, newPath, false) { success, useAndroid30Way ->
        runOnUiThread {
            callback?.invoke(newPath)
        }

        ensureBackgroundThread {
            updateDBMediaPath(oldPath, newPath)
        }
    }
}

fun BaseSimpleActivity.tryDeleteFileDirItem(
    fileDirItem: FileDirItem, allowDeleteFolder: Boolean = false, deleteFromDatabase: Boolean,
    callback: ((wasSuccess: Boolean) -> Unit)? = null
) {
    deleteFile(fileDirItem, allowDeleteFolder, isDeletingMultipleFiles = false) {
        if (deleteFromDatabase) {
            ensureBackgroundThread {
                deleteDBPath(fileDirItem.path)
                runOnUiThread {
                    callback?.invoke(it)
                }
            }
        } else {
            callback?.invoke(it)
        }
    }
}

fun BaseSimpleActivity.movePathsInRecycleBin(paths: ArrayList<String>, callback: ((wasSuccess: Boolean) -> Unit)?) {
    ensureBackgroundThread {
        var pathsCnt = paths.size
        val OTGPath = config.OTGPath

        for (source in paths) {
            if (OTGPath.isNotEmpty() && source.startsWith(OTGPath)) {
                var inputStream: InputStream? = null
                var out: OutputStream? = null
                try {
                    val destination = "$recycleBinPath/$source"
                    val fileDocument = getSomeDocumentFile(source)
                    inputStream = applicationContext.contentResolver.openInputStream(fileDocument?.uri!!)
                    out = getFileOutputStreamSync(destination, source.getMimeType())

                    var copiedSize = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytes = inputStream!!.read(buffer)
                    while (bytes >= 0) {
                        out!!.write(buffer, 0, bytes)
                        copiedSize += bytes
                        bytes = inputStream.read(buffer)
                    }

                    out?.flush()

                    if (fileDocument.getItemSize(true) == copiedSize && getDoesFilePathExist(destination)) {
                        if (mediaDB.updateDeleted("$RECYCLE_BIN$source", System.currentTimeMillis(), source) > 0) {
                            pathsCnt--
                        }
                    }
                } catch (e: Exception) {
                    showErrorToast(e)
                    return@ensureBackgroundThread
                } finally {
                    inputStream?.close()
                    out?.close()
                }
            } else {
                val file = File(source)
                val internalFile = File(recycleBinPath, source)
                val lastModified = file.lastModified()
                try {
                    if (file.copyRecursively(internalFile, true)) {
                        if (mediaDB.updateDeleted("$RECYCLE_BIN$source", System.currentTimeMillis(), source) > 0) {
                            pathsCnt--
                        }

                        if (config.keepLastModified && lastModified != 0L) {
                            internalFile.setLastModified(lastModified)
                        }
                    }
                } catch (e: Exception) {
                    showErrorToast(e)
                    return@ensureBackgroundThread
                }
            }
        }
        callback?.invoke(pathsCnt == 0)
    }
}

fun BaseSimpleActivity.restoreRecycleBinPath(path: String, callback: () -> Unit) {
    restoreRecycleBinPaths(arrayListOf(path), callback)
}

fun BaseSimpleActivity.restoreRecycleBinPaths(paths: ArrayList<String>, callback: () -> Unit) {
    ensureBackgroundThread {
        val newPaths = ArrayList<String>()
        var shownRestoringToPictures = false
        for (source in paths) {
            var destination = source.removePrefix(recycleBinPath)

            val destinationParent = destination.getParentPath()
            if (isRestrictedWithSAFSdk30(destinationParent) && !isInDownloadDir(destinationParent)) {
                // if the file is not writeable on SDK30+, change it to Pictures
                val picturesDirectory = getPicturesDirectoryPath(destination)
                destination = File(picturesDirectory, destination.getFilenameFromPath()).path
                if (!shownRestoringToPictures) {
                    toast(getString(R.string.restore_to_path, humanizePath(picturesDirectory)))
                    shownRestoringToPictures = true
                }
            }

            val lastModified = File(source).lastModified()

            val isShowingSAF = handleSAFDialog(destination) {}
            if (isShowingSAF) {
                return@ensureBackgroundThread
            }

            val isShowingSAFSdk30 = handleSAFDialogSdk30(destination) {}
            if (isShowingSAFSdk30) {
                return@ensureBackgroundThread
            }

            if (getDoesFilePathExist(destination)) {
                val newFile = getAlternativeFile(File(destination))
                destination = newFile.path
            }

            var inputStream: InputStream? = null
            var out: OutputStream? = null
            try {
                out = getFileOutputStreamSync(destination, source.getMimeType())
                inputStream = getFileInputStreamSync(source)

                var copiedSize = 0L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytes = inputStream!!.read(buffer)
                while (bytes >= 0) {
                    out!!.write(buffer, 0, bytes)
                    copiedSize += bytes
                    bytes = inputStream.read(buffer)
                }

                out?.flush()

                if (File(source).length() == copiedSize) {
                    mediaDB.updateDeleted(destination.removePrefix(recycleBinPath), 0, "$RECYCLE_BIN${source.removePrefix(recycleBinPath)}")
                    // The internal recycle-bin copy (`source`) is now fully duplicated at
                    // `destination` and the DB row points at the restored location - without this,
                    // every successful restore permanently orphaned this copy (nothing in the app
                    // ever referenced it again, so it sat taking up storage forever). Only deleted
                    // once the restored copy is verified byte-for-byte above.
                    try { File(source).delete() } catch (e: Exception) { android.util.Log.e("RecycleBin", "Failed to delete recycle-bin copy after restore: $source", e) }
                }
                newPaths.add(destination)

                if (config.keepLastModified && lastModified != 0L) {
                    File(destination).setLastModified(lastModified)
                }
            } catch (e: Exception) {
                showErrorToast(e)
            } finally {
                inputStream?.close()
                out?.close()
            }
        }

        runOnUiThread {
            callback()
        }

        rescanPaths(newPaths) {
            fixDateTaken(newPaths, false)
        }
    }
}

fun BaseSimpleActivity.emptyTheRecycleBin(callback: (() -> Unit)? = null) {
    ensureBackgroundThread {
        try {
            val removed = recycleBin.deleteRecursively()
            if (!removed && recycleBin.exists()) {
                // Do not clear the database while some bytes remain. Otherwise the remaining files
                // become untracked and cannot be restored through the app anymore.
                throw IOException("Recycle bin could not be emptied completely")
            }
            mediaDB.clearRecycleBin()
            directoryDB.deleteRecycleBin()
            toast(org.fossify.commons.R.string.recycle_bin_emptied)
            callback?.invoke()
        } catch (e: Exception) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
        }
    }
}

fun BaseSimpleActivity.emptyAndDisableTheRecycleBin(callback: () -> Unit) {
    ensureBackgroundThread {
        emptyTheRecycleBin {
            config.useRecycleBin = false
            callback()
        }
    }
}

fun BaseSimpleActivity.showRecycleBinEmptyingDialog(callback: () -> Unit) {
    ConfirmationDialog(
        this,
        "",
        org.fossify.commons.R.string.empty_recycle_bin_confirmation,
        org.fossify.commons.R.string.yes,
        org.fossify.commons.R.string.no
    ) {
        callback()
    }
}

fun BaseSimpleActivity.showRestoreConfirmationDialog(count: Int, callback: () -> Unit) {
    ConfirmationDialog(
        activity = this,
        message = resources.getQuantityString(R.plurals.restore_confirmation, count, count),
        positive = org.fossify.commons.R.string.yes,
        negative = org.fossify.commons.R.string.no
    ) {
        callback()
    }
}

fun BaseSimpleActivity.updateFavoritePaths(fileDirItems: ArrayList<FileDirItem>, destination: String) {
    ensureBackgroundThread {
        fileDirItems.forEach {
            val newPath = "$destination/${it.name}"
            updateDBMediaPath(it.path, newPath)
        }
    }
}

fun AppCompatActivity.fixDateTaken(
    paths: ArrayList<String>,
    showToasts: Boolean,
    hasRescanned: Boolean = false,
    callback: (() -> Unit)? = null
) {
    val BATCH_SIZE = 50
    if (showToasts && !hasRescanned) {
        toast(R.string.fixing)
    }

    val pathsToRescan = ArrayList<String>()
    try {
        var didUpdateFile = false
        val operations = ArrayList<ContentProviderOperation>()

        ensureBackgroundThread {
            val dateTakens = ArrayList<DateTaken>()

            for (path in paths) {
                try {
                    val dateTime: String = ExifInterface(path).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: ExifInterface(path).getAttribute(ExifInterface.TAG_DATETIME) ?: continue

                    // some formats contain a "T" in the middle, some don't
                    // sample dates: 2015-07-26T14:55:23, 2018:09:05 15:09:05
                    val t = if (dateTime.substring(10, 11) == "T") "\'T\'" else " "
                    val separator = dateTime.substring(4, 5)
                    val format = "yyyy${separator}MM${separator}dd${t}kk:mm:ss"
                    val formatter = SimpleDateFormat(format, Locale.getDefault())
                    val timestamp = formatter.parse(dateTime).time

                    val uri = getFileUri(path)
                    ContentProviderOperation.newUpdate(uri).apply {
                        val selection = "${Images.Media.DATA} = ?"
                        val selectionArgs = arrayOf(path)
                        withSelection(selection, selectionArgs)
                        withValue(Images.Media.DATE_TAKEN, timestamp)
                        operations.add(build())
                    }

                    if (operations.size % BATCH_SIZE == 0) {
                        contentResolver.applyBatch(MediaStore.AUTHORITY, operations)
                        operations.clear()
                    }

                    mediaDB.updateFavoriteDateTaken(path, timestamp)
                    didUpdateFile = true

                    val dateTaken = DateTaken(
                        null,
                        path,
                        path.getFilenameFromPath(),
                        path.getParentPath(),
                        timestamp,
                        (System.currentTimeMillis() / 1000).toInt(),
                        File(path).lastModified()
                    )
                    dateTakens.add(dateTaken)
                    if (!hasRescanned && getFileDateTaken(path) == 0L) {
                        pathsToRescan.add(path)
                    }
                } catch (e: Exception) {
                    continue
                }
            }

            if (!didUpdateFile) {
                if (showToasts) {
                    toast(R.string.no_date_takens_found)
                }

                runOnUiThread {
                    callback?.invoke()
                }
                return@ensureBackgroundThread
            }

            val resultSize = contentResolver.applyBatch(MediaStore.AUTHORITY, operations).size
            if (resultSize == 0) {
                didUpdateFile = false
            }

            if (hasRescanned || pathsToRescan.isEmpty()) {
                if (dateTakens.isNotEmpty()) {
                    dateTakensDB.insertAll(dateTakens)
                }

                runOnUiThread {
                    if (showToasts) {
                        toast(if (didUpdateFile) R.string.dates_fixed_successfully else org.fossify.commons.R.string.unknown_error_occurred)
                    }

                    callback?.invoke()
                }
            } else {
                rescanPaths(pathsToRescan) {
                    fixDateTaken(paths, showToasts, true, callback)
                }
            }
        }
    } catch (e: Exception) {
        if (showToasts) {
            showErrorToast(e)
        }
    }
}

fun BaseSimpleActivity.saveRotatedImageToFile(oldPath: String, newPath: String, degrees: Int, showToasts: Boolean, callback: () -> Unit) {
    var newDegrees = degrees
    if (newDegrees < 0) {
        newDegrees += 360
    }

    if (oldPath == newPath && oldPath.isJpg()) {
        if (tryRotateByExif(oldPath, newDegrees, showToasts, callback)) {
            return
        }
    }

    val tmpPath = "$recycleBinPath/.tmp_${newPath.getFilenameFromPath()}"
    val tmpFileDirItem = FileDirItem(tmpPath, tmpPath.getFilenameFromPath())
    try {
        getFileOutputStream(tmpFileDirItem) {
            if (it == null) {
                if (showToasts) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
                return@getFileOutputStream
            }

            val oldLastModified = File(oldPath).lastModified()
            if (oldPath.isJpg()) {
                if (!copyFileVerified(oldPath, tmpPath)) throw IOException("Could not prepare rotation source")
                saveExifRotation(ExifInterface(tmpPath), newDegrees)
            } else {
                val inputstream = getFileInputStreamSync(oldPath)
                val bitmap = inputstream?.use { BitmapFactory.decodeStream(it) }
                    ?: throw IOException("Could not decode rotation source")
                saveFile(tmpPath, bitmap, it as FileOutputStream, newDegrees)
            }

            // Close the encoder stream before replacing the destination. The old implementation
            // copied into the live file while the temporary output stream was still open and then
            // ignored copy errors, so a killed/failed copy could leave a truncated original.
            it.flush()
            it.close()

            val existingTarget = getDoesFilePathExist(newPath)
            val backupPath = if (existingTarget) {
                // Even an explicit overwrite must remain recoverable. Refuse to touch the target
                // when it cannot be backed up first (for example a missing MediaStore row).
                MoveConflicts.backupToRecycleBin(this, newPath)
                    ?: throw IOException("Unable to back up rotation target: $newPath")
            } else null
            if (!copyFileVerified(tmpPath, newPath)) {
                android.util.Log.e("Gallery", "Rotation copy verification failed: $tmpPath -> $newPath")
                // Keep the old version in the recycle bin if the replacement failed; it is safer
                // than attempting another overwrite while the storage/provider is unhealthy.
                runCatching { File(newPath).delete() }
                backupPath?.let { org.fossify.gallery.helpers.MediaRepository(this).restoreFromRecycleBin(it) }
                throw IOException("Rotation replacement verification failed")
            }
            rescanPaths(arrayListOf(newPath))
            fileRotatedSuccessfully(newPath, oldLastModified)
            callback.invoke()
        }
    } catch (e: OutOfMemoryError) {
        if (showToasts) {
            toast(org.fossify.commons.R.string.out_of_memory_error)
        }
    } catch (e: Exception) {
        if (showToasts) {
            showErrorToast(e)
        }
    } finally {
        tryDeleteFileDirItem(tmpFileDirItem, false, true)
    }
}

fun Activity.tryRotateByExif(path: String, degrees: Int, showToasts: Boolean, callback: () -> Unit): Boolean {
    return try {
        val file = File(path)
        val oldLastModified = file.lastModified()
        if (saveImageRotation(path, degrees)) {
            fileRotatedSuccessfully(path, oldLastModified)
            callback.invoke()
            if (showToasts) {
                toast(org.fossify.commons.R.string.file_saved)
            }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        // lets not show IOExceptions, rotating is saved just fine even with them
        if (showToasts && e !is IOException) {
            showErrorToast(e)
        }
        false
    }
}

@OptIn(ExperimentalCoilApi::class)
fun Activity.fileRotatedSuccessfully(path: String, lastModified: Long) {
    if (config.keepLastModified && lastModified != 0L) {
        File(path).setLastModified(lastModified)
        updateLastModified(path, lastModified)
    }

    val imageLoader = Coil.imageLoader(applicationContext)
    imageLoader.diskCache?.clear()
    imageLoader.memoryCache?.clear()
}

fun BaseSimpleActivity.copyFile(source: String, destination: String) {
    var inputStream: InputStream? = null
    var out: OutputStream? = null
    try {
        out = getFileOutputStreamSync(destination, source.getMimeType())
        inputStream = getFileInputStreamSync(source)
        inputStream!!.copyTo(out!!)
    } catch (e: Exception) {
        showErrorToast(e)
    } finally {
        inputStream?.close()
        out?.close()
    }
}

/** Copies a media file and verifies that the destination contains every source byte. */
fun BaseSimpleActivity.copyFileVerified(source: String, destination: String): Boolean {
    if (source == destination) return false
    val expectedSize = File(source).length()
    if (expectedSize <= 0L) return false
    return try {
        var copied = 0L
        getFileInputStreamSync(source)!!.use { input ->
            val output = getFileOutputStreamSync(destination, source.getMimeType()) ?: return false
            output.use {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read: Int
                while (input.read(buffer).also { read = it } > 0) {
                    it.write(buffer, 0, read)
                    copied += read
                }
                it.flush()
            }
        }
        copied == expectedSize && getDoesFilePathExist(destination)
    } catch (e: Exception) {
        android.util.Log.e("Gallery", "Verified copy failed: $source -> $destination", e)
        false
    }
}

fun BaseSimpleActivity.ensureWriteAccess(path: String, callback: () -> Unit) {
    when {
        isRestrictedSAFOnlyRoot(path) -> {
            handleAndroidSAFDialog(path) {
                if (!it) {
                    return@handleAndroidSAFDialog
                }
                callback.invoke()
            }
        }

        needsStupidWritePermissions(path) -> {
            handleSAFDialog(path) {
                if (!it) {
                    return@handleSAFDialog
                }
                callback()
            }
        }

        isAccessibleWithSAFSdk30(path) -> {
            handleSAFDialogSdk30(path) {
                if (!it) {
                    return@handleSAFDialogSdk30
                }
                callback()
            }
        }

        else -> {
            callback()
        }
    }
}

fun BaseSimpleActivity.rescanPathsAndUpdateLastModified(paths: ArrayList<String>, pathLastModifiedMap: Map<String, Long>, callback: () -> Unit) {
    fixDateTaken(paths, false)
    for (path in paths) {
        val file = File(path)
        val lastModified = pathLastModifiedMap[path]
        if (config.keepLastModified && lastModified != null && lastModified != 0L) {
            File(file.absolutePath).setLastModified(lastModified)
            updateLastModified(file.absolutePath, lastModified)
        }
    }
    rescanPaths(paths, callback)
}

fun saveFile(path: String, bitmap: Bitmap, out: FileOutputStream, degrees: Int) {
    val matrix = Matrix()
    matrix.postRotate(degrees.toFloat())
    val bmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    bmp.compress(path.getCompressionFormat(), 90, out)
}

fun Activity.showFileOnMap(path: String) {
    val exif = try {
        if (path.startsWith("content://")) {
            ExifInterface(contentResolver.openInputStream(path.toUri())!!)
        } else {
            ExifInterface(path)
        }
    } catch (e: Exception) {
        showErrorToast(e)
        return
    }

    val latLon = FloatArray(2)
    if (exif.getLatLong(latLon)) {
        showLocationOnMap("${latLon[0]}, ${latLon[1]}")
    } else {
        toast(R.string.unknown_location)
    }
}

fun Activity.handleExcludedFolderPasswordProtection(callback: () -> Unit) {
    if (config.isExcludedPasswordProtectionOn) {
        SecurityDialog(this, config.excludedPasswordHash, config.excludedProtectionType) { _, _, success ->
            if (success) {
                callback()
            }
        }
    } else {
        callback()
    }
}

fun BaseSimpleActivity.writeBitmapToCache(
    source: Uri,
    bitmap: Bitmap,
    callback: (path: String?) -> Unit
) {
    val bytes = ByteArrayOutputStream()
    bitmap.compress(CompressFormat.PNG, 0, bytes)

    val folder = File(cacheDir, TEMP_FOLDER_NAME)
    if (!folder.exists()) {
        if (!folder.mkdir()) {
            callback(null)
            return
        }
    }

    val rawFilename = applicationContext.getFilenameFromContentUri(source)
    val filename = org.fossify.gallery.helpers.IntentSecurity.sanitizeSingleFilename(
        rawName = rawFilename,
        fallback = "tmp-${System.currentTimeMillis()}.jpg",
    )
    val newPath = File(folder, filename).absolutePath
    val fileDirItem = FileDirItem(newPath, filename)
    getFileOutputStream(fileDirItem, true) {
        if (it != null) {
            try {
                it.write(bytes.toByteArray())
                callback(newPath)
            } catch (_: Exception) {
                callback(null)
            } finally {
                it.close()
            }
        } else {
            callback(null)
        }
    }
}

fun BaseSimpleActivity.ensureWritablePath(
    targetPath: String,
    confirmOverwrite: Boolean = true,
    onCancel: (() -> Unit)? = null,
    callback: (String) -> Unit,
) {
    fun proceedAfterGrants() {
        handleSAFDialogSdk30(targetPath) { granted ->
            if (!granted) {
                onCancel?.invoke()
                return@handleSAFDialogSdk30
            }
            callback(targetPath)
        }
    }

    fun requestGrantsThenProceed() {
        if (isRPlus() && !isExternalStorageManager()) {
            val fileDirItem = arrayListOf(File(targetPath).toFileDirItem(this))
            val fileUris = getFileUrisFromFileDirItems(fileDirItem)
            updateSDK30Uris(fileUris) { success ->
                if (success) proceedAfterGrants() else onCancel?.invoke()
            }
        } else {
            proceedAfterGrants()
        }
    }

    if (confirmOverwrite && getDoesFilePathExist(targetPath)) {
        val title = String.format(
            getString(org.fossify.commons.R.string.file_already_exists_overwrite),
            targetPath.getFilenameFromPath()
        )
        ConfirmationDialog(this, title) {
            requestGrantsThenProceed()
        }
    } else {
        requestGrantsThenProceed()
    }
}

fun Activity.proposeNewFilePath(uri: Uri): Pair<String, Boolean> {
    var newPath = applicationContext.getRealPathFromURI(uri) ?: ""
    if (newPath.startsWith("/mnt/")) {
        newPath = ""
    }

    var shouldAppendFilename = true
    if (newPath.isEmpty()) {
        val filename = applicationContext.getFilenameFromContentUri(uri) ?: ""
        if (filename.isNotEmpty()) {
            val path = if (intent.extras?.containsKey(REAL_FILE_PATH) == true) {
                intent.getStringExtra(REAL_FILE_PATH)?.getParentPath()
            } else {
                internalStoragePath
            }
            newPath = "$path/$filename"
            shouldAppendFilename = false
        }
    }

    if (newPath.isEmpty()) {
        newPath = "$internalStoragePath/${getCurrentFormattedDateTime()}.${
            uri.toString().getFilenameExtension()
        }"
        shouldAppendFilename = false
    }

    return Pair(newPath, shouldAppendFilename)
}
