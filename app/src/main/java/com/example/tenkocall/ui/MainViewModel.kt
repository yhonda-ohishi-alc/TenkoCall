package com.example.tenkocall.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tenkocall.data.model.RegisterRequest
import com.example.tenkocall.data.model.TenkoRequest
import com.example.tenkocall.data.remote.ApiClient
import kotlinx.coroutines.launch

enum class TenkoState {
    READY,
    GETTING_LOCATION,
    SENDING,
    CALLING,
    DONE,
    ERROR
}

class MainViewModel : ViewModel() {
    private val _state = MutableLiveData(TenkoState.READY)
    val state: LiveData<TenkoState> = _state

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    private val _callNumber = MutableLiveData<String?>()
    val callNumber: LiveData<String?> = _callNumber

    private val _lastTenko = MutableLiveData<String>()
    val lastTenko: LiveData<String> = _lastTenko

    var registeredCallNumber: String? = null
        private set

    fun register(phoneNumber: String, driverName: String, callNumber: String) {
        viewModelScope.launch {
            try {
                val response = ApiClient.api.register(
                    RegisterRequest(phoneNumber, driverName, callNumber)
                )
                if (response.success) {
                    registeredCallNumber = response.call_number
                    _state.value = TenkoState.READY
                    _statusMessage.value = "登録完了。点呼ボタンを押してください"
                } else {
                    _state.value = TenkoState.ERROR
                    _statusMessage.value = response.error ?: "登録に失敗しました"
                }
            } catch (e: Exception) {
                _state.value = TenkoState.ERROR
                _statusMessage.value = "サーバーに接続できません: ${e.message}"
            }
        }
    }

    fun sendTenko(phoneNumber: String, driverName: String, latitude: Double, longitude: Double) {
        _state.value = TenkoState.SENDING
        _statusMessage.value = "サーバーに送信中..."

        viewModelScope.launch {
            try {
                val response = ApiClient.api.sendTenko(
                    TenkoRequest(phoneNumber, driverName, latitude, longitude)
                )
                if (response.success) {
                    val callTo = response.call_number ?: registeredCallNumber
                    if (callTo != null) {
                        _callNumber.value = callTo
                        _state.value = TenkoState.CALLING
                        _statusMessage.value = "電話を発信します: $callTo"
                        _lastTenko.value = "最終点呼: ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.JAPAN).format(java.util.Date())}"
                    } else {
                        _state.value = TenkoState.ERROR
                        _statusMessage.value = "発信先番号が登録されていません"
                    }
                } else {
                    _state.value = TenkoState.ERROR
                    _statusMessage.value = response.error ?: "送信に失敗しました"
                }
            } catch (e: Exception) {
                _state.value = TenkoState.ERROR
                _statusMessage.value = "サーバーに接続できません: ${e.message}"
            }
        }
    }

    fun resetState() {
        _state.value = TenkoState.READY
        _statusMessage.value = "点呼ボタンを押してください"
        _callNumber.value = null
    }
}
