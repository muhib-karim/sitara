package app.sitara.music.source.scrobble

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Plays a scrobbling service: answers every call with the answer set last, or drops it like a lost connection; keeps each request. */
internal class FakeServer {
    val requests = mutableListOf<Request>()
    private var answer: Pair<Int, String>? = null

    fun answer(code: Int, body: String) {
        answer = code to body
    }

    fun fail() {
        answer = null
    }

    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        requests += chain.request()
        val (code, body) = answer ?: throw IOException("no connection")
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("status $code").body(body.toResponseBody()).build()
    }.build()
}
