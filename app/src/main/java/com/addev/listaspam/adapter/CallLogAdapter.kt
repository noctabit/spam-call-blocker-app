package com.addev.listaspam.adapter

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.addev.listaspam.R
import com.addev.listaspam.util.ApiUtils
import com.addev.listaspam.util.CallLogEntry
import com.addev.listaspam.util.addNumberToWhitelist
import com.addev.listaspam.util.getListaSpamApiLang
import com.addev.listaspam.util.getTellowsApiCountry
import com.addev.listaspam.util.removeSpamNumber
import com.addev.listaspam.util.removeWhitelistNumber
import com.addev.listaspam.util.saveSpamNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class CallLogAdapter(
    private val context: Context,
    var callLogs: List<CallLogEntry>,
    var blockedNumbers: Set<String>,
    var whitelistNumbers: Set<String>
) : RecyclerView.Adapter<CallLogAdapter.CallLogViewHolder>() {

    interface OnItemChangedListener {
        fun onItemChanged(number: String)
    }

    companion object {
        const val GOOGLE_URL_TEMPLATE = "https://www.google.com/search?q=%s"
        const val LISTA_SPAM_URL_TEMPLATE = "https://www.listaspam.com/busca.php?Telefono=%s"
        const val UNKNOWN_PHONE_URL_TEMPLATE = "https://www.unknownphone.com/phone/%s"
    }

    private val locale = Locale.getDefault()

    private val formatter: SimpleDateFormat = if (locale.language == "en") {
        SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", locale)
    } else {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", locale)
    }

    private var onItemChangedListener: OnItemChangedListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallLogViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_call_log, parent, false)
        return CallLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallLogViewHolder, position: Int) {
        val callLog = callLogs[position]
        holder.bind(
            callLog,
            blockedNumbers.contains(callLog.number),
            whitelistNumbers.contains(callLog.number)
        )
    }

    override fun getItemCount(): Int = callLogs.size

    inner class CallLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val numberTextView: TextView = itemView.findViewById(R.id.numberTextView)
        private val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
        private val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)
        private val actionTextView: TextView = itemView.findViewById(R.id.actionTextView)
        private val overflowMenuButton = itemView.findViewById<ImageButton>(R.id.overflowMenuButton)

        fun bind(callLog: CallLogEntry, isBlocked: Boolean, isWhitelisted: Boolean = false) {
            val number = callLog.number ?: "Unknown number"
            val contactName = getContactName(context, number)
            val textToShow = if (isBlocked) {
                context.getString(R.string.blocked_text_format, contactName ?: number)
            } else if (isWhitelisted) {
                context.getString(R.string.whitelisted_text_format, contactName ?: number)
            } else {
                contactName ?: number
            }
            numberTextView.text = textToShow
            dateTextView.text = formatter.format(callLog.date)
            durationTextView.text = context.getString(R.string.duration_label, callLog.duration)

            val action = when (callLog.type) {
                CallLog.Calls.INCOMING_TYPE -> context.getString(R.string.call_incoming)
                CallLog.Calls.MISSED_TYPE -> context.getString(R.string.call_missed)
                CallLog.Calls.REJECTED_TYPE -> context.getString(R.string.call_rejected)
                CallLog.Calls.BLOCKED_TYPE -> context.getString(R.string.call_blocked)
                else -> context.getString(R.string.call_unknown)
            }

            actionTextView.text = action

            if (callLog.type == CallLog.Calls.BLOCKED_TYPE) {
                actionTextView.setTextColor(
                    ContextCompat.getColor(
                        context,
                        android.R.color.holo_red_light
                    )
                )
            } else {
                actionTextView.setTextColor(
                    ContextCompat.getColor(
                        context,
                        android.R.color.darker_gray
                    )
                )
            }

            when {
                isBlocked -> numberTextView.setTextColor(
                    ContextCompat.getColor(
                        context,
                        android.R.color.holo_red_light
                    )
                )

                isWhitelisted -> numberTextView.setTextColor(
                    ContextCompat.getColor(
                        context,
                        android.R.color.holo_blue_dark
                    )
                )

                else -> {
                    numberTextView.setTextColor(ContextCompat.getColor(context, R.color.textColor))
                }
            }

            if (number.isBlank()) {
                overflowMenuButton.visibility = View.GONE
                return
            }

            overflowMenuButton.visibility = View.VISIBLE
            overflowMenuButton.setOnClickListener {
                val popupMenu = PopupMenu(
                    itemView.context,
                    overflowMenuButton,
                    Gravity.NO_GRAVITY,
                    android.R.attr.popupMenuStyle,
                    R.style.PopupMenuStyle
                )
                popupMenu.inflate(R.menu.item_actions)

                setDynamicTitles(popupMenu, isBlocked, isWhitelisted)

                popupMenu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.search_action -> {
                            searchAction(number)
                            true
                        }

                        R.id.open_report_alert -> {
                            openReportAlert(number)
                            true
                        }

                        R.id.open_in_lista_spam_action -> {
                            openInListaSpam(number)
                            true
                        }

                        R.id.open_in_unknown_phone_action -> {
                            openInUnknownPhone(number)
                            true
                        }

                        R.id.whitelist_action -> {
                            if (isWhitelisted) {
                                removeWhitelistNumber(context, number)
                            } else {
                                addNumberToWhitelist(context, number)
                            }
                            onItemChangedListener?.onItemChanged(number)
                            true
                        }

                        R.id.block_action -> {
                            if (isBlocked) {
                                removeSpamNumber(context, number)
                            } else {
                                saveSpamNumber(context, number)
                            }
                            onItemChangedListener?.onItemChanged(number)
                            true
                        }

                        else -> false
                    }
                }
                popupMenu.show()
            }

            // Copy number to clipboard
            itemView.setOnLongClickListener {
                clipboardAction(number)
                true
            }
        }

        private fun setDynamicTitles(
            popupMenu: PopupMenu,
            isBlocked: Boolean,
            isWhitelisted: Boolean
        ) {
            val blockMenuItem = popupMenu.menu.findItem(R.id.block_action)
            val whitelistMenuItem = popupMenu.menu.findItem(R.id.whitelist_action)
            if (isBlocked) {
                blockMenuItem.setTitle(R.string.unblock)
            } else {
                blockMenuItem.setTitle(R.string.block)
            }

            if (isWhitelisted) {
                whitelistMenuItem.setTitle(R.string.remove_from_whitelist)
            } else {
                whitelistMenuItem.setTitle(R.string.add_to_whitelist)
            }
        }
    }

    private fun getContactName(context: Context, phoneNumber: String): String? {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val contentResolver = context.contentResolver
            val uri =
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon().appendPath(phoneNumber)
                    .build()
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                }
            }
        }

        return null
    }

    private fun clipboardAction(number: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("phone number", number)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(
            context,
            context.getString(R.string.number_copied_to_clipboard),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun openInListaSpam(number: String) {
        val url = String.format(LISTA_SPAM_URL_TEMPLATE, number)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    private fun openInUnknownPhone(number: String) {
        val url = String.format(UNKNOWN_PHONE_URL_TEMPLATE, number)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    private fun searchAction(number: String) {
        val url = String.format(GOOGLE_URL_TEMPLATE, number)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    fun setOnItemChangedListener(listener: OnItemChangedListener) {
        this.onItemChangedListener = listener
    }

    private fun openReportAlert(number: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_report, null)
        val messageEditText = dialogView.findViewById<EditText>(R.id.messageEditText)
        val spamRadio = dialogView.findViewById<RadioButton>(R.id.radioSpam)
        val noSpamRadio = dialogView.findViewById<RadioButton>(R.id.radioNoSpam)

        messageEditText.hint = context.getString(R.string.report_hint)
        spamRadio.text = context.getString(R.string.report_spam)
        noSpamRadio.text = context.getString(R.string.report_not_spam)

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.report_title))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.accept), null)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .create()
            .also { alertDialog ->
                alertDialog.setOnShowListener {
                    val button = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    button.setOnClickListener {
                        val message = messageEditText.text.toString().trim()
                        val wordCount = message.split("\\s+".toRegex()).size
                        val charCount = message.replace("\\s".toRegex(), "").length

                        if (wordCount < 2 || charCount < 10) {
                            messageEditText.error =
                                context.getString(R.string.report_validation_message)
                            return@setOnClickListener
                        }

                        if (!spamRadio.isChecked && !noSpamRadio.isChecked) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.report_radio_validation),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }

                        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
                        progressBar.visibility = View.VISIBLE
                        button.isEnabled = false // Disable button to prevent duplicate submissions

                        // Launch background work
                        CoroutineScope(Dispatchers.IO).launch {
                            val reportedTo = mutableListOf<String>()

                            getListaSpamApiLang(context)?.let {
                                if (ApiUtils.reportToUnknownPhone(
                                        number,
                                        message,
                                        spamRadio.isChecked,
                                        it
                                    )
                                ) {
                                    reportedTo.add("UnknownPhone")
                                }
                            }

                            getTellowsApiCountry(context)?.let {
                                if (ApiUtils.reportToTellows(
                                        number,
                                        message,
                                        spamRadio.isChecked,
                                        it
                                    )
                                ) {
                                    reportedTo.add("Tellows")
                                }
                            }

                            val reportMessage = if (reportedTo.isNotEmpty()) {
                                context.getString(R.string.report_success_prefix) + " " + reportedTo.joinToString(
                                    context.getString(R.string.report_title)
                                )
                            } else {
                                context.getString(R.string.report_failure)
                            }

                            // Switch to main thread to show Toast and dismiss dialog
                            withContext(Dispatchers.Main) {
                                progressBar.visibility = View.GONE
                                button.isEnabled = true
                                Toast.makeText(context, reportMessage, Toast.LENGTH_SHORT).show()
                                alertDialog.dismiss()
                            }
                        }
                    }
                }
            }
            .show()
    }


}
