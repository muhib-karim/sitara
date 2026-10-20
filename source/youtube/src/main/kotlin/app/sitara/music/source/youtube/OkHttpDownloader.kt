package app.sitara.music.source.youtube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

// YouTube serves its regular web responses, which the extractor parses, only to browser user agents.
internal const val BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

/** NewPipeExtractor's HTTP layer on OkHttp. HTTP 429 becomes [ReCaptchaException], which the extractor expects for rate limiting. */
class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        client.newCall(request.toOkHttp()).execute().use { response ->
            if (response.code == 429) throw ReCaptchaException("Rate limited by ${response.request.url.host}", request.url())
            return Response(response.code, response.message, response.headers.toMultimap(), response.body.string(), response.request.url.toString())
        }
    }
}

private fun Request.toOkHttp(): okhttp3.Request {
    val builder = okhttp3.Request.Builder().url(url())
    headers().forEach { (name, values) -> values.forEach { builder.addHeader(name, it) } }
    if (headers().keys.none { it.equals("User-Agent", ignoreCase = true) }) builder.header("User-Agent", BROWSER_USER_AGENT)
    val body = dataToSend()?.toRequestBody() ?: if (httpMethod() == "POST") ByteArray(0).toRequestBody() else null
    return builder.method(httpMethod(), body).build()
}
