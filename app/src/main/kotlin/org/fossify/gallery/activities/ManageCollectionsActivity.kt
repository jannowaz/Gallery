package org.fossify.gallery.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.navigation.NavigationBarView
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.databinding.ActivityManageCollectionsBinding
import org.fossify.gallery.dialogs.EditCollectionDialog
import org.fossify.gallery.extensions.collectionDB
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.MediaCollection

class ManageCollectionsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityManageCollectionsBinding::inflate)
    private var activeDialog: EditCollectionDialog? = null

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = uriToPath(uri)
            if (path != null) {
                activeDialog?.onFolderPicked(path)
                toast("Ordner: ${java.io.File(path).name}")
            }
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (config.forceDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
            delegate.localNightMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        }
        setContentView(binding.root)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.collectionsRecyclerView, binding.bottomNavigation))
        binding.addCollectionFab.setOnClickListener { editCollection(null) }
        setupCollectionsTabs()
    }

    private fun setupCollectionsTabs() {
        val bottomNav = binding.bottomNavigation
        val bgColor = getProperBackgroundColor()
        val textColor = getProperTextColor()
        val primaryColor = getProperPrimaryColor()

        bottomNav.setBackgroundColor(bgColor)
        val states = arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(-android.R.attr.state_selected)
        )
        val iconColors = intArrayOf(bgColor, textColor)
        bottomNav.itemIconTintList = android.content.res.ColorStateList(states, iconColors)
        
        bottomNav.itemTextColor = android.content.res.ColorStateList(states, intArrayOf(primaryColor, textColor))
        bottomNav.itemActiveIndicatorColor = android.content.res.ColorStateList.valueOf(primaryColor)

        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.nav_collections) return@setOnItemSelectedListener true
            bottomNav.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

            val t = when (item.itemId) {
                R.id.nav_media -> 0
                R.id.nav_folders -> 1
                R.id.nav_explorer -> 2
                R.id.nav_collections -> 3
                R.id.nav_favorites -> 4
                else -> 1
            }

            val intent = Intent(this@ManageCollectionsActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra("SELECTED_TAB", t)
            }
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
            true
        }

        bottomNav.selectedItemId = R.id.nav_collections
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun uriToPath(uri: Uri): String? {
        try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.indexOf(':')
            if (split >= 0) {
                val type = docId.substring(0, split)
                val relative = docId.substring(split + 1)
                return (if (type == "primary") "/storage/emulated/0/$relative" else "/storage/$type/$relative").trimEnd('/')
            }
        } catch (_: Exception) {}
        return null
    }

    private fun refreshList() {
        ensureBackgroundThread {
            val collections = collectionDB.getAll()
            if (collections.isEmpty()) {
                runOnUiThread {
                    binding.collectionsEmptyPlaceholder.visibility = View.VISIBLE
                    binding.collectionsRecyclerView.adapter = null
                }
                return@ensureBackgroundThread
            }
            val dirs = ArrayList<Directory>()
            for (col in collections) {
                dirs.add(Directory().apply {
                    path = "collection:${col.id}"
                    name = col.name
                    location = 1
                    tmb = ""
                    mediaCnt = 0
                    subfoldersMediaCount = 0
                    subfoldersCount = 1
                    containsMediaFilesDirectly = false
                })
            }
            runOnUiThread {
                binding.collectionsEmptyPlaceholder.visibility = if (dirs.isEmpty()) View.VISIBLE else View.GONE
                if (dirs.isNotEmpty()) {
                    binding.collectionsRecyclerView.adapter = DirectoryAdapter(
                        this@ManageCollectionsActivity, dirs, null, binding.collectionsRecyclerView,
                        false, false, null
                    ) { clicked ->
                        val d = clicked as Directory
                        if (d.path.startsWith("collection:")) {
                            startActivity(Intent(this@ManageCollectionsActivity, MediaActivity::class.java).apply {
                                putExtra(org.fossify.gallery.helpers.DIRECTORY, d.path)
                            })
                        }
                    }
                }
            }
        }
    }

    private fun editCollection(existing: MediaCollection?) {
        activeDialog = EditCollectionDialog(this, existing, onPickFolder = { folderPickerLauncher.launch(null) }) { saved ->
            ensureBackgroundThread {
                collectionDB.insert(saved)
                runOnUiThread { refreshList() }
            }
        }
    }
}
