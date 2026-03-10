package com.atheer.demo.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.atheer.demo.R
import com.atheer.demo.data.local.TokenManager
import com.atheer.demo.ui.customer.CustomerMainActivity
import com.atheer.demo.ui.login.LoginActivity
import com.atheer.demo.ui.merchant.MerchantMainActivity

/**
 * SplashActivity — شاشة البداية
 * تعرض شعار التطبيق لمدة ثانيتين ثم تنتقل حسب حالة المستخدم
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val tokenManager = TokenManager(this)
            val intent = if (tokenManager.isLoggedIn()) {
                when (tokenManager.getUserRole()) {
                    "merchant" -> Intent(this, MerchantMainActivity::class.java)
                    else -> Intent(this, CustomerMainActivity::class.java)
                }
            } else {
                Intent(this, LoginActivity::class.java)
            }
            startActivity(intent)
            finish()
        }, SPLASH_DELAY_MS)
    }

    companion object {
        private const val SPLASH_DELAY_MS = 2000L
    }
}
