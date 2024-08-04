// CallLogAdapter.kt
package com.addev.listaspam.calllog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.addev.listaspam.R

class CallLogAdapter(
    private val context: Context,
    private val callLogs: List<CallLogEntry>,
    private val blockedNumbers: Set<String>
) : RecyclerView.Adapter<CallLogAdapter.CallLogViewHolder>() {

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

        fun bind(callLog: CallLogEntry, isBlocked: Boolean) {
            numberTextView.text = "${callLog.number}${if (isBlocked) " (blocked)" else ""}"
            dateTextView.text = callLog.date.toString()
            durationTextView.text = "Duration: ${callLog.duration} seconds"

            if (isBlocked) {
                numberTextView.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            } else {
                numberTextView.setTextColor(ContextCompat.getColor(context, android.R.color.black))
            }
        }
    }
}
