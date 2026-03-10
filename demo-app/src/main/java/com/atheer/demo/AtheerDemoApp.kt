package com.atheer.demo

import android.app.Application
import android.util.Log
import com.atheer.sdk.AtheerSdk

/**
 * Application class لتطبيق Atheer Demo
 * يتولى تهيئة Atheer SDK عند بدء التطبيق
 */
class AtheerDemoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initSdk()
    }

    private fun initSdk() {
        try {
            AtheerSdk.init(
                context = this,
                merchantId = "777000000",
                apiBaseUrl = "https://sea-lion-app-jnyrn.ondigitalocean.app"
            )
            Log.i(TAG, "Atheer SDK initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Atheer SDK: ${e.message}", e)
        } catch (e: LinkageError) {
            Log.e(TAG, "Failed to initialize Atheer SDK (missing dependency): ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "AtheerDemoApp"
    }
}
