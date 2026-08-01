package com.dogechat.watch

import android.app.Application
import com.dogechat.android.mesh.PowerManager
import com.dogechat.watch.notification.WearNotificationCoordinator
import com.dogechat.watch.ui.WearPeerIdentityState

class DogechatWatchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PowerManager.getInstance(applicationContext)
        WearNotificationCoordinator.getInstance(applicationContext)
        WearPeerIdentityState.initialize(applicationContext)
    }
}
