package com.example.spotifly.data.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkParserTest {

    @Test
    fun `parses entity links`() {
        assertEquals(
            DeepLinkTarget.Track("abc123"),
            DeepLinkParser.parse("spotifly://track/abc123")
        )
        assertEquals(
            DeepLinkTarget.Album("saavn_album_42"),
            DeepLinkParser.parse("spotifly://album/saavn_album_42")
        )
        assertEquals(
            DeepLinkTarget.Artist("artist-7"),
            DeepLinkParser.parse("spotifly://artist/artist-7")
        )
        assertEquals(
            DeepLinkTarget.Playlist("pl_1"),
            DeepLinkParser.parse("spotifly://playlist/pl_1")
        )
    }

    @Test
    fun `song is an alias for track`() {
        assertEquals(
            DeepLinkTarget.Track("x"),
            DeepLinkParser.parse("spotifly://song/x")
        )
    }

    @Test
    fun `parses search query and decodes it`() {
        assertEquals(
            DeepLinkTarget.Search("midnight city"),
            DeepLinkParser.parse("spotifly://search?q=midnight%20city")
        )
        assertEquals(
            DeepLinkTarget.Search("daft punk"),
            DeepLinkParser.parse("spotifly://search/daft%20punk")
        )
    }

    @Test
    fun `search query wins over path segment`() {
        assertEquals(
            DeepLinkTarget.Search("typed"),
            DeepLinkParser.parse("spotifly://search/ignored?q=typed")
        )
    }

    @Test
    fun `scheme and path matching are case-insensitive`() {
        assertEquals(
            DeepLinkTarget.Album("A1"),
            DeepLinkParser.parse("Spotifly://Album/A1")
        )
    }

    @Test
    fun `home link`() {
        assertEquals(DeepLinkTarget.Home, DeepLinkParser.parse("spotifly://home"))
    }

    @Test
    fun `rejects malformed or foreign links`() {
        assertNull(DeepLinkParser.parse(null))
        assertNull(DeepLinkParser.parse(""))
        assertNull(DeepLinkParser.parse("spotifly://"))
        assertNull(DeepLinkParser.parse("spotifly://track"))
        assertNull(DeepLinkParser.parse("spotifly://track/"))
        assertNull(DeepLinkParser.parse("spotifly://track/%20"))
        assertNull(DeepLinkParser.parse("spotifly://unknown/x"))
        assertNull(DeepLinkParser.parse("https://open.example.com/track/x"))
        assertNull(DeepLinkParser.parse("spotifly:track/x"))
    }
}
