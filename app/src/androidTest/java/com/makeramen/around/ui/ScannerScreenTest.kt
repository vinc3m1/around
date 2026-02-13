package com.makeramen.around.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.makeramen.around.ui.theme.AroundTheme
import org.junit.Rule
import org.junit.Test

class ScannerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsScanningMessage_whenEmptyAndScanning() {
        composeTestRule.setContent {
            AroundTheme(dynamicColor = false) {
                ScannerScreenContent(
                    uiState = TestFixtures.emptyScanning(),
                    onClearDevices = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Scanning...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Oura Devices (0)").assertIsDisplayed()
    }

    @Test
    fun showsPermissionError_whenPermissionsNotGranted() {
        composeTestRule.setContent {
            AroundTheme(dynamicColor = false) {
                ScannerScreenContent(
                    uiState = TestFixtures.permissionsRequired(),
                    onClearDevices = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Bluetooth permissions required. Please grant permissions to scan for devices.")
            .assertIsDisplayed()
    }

    @Test
    fun showsBluetoothError_whenBluetoothDisabled() {
        composeTestRule.setContent {
            AroundTheme(dynamicColor = false) {
                ScannerScreenContent(
                    uiState = TestFixtures.bluetoothDisabled(),
                    onClearDevices = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Bluetooth is disabled. Please enable Bluetooth to scan for devices.")
            .assertIsDisplayed()
    }

    @Test
    fun showsOuraDevices_withNamesAndCount() {
        composeTestRule.setContent {
            AroundTheme(dynamicColor = false) {
                ScannerScreenContent(
                    uiState = TestFixtures.ouraFound(),
                    onClearDevices = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Oura Devices (2)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Oura_ABCD").assertIsDisplayed()
        // Second device has no name, should show "Oura Ring"
        composeTestRule.onNodeWithText("Oura Ring").assertIsDisplayed()
    }

    @Test
    fun showsOuraRssiValues() {
        composeTestRule.setContent {
            AroundTheme(dynamicColor = false) {
                ScannerScreenContent(
                    uiState = TestFixtures.ouraFound(),
                    onClearDevices = {}
                )
            }
        }

        composeTestRule.onNodeWithText("-55 dBm").assertIsDisplayed()
        composeTestRule.onNodeWithText("-72 dBm").assertIsDisplayed()
    }

    @Test
    fun showsCollapsibleSectionHeaders_inFullScan() {
        composeTestRule.setContent {
            AroundTheme(dynamicColor = false) {
                ScannerScreenContent(
                    uiState = TestFixtures.fullMixedScan(),
                    onClearDevices = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Apple Devices (3)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Named Devices (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unnamed Devices (1)").assertIsDisplayed()
    }

    @Test
    fun expandsAppleSection_whenHeaderClicked() {
        composeTestRule.setContent {
            AroundTheme(dynamicColor = false) {
                ScannerScreenContent(
                    uiState = TestFixtures.fullMixedScan(),
                    onClearDevices = {}
                )
            }
        }

        // Apple section collapsed by default — device addresses not visible
        composeTestRule.onNodeWithText("11:22:33:44:55:66").assertDoesNotExist()

        // Click to expand
        composeTestRule.onNodeWithText("Apple Devices (3)").performClick()

        // Now device addresses should be visible
        composeTestRule.onNodeWithText("11:22:33:44:55:66", substring = true).assertIsDisplayed()
    }

    @Test
    fun clearButton_invokesCallback() {
        var clearCalled = false
        composeTestRule.setContent {
            AroundTheme(dynamicColor = false) {
                ScannerScreenContent(
                    uiState = TestFixtures.emptyScanning(),
                    onClearDevices = { clearCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Clear device list").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Clear device list").performClick()

        assert(clearCalled) { "Expected clearDevices callback to be invoked" }
    }

    @Test
    fun showsNoOuraMessage_whenNoOuraDevices() {
        composeTestRule.setContent {
            AroundTheme(dynamicColor = false) {
                ScannerScreenContent(
                    uiState = TestFixtures.emptyScanning(),
                    onClearDevices = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No Oura devices found yet").assertIsDisplayed()
    }
}
