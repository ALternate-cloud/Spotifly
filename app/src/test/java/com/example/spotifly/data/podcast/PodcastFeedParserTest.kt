package com.example.spotifly.data.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the RSS episode parser. Real podcast feeds declare HTML
 * entities in an inline DTD, so these fixtures deliberately include the shapes
 * that broke the previous streaming XML parser.
 */
class PodcastFeedParserTest {

    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE rss [ <!ENTITY rsquo "&#8217;"> <!ENTITY nbsp "&#160;"> ]>
        <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
             xmlns:content="http://purl.org/rss/1.0/modules/content/">
          <channel>
            <title>Show &amp; Tell</title>
            <itunes:image href="https://art/channel.jpg"/>
            <item>
              <title><![CDATA[The one with the code]]></title>
              <description>&lt;p&gt;Ben&rsquo;s take&nbsp;on things&lt;/p&gt;</description>
              <pubDate>Tue, 21 Oct 2025 09:30:00 +0000</pubDate>
              <itunes:duration>1:02:03</itunes:duration>
              <guid isPermaLink="false">ep-1</guid>
              <enclosure url="https://cdn.example.com/ep1.mp3" type="audio/mpeg" length="1"/>
            </item>
            <item>
              <title>Second episode</title>
              <content:encoded><![CDATA[<p>Full <b>notes</b></p>]]></content:encoded>
              <description>Short blurb</description>
              <pubDate>Mon, 20 Oct 2025 08:00:00 GMT</pubDate>
              <itunes:duration>754</itunes:duration>
              <enclosure url="https://cdn.example.com/ep2.mp3" type="audio/mpeg" length="1"/>
            </item>
            <item>
              <title>No audio here</title>
              <description>A bonus note without an enclosure.</description>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun parse_readsEveryPlayableItemAndSkipsOnesWithoutAudio() {
        val episodes = PodcastFeedParser.parse(feed, "show-1", "Show & Tell")

        assertEquals(2, episodes.size)
        assertEquals("The one with the code", episodes[0].title)
        assertEquals("Second episode", episodes[1].title)
        assertEquals("show-1", episodes[0].showId)
        assertEquals("Show & Tell", episodes[0].showTitle)
    }

    @Test
    fun parse_decodesDtdEntitiesAndStripsEncodedHtml() {
        val episodes = PodcastFeedParser.parse(feed, "show-1", "Show & Tell")

        assertEquals("Ben’s take on things", episodes[0].description)
        assertEquals("Full notes", episodes[1].description)
    }

    @Test
    fun parse_extractsEnclosureUrlAndChannelArtwork() {
        val episodes = PodcastFeedParser.parse(feed, "show-1", "Show & Tell")

        assertEquals("https://cdn.example.com/ep1.mp3", episodes[0].audioUrl)
        assertEquals("https://cdn.example.com/ep2.mp3", episodes[1].audioUrl)
        assertEquals("https://art/channel.jpg", episodes[0].artworkUrl)
    }

    @Test
    fun parse_fallsBackToShowArtworkWhenFeedHasNone() {
        val withoutImage = feed.lines()
            .filterNot { it.contains("itunes:image") }
            .joinToString("\n")

        val episodes = PodcastFeedParser.parse(
            withoutImage,
            "show-1",
            "Show & Tell",
            fallbackArtwork = "https://art/fallback.jpg"
        )

        assertEquals("https://art/fallback.jpg", episodes[0].artworkUrl)
    }

    @Test
    fun parse_readsDurationsInHoursMinutesSecondsAndPlainSeconds() {
        val episodes = PodcastFeedParser.parse(feed, "show-1", "Show & Tell")

        assertEquals((1 * 3600 + 2 * 60 + 3) * 1000L, episodes[0].durationMs)
        assertEquals(754_000L, episodes[1].durationMs)
    }

    @Test
    fun parse_readsRfc822AndGmtDates() {
        val episodes = PodcastFeedParser.parse(feed, "show-1", "Show & Tell")

        assertTrue(episodes[0].publishedAt > 0L)
        assertTrue(episodes[1].publishedAt > 0L)
        // The first episode is published a day later than the second.
        assertTrue(episodes[0].publishedAt > episodes[1].publishedAt)
    }

    @Test
    fun parse_idsAreStableShortHexAndDifferPerGuid() {
        val first = PodcastFeedParser.parse(feed, "show-1", "Show & Tell")
        val second = PodcastFeedParser.parse(feed, "show-1", "Show & Tell")

        assertEquals(first[0].id, second[0].id)
        assertNotEquals(first[0].id, first[1].id)
        assertEquals(24, first[0].id.length)
        assertTrue(first[0].id.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun parse_usesEnclosureUrlAsIdentityWhenGuidIsMissing() {
        val noGuid = feed.replace(Regex("<guid[^>]*>.*?</guid>"), "")

        val episodes = PodcastFeedParser.parse(noGuid, "show-1", "Show & Tell")

        assertEquals(2, episodes.size)
        assertEquals(24, episodes[0].id.length)
    }

    @Test
    fun parse_respectsTheItemLimit() {
        val episodes = PodcastFeedParser.parse(feed, "show-1", "Show & Tell", limit = 1)

        assertEquals(1, episodes.size)
    }

    @Test
    fun parse_returnsEmptyForBlankInput() {
        assertTrue(PodcastFeedParser.parse("", "show-1", "Show").isEmpty())
        assertTrue(PodcastFeedParser.parse("   \n ", "show-1", "Show").isEmpty())
    }

    @Test
    fun cleanText_keepsComparisonProseWhileRemovingRealTags() {
        val cleaned = PodcastFeedParser.cleanText("He said 5 &lt; 6 &gt; 4, <b>really</b>.")

        assertEquals("He said 5 < 6 > 4, really.", cleaned)
    }

    @Test
    fun parseDuration_handlesEveryFeedShape() {
        assertEquals(0L, PodcastFeedParser.parseDuration(""))
        assertEquals(90_000L, PodcastFeedParser.parseDuration("90"))
        assertEquals(90_000L, PodcastFeedParser.parseDuration("1:30"))
        assertEquals(3_723_000L, PodcastFeedParser.parseDuration("1:02:03"))
        assertEquals(0L, PodcastFeedParser.parseDuration("not a duration"))
    }
}
