package com.example.myapplication

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun testVodStreamDeserialization() {
        val gson = Gson()
        // Test with number added, string added, null added, float rating, string rating, etc.
        val json = """
        [
            {"stream_id": 1, "name": "Movie 1", "added": 1731682345, "rating": 7.5, "category_id": 10},
            {"stream_id": 2, "name": "Movie 2", "added": "1731682346", "rating": "8.0", "category_id": "10"},
            {"stream_id": 3, "name": "Movie 3", "added": null, "rating": null, "category_id": null}
        ]
        """.trimIndent()

        val type = object : TypeToken<List<VodStream>>() {}.type
        val list: List<VodStream> = gson.fromJson(json, type)
        assertEquals(3, list.size)
        println("Movie 1 added: ${list[0].added}")
        println("Movie 2 added: ${list[1].added}")
    }
}
