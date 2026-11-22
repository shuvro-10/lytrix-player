package com.musicplayer.lytrix

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.musicplayer.lytrix.utils.RadioApi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private const val SAAVN_BASE_URL = "https://jiosaavn-api.shuvrodaspranto20170014.workers.dev/"

    val api: JioSaavnApi by lazy {
        Retrofit.Builder()
            .baseUrl(SAAVN_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JioSaavnApi::class.java)
    }

    private const val PODCAST_BASE_URL = "https://itunes.apple.com/"

    val podcastApi: PodcastApi by lazy {
        Retrofit.Builder()
            .baseUrl(PODCAST_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PodcastApi::class.java)
    }

    // 📻 রেডিওর জন্য API Base URL (Radio Browser)
    private const val RADIO_BASE_URL = "https://de1.api.radio-browser.info/"

    val radioApi: RadioApi by lazy {
        Retrofit.Builder()
            .baseUrl(RADIO_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RadioApi::class.java)
    }

    // 🔥 ক্লায়েন্ট যুক্ত করা হয়েছে (TimeOut 60 Sec)
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val LYTRIX_API_BASE_URL = "https://lytrix-music-api.onrender.com/"

    val lytrixApi: LytrixApi by lazy {
        Retrofit.Builder()
            .baseUrl(LYTRIX_API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LytrixApi::class.java)
    }
}