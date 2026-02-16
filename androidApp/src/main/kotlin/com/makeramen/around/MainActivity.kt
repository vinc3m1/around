package com.makeramen.around

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.makeramen.around.ble.BleViewModel
import com.makeramen.around.ui.ScannerScreen
import com.makeramen.around.ui.theme.AroundTheme

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

        val factory = viewModelFactory {
            initializer {
                BleViewModel(applicationContext)
            }
        }
        viewModel = ViewModelProvider(this, factory)[BleViewModel::class.java]

        setContent {
            AroundTheme {
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
