package org.fossify.gallery.dialogs

import android.widget.TextView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogEditCollectionBinding
import org.fossify.gallery.models.MediaCollection

class EditCollectionDialog(
    private val activity: BaseSimpleActivity,
    private val existing: MediaCollection?,
    private val onPickFolder: (() -> Unit),
    private val callback: (MediaCollection) -> Unit
) {
    private val binding = DialogEditCollectionBinding.inflate(activity.layoutInflater)
    private var includedPaths = existing?.getIncludedPaths()?.toMutableList() ?: mutableListOf()
    private var excludedPaths = existing?.getExcludedPaths()?.toMutableList() ?: mutableListOf()
    private var pickingForIncluded = true

    fun onFolderPicked(path: String) {
        if (pickingForIncluded) {
            if (!includedPaths.contains(path)) includedPaths.add(path)
        } else {
            if (!excludedPaths.contains(path)) excludedPaths.add(path)
        }
        refreshPathLists()
    }

    init {
        binding.collectionNameInput.setText(existing?.name ?: "")
        refreshPathLists()

        binding.addIncludedPath.setOnClickListener {
            pickingForIncluded = true
            onPickFolder()
        }

        binding.addExcludedPath.setOnClickListener {
            pickingForIncluded = false
            onPickFolder()
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                val name = binding.collectionNameInput.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton

                val collection = existing?.copy(
                    name = name,
                    includedPaths = MediaCollection.createPathsJson(includedPaths),
                    excludedPaths = MediaCollection.createPathsJson(excludedPaths)
                ) ?: MediaCollection(
                    name = name,
                    includedPaths = MediaCollection.createPathsJson(includedPaths),
                    excludedPaths = MediaCollection.createPathsJson(excludedPaths)
                )
                callback(collection)
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.collections)
            }
    }

    private fun refreshPathLists() {
        binding.includedPathsContainer.removeAllViews()
        if (includedPaths.isEmpty()) {
            binding.includedPathsContainer.addView(TextView(activity).apply {
                text = "Keine Auswahl = alle Ordner"
                setTextColor(android.graphics.Color.GRAY)
                textSize = 14f
                setPadding(0, 8, 0, 8)
            })
        } else {
            includedPaths.forEach { path ->
                binding.includedPathsContainer.addView(createChip(path, true))
            }
        }
        binding.excludedPathsContainer.removeAllViews()
        if (excludedPaths.isEmpty()) {
            binding.excludedPathsContainer.addView(TextView(activity).apply {
                text = "Keine Ordner exkludiert"
                setTextColor(android.graphics.Color.GRAY)
                textSize = 14f
                setPadding(0, 8, 0, 8)
            })
        } else {
            excludedPaths.forEach { path ->
                binding.excludedPathsContainer.addView(createChip(path, false))
            }
        }
    }

    private fun createChip(path: String, isIncluded: Boolean): TextView {
        val name = path.split("/").lastOrNull { it.isNotEmpty() } ?: path
        return TextView(activity).apply {
            text = "✕ $name"
            textSize = 13f
            setPadding(16, 8, 16, 8)
            setBackgroundColor(0x33333333)
            setOnClickListener {
                if (isIncluded) includedPaths.remove(path) else excludedPaths.remove(path)
                refreshPathLists()
            }
        }
    }
}
