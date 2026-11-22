package org.fossify.gallery.models

import com.google.gson.annotations.SerializedName

data class AlbumCover(
    @SerializedName("path") val path: String = "",
    @SerializedName("tmb") val tmb: String = "",
)
