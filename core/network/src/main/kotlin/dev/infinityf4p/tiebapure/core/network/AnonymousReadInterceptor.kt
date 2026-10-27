package dev.infinityf4p.tiebapure.core.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/** Reading endpoints may use POST; access is determined by their protocol contract. */
class AnonymousReadInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        requireAnonymousRead(chain.request())
        return chain.proceed(chain.request())
    }
}

internal fun requireAnonymousRead(request: Request) {
    val endpoint = request.tag(TiebaEndpoint::class.java) ?: throw IOException("只读客户端不支持未标记接口。")
    if (endpoint.access != EndpointAccess.AnonymousRead ||
        request.url.scheme != endpoint.url.scheme ||
        request.url.host != endpoint.url.host ||
        request.url.port != endpoint.url.port ||
        request.url.encodedPath != endpoint.url.encodedPath ||
        request.url.queryParameter("cmd") != endpoint.url.queryParameter("cmd")
    ) {
        throw IOException("只读客户端不支持此接口。")
    }
}
