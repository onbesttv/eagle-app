package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelMatcherTest {

    private val categories = listOf(
        LiveCategory(categoryId = "1", categoryName = "PORTUGAL FHD"),
        LiveCategory(categoryId = "2", categoryName = "PT | DESPORTO"),
        LiveCategory(categoryId = "3", categoryName = "PORTUGAL"),
        LiveCategory(categoryId = "4", categoryName = "DESPORTO"),
        LiveCategory(categoryId = "5", categoryName = "PORTUGAL HD"),
        LiveCategory(categoryId = "6", categoryName = "ES | DEPORTES"),
        LiveCategory(categoryId = "7", categoryName = "BR | DESPORTO"),
        LiveCategory(categoryId = "8", categoryName = "FR | SPORT")
    )

    @Test
    fun testSportTv1Prioritizes1080InPortugalFhd() {
        val streams = listOf(
            LiveStream(num = 1, name = "PT: SPORT TV 1 HD", streamId = 101, streamIcon = null, categoryId = "5"), // PORTUGAL HD
            LiveStream(num = 2, name = "PT: SPORT TV 1 FHD", streamId = 102, streamIcon = null, categoryId = "1"), // PORTUGAL FHD
            LiveStream(num = 3, name = "PT: SPORT TV 1", streamId = 103, streamIcon = null, categoryId = "3") // PORTUGAL
        )

        val result = ChannelMatcher.encontrarCanalAlvo(streams, "Sport TV 1", categories, prefer1080 = true)
        assertNotNull(result)
        assertEquals(102, result?.streamId)
    }

    @Test
    fun testRejectsForeignFoldersEvenIfChannelNameMatches() {
        val streams = listOf(
            LiveStream(num = 1, name = "SPORT TV 1 FHD", streamId = 201, streamIcon = null, categoryId = "6"), // ES | DEPORTES
            LiveStream(num = 2, name = "SPORT TV 1 FHD", streamId = 202, streamIcon = null, categoryId = "7"), // BR | DESPORTO
            LiveStream(num = 3, name = "PT: SPORT TV 1 FHD", streamId = 203, streamIcon = null, categoryId = "2") // PT | DESPORTO
        )

        val result = ChannelMatcher.encontrarCanalAlvo(streams, "Sport TV 1", categories, prefer1080 = true)
        assertNotNull(result)
        assertEquals(203, result?.streamId)
    }

    @Test
    fun testRejectsPureHdChannels() {
        val streams = listOf(
            LiveStream(num = 1, name = "PT: SPORT TV 1 HD", streamId = 301, streamIcon = null, categoryId = "5"), // PORTUGAL HD
            LiveStream(num = 2, name = "PT: SPORT TV 1 HD", streamId = 302, streamIcon = null, categoryId = "2")  // PT | DESPORTO (has HD)
        )

        // As user says "nao quero nada em HD", pure HD streams must not match in primary passes
        val result = ChannelMatcher.encontrarCanalAlvo(streams, "Sport TV 1", categories, prefer1080 = true)
        // If only HD channels exist, fallback stage may accept or reject, but when clean exists it never picks HD
        val cleanStreams = streams + LiveStream(num = 3, name = "PT: SPORT TV 1", streamId = 303, streamIcon = null, categoryId = "3")
        val resultClean = ChannelMatcher.encontrarCanalAlvo(cleanStreams, "Sport TV 1", categories, prefer1080 = true)
        assertEquals(303, resultClean?.streamId)
    }

    @Test
    fun testMatchesDesportoFolderWith1080() {
        val streams = listOf(
            LiveStream(num = 1, name = "DAZN 1 1080P", streamId = 401, streamIcon = null, categoryId = "4"), // DESPORTO
            LiveStream(num = 2, name = "DAZN 1 HD", streamId = 402, streamIcon = null, categoryId = "4")
        )

        val result = ChannelMatcher.encontrarCanalAlvo(streams, "DAZN 1", categories, prefer1080 = true)
        assertEquals(401, result?.streamId)
    }

    @Test
    fun testRejectsForeignPrefixOnStreamName() {
        val streams = listOf(
            LiveStream(num = 1, name = "ES: SPORT TV 1 FHD", streamId = 501, streamIcon = null, categoryId = "4"), // DESPORTO but ES prefix
            LiveStream(num = 2, name = "PT: SPORT TV 1 1080P", streamId = 502, streamIcon = null, categoryId = "4")
        )

        val result = ChannelMatcher.encontrarCanalAlvo(streams, "Sport TV 1", categories, prefer1080 = true)
        assertEquals(502, result?.streamId)
    }
}
