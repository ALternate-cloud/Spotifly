package com.example.spotifly.model

/** How the library lists its content. Spotify toggles between the two in place. */
enum class LibraryLayout {
    LIST,
    GRID;

    fun toggled(): LibraryLayout = if (this == LIST) GRID else LIST
}

/** Appearance choice. SYSTEM follows the phone's dark-theme setting. */
enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System");

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}

/**
 * Streaming quality. [bitrateKbps] is the ceiling handed to the catalogue
 * service; AUTO asks for the best the connection can carry.
 */
enum class StreamQuality(
    val label: String,
    val description: String,
    val bitrateKbps: Int
) {
    AUTO("Automatic", "Adjusts to your connection", 0),
    LOW("Low", "Uses less data", 96),
    NORMAL("Normal", "Balanced quality", 160),
    HIGH("High", "Best quality", 320);

    fun next(): StreamQuality = entries[(ordinal + 1) % entries.size]
}
