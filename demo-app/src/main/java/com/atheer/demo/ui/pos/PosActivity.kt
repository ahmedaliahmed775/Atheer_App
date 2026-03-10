package com.atheer.demo.ui.pos

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.atheer.demo.R
import com.atheer.demo.data.local.TokenManager
import com.atheer.demo.databinding.ActivityPosBinding
import com.atheer.demo.ui.result.TransactionResultActivity
import com.atheer.sdk.AtheerSdk
import com.atheer.sdk.model.AtheerTransaction
import com.atheer.sdk.model.ChargeRequest
import com.atheer.sdk.nfc.AtheerNfcReader
import kotlinx.coroutines.launch

/**
 * PosActivity — مسار نقطة المبيعات (SoftPOS)
 *
 * يحول الهاتف إلى جهاز نقطة بيع:
 * - يقرأ بطاقات NFC وهواتف تعمل بوضع HCE
 * - يستقبل بيانات الدفع المشفرة عبر Atheer SDK
 * - يعرض نتيجة المعاملة في شاشة مستقلة
 */
class PosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPosBinding
    private lateinit var tokenManager: TokenManager
    private var merchantId: String = DEFAULT_MERCHANT_ID
    private var accessToken: String = ""
    private var amountInput: Long = 0L // تم التحويل إلى Long
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

        // جلب المبلغ كـ Long بشكل آمن
        val doubleAmount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        amountInput = if (doubleAmount > 0) Math.round(doubleAmount * 100)
        else intent.getLongExtra(EXTRA_AMOUNT, 0L)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        checkNfcAvailability()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnStartReading.setOnClickListener { startNfcReading() }
        binding.btnStopReading.setOnClickListener { stopNfcReading() }
        binding.btnOpenNfcSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        }
    }

    /** التحقق من توفر NFC وتفعيله */
    private fun checkNfcAvailability() {
        when {
            nfcAdapter == null -> {
                binding.layoutNfcUnavailable.visibility = View.VISIBLE
                binding.tvPosStatus.text = getString(R.string.pos_nfc_unavailable)
                binding.btnStartReading.isEnabled = false
            }
            !nfcAdapter!!.isEnabled -> {
                binding.layoutNfcUnavailable.visibility = View.VISIBLE
                binding.tvPosStatus.text = getString(R.string.pos_enable_nfc)
                binding.btnOpenNfcSettings.visibility = View.VISIBLE
                binding.btnStartReading.isEnabled = false
            }
            else -> {
                binding.layoutNfcUnavailable.visibility = View.GONE
                binding.btnStartReading.isEnabled = true
            }
        }
    }

    /** بدء قراءة NFC */
    private fun startNfcReading() {
        if (isReading) return

        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) {
            checkNfcAvailability()
            return
        }

        nfcReader = AtheerNfcReader(
            merchantId = merchantId,
            transactionCallback = { transaction ->
                runOnUiThread {
                    stopNfcReading()
                    val capturedAtheerToken = transaction.tokenizedCard ?: transaction.transactionId ?: ""
                    processChargeWithSdk(capturedAtheerToken, transaction)
                }
            },
            errorCallback = { error ->
                runOnUiThread {
                    stopNfcReading()
                    binding.tvPosStatus.text = "خطأ: ${error.message}"
                    binding.progressReading.visibility = View.GONE
                }
            }
        )

        adapter.enableReaderMode(
            this,
            nfcReader,
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )

        isReading = true
        updateReadingUi(true)
    }

    /** إيقاف قراءة NFC */
    private fun stopNfcReading() {
        if (!isReading) return
        nfcAdapter?.disableReaderMode(this)
        isReading = false
        runOnUiThread { updateReadingUi(false) }
    }

    private fun updateReadingUi(reading: Boolean) {
        if (reading) {
            binding.tvPosStatus.text = getString(R.string.pos_reading)
            binding.tvPosGuide.text = getString(R.string.pos_tap_card)
            binding.progressReading.visibility = View.VISIBLE
            binding.btnStartReading.isEnabled = false
            binding.btnStopReading.isEnabled = true

            val blink = AlphaAnimation(0.3f, 1.0f).apply {
                duration = 700
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            binding.ivPosNfcIcon.startAnimation(blink)
            binding.ivPosNfcIcon.alpha = 1f
        } else {
            binding.tvPosStatus.text = getString(R.string.pos_waiting)
            binding.tvPosGuide.text = ""
            binding.progressReading.visibility = View.GONE
            binding.btnStartReading.isEnabled = nfcAdapter?.isEnabled == true
            binding.btnStopReading.isEnabled = false
            binding.ivPosNfcIcon.clearAnimation()
            binding.ivPosNfcIcon.alpha = 0.5f
        }
    }

    override fun onResume() {
        super.onResume()
        checkNfcAvailability()
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

    /** معالجة الدفع عبر SDK */
    private fun processChargeWithSdk(capturedAtheerToken: String, transaction: AtheerTransaction) {
        val transactionAmount = Math.round(transaction.amount.toDouble() * 100)
        val finalAmount = if (amountInput > 0L) amountInput else transactionAmount

        val chargeRequest = ChargeRequest(
            amount = finalAmount,
            currency = "YER",
            merchantId = DEFAULT_MERCHANT_ID,
            atheerToken = capturedAtheerToken
        )

        val token = accessToken
        lifecycleScope.launch {
            try {
                val result = AtheerSdk.getInstance().charge(chargeRequest, "Bearer $token")

                result.onSuccess { response ->
                    val intent = Intent(this@PosActivity, TransactionResultActivity::class.java).apply {
                        putExtra(TransactionResultActivity.EXTRA_TRANSACTION_ID, response.transactionId)
                        putExtra(TransactionResultActivity.EXTRA_AMOUNT, finalAmount / 100.0)
                        putExtra(TransactionResultActivity.EXTRA_CURRENCY, "YER")
                        putExtra(TransactionResultActivity.EXTRA_MERCHANT_ID, DEFAULT_MERCHANT_ID)
                        putExtra(TransactionResultActivity.EXTRA_TIMESTAMP, transaction.timestamp)
                        putExtra(TransactionResultActivity.EXTRA_IS_SUCCESS, true)
                        putExtra(TransactionResultActivity.EXTRA_IS_SYNCED, true)
                    }
                    startActivity(intent)
                    finish() // إغلاق شاشة الـ POS بعد النجاح
                }.onFailure { error ->
                    binding.tvPosStatus.text = "خطأ: ${error.message}"
                    binding.progressReading.visibility = View.GONE
                }
            } catch (e: Exception) {
                binding.tvPosStatus.text = "خطأ: ${e.message}"
                binding.progressReading.visibility = View.GONE
            }
        }
    }
}