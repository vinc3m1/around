package com.makeramen.around.ble

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

@Serializable
private data class CompanyEntry(val code: Int, val name: String)

object CompanyDatabase {
    private var companies: Map<Int, String>? = null

    @OptIn(ExperimentalResourceApi::class)
    suspend fun load() {
        if (companies != null) return
        companies = try {
            val bytes = org.jetbrains.compose.resources.readResourceBytes("files/company_ids.json")
            val json = bytes.decodeToString()
            val entries = Json.decodeFromString<List<CompanyEntry>>(json)
            entries.associate { it.code to it.name }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getCompanyName(code: Int): String? = companies?.get(code)
}
