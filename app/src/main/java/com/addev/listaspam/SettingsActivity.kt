package com.addev.listaspam

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

class SettingsActivity : AppCompatActivity() {
    private val exportFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let {
            if (exportAllSharedPreferences(it)) {
                Toast.makeText(this, "Preferencias exportadas con éxito", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Error al exportar preferencias", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            if (importAllSharedPreferences(it)) {
                updateSettingsContainer()
                Toast.makeText(this, "Preferencias importadas con éxito", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Error al importar preferencias", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        updateSettingsContainer()

        val exportButton: Button = findViewById(R.id.btn_export)
        val importButton: Button = findViewById(R.id.btn_import)

        exportButton.setOnClickListener {
            exportFileLauncher.launch("backup_prefs.json")
        }

        importButton.setOnClickListener {
            importFileLauncher.launch(arrayOf("application/json"))
        }
    }

    private fun updateSettingsContainer() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
    }

    // Fragmento para cargar las preferencias
    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
        }
    }

    private fun exportAllSharedPreferences(uri: Uri): Boolean {
        return try {
            val prefsDir = File(applicationInfo.dataDir, "shared_prefs")
            val files = prefsDir.listFiles()
            val jsonObject = JSONObject()

            files?.forEach { file ->
                val prefName = file.nameWithoutExtension
                val sharedPreferences = getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val allEntries: Map<String, *> = sharedPreferences.all

                val prefJsonObject = JSONObject()
                for ((key, value) in allEntries) {
                    when (value) {
                        is Set<*> -> {
                            val jsonArray = JSONArray(value)
                            prefJsonObject.put(key, jsonArray)
                        }
                        else -> prefJsonObject.put(key, value)
                    }
                }

                jsonObject.put(prefName, prefJsonObject)
            }

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonObject.toString().toByteArray())
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun importAllSharedPreferences(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(jsonString)

                // Validar el archivo JSON antes de importar
                if (!isValidSharedPreferencesJson(jsonObject)) {
                    Toast.makeText(this, "El archivo JSON no es válido", Toast.LENGTH_SHORT).show()
                    return false
                }

                for (prefName in jsonObject.keys()) {
                    val sharedPreferences = getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    val editor = sharedPreferences.edit()

                    val prefJsonObject = jsonObject.getJSONObject(prefName)
                    for (key in prefJsonObject.keys()) {
                        val value = prefJsonObject.get(key)
                        when (value) {
                            is JSONArray -> {
                                val set = mutableSetOf<String>()
                                for (i in 0 until value.length()) {
                                    set.add(value.getString(i))
                                }
                                editor.putStringSet(key, set)
                            }
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Float -> editor.putFloat(key, value)
                            is Boolean -> editor.putBoolean(key, value)
                            is String -> editor.putString(key, value)
                            else -> throw IllegalArgumentException("Tipo no soportado")
                        }
                    }
                    editor.apply()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun isValidSharedPreferencesJson(jsonObject: JSONObject): Boolean {
        return try {
            for (prefName in jsonObject.keys()) {
                val prefJsonObject = jsonObject.getJSONObject(prefName)

                // Verificar cada valor en las SharedPreferences
                for (key in prefJsonObject.keys()) {
                    val value = prefJsonObject.get(key)
                    when (value) {
                        is JSONArray -> {
                            // Verificar que el JSONArray contiene solo Strings
                            for (i in 0 until value.length()) {
                                if (value.get(i) !is String) {
                                    return false
                                }
                            }
                        }
                        is Int, is Long, is Float, is Boolean, is String -> {
                            // Tipos válidos, no hacer nada
                        }
                        else -> {
                            return false
                        }
                    }
                }
            }
            true
        } catch (e: JSONException) {
            e.printStackTrace()
            false
        }
    }
}