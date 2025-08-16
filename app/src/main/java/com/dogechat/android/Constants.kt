package com.dogechat.android

import java.util.UUID

object Constants {
    const val MESSAGE_READ = 1
    const val MESSAGE_WRITE = 2
    const val MESSAGE_TOAST = 3

    // Example UUIDs — replace with actual UUIDs if you have them
    val SERVICE_UUID: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
}
