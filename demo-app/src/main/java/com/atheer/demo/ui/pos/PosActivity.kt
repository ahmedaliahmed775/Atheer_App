package com.atheer.demo.ui.pos

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Toast
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
 */
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

        adapter.enableReaderMode(this, nfcReader,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)

        isReading = true
        updateReadingUi(true)
    }

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

    /** معالجة الدفع عبر SDK */
    private fun processChargeWithSdk(capturedAtheerToken: String, transaction: AtheerTransaction) {
        // 🌟 تفعيل الاهتزاز عند التقاط البطاقة
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }

        // 🌟 إظهار رسالة توضيحية للتاجر
        Toast.makeText(this, "تم التقاط البطاقة.. جاري المعالجة", Toast.LENGTH_SHORT).show()

        val transactionAmount = Math.round(transaction.amount.toDouble() * 100)
        val finalAmount = if (amountInput > 0L) amountInput else transactionAmount

        val chargeRequest = ChargeRequest(
            amount = finalAmount,
            currency = "YER",
            merchantId = DEFAULT_MERCHANT_ID,
            atheerToken = capturedAtheerToken
        )

        lifecycleScope.launch {
            try {
                val result = AtheerSdk.getInstance().charge(chargeRequest, "Bearer $accessToken")

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
                    finish()
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
}