package app.sitara.music.source.youtube

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe

/** Installs the OkHttp downloader that every NewPipeExtractor call uses. Call once at process start. */
fun installNewPipe() {
    NewPipe.init(OkHttpDownloader(OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()))
}
