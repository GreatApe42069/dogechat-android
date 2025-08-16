package com.dogechat.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DogechatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Hilt initialization happens automatically
    }
}
