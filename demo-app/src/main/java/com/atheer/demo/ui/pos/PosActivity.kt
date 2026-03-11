package com.atheer.demo.ui.pos

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.atheer.demo.data.local.TokenManager
import com.atheer.demo.databinding.ActivityPosBinding
import com.atheer.demo.ui.result.TransactionResultActivity
import com.atheer.sdk.AtheerSdk
import com.atheer.sdk.model.AtheerTransaction
import com.atheer.sdk.model.ChargeRequest
import com.atheer.sdk.nfc.AtheerNfcReader
import kotlinx.coroutines.launch

class PosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPosBinding
    private lateinit var tokenManager: TokenManager
    private var merchantId: String = DEFAULT_MERCHANT_ID
    private var accessToken: String = ""
    private var amountInput: Long = 0L
    private var nfcAdapter: NfcAdapter? = null
    private var nfcReader: AtheerNfcReader? = null
    private var isReading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)
        merchantId = intent.getStringExtra(EXTRA_MERCHANT_ID) ?: DEFAULT_MERCHANT_ID
        accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN) ?: (tokenManager.getAccessToken() ?: "")

        // 🌟 إصلاح ذكي لجلب المبلغ بأمان تام
        val extraValue = intent.extras?.get(EXTRA_AMOUNT)
        amountInput = when(extraValue) {
            is Long -> extraValue
            is Double -> Math.round(extraValue * 100)
            is Int -> extraValue.toLong()
            else -> 0L
        }

        if (amountInput > 0) {
            binding.etPosAmount.setText((amountInput / 100.0).toString())
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        binding.btnBack.setOnClickListener { finish() }

        // 🌟 زر استلام المبلغ
        binding.btnStartReading.setOnClickListener { view ->
            // 🌟 إخفاء الكيبورد فوراً لمنع حجب إشارة الـ NFC
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)

            val inputStr = binding.etPosAmount.text.toString()
            if (inputStr.isNotEmpty()) {
                amountInput = Math.round((inputStr.toDoubleOrNull() ?: 0.0) * 100)
            }
            if(amountInput <= 0) {
                Toast.makeText(this, "يرجى إدخال مبلغ صحيح", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startNfcReading()
        }

        binding.btnStopReading.setOnClickListener { stopNfcReading() }
    }

    private fun startNfcReading() {
        if (isReading) return
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) {
            Toast.makeText(this, "يرجى تفعيل الـ NFC", Toast.LENGTH_SHORT).show()
            return
        }

        nfcReader = AtheerNfcReader(
            merchantId = merchantId,
            transactionCallback = { transaction ->
                runOnUiThread {
                    adapter.disableReaderMode(this) // إيقاف القراءة لتجنب التكرار
                    val capturedAtheerToken = transaction.tokenizedCard ?: transaction.transactionId ?: ""
                    processChargeWithSdk(capturedAtheerToken, transaction)
                }
            },
            errorCallback = { error ->
                runOnUiThread {
                    stopNfcReading()
                    Toast.makeText(this, "خطأ في القراءة: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        )

        adapter.enableReaderMode(this, nfcReader, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)

        isReading = true
        showFullscreenOverlay(true)
    }

    private fun stopNfcReading() {
        if (!isReading) return
        nfcAdapter?.disableReaderMode(this)
        isReading = false
        showFullscreenOverlay(false)
    }

    private fun showFullscreenOverlay(show: Boolean) {
        if (show) {
            binding.layoutFullscreenReading.alpha = 0f
            binding.layoutFullscreenReading.visibility = View.VISIBLE
            binding.layoutFullscreenReading.animate().alpha(1f).setDuration(300).start()
            binding.tvFullscreenStatus.text = "يرجى تقريب بطاقة أو هاتف العميل"

            val scaleAnim = ScaleAnimation(1f, 4.5f, 1f, 4.5f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
                duration = 1500
                repeatCount = Animation.INFINITE
            }
            val alphaAnim = AlphaAnimation(0.8f, 0f).apply {
                duration = 1500
                repeatCount = Animation.INFINITE
            }
            val set = AnimationSet(true).apply {
                addAnimation(scaleAnim)
                addAnimation(alphaAnim)
            }

            binding.pulseRipple1.startAnimation(set)
            binding.pulseRipple2.postDelayed({
                binding.pulseRipple2.visibility = View.VISIBLE
                binding.pulseRipple2.startAnimation(set)
            }, 750)

        } else {
            binding.pulseRipple1.clearAnimation()
            binding.pulseRipple2.clearAnimation()
            binding.pulseRipple2.visibility = View.GONE
            binding.layoutFullscreenReading.animate().alpha(0f).setDuration(200).withEndAction {
                binding.layoutFullscreenReading.visibility = View.GONE
            }.start()
        }
    }

    private fun processChargeWithSdk(capturedAtheerToken: String, transaction: AtheerTransaction) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }

        binding.tvFullscreenStatus.text = "جاري التحقق من السيرفر..."

        val transactionAmount = Math.round(transaction.amount.toDouble() * 100)
        val finalAmount = if (amountInput > 0L) amountInput else transactionAmount

        val chargeRequest = ChargeRequest(amount = finalAmount, currency = "YER", merchantId = merchantId, atheerToken = capturedAtheerToken)

        lifecycleScope.launch {
            try {
                // 🌟 التوكن يرسل الآن صافياً ونظيفاً بدون كلمة Bearer المكررة
                val result = AtheerSdk.getInstance().charge(chargeRequest, accessToken)

                result.onSuccess { response ->
                    navigateToResult(true, response.transactionId, finalAmount, null, null, transaction.timestamp)
                }.onFailure { error ->
                    val errorMsg = error.message ?: ""
                    var errorCode = "UNKNOWN"
                    if (errorMsg.contains("غير كاف")) errorCode = "INSUFFICIENT_FUNDS"
                    else if (errorMsg.contains("يتجاوز")) errorCode = "LIMIT_EXCEEDED"

                    navigateToResult(false, null, finalAmount, errorCode, errorMsg, transaction.timestamp)
                }
            } catch (e: Exception) {
                navigateToResult(false, null, finalAmount, "ERROR", e.message, transaction.timestamp)
            }
        }
    }

    private fun navigateToResult(isSuccess: Boolean, txId: String?, amount: Long, errCode: String?, errMsg: String?, timestamp: Long) {
        val intent = Intent(this, TransactionResultActivity::class.java).apply {
            putExtra(TransactionResultActivity.EXTRA_IS_SUCCESS, isSuccess)
            putExtra(TransactionResultActivity.EXTRA_TRANSACTION_ID, txId ?: "—")
            putExtra(TransactionResultActivity.EXTRA_AMOUNT, amount.toDouble() / 100.0)
            putExtra(TransactionResultActivity.EXTRA_CURRENCY, "YER")
            putExtra(TransactionResultActivity.EXTRA_MERCHANT_ID, merchantId)
            putExtra(TransactionResultActivity.EXTRA_TIMESTAMP, timestamp)
            if (!isSuccess) {
                putExtra("error_code", errCode)
                putExtra("error_message", errMsg)
            }
        }
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter == null) {
            Toast.makeText(this, "هذا الجهاز لا يدعم NFC", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        stopNfcReading()
    }

    companion object {
        const val EXTRA_MERCHANT_ID = "extra_merchant_id"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
        const val EXTRA_AMOUNT = "extra_amount"
        const val DEFAULT_MERCHANT_ID = "777000000"
    }
}