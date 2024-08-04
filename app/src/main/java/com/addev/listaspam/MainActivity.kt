package com.addev.listaspam

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * MainActivity for handling permissions and requesting call screening role.
 * Requires Android P (API level 28) or higher.
 *
 * Note: For Android Q (API level 29) or higher, MyCallScreeningService should be used instead.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PERMISSION_PHONE_STATE = 1
    }

    private lateinit var intentLauncher: ActivityResultLauncher<Intent>

    /**
     * Called when the activity is starting.
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle). Otherwise it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupWindowInsets()

        setupIntentLauncher()
        checkPermissionsAndRequest()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestCallScreeningRole()
        }
    }

    /**
     * Sets up the window insets to ensure proper padding for system bars.
     */
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    /**
     * Sets up the ActivityResultLauncher for handling the result of requesting the call screening role.
     */
    private fun setupIntentLauncher() {
        intentLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == Activity.RESULT_OK) {
                    showToast(this, "Success requesting ROLE_CALL_SCREENING!")
                } else {
                    showToast(this, "Failed requesting ROLE_CALL_SCREENING")
                }
            }
    }

    /**
     * Shows a toast message.
     * @param context Context for accessing resources.
     * @param message The message to display in the toast.
     * @param duration The length of time to show the toast. Default is Toast.LENGTH_SHORT.
     */
    private fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }

    /**
     * Checks and requests the necessary permissions.
     */
    private fun checkPermissionsAndRequest() {
        val permissions = mutableListOf(
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(android.Manifest.permission.ANSWER_PHONE_CALLS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                REQUEST_PERMISSION_PHONE_STATE
            )
        }
    }

    /**
     * Requests the call screening role.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestCallScreeningRole() {
        val telecomManager = ContextCompat.getSystemService(this, TelecomManager::class.java)

        if (telecomManager == null) {
            showToast(this, getString(R.string.telecom_manager_unavailable))
            return
        }
        requestRoleForQAndAbove()
    }

    /**
     * Requests the call screening role for Android Q (API level 29) and above.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestRoleForQAndAbove() {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            intentLauncher.launch(intent)
            showToast(this, getString(R.string.call_screening_role_prompt), Toast.LENGTH_LONG)
        }
    }

    /*
    /**
     * Requests the call screening role for Android P (API level 28) and below.
     * @param telecomManager The TelecomManager for managing telecom services.
     */
    private fun requestRoleForBelowQ(telecomManager: TelecomManager) {
        if (telecomManager.defaultDialerPackage == packageName) {
            showToast(this, getString(R.string.call_screening_role_already_granted))
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
            showToast(this, getString(R.string.call_screening_role_prompt), Toast.LENGTH_LONG)
        }
    }
     */
}
