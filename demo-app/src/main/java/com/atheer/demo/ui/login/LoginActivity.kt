package com.atheer.demo.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.atheer.demo.R
import com.atheer.demo.data.local.TokenManager
import com.atheer.demo.data.model.LoginRequest
import com.atheer.demo.data.network.RetrofitClient
import com.atheer.demo.databinding.ActivityLoginBinding
import com.atheer.demo.ui.customer.CustomerMainActivity
import com.atheer.demo.ui.merchant.MerchantMainActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)

        if (tokenManager.isLoggedIn()) {
            navigateByRole(tokenManager.getUserRole() ?: "customer")
            return
        }

        binding.btnLogin.setOnClickListener { attemptLogin() }

        binding.tvCreateAccount.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun attemptLogin() {
        val phone = binding.etMerchantId.text?.toString()?.trim() ?: ""
        val password = binding.etPassword.text?.toString()?.trim() ?: ""

        binding.tvError.visibility = View.GONE
        binding.tilMerchantId.error = null
        binding.tilPassword.error = null

        if (phone.isEmpty() || password.isEmpty()) {
            binding.tvError.text = getString(R.string.login_error_empty)
            binding.tvError.visibility = View.VISIBLE
            return
        }

        binding.btnLogin.isEnabled = false
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // ── هنا التعديل: استخدام Retrofit الناجح والمضمون ──
                val request = LoginRequest(phone, password)
                val response = RetrofitClient.apiService.login(request)

                if (response.isSuccessful && response.body()?.data != null) {
                    val authData = response.body()!!.data!!

                    tokenManager.saveAccessToken(authData.accessToken)

                    val userRole = authData.user?.role ?: "customer"
                    tokenManager.saveUserRole(userRole)

                    authData.user?.let { user ->
                        user.name?.let { tokenManager.saveUserName(it) }
                        user.phone?.let { tokenManager.saveUserPhone(it) }
                    }

                    navigateByRole(userRole)
                } else {
                    binding.tvError.text = "رقم الهاتف أو كلمة المرور غير صحيحة"
                    binding.tvError.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = true
                }
            } catch (e: Exception) {
                binding.tvError.text = "خطأ في الشبكة: تأكد من اتصالك بالإنترنت"
                binding.tvError.visibility = View.VISIBLE
                binding.btnLogin.isEnabled = true
            }
        }
    }

    private fun navigateByRole(role: String) {
        val intent = when (role) {
            "merchant" -> Intent(this, MerchantMainActivity::class.java)
            else -> Intent(this, CustomerMainActivity::class.java)
        }
        startActivity(intent)
        finish()
    }
}