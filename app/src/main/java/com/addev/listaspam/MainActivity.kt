package com.addev.listaspam

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.addev.listaspam.adapter.CallLogAdapter
import com.addev.listaspam.service.UpdateChecker
import com.addev.listaspam.util.SpamUtils
import com.addev.listaspam.util.getBlockedNumbers
import com.addev.listaspam.util.getCallLogs
import com.addev.listaspam.util.getListaSpamApiLang
import com.addev.listaspam.util.getTellowsApiCountry
import com.addev.listaspam.util.getWhitelistNumbers
import com.addev.listaspam.util.setListaSpamApiLang
import com.addev.listaspam.util.setTellowsApiCountry
import com.addev.listaspam.util.getTruecallerApiCountry
import com.addev.listaspam.util.setTruecallerApiCountry
import com.addev.listaspam.util.isUpdateCheckEnabled
import java.util.Locale
import androidx.core.net.toUri

class MainActivity : AppCompatActivity(), CallLogAdapter.OnItemChangedListener {

    private lateinit var intentLauncher: ActivityResultLauncher<Intent>
    private var permissionDeniedDialog: AlertDialog? = null
    private var callLogAdapter: CallLogAdapter? = null
    private var recyclerView: RecyclerView? = null

    private val spamUtils = SpamUtils()

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1
        private const val GITHUB_USER = "adamff-dev"
        private const val GITHUB_REPO = "spam-call-blocker-app"
        private const val ABOUT_LINK = "https://github.com/$GITHUB_USER/$GITHUB_REPO"
        private const val DONATE_LINK = "https://buymeacoffee.com/rsiztb3"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupWindowInsets()
        setupIntentLauncher()

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView?.layoutManager = LinearLayoutManager(this)

        setLanguage()
        setTellowsCountry()
        setTruecallerCountry()
        if (isUpdateCheckEnabled(this)) {
            checkUpdates()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }

            R.id.action_about -> {
                val intent = Intent(Intent.ACTION_VIEW, ABOUT_LINK.toUri())
                this.startActivity(intent)
                true
            }

            R.id.donate -> {
                val intent = Intent(this, DonationActivity::class.java)
                startActivity(intent)
                true
            }

            R.id.test_number -> {
                showNumberInputDialog()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkUpdates() {
        Thread {
            val checker = UpdateChecker(
                context = this,
                githubUser = GITHUB_USER,
                githubRepo = GITHUB_REPO
            )
            checker.checkForUpdateSync()
        }.start()
    }

    private fun setLanguage() {
        if (getListaSpamApiLang(this) != null) return

        val systemLanguage = Locale.getDefault().language.uppercase()
        val supportedLanguages = resources.getStringArray(R.array.language_values).toSet()

        val finalLang = if (supportedLanguages.contains(systemLanguage)) systemLanguage else "EN"
        setListaSpamApiLang(this, finalLang.uppercase())
    }

    private fun setTellowsCountry() {
        if (getTellowsApiCountry(this) != null) return

        val systemCountry = Locale.getDefault().country.lowercase()
        val supportedCountries =
            resources.getStringArray(R.array.entryvalues_region_preference).toSet()

        val finalCountry = if (supportedCountries.contains(systemCountry)) systemCountry else "us"
        setTellowsApiCountry(this, finalCountry)
    }

    private fun setTruecallerCountry() {
        if (getTruecallerApiCountry(this) != null) return

        val systemCountry = Locale.getDefault().country.uppercase()
        val supportedCountries =
            resources.getStringArray(R.array.truecaller_region_code).toSet()

        val finalCountry = if (supportedCountries.contains(systemCountry)) systemCountry else "US"
        setTruecallerApiCountry(this, finalCountry)
    }


    private fun showNumberInputDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.test_number))

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_PHONE
        builder.setView(input)

        builder.setPositiveButton(getString(R.string.aceptar)) { dialog, _ ->
            val number = input.text.toString().trim()
            if (number.isNotEmpty()) {
                spamUtils.checkSpamNumber(this, number, null)
            } else {
                Toast.makeText(this, getString(R.string.type_number), Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton(getString(R.string.cancelar)) { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    override fun onItemChanged(number: String) {
        val positions = mutableListOf<Int>()
        callLogAdapter?.callLogs?.forEachIndexed { index, callLog ->
            if (callLog.number == number) {
                positions.add(index)
            }
        }
        refreshCallLogs(positions)
    }

    private fun init() {
        checkPermissionsAndRequest()

        requestCallScreeningRole()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            refreshCallLogs()
        }
    }

    override fun onResume() {
        super.onResume()
        init()
    }

    private fun refreshCallLogs(positions: List<Int> = listOf()) {
        val blockedNumbers = getBlockedNumbers(this)
        val whitelistNumbers = getWhitelistNumbers(this)

        val callLogs = getCallLogs(this)

        if (callLogAdapter == null) {
            callLogAdapter = CallLogAdapter(this, callLogs, blockedNumbers, whitelistNumbers)
            recyclerView?.adapter = callLogAdapter
            callLogAdapter?.setOnItemChangedListener(this)
        } else {
            callLogAdapter?.callLogs = callLogs
            callLogAdapter?.blockedNumbers = blockedNumbers
            callLogAdapter?.whitelistNumbers = whitelistNumbers
            callLogAdapter?.notifyDataSetChanged()
        }

        if (positions.isNotEmpty()) {
            positions.forEach { position ->
                callLogAdapter?.notifyItemChanged(position)
            }
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupIntentLauncher() {
        intentLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK) {
                    showToast(this, getString(R.string.success_call_screening_role))
                } else {
                    showToast(this, getString(R.string.failed_call_screening_role))
                }
            }
    }

    private fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }

    /**
     * Checks for necessary permissions and requests them if not granted.
     *
     * This function identifies which of the required permissions (READ_CALL_LOG,
     * READ_PHONE_STATE, READ_CONTACTS, ANSWER_PHONE_CALLS, and POST_NOTIFICATIONS
     * for Android Tiramisu and above) are not granted.
     *
     * It then differentiates between permissions that haven't been requested yet
     * (missingPermissions) and those that have been denied by the user previously
     * (deniedPermissions).
     *
     * For missing permissions, it directly requests them using `ActivityCompat.requestPermissions`.
     * For denied permissions, it calls `showPermissionToastAndRequest` to inform the user
     * and guide them to settings.
     */
    private fun checkPermissionsAndRequest() {
        val permissions = mutableListOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ANSWER_PHONE_CALLS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val notGrantedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        val missingPermissions = notGrantedPermissions.filter {
            !ActivityCompat.shouldShowRequestPermissionRationale(this, it)
        }
        val deniedPermissions = notGrantedPermissions.filter {
            ActivityCompat.shouldShowRequestPermissionRationale(this, it)
        }
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                REQUEST_CODE_PERMISSIONS
            )
        }
        if (deniedPermissions.isNotEmpty()) {
            showPermissionToastAndRequest(deniedPermissions)
        }
    }

    /**
     * Shows a dialog to the user when permissions are required but not granted, and requests the missing permissions.
     * If the dialog is already visible, it will not be shown again.
     *
     * @param missingPermissions a list of permissions that are missing.
     */
    private fun showPermissionToastAndRequest(missingPermissions: List<String>) {
        val permissionNames = missingPermissions.map { "• " + getPermissionName(it) }
        val message =
            getString(R.string.permissions_required_message, permissionNames.joinToString("\n"))

        if (permissionDeniedDialog?.isShowing == true) {
            return
        }

        permissionDeniedDialog = AlertDialog.Builder(this)
            .setCancelable(false)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.permissions_required_title)
            .setMessage(message)
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setOnDismissListener {
                permissionDeniedDialog = null
            }
            .create()
        permissionDeniedDialog?.show()
    }

    /**
     * Returns the human-readable name for a given permission string.
     *
     * @param permission The permission string (e.g., Manifest.permission.READ_CALL_LOG).
     * @return The human-readable name of the permission, or the original permission string if no mapping is found.
     */
    private fun getPermissionName(permission: String): String {
        return when (permission) {
            Manifest.permission.READ_CALL_LOG -> getString(R.string.permission_read_call_log)
            Manifest.permission.READ_PHONE_STATE -> getString(R.string.permission_read_phone_state)
            Manifest.permission.READ_CONTACTS -> getString(R.string.permission_read_contacts)
            Manifest.permission.ANSWER_PHONE_CALLS -> getString(R.string.permission_answer_phone_calls)
            Manifest.permission.POST_NOTIFICATIONS -> getString(R.string.permission_post_notifications)
            else -> permission
        }
    }

    /**
     * Requests the call screening role.
     */
    private fun requestCallScreeningRole() {
        val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
        if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            intentLauncher.launch(intent)
            showToast(this, getString(R.string.call_screening_role_prompt), Toast.LENGTH_LONG)
        }
    }
}
