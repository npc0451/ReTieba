package dev.infinityf4p.tiebapure.core.network

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.RequestBody.Companion.toRequestBody

class AnonymousReadInterceptorTest {
    @Test
    fun allowsAnonymousPostAndBlocksEveryOtherAccessBeforeDispatch() {
        val dispatched = AtomicInteger()
        val client = OkHttpClient.Builder()
            .addInterceptor(AnonymousReadInterceptor())
            .addInterceptor { chain ->
                dispatched.incrementAndGet()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body("read".toResponseBody()).build()
            }.build()
        for (endpoint in TiebaEndpoint.All) {
            val request = Request.Builder().url(endpoint.url).post("".toRequestBody())
                .tag(TiebaEndpoint::class.java, endpoint).build()
            if (endpoint.access == EndpointAccess.AnonymousRead) {
                client.newCall(request).execute().use { assertEquals(200, it.code) }
            } else {
                assertFailsWith<IOException> { client.newCall(request).execute() }
            }
        }
        assertEquals(TiebaEndpoint.All.count { it.access == EndpointAccess.AnonymousRead }, dispatched.get())
        assertFailsWith<IOException> {
            client.newCall(Request.Builder().url(TiebaEndpoint.Login.url).build()).execute()
        }
    }

    @Test
    fun rejectsReadTagsReusedForAnotherEndpointOrCommand() {
        val tagged = Request.Builder().url(TiebaEndpoint.Personalized.url)
            .tag(TiebaEndpoint::class.java, TiebaEndpoint.Personalized).build()
        requireAnonymousRead(tagged)
        assertFailsWith<IOException> {
            requireAnonymousRead(tagged.newBuilder().url(TiebaEndpoint.AgreePost.url).build())
        }
        assertFailsWith<IOException> {
            requireAnonymousRead(tagged.newBuilder().url(tagged.url.newBuilder().setQueryParameter("cmd", "0").build()).build())
        }
    }

    @Test
    fun requestFactoriesCarryEndpointMetadata() {
        val builder = testRequestBuilder()
        assertEquals(TiebaEndpoint.SearchForum, builder.getRequest(TiebaEndpoint.SearchForum, emptyMap()).tag(TiebaEndpoint::class.java))
        assertEquals(TiebaEndpoint.ForumPageForm, builder.formRequest(TiebaEndpoint.ForumPageForm, emptyMap()).tag(TiebaEndpoint::class.java))
        val proto = tieba.Personalized.PersonalizedRequest.getDefaultInstance()
        assertEquals(TiebaEndpoint.Personalized, builder.protobufRequest(TiebaEndpoint.Personalized, proto, null, false).tag(TiebaEndpoint::class.java))
    }
}
