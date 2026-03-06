package com.example.tenkocall.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

object PhoneNumberUtil {
    fun getPhoneNumber(context: Context): String? {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_PHONE_NUMBERS
        } else {
            Manifest.permission.READ_PHONE_STATE
        }

        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val number = try {
            tm.line1Number
        } catch (e: SecurityException) {
            null
        }

        if (number.isNullOrBlank()) return null

        // 正規化: 先頭の+81を0に変換
        return if (number.startsWith("+81")) {
            "0${number.substring(3)}"
        } else {
            number
        }
    }
}
