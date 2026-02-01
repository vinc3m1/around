package com.makeramen.ouround

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.makeramen.ouround.ble.BleViewModel
import com.makeramen.ouround.ui.ScannerScreen
import com.makeramen.ouround.ui.theme.OuroundTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: BleViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        viewModel.setPermissionsGranted(allGranted)
        if (allGranted) {
            viewModel.startScanning()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[BleViewModel::class.java]

        setContent {
            OuroundTheme {
                ScannerScreen(viewModel = viewModel)
            }
        }

        requestPermissions()
    }

    override fun onStart() {
        super.onStart()
        if (viewModel.uiState.value.permissionsGranted) {
            viewModel.startScanning()
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopScanning()
    }

    private fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        )
    }
}
