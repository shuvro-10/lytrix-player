package com.musicplayer.lytrix

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class LytrixDownloader : Downloader() {

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())

        request.headers().forEach { (key, list) ->
            list.forEach { value -> builder.addHeader(key, value) }
        }

        if (request.httpMethod() == "POST") {
            builder.post((request.dataToSend() ?: byteArrayOf()).toRequestBody(null))
        }

        return client.newCall(builder.build()).execute().use { okHttpResponse ->
            val outHeaders = mutableMapOf<String, List<String>>()
            okHttpResponse.headers.names().forEach { name ->
                outHeaders[name] = okHttpResponse.headers.values(name)
            }

            Response(
                okHttpResponse.code,
                okHttpResponse.message,
                outHeaders,
                okHttpResponse.body?.string() ?: "",
                request.url()
            )
        }
    }
}
