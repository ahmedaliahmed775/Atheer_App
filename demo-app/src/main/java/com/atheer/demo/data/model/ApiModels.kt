package com.atheer.demo.data.model

import com.google.gson.annotations.SerializedName

/**
 * نماذج البيانات لطلبات واستجابات API
 */

// ── طلب تسجيل الدخول ──
data class LoginRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("password") val password: String
)

// ── استجابة تسجيل الدخول والتسجيل (تم التعديل لتتوافق مع السيرفر) ──
data class LoginResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: AuthData?
)

data class AuthData(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("user") val user: UserInfo?
)

data class UserInfo(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("phone") val phone: String?,
    @SerializedName("role") val role: String?
)

// ── طلب التسجيل (تم تصحيح email إلى phone) ──
data class SignupRequest(
    @SerializedName("name") val name: String,
    @SerializedName("phone") val phone: String, // تم التعديل هنا ✅
    @SerializedName("password") val password: String,
    @SerializedName("role") val role: String
)
/*
// ── استجابة الرصيد ──
data class BalanceResponse(
    @SerializedName("balance") val balance: Double,
    @SerializedName("currency") val currency: String?
)
*/
// ── استجابة الرصيد (تم إضافة data) ──
data class BalanceResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: BalanceData?
)

data class BalanceData(
    @SerializedName("balance") val balance: Double,
    @SerializedName("currency") val currency: String?
)

// ── استجابة سجل المعاملات (تم إضافة data) ──
data class HistoryResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: HistoryData?
)

data class HistoryData(
    @SerializedName("transactions") val transactions: List<TransactionItem>?
)
// ── استجابة سجل المعاملات ──
data class TransactionItem(
    @SerializedName("id") val id: String?,
    @SerializedName("transaction_id") val transactionId: String?,
    @SerializedName("amount") val amount: Double,
    @SerializedName("currency") val currency: String?,
    @SerializedName("merchant_id") val merchantId: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("synced") val synced: Boolean = true
)
/*
data class HistoryResponse(
    @SerializedName("transactions")
    val transactions: List<TransactionItem>?
)*/