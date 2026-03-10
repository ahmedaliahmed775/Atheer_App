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
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.atheer.demo.R
import com.atheer.demo.data.local.TokenManager
import com.atheer.demo.data.network.RetrofitClient
import com.atheer.demo.databinding.ActivityMerchantMainBinding
import com.atheer.demo.ui.login.LoginActivity
import com.atheer.sdk.AtheerSdk
import com.atheer.sdk.model.ChargeRequest
import com.atheer.sdk.nfc.AtheerNfcReader
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class MerchantMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMerchantMainBinding
    private lateinit var tokenManager: TokenManager
    private var nfcAdapter: NfcAdapter? = null
    private var nfcReader: AtheerNfcReader? = null
    private var enteredAmount: StringBuilder = StringBuilder()
    private var paymentDialog: BottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMerchantMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setupBottomNavigation()
        setupNumericKeypad()
        setupReceivePayment()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    binding.layoutHome.visibility = View.VISIBLE
                    binding.layoutHistory.visibility = View.GONE
                    binding.layoutSettings.visibility = View.GONE
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
        // هذا هو الزر الرئيسي لاستقبال الدفع
        binding.btnReceivePayment.setOnClickListener {
            val amountDouble = enteredAmount.toString().toDoubleOrNull()
            val amountLong = amountDouble?.let { Math.round(it * 100) }

            if (amountLong == null || amountLong <= 0L) {
                Toast.makeText(this, getString(R.string.error_invalid_amount), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // إظهار الواجهة الاحترافية فوراً
            showProfessionalPaymentSheet(amountLong)
        }
    }

    private fun showProfessionalPaymentSheet(amount: Long) {
        paymentDialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.layout_receive_payment, null)
        paymentDialog?.setContentView(view)
        paymentDialog?.setCancelable(false)

        val tvStatus = view.findViewById<TextView>(R.id.tvPaymentStatus) ?: view.findViewById(android.R.id.text1)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)

        btnCancel.setOnClickListener {
            stopNfcReading()
            paymentDialog?.dismiss()
        }

        // بدء القراءة تلقائياً عند ظهور الواجهة
        startNfcReading(amount, tvStatus)

        paymentDialog?.show()
    }

    private fun startNfcReading(amount: Long, statusTextView: TextView?) {
        val adapter = nfcAdapter
        if (adapter == null || !adapter.isEnabled) {
            statusTextView?.text = getString(R.string.pos_nfc_unavailable)
            return
        }

        statusTextView?.text = "جاري البحث عن بطاقة..."

        val merchantId = tokenManager.getUserPhone() ?: "MERCHANT"

        nfcReader = AtheerNfcReader(
            merchantId = merchantId,
            transactionCallback = { transaction ->
                triggerHapticFeedback()
                runOnUiThread {
                    statusTextView?.text = "تم التقاط البطاقة.. جاري المعالجة"
                    val token = transaction.tokenizedCard
                    if (token != null) {
                        processCharge(amount, token, merchantId)
                    } else {
                        statusTextView?.text = "فشل في قراءة التوكن"
                    }
                }
            },
            errorCallback = { error ->
                runOnUiThread {
                    statusTextView?.text = "خطأ: ${error.message}"
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
    }

    private fun stopNfcReading() {
        nfcAdapter?.disableReaderMode(this)
    }

    private fun triggerHapticFeedback() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun processCharge(amount: Long, token: String, merchantId: String) {
        lifecycleScope.launch {
            try {
                val accessToken = tokenManager.getAccessToken() ?: ""
                val chargeRequest = ChargeRequest(
                    amount = amount,
                    currency = "YER",
                    merchantId = merchantId,
                    atheerToken = token
                )

                val result = AtheerSdk.getInstance().charge(chargeRequest, "Bearer $accessToken")

                result.onSuccess {
                    paymentDialog?.dismiss()
                    stopNfcReading()
                    showSuccessDialog()
                    enteredAmount.clear()
                    updateAmountDisplay()
                }.onFailure {
                    Toast.makeText(this@MerchantMainActivity, "فشلت عملية الخصم", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MerchantMainActivity, "خطأ في الاتصال", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("تمت العملية بنجاح")
            .setMessage("تم استلام المبلغ ومزامنة العملية مع السيرفر.")
            .setPositiveButton("موافق", null)
            .show()
    }

    private fun loadHistory() {
        val context = this
        lifecycleScope.launch {
            try {
                val token = tokenManager.getAccessToken() ?: ""
                val response = RetrofitClient.apiService.getHistory("Bearer $token")

                if (response.isSuccessful && response.body()?.data?.transactions != null) {
                    displayTransactions(response.body()!!.data!!.transactions!!)
                } else {
                    binding.tvHistoryEmpty.visibility = View.VISIBLE
                    binding.rvHistory.visibility = View.GONE
                }
            } catch (e: Exception) {
                binding.tvHistoryEmpty.text = getString(R.string.error_offline)
                binding.tvHistoryEmpty.visibility = View.VISIBLE
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
        for (txn in transactions) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_transaction, container, false)
            itemView.findViewById<TextView>(R.id.tvTxnAmount).text =
                String.format("%s %s", txn.amount.toString(), txn.currency ?: "YER")
            itemView.findViewById<TextView>(R.id.tvTxnDate).text = txn.createdAt ?: ""

            val statusIcon = itemView.findViewById<ImageView>(R.id.ivTxnIcon)
            statusIcon.setColorFilter(ContextCompat.getColor(this,
                if (txn.synced) R.color.success_color else R.color.text_secondary))

            container.addView(itemView)
        }
    }

    private fun syncTransactions() {
        binding.btnSyncNow.isEnabled = false
        lifecycleScope.launch {
            try {
                loadHistory()
                Toast.makeText(this@MerchantMainActivity, "تمت المزامنة", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnSyncNow.isEnabled = true
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopNfcReading()
    }
}