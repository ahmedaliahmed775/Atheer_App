package com.atheer.demo.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.atheer.demo.data.local.TokenManager
import com.atheer.demo.data.model.SignupRequest
import com.atheer.demo.data.network.RetrofitClient
import com.atheer.demo.databinding.ActivityRegisterBinding
import com.atheer.demo.ui.customer.CustomerMainActivity
import com.atheer.demo.ui.merchant.MerchantMainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)

        binding.btnRegister.setOnClickListener {
            attemptRegister()
        }
    }

    private fun attemptRegister() {
        val name = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        // تحديد نوع الحساب
        val role = if (binding.rbCustomer.isChecked) "customer" else "merchant"

        if (name.isEmpty() || phone.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "يرجى تعبئة جميع الحقول", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnRegister.isEnabled = false // إيقاف الزر أثناء التحميل

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = SignupRequest(name, phone, password, role)
                val response = RetrofitClient.apiService.register(request)

                withContext(Dispatchers.Main) {
                    binding.btnRegister.isEnabled = true

                    // التعديل هنا: التحقق من نجاح الطلب ووجود الـ data
                    if (response.isSuccessful && response.body()?.data != null) {
                        val authData = response.body()!!.data!!

                        // حفظ التوكن من داخل الـ data
                        tokenManager.saveAccessToken(authData.accessToken)
                        // حفظ نوع الحساب
                        tokenManager.saveUserRole(role)

                        Toast.makeText(this@RegisterActivity, "تم إنشاء الحساب بنجاح!", Toast.LENGTH_SHORT).show()

                        // توجيه المستخدم حسب نوع حسابه
                        if (role == "customer") {
                            startActivity(Intent(this@RegisterActivity, CustomerMainActivity::class.java))
                        } else {
                            startActivity(Intent(this@RegisterActivity, MerchantMainActivity::class.java))
                        }
                        finishAffinity() // إغلاق شاشات تسجيل الدخول

                    } else {
                        // إظهار رسالة الخطأ القادمة من السيرفر
                        // بدلاً من الرسالة الثابتة، سنعرض الرسالة القادمة من السيرفر مباشرة
                        val errorBody = response.errorBody()?.string()
                        Toast.makeText(this@RegisterActivity, "سبب الفشل: $errorBody", Toast.LENGTH_LONG).show()
                        android.util.Log.e("MY_SERVER_ERROR", "رسالة الخطأ هي: $errorBody")}

                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnRegister.isEnabled = true
                    Toast.makeText(this@RegisterActivity, "خطأ في الاتصال بالخادم: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}