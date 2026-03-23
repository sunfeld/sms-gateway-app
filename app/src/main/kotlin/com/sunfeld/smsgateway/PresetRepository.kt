package com.sunfeld.smsgateway

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PresetRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun save(preset: HidPreset) {
        val presets = getAllMutable()
        presets.removeAll { it.id == preset.id }
        presets.add(preset)
        persist(presets)
    }

    fun delete(id: String) {
        val presets = getAllMutable()
        presets.removeAll { it.id == id }
        persist(presets)
    }

    fun getAll(): List<HidPreset> {
        val json = prefs.getString(KEY_PRESETS, null) ?: return emptyList()
        val type = object : TypeToken<List<HidPreset>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun getById(id: String): HidPreset? = getAll().find { it.id == id }

    private fun getAllMutable(): MutableList<HidPreset> = getAll().toMutableList()

    private fun persist(presets: List<HidPreset>) {
        prefs.edit().putString(KEY_PRESETS, gson.toJson(presets)).apply()
    }

    companion object {
        const val PREFS_NAME = "bt_hid_presets"
        const val KEY_PRESETS = "presets"

        fun serializePresets(presets: List<HidPreset>): String = Gson().toJson(presets)

        fun deserializePresets(json: String): List<HidPreset> {
            val type = object : TypeToken<List<HidPreset>>() {}.type
            return Gson().fromJson(json, type) ?: emptyList()
        }
    }
}
