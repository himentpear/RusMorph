package org.namchieh.rusmorph.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class WordBookApiTest {
    @Test fun scopedReadContractsAcceptEmptyContent() = runTest {
        MockWebServer().use { server ->
            server.start()
            val api = RusMorphApiFactory.create(server.url("/").toString())
            val body = """{"schemaVersion":1,"wordBookId":"book","lessonId":"lesson-1","items":[],"nextCursor":null}"""
            repeat(3) { server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json")) }
            assertTrue(api.lessonWords("book", "lesson-1").items.isEmpty())
            assertTrue(api.lessonDialogues("book", "lesson-1").items.isEmpty())
            val texts = api.lessonTexts("book", "lesson-1")
            assertTrue(texts.items.isEmpty()); assertEquals(1, texts.schemaVersion); assertNull(texts.nextCursor)
            for (type in listOf("words", "dialogues", "texts")) {
                val request = server.takeRequest()
                assertEquals("GET", request.method)
                assertEquals("/v1/wordbooks/book/lessons/lesson-1/$type", request.path)
            }
        }
    }
}
