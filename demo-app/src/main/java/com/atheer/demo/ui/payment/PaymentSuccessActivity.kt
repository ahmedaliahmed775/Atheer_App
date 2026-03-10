package com.atheer.demo.ui.payment

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.atheer.demo.R

/**
 * PaymentSuccessActivity — شاشة نجاح الدفع بخلفية شفافة
 *
 * تعرض علامة ✓ خضراء مع صوت "Beep"
 * ثم تُغلق تلقائياً بعد ثانيتين
 * تُستخدم لإشعارات HCE في الخلفية
 */
class PaymentSuccessActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_success)

        // عرض أيقونة النجاح
        val ivSuccess = findViewById<ImageView>(R.id.ivSuccessIcon)
        val tvSuccess = findViewById<TextView>(R.id.tvSuccessMessage)

        ivSuccess.setImageResource(R.drawable.ic_check_circle)
        tvSuccess.text = getString(R.string.payment_success_message)

        // تشغيل صوت Beep
        playBeepSound()

        // إغلاق بعد ثانيتين
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 2000)
    }

    private fun playBeepSound() {
        try {
            mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
            mediaPlayer?.start()
        } catch (e: Exception) {
            // تجاهل خطأ الصوت
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
