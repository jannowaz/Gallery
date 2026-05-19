package org.fossify.gallery.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import com.google.android.material.tabs.TabLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.GridLayoutManager
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.databinding.ActivityManageCollectionsBinding
import org.fossify.gallery.dialogs.EditCollectionDialog
import org.fossify.gallery.extensions.collectionDB
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.mediaDB
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
        setContentView(binding.root)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.collectionsRecyclerView, binding.tabLayout))
        binding.addCollectionFab.setOnClickListener { editCollection(null) }
        setupCollectionsTabs()
    }

    private fun setupCollectionsTabs() {
        val tabLayout = binding.tabLayout
        tabLayout.removeAllTabs()
        val bgColor = getProperBackgroundColor()
        val textColor = getProperTextColor()
        val primaryColor = getProperPrimaryColor()
        tabLayout.setBackgroundColor(bgColor)
        tabLayout.setTabTextColors(textColor, textColor)
        tabLayout.setSelectedTabIndicatorColor(primaryColor)

        tabLayout.addTab(tabLayout.newTab().apply {
            setIcon(R.drawable.ic_files_vector)
            text = getString(R.string.media_tab)
        })
        tabLayout.addTab(tabLayout.newTab().apply {
            setIcon(R.drawable.ic_folders_vector)
            text = getString(R.string.folders_tab)
        })
        tabLayout.addTab(tabLayout.newTab().apply {
            setIcon(R.drawable.ic_explore2_vector)
            text = getString(R.string.explorer2)
        })
        tabLayout.addTab(tabLayout.newTab().apply {
            setIcon(R.drawable.ic_collections_vector)
            text = getString(R.string.collections)
        })
        tabLayout.addTab(tabLayout.newTab().apply {
            setIcon(R.drawable.ic_star_vector)
            text = getString(org.fossify.commons.R.string.favorites)
        })

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.position == 3) return
                val intent = Intent(this@ManageCollectionsActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra("SELECTED_TAB", tab.position)
                }
                startActivity(intent)
                overridePendingTransition(0, 0)
                finish()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        tabLayout.getTabAt(3)?.select()
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
            val dirs = ArrayList<Directory>()
            for (col in collections) {
                val dir = Directory().apply {
                    path = "collection:${col.id}"
                    name = col.name
                    location = 1
                    containsMediaFilesDirectly = false
                    // get thumbnail
                    val included = col.getIncludedPaths()
                    val tmb = if (included.isNotEmpty()) {
                        try { mediaDB.getMediaFromPath(included.first()).firstOrNull()?.path } catch (_: Exception) { null } ?: ""
                    } else {
                        // all folders: try first folder with media
                        try {
                            val allDirs = directoryDB.getAll()
                            var found: String? = null
                            for (d in allDirs) {
                                val m = mediaDB.getMediaFromPath(d.path).firstOrNull()
                                if (m != null) { found = m.path; break }
                            }
                            found ?: ""
                        } catch (_: Exception) { "" }
                    }
                    this.tmb = tmb
                }
                dirs.add(dir)
            }
            runOnUiThread {
                binding.collectionsEmptyPlaceholder.visibility = if (dirs.isEmpty()) View.VISIBLE else View.GONE
                if (dirs.isNotEmpty()) {
                    binding.collectionsRecyclerView.adapter = DirectoryAdapter(
                        this@ManageCollectionsActivity, dirs, null, binding.collectionsRecyclerView,
                        false, null
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
