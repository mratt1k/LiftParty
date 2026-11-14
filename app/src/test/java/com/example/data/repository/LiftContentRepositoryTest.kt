package com.example.data.repository

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LiftContentRepositoryTest {

    @Test
    fun fetchFromLift_parsesOpenGraphJsonLdAndCards() = runBlocking {
        val sampleHtml = """
            <!doctype html>
            <html>
              <head>
                <title>Lift Streaming</title>
                <meta property="og:title" content="Lift Featured Film" />
                <meta property="og:description" content="Watch independent cinema on Lift." />
                <meta property="og:image" content="https://www.viewlift.com/thumb.jpg" />
                <meta property="og:video" content="https://cdn.viewlift.com/featured/master.m3u8" />
                <script type="application/ld+json">
                  {
                    "@type": "VideoObject",
                    "name": "Behind the Lift",
                    "description": "Creator documentary",
                    "contentUrl": "https://cdn.viewlift.com/doc.mp4",
                    "thumbnailUrl": "https://www.viewlift.com/doc.jpg",
                    "url": "https://www.viewlift.com/watch/behind-the-lift"
                  }
                </script>
              </head>
              <body>
                <article>
                  <a href="/watch/night-shift" title="Night Shift">
                    <img src="/images/night.jpg" />
                    <h3>Night Shift</h3>
                  </a>
                  <p>Late-night thriller streaming now.</p>
                </article>
              </body>
            </html>
        """.trimIndent()

        val fakeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "text/html")
                    .body(sampleHtml.toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        val repo = LiftContentRepository(fakeClient)
        val result = repo.fetchFromLift("https://www.viewlift.com").getOrThrow()

        assertEquals("Lift Featured Film", result.pageTitle)
        assertEquals("https://cdn.viewlift.com/featured/master.m3u8", result.primaryStreamUrl)
        assertTrue(result.detectedStreamUrls.contains("https://cdn.viewlift.com/featured/master.m3u8"))
        assertTrue(result.detectedStreamUrls.contains("https://cdn.viewlift.com/doc.mp4"))
        assertTrue(result.items.any { it.title == "Behind the Lift" && it.streamUrl == "https://cdn.viewlift.com/doc.mp4" })
        assertTrue(result.items.any { it.title == "Night Shift" && it.sourcePageUrl == "https://www.viewlift.com/watch/night-shift" })
    }
}
