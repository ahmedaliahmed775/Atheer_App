package com.atheer.demo.ui.customer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
 */
class CustomerMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerMainBinding
    private lateinit var tokenManager: TokenManager
    private var isBalanceVisible = true
    private var currentBalance: Double = 0.0

    // --- 1. تعريف مستقبل الإشعارات (BroadcastReceiver) ---
    private val paymentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.atheer.sdk.ACTION_PAYMENT_REJECTED" -> {
                    val requested = intent.getDoubleExtra("amount", 0.0)
                    val limit = intent.getDoubleExtra("limit", 0.0)

                    // إظهار تنبيه قوي للعميل بحدوث محاولة سحب زائدة
                    AlertDialog.Builder(this@CustomerMainActivity)
                        .setTitle("⚠️ حماية أثير - تم حظر العملية")
                        .setMessage("حاول التاجر سحب مبلغ ($requested ريال)، وهو أعلى من السقف المالي الذي حددته ($limit ريال).\n\nتم إيقاف العملية لحماية أموالك.")
                        .setPositiveButton("حسناً", null)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show()
                }
                "com.atheer.sdk.ACTION_NFC_TAP_SUCCESS" -> {
                    Toast.makeText(this@CustomerMainActivity, "✅ تمت عملية الدفع بنجاح!", Toast.LENGTH_SHORT).show()
                    // تحديث الرصيد تلقائياً بعد نجاح الدفع
                    loadBalance()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)

        setupBottomNavigation()
        setupBalanceCard()
        setupAtheerPayButton()

        // --- التعديل الجديد: تفعيل أزرار إعدادات الحماية اللاتلامسية ---
        setupSettings()

        // جلب البيانات عند فتح الشاشة
        loadBalance()
        // جلب مفاتيح الدفع بصمت في الخلفية
        provisionOfflineTokens(showFeedback = false)
    }

    // --- 2. تسجيل المستقبل عند فتح التطبيق ---
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction("com.atheer.sdk.ACTION_PAYMENT_REJECTED")
            addAction("com.atheer.sdk.ACTION_NFC_TAP_SUCCESS")
        }
        // استخدام RECEIVER_NOT_EXPORTED للأمان والتوافق مع أندرويد 14+
        ContextCompat.registerReceiver(this, paymentReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    // --- 3. إيقاف المستقبل عند إغلاق التطبيق لتوفير البطارية ---
    override fun onPause() {
        super.onPause()
        unregisterReceiver(paymentReceiver)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    binding.layoutHome.visibility = View.VISIBLE
                    binding.layoutHistory.visibility = View.GONE
                    binding.layoutSettings.visibility = View.GONE
                    loadBalance()
                    provisionOfflineTokens(showFeedback = false)
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
                    binding.tvBalance.text = "0.00"
                }
            } catch (e: Exception) {
                binding.tvBalance.text = getString(R.string.error_offline)
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                val token = tokenManager.getAccessToken() ?: ""
                val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"
                val response = RetrofitClient.apiService.getHistory(authHeader)

                if (response.isSuccessful && response.body()?.data?.transactions != null) {
                    displayTransactions(response.body()!!.data!!.transactions!!)
                } else {
                    binding.tvHistoryEmpty.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Toast.makeText(this@CustomerMainActivity, "خطأ في الشبكة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayTransactions(transactions: List<com.atheer.demo.data.model.TransactionItem>) {
        val container = binding.rvHistory
        container.removeAllViews()

        if (transactions.isEmpty()) {
            binding.tvHistoryEmpty.visibility = View.VISIBLE
            return
        }

        binding.tvHistoryEmpty.visibility = View.GONE

        for (transaction in transactions) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_transaction, container, false)

            itemView.findViewById<TextView>(R.id.tvTxnAmount)?.text = "${transaction.amount} ${transaction.currency ?: "ر.س"}"
            itemView.findViewById<TextView>(R.id.tvTxnDate)?.text = transaction.createdAt ?: "بدون تاريخ"
            itemView.findViewById<TextView>(R.id.tvTxnStatus)?.text = if (transaction.type == "debit") "دفع" else "شحن"

            container.addView(itemView)
        }
    }

    /**
     * جلب مفاتيح الدفع من السيرفر.
     * @param showFeedback إذا كان true، سيظهر Toast في حالة الفشل أو النجاح (يستخدم عند ضغط المستخدم على زر الدفع)
     */
    private fun provisionOfflineTokens(showFeedback: Boolean) {
        lifecycleScope.launch {
            try {
                // جلب التوكن الخام فقط
                val token = tokenManager.getAccessToken() ?: return@launch

                android.util.Log.d("AtheerSDK", "🔄 جاري محاولة جلب المفاتيح من السيرفر...")

                // التعديل هنا: تمرير التوكن مباشرة (token) بدلاً من (authHeader)
                // التعديل: إضافة قيم افتراضية (مثلاً 5 توكنات بسقف 5000 ريال)
                val result = AtheerSdk.getInstance().fetchAndProvisionTokens(token, 5, 5000L)

                result.onSuccess { count ->
                    android.util.Log.d("AtheerSDK", "✅ تم جلب وتخزين $count مفتاح دفع بنجاح")
                    if (showFeedback) {
                        Toast.makeText(this@CustomerMainActivity, "تم تجهيز مفاتيح الدفع بنجاح ($count)", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { error ->
                    val errorMsg = error.message ?: "خطأ غير معروف"
                    android.util.Log.e("AtheerSDK", "❌ فشل التجهيز: $errorMsg")

                    if (showFeedback) {
                        val userMsg = if (errorMsg.contains("شبكة") || errorMsg.contains("network") || errorMsg.contains("الوقت المحدد")) {
                            "فشل الاتصال: تأكد من جودة الإنترنت أو البيانات الخلوية وحاول مجدداً"
                        } else {
                            "فشل تجهيز المفاتيح: $errorMsg"
                        }
                        Toast.makeText(this@CustomerMainActivity, userMsg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AtheerSDK", "❌ خطأ غير متوقع: ${e.message}")
            }
        }
    }

    private fun setupAtheerPayButton() {
        binding.btnAtheerPay.setOnClickListener {
            val remainingTokens = AtheerSdk.getInstance().getRemainingTokensCount()
            if (remainingTokens > 0) {
                authenticateWithBiometric()
            } else {
                Toast.makeText(this, "جاري محاولة جلب مفاتيح الدفع من السيرفر، يرجى الانتظار...", Toast.LENGTH_SHORT).show()
                provisionOfflineTokens(showFeedback = true) // هنا نطلب إظهار الخطأ إذا فشل
            }
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
        val remainingTokens = AtheerSdk.getInstance().getRemainingTokensCount()
        if (remainingTokens > 0) {
            val dialog = BottomSheetDialog(this)
            val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_nfc, null)
            dialog.setContentView(view)
            dialog.show()
        } else {
            Toast.makeText(this, "لا توجد مفاتيح جاهزة. جاري محاولة الجلب...", Toast.LENGTH_LONG).show()
            provisionOfflineTokens(showFeedback = true)
        }
    }

    /**
     * إعدادات حماية الدفع اللاتلامسي (الجدار الناري المحلي)
     */
    private fun setupSettings() {
        binding.btnSaveLimit.setOnClickListener {
            val limitText = binding.etOfflineLimit.text.toString()

            if (limitText.isNotEmpty()) {
                try {
                    val amount = limitText.toInt()

                    // استدعاء دالة الجدار الناري من الـ SDK
                    AtheerSdk.getInstance().setNextOfflineLimit(amount)

                    Toast.makeText(this, "تم تفعيل الحماية: أقصى سحب قادم هو $amount ريال", Toast.LENGTH_LONG).show()

                    // مسح التركيز لإخفاء لوحة المفاتيح
                    binding.etOfflineLimit.clearFocus()

                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "الرجاء إدخال رقم صحيح", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "الرجاء إدخال المبلغ أولاً", Toast.LENGTH_SHORT).show()
            }
        }
    }
}