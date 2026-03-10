package com.atheer.demo.ui.customer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.atheer.demo.R
import com.atheer.demo.data.local.TokenManager
import com.atheer.demo.data.network.RetrofitClient
import com.atheer.demo.databinding.ActivityCustomerMainBinding
import com.atheer.demo.ui.login.LoginActivity
import com.atheer.sdk.AtheerSdk
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

/**
 * CustomerMainActivity — الشاشة الرئيسية للعميل
 * تعرض الرصيد والسجل باستخدام Retrofit
 */
class CustomerMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerMainBinding
    private lateinit var tokenManager: TokenManager
    private var isBalanceVisible = true
    private var currentBalance: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)

        setupBottomNavigation()
        setupBalanceCard()
        setupAtheerPayButton()

        // جلب البيانات عند فتح الشاشة
        loadBalance()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    binding.layoutHome.visibility = View.VISIBLE
                    binding.layoutHistory.visibility = View.GONE
                    binding.layoutSettings.visibility = View.GONE
                    loadBalance() // تحديث الرصيد عند العودة للرئيسية
                    true
                }
                R.id.nav_history -> {
                    binding.layoutHome.visibility = View.GONE
                    binding.layoutHistory.visibility = View.VISIBLE
                    binding.layoutSettings.visibility = View.GONE
                    loadHistory()
                    true
                }
                R.id.nav_settings -> {
                    binding.layoutHome.visibility = View.GONE
                    binding.layoutHistory.visibility = View.GONE
                    binding.layoutSettings.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }

        binding.btnLogout.setOnClickListener {
            tokenManager.clearAll()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }
    }

    private fun setupBalanceCard() {
        binding.ivToggleBalance.setOnClickListener {
            isBalanceVisible = !isBalanceVisible
            updateBalanceDisplay()
        }
    }

    private fun updateBalanceDisplay() {
        if (isBalanceVisible) {
            binding.tvBalance.text = String.format("%.2f", currentBalance)
            binding.tvCurrencyLabel.text = getString(R.string.sar_currency)
            binding.ivToggleBalance.setImageResource(android.R.drawable.ic_menu_view)
        } else {
            binding.tvBalance.text = "••••••"
            binding.tvCurrencyLabel.text = ""
            binding.ivToggleBalance.setImageResource(android.R.drawable.ic_secure)
        }
    }

    private fun loadBalance() {
        lifecycleScope.launch {
            try {
                val token = tokenManager.getAccessToken() ?: ""
                val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"

                val response = RetrofitClient.apiService.getBalance(authHeader)

                if (response.isSuccessful && response.body()?.data != null) {
                    currentBalance = response.body()!!.data!!.balance
                    updateBalanceDisplay()
                } else {
                    val errorBody = response.errorBody()?.string()
                    binding.tvBalance.text = "0.00"
                    AlertDialog.Builder(this@CustomerMainActivity)
                        .setTitle("تنبيه من السيرفر (الرصيد)")
                        .setMessage(errorBody ?: "فشل جلب البيانات")
                        .setPositiveButton("حسناً", null)
                        .show()
                }
                // سيتم استدعاء الدالة لجلب التوكنز الحقيقية بصمت في الخلفية
                provisionOfflineTokens()
            } catch (e: Exception) {
                binding.tvBalance.text = getString(R.string.error_offline)
            }
        }
    }

    private fun loadHistory() {
        val context = this
        lifecycleScope.launch {
            try {
                val token = tokenManager.getAccessToken() ?: ""
                val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"

                // طلب السجل عبر Retrofit
                val response = com.atheer.demo.data.network.RetrofitClient.apiService.getHistory(authHeader)

                // التعديل الضروري: إضافة .data قبل .transactions
                if (response.isSuccessful && response.body()?.data?.transactions != null) {
                    val transactionsList = response.body()!!.data!!.transactions!!
                    displayTransactions(transactionsList)
                } else {
                    android.widget.Toast.makeText(context, "لا يوجد سجل معاملات حالياً", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "خطأ في الشبكة: تعذر جلب السجل", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayTransactions(transactions: List<com.atheer.demo.data.model.TransactionItem>) {
        val container = binding.rvHistory
        container.removeAllViews()
        for (txn in transactions) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_transaction, container, false)
            itemView.findViewById<TextView>(R.id.tvTxnAmount).text =
                String.format("%.2f %s", txn.amount, txn.currency ?: "SAR")
            itemView.findViewById<TextView>(R.id.tvTxnDate).text = txn.createdAt ?: ""

            val tvStatus = itemView.findViewById<TextView>(R.id.tvTxnStatus)
            tvStatus.text = if (txn.synced) getString(R.string.result_status_synced) else getString(R.string.result_status_offline)

            val statusIcon = itemView.findViewById<ImageView>(R.id.ivTxnIcon)
            statusIcon.setColorFilter(
                ContextCompat.getColor(this, if (txn.synced) R.color.success_color else R.color.text_secondary)
            )
            container.addView(itemView)
        }
    }

    // ⭐ التعديل الأهم: دالة جلب التوكنز الحقيقية من السيرفر باستخدام الـ SDK
    private fun provisionOfflineTokens() {
        lifecycleScope.launch {
            try {
                // جلب رمز المصادقة (JWT)
                val token = tokenManager.getAccessToken() ?: return@launch
                val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"

                // استدعاء دالة الـ SDK التي تتصل بالشبكة، تفك الـ JSON، وتخزن المفاتيح محلياً
                val result = AtheerSdk.getInstance().fetchAndProvisionTokens(authHeader)

                result.onSuccess { count ->
                    android.util.Log.d("AtheerSDK", "✅ تم جلب وتجهيز $count مفتاح دفع مشفر من السيرفر بنجاح")
                }.onFailure { error ->
                    android.util.Log.e("AtheerSDK", "❌ فشل تجهيز مفاتيح الدفع: ${error.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("AtheerSDK", "❌ خطأ غير متوقع أثناء تجهيز المفاتيح: ${e.message}")
            }
        }
    }

    private fun setupAtheerPayButton() {
        binding.btnAtheerPay.setOnClickListener {
            authenticateWithBiometric()
        }
    }

    private fun authenticateWithBiometric() {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            showNfcBottomSheet()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    showNfcBottomSheet()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setNegativeButtonText(getString(R.string.biometric_cancel))
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun showNfcBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_nfc, null)
        dialog.setContentView(view)

        // التحقق من وجود توكنز جاهزة قبل عرض النافذة
        val remainingTokens = AtheerSdk.getInstance().getRemainingTokensCount()
        if (remainingTokens > 0) {
            dialog.show()
        } else {
            Toast.makeText(this, "يرجى تحديث الرصيد أولاً لتجهيز مفاتيح الدفع (جاري جلبها من السيرفر...)", Toast.LENGTH_LONG).show()
        }
    }
}