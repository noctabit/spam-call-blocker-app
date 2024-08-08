package com.addev.listaspam.calllog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.addev.listaspam.R
import com.addev.listaspam.utils.SpamUtils
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter

class CallLogAdapter(
    private val context: Context,
    private val callLogs: List<CallLogEntry>,
    private val blockedNumbers: Set<String>
) : RecyclerView.Adapter<CallLogAdapter.CallLogViewHolder>() {

    val formatter = SimpleDateFormat("dd/MM/yyyy HH:ss")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallLogViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_call_log, parent, false)
        return CallLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallLogViewHolder, position: Int) {
        val callLog = callLogs[position]
        holder.bind(callLog, blockedNumbers.contains(callLog.number))
    }

    override fun getItemCount(): Int = callLogs.size

    inner class CallLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val numberTextView: TextView = itemView.findViewById(R.id.numberTextView)
        private val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
        private val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)
        private val reportButton: Button = itemView.findViewById(R.id.reportButton)

        fun bind(callLog: CallLogEntry, isBlocked: Boolean) {
            val number = callLog.number ?: ""
            val textToShow = if (isBlocked) {
                context.getString(R.string.blocked_text_format, number)
            } else {
                number
            }
            numberTextView.text = textToShow
            dateTextView.text = formatter.format(callLog.date)
            durationTextView.text = context.getString(R.string.duration_label, callLog.duration)

            if (isBlocked) {
                numberTextView.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_light))
            } else {
                numberTextView.setTextColor(ContextCompat.getColor(context, android.R.color.system_palette_key_color_neutral_light)) // O el color por defecto
            }

            // Open ListaSpam reporting form in browser
            reportButton.setOnClickListener {
                val url = String.format(SpamUtils.REPORT_URL_TEMPLATE, number)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }

            // Copy number to clipboard
            itemView.setOnLongClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("phone number", number)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, context.getString(R.string.number_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                true
            }
        }
    }
}
