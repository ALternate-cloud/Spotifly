package com.example.spotifly.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.spotifly.data.stats.ListeningStats
import com.example.spotifly.model.Playlist
import com.example.spotifly.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sin

enum class ShareCardFormat {
    STORY_9_16,
    SQUARE_1_1
}

/**
 * Android's share utility supporting both native text sharing and the
 * Dark Cinematic "Shareable Story / Card Generator" for Instagram Stories,
 * WhatsApp Status, and social media apps.
 */
object ShareUtils {

    /**
     * Plain-text track sharing (legacy / fallback).
     */
    fun shareSong(context: Context, song: Song) {
        val body = buildString {
            append("${song.title} — ${song.artist}")
            if (song.album.isNotBlank()) append("\nAlbum: ${song.album}")
            append("\n\nShared from Spotifly")
        }
        share(context, song.title, body)
    }

    fun sharePlaylist(context: Context, playlist: Playlist) {
        val body = buildString {
            append("${playlist.name}\n")
            if (playlist.description.isNotBlank()) append("${playlist.description}\n")
            append("${playlist.songs.size} tracks\n\n")
            playlist.songs.take(10).forEachIndexed { index, song ->
                append("${index + 1}. ${song.title} — ${song.artist}\n")
            }
            if (playlist.songs.size > 10) append("…and ${playlist.songs.size - 10} more\n")
            append("\nShared from Spotifly")
        }
        share(context, playlist.name, body)
    }

    /** Shares one lyric line as a quote card; lyrics are text, so this stays honest. */
    fun shareLyric(context: Context, song: Song, line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        val body = buildString {
            append("\u201C$trimmed\u201D\n")
            append("${song.artist} — ${song.title}\n\n")
            append("Shared from Spotifly")
        }
        share(context, "${song.title} — ${song.artist}", body)
    }

    /** The Wrapped card as text: numbers this device actually recorded. */
    fun shareWrapped(context: Context, stats: ListeningStats) {
        share(context, "My Spotifly listening", wrappedText(stats))
    }

    internal fun wrappedText(stats: ListeningStats): String = buildString {
        append("My Spotifly listening\n")
        append("${stats.totalMinutes} minutes · ${stats.totalPlays} plays · ${stats.uniqueArtists} artists\n")
        stats.topArtists.firstOrNull()?.let {
            append("Top artist: ${it.name} (${it.plays} plays)\n")
        }
        stats.topTracks.firstOrNull()?.let {
            append("Top track: ${it.title} — ${it.artist}\n")
        }
        append("Last 7 days: ${stats.minutesLast7Days} minutes\n")
        append("\nShared from Spotifly")
    }

    private fun share(context: Context, subject: String, body: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        val chooser = Intent.createChooser(send, "Share").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    /** Shares an exported `.m3u8` or `.json` playlist file via FileProvider. */
    suspend fun sharePlaylistExportFile(
        context: Context,
        filename: String,
        mimeType: String,
        content: String
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val exportsDir = File(context.cacheDir, "shared_exports").apply { mkdirs() }
            val exportFile = File(exportsDir, filename)
            exportFile.writeText(content)
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile
            )
            withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, filename)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Export Playlist").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        }
    }

    // =========================================================================
    // Dark Cinematic Shareable Story & Card Generator Engine
    // =========================================================================

    /**
     * Generates a studio-grade visual story / card image and opens Android's
     * native image share chooser (Instagram Stories, WhatsApp, Twitter, etc.).
     */
    suspend fun shareStoryCard(
        context: Context,
        song: Song,
        format: ShareCardFormat = ShareCardFormat.STORY_9_16,
        lyricsExcerpt: String? = null,
        accentColorInt: Int = 0xFF1ED760.toInt()
    ) = withContext(Dispatchers.IO) {
        val bitmap = createCardBitmap(context, song, format, lyricsExcerpt, accentColorInt)
        val imagesDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val imageFile = File(imagesDir, "spotifly_${song.id.hashCode()}_${System.currentTimeMillis()}.png")

        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )

        withContext(Dispatchers.Main) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "${song.title} — ${song.artist}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Now playing: ${song.title} by ${song.artist} on Spotifly \uD83C\uDFB5"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Music Card").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    /**
     * Saves the generated high-resolution card bitmap directly to the device's
     * Pictures/Spotifly directory in MediaStore.
     */
    suspend fun saveCardToGallery(
        context: Context,
        song: Song,
        format: ShareCardFormat = ShareCardFormat.STORY_9_16,
        lyricsExcerpt: String? = null,
        accentColorInt: Int = 0xFF1ED760.toInt()
    ): Boolean = withContext(Dispatchers.IO) {
        val bitmap = createCardBitmap(context, song, format, lyricsExcerpt, accentColorInt)
        try {
            val filename = "Spotifly_${song.title.filter { it.isLetterOrDigit() }}_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Spotifly")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved image to Pictures/Spotifly", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext true
                }
            } else {
                @Suppress("DEPRECATION")
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val spotiflyDir = File(picturesDir, "Spotifly").apply { mkdirs() }
                val targetFile = File(spotiflyDir, filename)
                FileOutputStream(targetFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved image to Pictures/Spotifly", Toast.LENGTH_SHORT).show()
                }
                return@withContext true
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    /**
     * Renders a high-resolution canvas bitmap representing the song in either
     * 9:16 Instagram Story (1080x1920) or 1:1 Square (1080x1080) format.
     */
    suspend fun createCardBitmap(
        context: Context,
        song: Song,
        format: ShareCardFormat,
        lyricsExcerpt: String? = null,
        accentColorInt: Int = 0xFF1ED760.toInt()
    ): Bitmap = withContext(Dispatchers.IO) {
        val width = 1080
        val height = if (format == ShareCardFormat.STORY_9_16) 1920 else 1080

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Fetch artwork bitmap if available
        var artworkBitmap: Bitmap? = null
        if (!song.artworkUrl.isNullOrEmpty()) {
            try {
                val loader = Coil.imageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(song.artworkUrl)
                    .allowHardware(false)
                    .build()
                val result = (loader.execute(req) as? SuccessResult)?.drawable?.toBitmap()
                if (result != null) {
                    artworkBitmap = result
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Base Dark Obsidian Background
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    AndroidColor.rgb(18, 18, 26),
                    AndroidColor.rgb(10, 10, 15),
                    AndroidColor.rgb(6, 6, 9)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 3. Ambient Atmospheric Glow
        val glowPaint = Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(
                width * 0.5f,
                height * (if (format == ShareCardFormat.STORY_9_16) 0.38f else 0.40f),
                width * 0.75f,
                intArrayOf(
                    (accentColorInt and 0x00FFFFFF) or 0x40000000,
                    AndroidColor.TRANSPARENT
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(
            width * 0.5f,
            height * (if (format == ShareCardFormat.STORY_9_16) 0.38f else 0.40f),
            width * 0.75f,
            glowPaint
        )

        // 4. Header: App branding badge
        val brandY = if (format == ShareCardFormat.STORY_9_16) 160f else 90f
        val brandPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.18f
        }
        canvas.drawText("SPOTIFLY", width / 2f, brandY, brandPaint)

        val subBrandPaint = Paint().apply {
            isAntiAlias = true
            color = (accentColorInt and 0x00FFFFFF) or 0xCC000000.toInt()
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.22f
        }
        canvas.drawText("LOSSLESS AUDIO", width / 2f, brandY + 40f, subBrandPaint)

        // 5. Album Artwork Drawing
        val artSize = if (format == ShareCardFormat.STORY_9_16) 680f else 500f
        val artLeft = (width - artSize) / 2f
        val artTop = if (format == ShareCardFormat.STORY_9_16) 300f else 170f
        val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
        val cornerRadius = 40f

        // Drop shadow behind artwork
        val shadowPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.argb(120, 0, 0, 0)
        }
        canvas.drawRoundRect(
            RectF(artLeft + 10f, artTop + 16f, artLeft + artSize + 10f, artTop + artSize + 20f),
            cornerRadius,
            cornerRadius,
            shadowPaint
        )

        if (artworkBitmap != null) {
            val scaledArt = scaleSquareCenterCrop(artworkBitmap, artSize.toInt())
            val shader = BitmapShader(scaledArt, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val artMatrix = android.graphics.Matrix()
            artMatrix.setTranslate(artLeft, artTop)
            shader.setLocalMatrix(artMatrix)

            val artPaint = Paint().apply {
                isAntiAlias = true
                this.shader = shader
            }
            canvas.drawRoundRect(artRect, cornerRadius, cornerRadius, artPaint)
        } else {
            // Elegant placeholder card
            val placeholderPaint = Paint().apply {
                isAntiAlias = true
                shader = LinearGradient(
                    artLeft, artTop, artLeft + artSize, artTop + artSize,
                    intArrayOf(AndroidColor.rgb(36, 36, 52), AndroidColor.rgb(18, 18, 28)),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(artRect, cornerRadius, cornerRadius, placeholderPaint)

            val notePaint = Paint().apply {
                isAntiAlias = true
                color = AndroidColor.WHITE
                textSize = 100f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("♫", width / 2f, artTop + artSize / 2f + 35f, notePaint)
        }

        // Specular cut-glass border around artwork
        val borderPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = AndroidColor.argb(60, 255, 255, 255)
        }
        canvas.drawRoundRect(artRect, cornerRadius, cornerRadius, borderPaint)

        // 6. Track Title and Artist
        val titleY = artTop + artSize + (if (format == ShareCardFormat.STORY_9_16) 110f else 80f)
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.WHITE
            textSize = if (format == ShareCardFormat.STORY_9_16) 54f else 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val displayTitle = if (song.title.length > 28) song.title.take(26) + "…" else song.title
        canvas.drawText(displayTitle, width / 2f, titleY, titlePaint)

        val artistY = titleY + (if (format == ShareCardFormat.STORY_9_16) 65f else 50f)
        val artistPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.argb(210, 220, 220, 235)
            textSize = if (format == ShareCardFormat.STORY_9_16) 36f else 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val displayArtist = if (song.artist.length > 34) song.artist.take(32) + "…" else song.artist
        canvas.drawText(displayArtist, width / 2f, artistY, artistPaint)

        // 7. Dynamic Equalizer Soundwave Graphic
        val waveTop = artistY + (if (format == ShareCardFormat.STORY_9_16) 70f else 45f)
        val waveBarCount = 28
        val barWidth = 14f
        val barSpacing = 12f
        val totalWaveWidth = waveBarCount * barWidth + (waveBarCount - 1) * barSpacing
        val waveStartX = (width - totalWaveWidth) / 2f

        val barPaint = Paint().apply {
            isAntiAlias = true
            color = accentColorInt
            style = Paint.Style.FILL
        }

        for (i in 0 until waveBarCount) {
            val progress = i.toFloat() / waveBarCount
            val barHeightMultiplier = (0.25f + 0.75f * ((sin(progress * Math.PI * 3.5).toFloat() + 1f) / 2f)).coerceIn(0.2f, 1f)
            val barHeight = (if (format == ShareCardFormat.STORY_9_16) 70f else 48f) * barHeightMultiplier
            val bx = waveStartX + i * (barWidth + barSpacing)
            val by = waveTop + (if (format == ShareCardFormat.STORY_9_16) 35f else 24f) - barHeight / 2f
            canvas.drawRoundRect(
                RectF(bx, by, bx + barWidth, by + barHeight),
                barWidth / 2f,
                barWidth / 2f,
                barPaint
            )
        }

        // 8. Optional Lyrics Excerpt Frosted Quote Card
        var currentBottom = waveTop + (if (format == ShareCardFormat.STORY_9_16) 100f else 60f)
        if (!lyricsExcerpt.isNullOrBlank() && format == ShareCardFormat.STORY_9_16) {
            val quoteBoxWidth = 840f
            val quoteBoxHeight = 160f
            val quoteLeft = (width - quoteBoxWidth) / 2f
            val quoteTop = currentBottom + 20f

            val quoteBgPaint = Paint().apply {
                isAntiAlias = true
                color = AndroidColor.argb(45, 255, 255, 255)
            }
            canvas.drawRoundRect(
                RectF(quoteLeft, quoteTop, quoteLeft + quoteBoxWidth, quoteTop + quoteBoxHeight),
                24f,
                24f,
                quoteBgPaint
            )

            val quoteBorder = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = AndroidColor.argb(80, 255, 255, 255)
            }
            canvas.drawRoundRect(
                RectF(quoteLeft, quoteTop, quoteLeft + quoteBoxWidth, quoteTop + quoteBoxHeight),
                24f,
                24f,
                quoteBorder
            )

            val lyricPaint = Paint().apply {
                isAntiAlias = true
                color = AndroidColor.WHITE
                textSize = 30f
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                textAlign = Paint.Align.CENTER
            }
            val quoteText = "“${lyricsExcerpt.take(65).trim()}”"
            canvas.drawText(quoteText, width / 2f, quoteTop + quoteBoxHeight / 2f + 10f, lyricPaint)
            currentBottom = quoteTop + quoteBoxHeight
        }

        // 9. Footer: Play on Spotifly
        val footerY = if (format == ShareCardFormat.STORY_9_16) height - 120f else height - 55f
        val footerPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.argb(170, 255, 255, 255)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.14f
        }
        canvas.drawText("PLAY ON SPOTIFLY", width / 2f, footerY, footerPaint)

        bitmap
    }

    private fun scaleSquareCenterCrop(source: Bitmap, targetSize: Int): Bitmap {
        val side = minOf(source.width, source.height).coerceAtLeast(1)
        val xOffset = (source.width - side) / 2
        val yOffset = (source.height - side) / 2
        val cropped = Bitmap.createBitmap(source, xOffset, yOffset, side, side)
        return if (cropped.width == targetSize && cropped.height == targetSize) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        }
    }
}
