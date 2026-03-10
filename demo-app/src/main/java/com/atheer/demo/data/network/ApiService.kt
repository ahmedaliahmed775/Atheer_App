package com.atheer.demo.data.network

import com.atheer.demo.data.model.HistoryResponse
import com.atheer.demo.data.model.LoginResponse
import com.atheer.demo.data.model.SignupRequest
import com.atheer.demo.data.model.LoginRequest
import com.atheer.demo.data.model.BalanceResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface ApiService {

    // جلب الرصيد
    @GET("api/v1/wallet/balance")
    suspend fun getBalance(@Header("Authorization") token: String): Response<BalanceResponse>

    // جلب السجل
    @GET("api/v1/wallet/history")
    suspend fun getHistory(@Header("Authorization") token: String): Response<HistoryResponse>

    // التسجيل
    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("api/v1/auth/signup")
    suspend fun register(@Body request: SignupRequest): Response<LoginResponse>

    // تسجيل الدخول
    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}