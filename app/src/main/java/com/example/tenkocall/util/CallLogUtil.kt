package com.example.tenkocall.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

object CallLogUtil {
    /**
     * 直近の着信履歴から電話番号リストを取得する (重複なし、新しい順)
     */
    fun getRecentIncomingNumbers(context: Context, limit: Int = 20): List<String> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val numbers = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE),
            "${CallLog.Calls.TYPE} = ?",
            arrayOf(CallLog.Calls.INCOMING_TYPE.toString()),
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            var count = 0
            while (it.moveToNext() && count < limit) {
                val raw = it.getString(numberIndex) ?: continue
                val normalized = normalizeNumber(raw)
                if (normalized.isNotEmpty() && seen.add(normalized)) {
                    numbers.add(normalized)
                    count++
                }
            }
        }

        return numbers
    }

    private fun normalizeNumber(number: String): String {
        val cleaned = number.replace(Regex("[\\s\\-()]"), "")
        return if (cleaned.startsWith("+81")) {
            "0${cleaned.substring(3)}"
        } else {
            cleaned
        }
    }
}
