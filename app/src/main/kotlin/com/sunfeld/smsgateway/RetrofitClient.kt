package com.sunfeld.smsgateway

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit client configured with OkHttp for the Mission Control API.
 * Uses [Config.BASE_URL] as the base endpoint and provides access to
 * [GatewayApiService] for type-safe HTTP calls.
 */
object RetrofitClient {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(Config.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val gatewayApi: GatewayApiService = retrofit.create(GatewayApiService::class.java)
}
