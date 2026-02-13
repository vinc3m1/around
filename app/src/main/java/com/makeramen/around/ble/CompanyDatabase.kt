package com.makeramen.around.ble

import android.content.Context
import android.util.SparseArray
import org.json.JSONArray

object CompanyDatabase {
    private var companies: SparseArray<String>? = null

    fun load(context: Context) {
        if (companies != null) return

        companies = try {
            val json = context.assets.open("company_ids.json")
                .bufferedReader()
                .use { it.readText() }

            val array = JSONArray(json)
            SparseArray<String>(array.length()).apply {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    put(obj.getInt("code"), obj.getString("name"))
                }
            }
        } catch (e: Exception) {
            SparseArray()
        }
    }

    fun getCompanyName(code: Int): String? = companies?.get(code)
}
