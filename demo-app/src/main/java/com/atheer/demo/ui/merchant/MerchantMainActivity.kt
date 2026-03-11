package com.atheer.demo.ui.merchant

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.atheer.demo.R
import com.atheer.demo.data.local.TokenManager
import com.atheer.demo.data.network.RetrofitClient
import com.atheer.demo.databinding.ActivityMerchantMainBinding
import com.atheer.demo.ui.login.LoginActivity
import com.atheer.demo.ui.result.TransactionResultActivity
import com.atheer.sdk.AtheerSdk
import com.atheer.sdk.model.ChargeRequest
import com.atheer.sdk.nfc.AtheerNfcReader
import kotlinx.coroutines.launch
import android.util.Log

class MerchantMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMerchantMainBinding
    private lateinit var tokenManager: TokenManager
    private var nfcAdapter: NfcAdapter? = null
    private var nfcReader: AtheerNfcReader? = null
    private var enteredAmount: StringBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMerchantMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setupBottomNavigation()
        setupNumericKeypad()
        setupReceivePayment()

        // 🌟 جلب الرصيد فور فتح التطبيق
        fetchBalance()
    }

    override fun onResume() {
        super.onResume()
        // 🌟 تحديث الرصيد تلقائياً عند العودة من شاشة الدفع
        fetchBalance()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    binding.layoutHome.visibility = View.VISIBLE
                    binding.layoutHistory.visibility = View.GONE
                    binding.layoutSettings.visibility = View.GONE
                    fetchBalance()
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

        binding.btnSyncNow.setOnClickListener {
            syncTransactions()
        }
    }

    private fun setupNumericKeypad() {
        val buttons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3,
            binding.btn4, binding.btn5, binding.btn6, binding.btn7,
            binding.btn8, binding.btn9, binding.btnDot
        )

        buttons.forEach { btn ->
            btn.setOnClickListener {
                val digit = (it as TextView).text.toString()
                if (digit == "." && enteredAmount.contains(".")) return@setOnClickListener
                enteredAmount.append(digit)
                updateAmountDisplay()
            }
        }

        binding.btnBackspace.setOnClickListener {
            if (enteredAmount.isNotEmpty()) {
                enteredAmount.deleteCharAt(enteredAmount.length - 1)
                updateAmountDisplay()
            }
        }

        binding.btnClear.setOnClickListener {
            enteredAmount.clear()
            updateAmountDisplay()
        }
    }

    private fun updateAmountDisplay() {
        val displayText = if (enteredAmount.isEmpty()) "0.00" else enteredAmount.toString()
        binding.tvAmountDisplay.text = displayText
    }

    private fun setupReceivePayment() {
        binding.btnReceivePayment.setOnClickListener {
            val amountDouble = enteredAmount.toString().toDoubleOrNull()
            val amountLong = amountDouble?.let { Math.round(it * 100) }

            if (amountLong == null || amountLong <= 0L) {
                Toast.makeText(this, getString(R.string.error_invalid_amount), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🌟 تشغيل الرادار في نفس الواجهة
            startIntegratedNfcReading(amountLong)
        }

        // زر الإلغاء في الرادار
        binding.btnCancelReading.setOnClickListener {
            stopNfcReading()
            showFullscreenOverlay(false)
        }
    }

    private fun startIntegratedNfcReading(amount: Long) {
        val adapter = nfcAdapter
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "يرجى تفعيل الـ NFC", Toast.LENGTH_SHORT).show()
            return
        }

        showFullscreenOverlay(true)
        val merchantId = tokenManager.getUserPhone() ?: "777000000"

        nfcReader = AtheerNfcReader(
            merchantId = merchantId,
            transactionCallback = { transaction ->
                runOnUiThread {
                    adapter.disableReaderMode(this)
                    triggerHapticFeedback()
                    binding.tvFullscreenStatus.text = "جاري المعالجة..."
                    processCharge(amount, transaction.tokenizedCard ?: "", merchantId)
                }
            },
            errorCallback = { error ->
                runOnUiThread {
                    stopNfcReading()
                    showFullscreenOverlay(false)
                    Toast.makeText(this, "خطأ: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        )

        adapter.enableReaderMode(this, nfcReader, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B, null)
    }

    private fun showFullscreenOverlay(show: Boolean) {
        if (show) {
            binding.layoutFullscreenReading.visibility = View.VISIBLE
            binding.layoutFullscreenReading.alpha = 0f
            binding.layoutFullscreenReading.animate().alpha(1f).setDuration(300).start()

            val scaleAnim = ScaleAnimation(1f, 4.5f, 1f, 4.5f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
                duration = 1500
                repeatCount = Animation.INFINITE
            }
            val alphaAnim = AlphaAnimation(0.8f, 0f).apply { duration = 1500; repeatCount = Animation.INFINITE }
            val set = AnimationSet(true).apply { addAnimation(scaleAnim); addAnimation(alphaAnim) }

            binding.pulseRipple1.startAnimation(set)
            binding.pulseRipple2.postDelayed({
                binding.pulseRipple2.visibility = View.VISIBLE
                binding.pulseRipple2.startAnimation(set)
            }, 750)
        } else {
            binding.pulseRipple1.clearAnimation()
            binding.pulseRipple2.clearAnimation()
            binding.layoutFullscreenReading.animate().alpha(0f).setDuration(200).withEndAction {
                binding.layoutFullscreenReading.visibility = View.GONE
            }.start()
        }
    }

    private fun processCharge(amount: Long, token: String, merchantId: String) {
        lifecycleScope.launch {
            try {
                val accessToken = tokenManager.getAccessToken() ?: ""
                val chargeRequest = ChargeRequest(amount, "YER", merchantId, token)

                val result = AtheerSdk.getInstance().charge(chargeRequest, accessToken)

                result.onSuccess { response ->
                    showFullscreenOverlay(false)
                    val intent = Intent(this@MerchantMainActivity, TransactionResultActivity::class.java).apply {
                        putExtra(TransactionResultActivity.EXTRA_IS_SUCCESS, true)
                        putExtra(TransactionResultActivity.EXTRA_TRANSACTION_ID, response.transactionId)
                        putExtra(TransactionResultActivity.EXTRA_AMOUNT, amount.toDouble() / 100.0)
                        putExtra(TransactionResultActivity.EXTRA_CURRENCY, "YER")
                        putExtra(TransactionResultActivity.EXTRA_MERCHANT_ID, merchantId)
                        putExtra(TransactionResultActivity.EXTRA_TIMESTAMP, System.currentTimeMillis())
                    }
                    startActivity(intent)
                    enteredAmount.clear()
                    updateAmountDisplay()
                }.onFailure { error ->
                    showFullscreenOverlay(false)
                    val intent = Intent(this@MerchantMainActivity, TransactionResultActivity::class.java).apply {
                        putExtra(TransactionResultActivity.EXTRA_IS_SUCCESS, false)
                        putExtra("error_message", error.message)
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                showFullscreenOverlay(false)
                Toast.makeText(this@MerchantMainActivity, "خطأ في الاتصال", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchBalance() {
        lifecycleScope.launch {
            try {
                val token = tokenManager.getAccessToken() ?: ""
                val response = RetrofitClient.apiService.getBalance("Bearer $token")
                if (response.isSuccessful && response.body()?.success == true) {
                    val balance = response.body()?.data?.balance ?: 0.0
                    binding.tvMerchantBalance.text = "${String.format("%,.2f", balance)} ريال"
                }
            } catch (e: Exception) { Log.e("Balance", e.message ?: "") }
        }
    }

    private fun stopNfcReading() { nfcAdapter?.disableReaderMode(this) }

    private fun triggerHapticFeedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                val token = tokenManager.getAccessToken() ?: ""
                val response = RetrofitClient.apiService.getHistory("Bearer $token")
                if (response.isSuccessful) displayTransactions(response.body()?.data?.transactions ?: emptyList())
            } catch (e: Exception) { }
        }
    }

    private fun displayTransactions(transactions: List<com.atheer.demo.data.model.TransactionItem>) {
        binding.rvHistory.removeAllViews()
        if (transactions.isEmpty()) { binding.tvHistoryEmpty.visibility = View.VISIBLE; return }
        binding.tvHistoryEmpty.visibility = View.GONE
        for (txn in transactions) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_transaction, binding.rvHistory, false)
            itemView.findViewById<TextView>(R.id.tvTxnAmount).text = "${txn.amount} YER"
            itemView.findViewById<TextView>(R.id.tvTxnDate).text = txn.createdAt ?: ""
            binding.rvHistory.addView(itemView)
        }
    }

    private fun syncTransactions() { fetchBalance(); loadHistory() }

    override fun onPause() { super.onPause(); stopNfcReading() }
}