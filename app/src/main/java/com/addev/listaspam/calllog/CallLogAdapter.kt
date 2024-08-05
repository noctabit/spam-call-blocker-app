package com.addev.listaspam.calllog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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

    val formatter = SimpleDateFormat("dd/MM/yyyy")

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
            numberTextView.text = "${callLog.number}${if (isBlocked) " (blocked)" else ""}"
            dateTextView.text = formatter.format(callLog.date)
            durationTextView.text = "Duration: ${callLog.duration} seconds"

            if (isBlocked) {
                numberTextView.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_light))
            }

            reportButton.setOnClickListener {
                val url = String.format(SpamUtils.REPORT_URL_TEMPLATE, callLog.number)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
        }
    }
}
