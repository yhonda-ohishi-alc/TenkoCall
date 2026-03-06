package com.example.tenkocall.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tenkocall.R
import com.example.tenkocall.databinding.ActivityMainBinding
import com.example.tenkocall.util.CallLogUtil
import com.example.tenkocall.util.PhoneNumberUtil
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var myPhoneNumber: String? = null

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_CALL_LOG
            )
        } else {
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_CALL_LOG
            )
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            onPermissionsGranted()
        } else {
            // 電話番号権限が拒否された場合は使用不可
            val phonePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_PHONE_NUMBERS
            } else {
                Manifest.permission.READ_PHONE_STATE
            }
            if (results[phonePermission] != true) {
                binding.tvStatus.text = getString(R.string.error_no_phone_number)
                binding.btnTenko.isEnabled = false
            } else {
                binding.tvStatus.text = getString(R.string.error_permission)
                binding.btnTenko.isEnabled = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // ドライバー名を SharedPreferences から復元
        val prefs = getSharedPreferences("tenko", MODE_PRIVATE)
        val savedName = prefs.getString("driver_name", "") ?: ""
        binding.etDriverName.setText(savedName)

        setupObservers()
        setupListeners()
        checkPermissions()
    }

    private fun checkPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onPermissionsGranted()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun onPermissionsGranted() {
        myPhoneNumber = PhoneNumberUtil.getPhoneNumber(this)
        if (myPhoneNumber == null) {
            binding.tvStatus.text = getString(R.string.error_no_phone_number)
            binding.btnTenko.isEnabled = false
            return
        }

        binding.tvPhoneNumber.text = getString(R.string.label_phone_number, myPhoneNumber)

        // 着信履歴から点呼用番号を検索
        val incomingNumbers = CallLogUtil.getRecentIncomingNumbers(this)
        if (incomingNumbers.isEmpty()) {
            binding.tvStatus.text = getString(R.string.error_no_call_log)
            binding.btnTenko.isEnabled = false
            return
        }

        // 直近の着信番号の1番目を点呼用番号として使用
        // サーバー側でマスタ検証する
        val callNumber = incomingNumbers.first()

        binding.btnTenko.isEnabled = true
        binding.tvStatus.text = getString(R.string.status_ready)

        // サーバーに登録 (自分の番号 + 着信履歴の点呼用番号)
        val driverName = binding.etDriverName.text.toString().ifBlank { "未設定" }
        viewModel.register(myPhoneNumber!!, driverName, callNumber)
    }

    private fun setupListeners() {
        binding.btnTenko.setOnClickListener {
            val driverName = binding.etDriverName.text.toString().ifBlank { "未設定" }
            // ドライバー名を保存
            getSharedPreferences("tenko", MODE_PRIVATE)
                .edit().putString("driver_name", driverName).apply()

            binding.btnTenko.isEnabled = false
            startTenko(driverName)
        }
    }

    private fun startTenko(driverName: String) {
        viewModel.resetState()
        binding.tvStatus.text = getString(R.string.status_getting_location)
        binding.progressBar.visibility = View.VISIBLE

        getLocationAndSend(driverName)
    }

    @Suppress("MissingPermission")
    private fun getLocationAndSend(driverName: String) {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.sendTenko(
                        myPhoneNumber!!,
                        driverName,
                        location.latitude,
                        location.longitude
                    )
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.tvStatus.text = getString(R.string.error_location)
                    binding.btnTenko.isEnabled = true
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = getString(R.string.error_location)
                binding.btnTenko.isEnabled = true
            }
    }

    private fun setupObservers() {
        viewModel.state.observe(this) { state ->
            when (state) {
                TenkoState.READY -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnTenko.isEnabled = myPhoneNumber != null
                }
                TenkoState.GETTING_LOCATION -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                TenkoState.SENDING -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                TenkoState.CALLING -> {
                    binding.progressBar.visibility = View.GONE
                }
                TenkoState.DONE -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnTenko.isEnabled = true
                }
                TenkoState.ERROR -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnTenko.isEnabled = true
                }
                null -> {}
            }
        }

        viewModel.statusMessage.observe(this) { message ->
            binding.tvStatus.text = message
        }

        viewModel.callNumber.observe(this) { number ->
            if (number != null) {
                makeCall(number)
            }
        }

        viewModel.lastTenko.observe(this) { text ->
            binding.tvLastTenko.text = text
        }
    }

    @Suppress("MissingPermission")
    private fun makeCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        startActivity(intent)
        // 電話発信後にステートをリセット
        viewModel.resetState()
    }
}
