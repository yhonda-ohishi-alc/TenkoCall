package com.example.tenkocall.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tenkocall.R
import com.example.tenkocall.databinding.ActivityMainBinding
import com.example.tenkocall.util.PhoneNumberUtil
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var myPhoneNumber: String? = null
    private var scannedCallNumber: String? = null

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA
            )
        } else {
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA
            )
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            onPermissionsGranted()
        } else {
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

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val callNumber = result.contents.trim()
            scannedCallNumber = callNumber

            // SharedPreferences に保存
            getSharedPreferences("tenko", MODE_PRIVATE)
                .edit().putString("call_number", callNumber).apply()

            // サーバーに登録
            val driverName = binding.etDriverName.text.toString().ifBlank { "未設定" }
            getSharedPreferences("tenko", MODE_PRIVATE)
                .edit().putString("driver_name", driverName).apply()

            viewModel.register(myPhoneNumber!!, driverName, callNumber)

            binding.tvCallNumber.text = getString(R.string.label_call_number, callNumber)
            binding.tvCallNumber.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // SharedPreferences から復元
        val prefs = getSharedPreferences("tenko", MODE_PRIVATE)
        val savedName = prefs.getString("driver_name", "") ?: ""
        val savedCallNumber = prefs.getString("call_number", null)
        binding.etDriverName.setText(savedName)

        if (savedCallNumber != null) {
            scannedCallNumber = savedCallNumber
            binding.tvCallNumber.text = getString(R.string.label_call_number, savedCallNumber)
            binding.tvCallNumber.visibility = View.VISIBLE
        }

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
        binding.btnScanQr.isEnabled = true

        // 保存済みの点呼先がある場合、自動で再登録
        if (scannedCallNumber != null) {
            val driverName = binding.etDriverName.text.toString().ifBlank { "未設定" }
            viewModel.register(myPhoneNumber!!, driverName, scannedCallNumber!!)
        } else {
            binding.tvStatus.text = getString(R.string.status_scan_qr)
            binding.btnTenko.isEnabled = false
        }
    }

    private fun setupListeners() {
        binding.btnScanQr.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("点呼用QRコードをスキャンしてください")
                setBeepEnabled(false)
                setOrientationLocked(true)
            }
            qrScanLauncher.launch(options)
        }

        binding.btnTenko.setOnClickListener {
            val driverName = binding.etDriverName.text.toString().ifBlank { "未設定" }
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
                    binding.btnTenko.isEnabled = myPhoneNumber != null && scannedCallNumber != null
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
                    binding.btnTenko.isEnabled = scannedCallNumber != null
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
        viewModel.resetState()
    }
}
