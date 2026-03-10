package com.atheer.demo.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * TokenManager — تخزين آمن لـ JWT Access Token
 * يستخدم EncryptedSharedPreferences لحماية البيانات
 */
class TokenManager(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveAccessToken(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    fun saveUserRole(role: String) {
        prefs.edit().putString(KEY_USER_ROLE, role).apply()
    }

    fun getUserRole(): String? {
        return prefs.getString(KEY_USER_ROLE, null)
    }

    fun saveUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    fun saveUserPhone(phone: String) {
        prefs.edit().putString(KEY_USER_PHONE, phone).apply()
    }

    fun getUserPhone(): String? {
        return prefs.getString(KEY_USER_PHONE, null)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean {
        return !getAccessToken().isNullOrEmpty()
    }

    companion object {
        private const val PREFS_NAME = "atheer_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PHONE = "user_phone"
    }
}
