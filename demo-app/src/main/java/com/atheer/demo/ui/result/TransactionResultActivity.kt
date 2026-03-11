package com.atheer.demo.ui.result

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.atheer.demo.R
import com.atheer.demo.databinding.ActivityTransactionResultBinding
import com.atheer.demo.ui.dashboard.DashboardActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.animation.OvershootInterpolator

/**
 * TransactionResultActivity — شاشة نتيجة المعاملة الاحترافية
 */
class TransactionResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        displayResult()
        setupButtons()
    }



// ... بقية الأكواد

    private fun displayResult() {
        val isSuccess = intent.getBooleanExtra(EXTRA_IS_SUCCESS, true)
        val errorCode = intent.getStringExtra("error_code")
        val errorMessage = intent.getStringExtra("error_message")

        val transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID) ?: "—"
        val amountDouble = intent.getDoubleExtra(EXTRA_AMOUNT, -1.0)
        val amountRiyal = if (amountDouble != -1.0) amountDouble else intent.getLongExtra(EXTRA_AMOUNT, 0L) / 100.0
        val currency = intent.getStringExtra(EXTRA_CURRENCY) ?: "YER"
        val merchantId = intent.getStringExtra(EXTRA_MERCHANT_ID) ?: "—"
        val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())

        // 🌟 تهيئة الأيقونة لتكون مخفية ومصغرة قبل الانطلاق (للأنيميشن)
        binding.iconContainer.scaleX = 0f
        binding.iconContainer.scaleY = 0f
        binding.iconContainer.alpha = 0f

        if (isSuccess) {
            binding.ivResultIcon.setImageResource(R.drawable.ic_check_circle)
            binding.iconContainer.setBackgroundResource(R.drawable.bg_success_circle)
            binding.tvResultTitle.text = getString(R.string.result_success_title)
            binding.tvResultTitle.setTextColor(ContextCompat.getColor(this, R.color.success_color))
            binding.tvSyncStatus.text = "تم التأكيد والمزامنة بنجاح"
            binding.tvSyncStatus.setTextColor(ContextCompat.getColor(this, R.color.success_color))

            triggerFeedback(true)
            playSound(R.raw.success)
        } else {
            binding.ivResultIcon.setImageResource(R.drawable.ic_error_circle)
            binding.iconContainer.setBackgroundResource(R.drawable.bg_error_circle)
            binding.tvResultTitle.setTextColor(ContextCompat.getColor(this, R.color.error_color))

            when (errorCode) {
                "INSUFFICIENT_FUNDS" -> {
                    binding.tvResultTitle.text = "رصيد غير كافٍ"
                    binding.tvSyncStatus.text = "يرجى من العميل شحن محفظته"
                }
                "LIMIT_EXCEEDED" -> {
                    binding.tvResultTitle.text = "تجاوز السقف"
                    binding.tvSyncStatus.text = "المبلغ أكبر من سقف البطاقة"
                }
                else -> {
                    binding.tvResultTitle.text = getString(R.string.result_failed_title)
                    binding.tvSyncStatus.text = errorMessage ?: "خطأ في المعالجة"
                }
            }

            triggerFeedback(false)
            playSound(R.raw.error)
        }

        // 🌟 إطلاق حركة الارتداد (Pop Animation) للأيقونة
        binding.iconContainer.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(600)
            .setInterpolator(OvershootInterpolator(1.5f)) // تأثير الارتداد
            .start()

        binding.tvTransactionId.text = transactionId
        binding.tvAmount.text = "%.2f".format(amountRiyal)
        binding.tvCurrency.text = if (currency == "YER") "ريال يمني" else currency
        binding.tvMerchant.text = merchantId

        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        binding.tvTimestamp.text = dateFormat.format(Date(timestamp))
    }

    private fun triggerFeedback(success: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (success) {
            // اهتزاز طويل ومريح للنجاح
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } else {
            // اهتزاز متقطع (نبضتين) للتنبيه بوجود خطأ
            val pattern = longArrayOf(0, 200, 100, 200)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }

    private fun playSound(resId: Int) {
        try {
            val mediaPlayer = MediaPlayer.create(this, resId)
            mediaPlayer.setOnCompletionListener { it.release() }
            mediaPlayer.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupButtons() {
        binding.btnNewTransaction.setOnClickListener {
            finish()
        }

        binding.btnBackDashboard.setOnClickListener {
            val dashboardIntent = Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(dashboardIntent)
            finish()
        }

        // 🌟 برمجة زر مشاركة الإيصال (تم إزالة علامة الاستفهام ليعمل بشكل صحيح)
        binding.btnShareReceipt.setOnClickListener {
            val transactionId = binding.tvTransactionId.text.toString()
            val amountStr = binding.tvAmount.text.toString()
            val isSuccess = intent.getBooleanExtra(EXTRA_IS_SUCCESS, true)
            val statusText = if (isSuccess) "تم الدفع بنجاح ✅" else "عملية مرفوضة ❌"

            val receiptText = """
                *إيصال دفع - أثير (Atheer Pay)*
                -----------------------
                رقم العملية: $transactionId
                المبلغ: $amountStr ${binding.tvCurrency.text}
                التاريخ: ${binding.tvTimestamp.text}
                الحالة: $statusText
                -----------------------
                شكراً لاستخدامك شبكة أثير للمدفوعات.
            """.trimIndent()

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, receiptText)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, "مشاركة الإيصال"))
        }
    }

    companion object {
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_CURRENCY = "extra_currency"
        const val EXTRA_MERCHANT_ID = "extra_merchant_id"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
        const val EXTRA_IS_SUCCESS = "extra_is_success"
        const val EXTRA_IS_SYNCED = "extra_is_synced"
    }
}