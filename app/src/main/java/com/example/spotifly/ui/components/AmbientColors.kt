package com.example.spotifly.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a representative color from the artwork so the full player can tint
 * its background with the album's vibe. Returns null on any failure — callers
 * must fall back to the static gradient.
 */
suspend fun extractAmbientColor(context: Context, model: Any?): Color? = withContext(Dispatchers.IO) {
    runCatching {
        if (model == null) return@runCatching null
        val request = ImageRequest.Builder(context)
            .data(model)
            .allowHardware(false)
            .size(64)
            .build()
        val drawable = context.imageLoader.execute(request).drawable
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
        val palette = Palette.from(bitmap).clearFilters().generate()
        val swatch = palette.dominantSwatch
            ?: palette.vibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.swatches.maxByOrNull { it.population }
        swatch?.rgb?.let { Color(it) }
    }.getOrNull()
}
