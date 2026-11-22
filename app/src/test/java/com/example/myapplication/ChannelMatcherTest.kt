package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ChannelMatcherTest {

    private val categories = listOf(
        LiveCategory(categoryId = "1", categoryName = "PT | DESPORTO"),
        LiveCategory(categoryId = "2", categoryName = "PORTUGAL 1080P"),
        LiveCategory(categoryId = "3", categoryName = "PT | GENERALISTAS"),
        LiveCategory(categoryId = "4", categoryName = "PT | DESPORTO FHD")
    )

    @Test
    fun testSportTv1Prioritizes1080OverNormal() {
        val streams = listOf(
            LiveStream(num = 1, name = "PT: SPORT TV 1 HD", streamId = 101, streamIcon = null, categoryId = "1"),
            LiveStream(num = 2, name = "PT: SPORT TV 1 1080P", streamId = 102, streamIcon = null, categoryId = "1"),
            LiveStream(num = 3, name = "PT: SPORT TV 1 SD", streamId = 103, streamIcon = null, categoryId = "1")
        )

        val result = ChannelMatcher.encontrarCanalAlvo(streams, "Sport TV 1", categories, prefer1080 = true)
        assertNotNull(result)
        assertEquals(102, result?.streamId)
    }

    @Test
    fun testSportTv1Prioritizes1080ByCategory() {
        val streams = listOf(
            LiveStream(num = 1, name = "PT: SPORT TV 1", streamId = 201, streamIcon = null, categoryId = "1"), // PT | DESPORTO (normal)
            LiveStream(num = 2, name = "PT: SPORT TV 1", streamId = 202, streamIcon = null, categoryId = "2")  // PORTUGAL 1080P
        )

        val result = ChannelMatcher.encontrarCanalAlvo(streams, "Sport TV 1", categories, prefer1080 = true)
        assertNotNull(result)
        assertEquals(202, result?.streamId)
    }

    @Test
    fun testSportTv1FallsBackToNormalWhenNo1080Exists() {
        val streams = listOf(
            LiveStream(num = 1, name = "PT: SPORT TV 1 HD", streamId = 301, streamIcon = null, categoryId = "1"),
            LiveStream(num = 2, name = "PT: SPORT TV 1 SD", streamId = 302, streamIcon = null, categoryId = "1")
        )

        val result = ChannelMatcher.encontrarCanalAlvo(streams, "Sport TV 1", categories, prefer1080 = true)
        assertNotNull(result)
        assertEquals(301, result?.streamId)
    }

    @Test
    fun testSportTv1DoesNotMatchSportTv10() {
        val streams = listOf(
            LiveStream(num = 1, name = "PT: SPORT TV 10 1080P", streamId = 401, streamIcon = null, categoryId = "1"),
            LiveStream(num = 2, name = "PT: SPORT TV 1 HD", streamId = 402, streamIcon = null, categoryId = "1")
        )

        val result = ChannelMatcher.encontrarCanalAlvo(streams, "Sport TV 1", categories, prefer1080 = true)
        assertNotNull(result)
        assertEquals(402, result?.streamId)
    }

    @Test
    fun testSportTv1DoesNotMatchSportTvPlus() {
        val streams = listOf(
            LiveStream(num = 1, name = "PT: SPORT TV + 1080P", streamId = 501, streamIcon = null, categoryId = "1"),
            LiveStream(num = 2, name = "PT: SPORT TV 1 HD", streamId = 502, streamIcon = null, categoryId = "1")
        )

        val result = ChannelMatcher.encontrarCanalAlvo(streams, "Sport TV 1", categories, prefer1080 = true)
        assertNotNull(result)
        assertEquals(502, result?.streamId)
    }

    @Test
    fun testBenficaTvVariants() {
        val streams = listOf(
            LiveStream(num = 1, name = "PT: BTV 1 HD", streamId = 601, streamIcon = null, categoryId = "1"),
            LiveStream(num = 2, name = "PT: BENFICA TV 1 FHD", streamId = 602, streamIcon = null, categoryId = "1")
        )

        val resultA = ChannelMatcher.encontrarCanalAlvo(streams, "BTV 1", categories, prefer1080 = true)
        assertEquals(602, resultA?.streamId)

        val resultB = ChannelMatcher.encontrarCanalAlvo(streams, "Benfica.Tv", categories, prefer1080 = true)
        assertEquals(602, resultB?.streamId)
    }

    @Test
    fun testDaznVariants() {
        val streams = listOf(
            LiveStream(num = 1, name = "PT: ELEVEN SPORTS 1 HD", streamId = 701, streamIcon = null, categoryId = "1"),
            LiveStream(num = 2, name = "PT: DAZN 1 1080P", streamId = 702, streamIcon = null, categoryId = "1")
        )

        val result = ChannelMatcher.encontrarCanalAlvo(streams, "DAZN 1", categories, prefer1080 = true)
        assertEquals(702, result?.streamId)
    }

    @Test
    fun testSicDoesNotMatchSicNoticias() {
        val streams = listOf(
            LiveStream(num = 1, name = "PT: SIC NOTICIAS 1080P", streamId = 801, streamIcon = null, categoryId = "3"),
            LiveStream(num = 2, name = "PT: SIC 1080P", streamId = 802, streamIcon = null, categoryId = "3")
        )

        val result = ChannelMatcher.encontrarCanalAlvo(streams, "SIC", categories, prefer1080 = true)
        assertEquals(802, result?.streamId)
    }

    @Test
    fun testMultiChannelPrioritizes1080() {
        val streams = listOf(
            LiveStream(num = 1, name = "PT: RTP 1 HD", streamId = 901, streamIcon = null, categoryId = "3"),
            LiveStream(num = 2, name = "PT: SPORT TV 1 1080P", streamId = 902, streamIcon = null, categoryId = "1")
        )

        // RTP 1 is only HD, but Sport TV 1 has 1080P -> picks 1080P!
        val result = ChannelMatcher.encontrarCanalAlvo(streams, "RTP 1 / Sport TV 1", categories, prefer1080 = true)
        assertEquals(902, result?.streamId)
    }
}
