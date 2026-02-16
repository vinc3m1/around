package com.makeramen.around.ble

import around.shared.generated.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

@Serializable
private data class CompanyEntry(val code: Int, val name: String)

object CompanyDatabase {
    private val _companies = MutableStateFlow<Map<Int, String>>(emptyMap())
    val companies: StateFlow<Map<Int, String>> = _companies.asStateFlow()

    init {
        @OptIn(ExperimentalResourceApi::class)
        CoroutineScope(Dispatchers.IO).launch {
            _companies.value = try {
                val bytes = Res.readBytes("files/company_ids.json")
                val json = bytes.decodeToString()
                val entries = Json.decodeFromString<List<CompanyEntry>>(json)
                entries.associate { it.code to it.name }
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }
}
