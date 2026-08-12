package com.example.flikky.ui.files

import com.example.flikky.R

internal fun FileCategory.labelResource(): Int = when (this) {
    FileCategory.ALL -> R.string.files_filter_all
    FileCategory.IMAGE -> R.string.files_filter_image
    FileCategory.VIDEO -> R.string.files_filter_video
    FileCategory.AUDIO -> R.string.files_filter_audio
    FileCategory.DOCUMENT -> R.string.files_filter_document
    FileCategory.OTHER -> R.string.files_filter_other
}

internal fun FileCategory.iconResource(): Int = when (this) {
    FileCategory.IMAGE -> R.drawable.ic_image
    FileCategory.VIDEO -> R.drawable.ic_movie
    FileCategory.AUDIO -> R.drawable.ic_audio_file
    FileCategory.DOCUMENT -> R.drawable.ic_description
    FileCategory.ALL,
    FileCategory.OTHER,
    -> R.drawable.ic_draft
}
