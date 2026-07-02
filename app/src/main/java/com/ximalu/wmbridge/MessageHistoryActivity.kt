package com.ximalu.wmbridge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ximalu.wmbridge.data.MessageHistory
import com.ximalu.wmbridge.model.MessageEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageHistoryActivity : AppCompatActivity() {

    private lateinit var history: MessageHistory
    private lateinit var adapter: MessageAdapter
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message_history)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "消息记录"

        history = MessageHistory(this)
        rvHistory = findViewById(R.id.rvHistory)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvSummary = findViewById(R.id.tvSummary)

        rvHistory.layoutManager = LinearLayoutManager(this)
        adapter = MessageAdapter()
        rvHistory.adapter = adapter

        findViewById<View>(R.id.btnClear).setOnClickListener {
            confirmClear()
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refresh() {
        val entries = history.getAll()
        adapter.submitList(entries)
        updateSummary(entries)
        tvEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateSummary(entries: List<MessageEntry>) {
        val total = entries.size
        val pending = entries.count { it.status == MessageEntry.Status.PENDING }
        val failed = entries.count { it.status == MessageEntry.Status.FAILED }
        val sent = entries.count { it.status == MessageEntry.Status.SENT }
        tvSummary.text = "共 $total 条 | 已发 $sent | 待发 $pending | 失败 $failed"
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("清空记录")
            .setMessage("确定要清空所有消息记录吗？此操作不可撤销。")
            .setPositiveButton("清空") { _, _ ->
                history.clear()
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── Adapter ──

    private class MessageAdapter : RecyclerView.Adapter<MessageViewHolder>() {

        private var items = listOf<MessageEntry>()
        private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

        fun submitList(list: List<MessageEntry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_entry, parent, false)
            return MessageViewHolder(view, dateFormat)
        }

        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size
    }

    private class MessageViewHolder(
        itemView: View,
        private val dateFormat: SimpleDateFormat
    ) : RecyclerView.ViewHolder(itemView) {

        private val dotStatus = itemView.findViewById<View>(R.id.dotStatus)
        private val tvSender = itemView.findViewById<TextView>(R.id.tvSender)
        private val tvContent = itemView.findViewById<TextView>(R.id.tvContent)
        private val tvGroup = itemView.findViewById<TextView>(R.id.tvGroup)
        private val tvTime = itemView.findViewById<TextView>(R.id.tvTime)
        private val tvStatusLabel = itemView.findViewById<TextView>(R.id.tvStatusLabel)

        fun bind(entry: MessageEntry) {
            val ctx = itemView.context

            // Sender
            tvSender.text = if (entry.groupName != null) {
                "[${entry.groupName}] ${entry.sender}"
            } else {
                entry.sender
            }

            // Content
            tvContent.text = entry.content

            // Group name visibility
            tvGroup.visibility = if (entry.groupName != null) View.VISIBLE else View.GONE
            tvGroup.text = entry.groupName

            // Time
            tvTime.text = dateFormat.format(Date(entry.timestamp))

            // Status
            when (entry.status) {
                MessageEntry.Status.SENT -> {
                    dotStatus.setBackgroundResource(R.drawable.shape_dot_green)
                    tvStatusLabel.text = "已发"
                    tvStatusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.status_sent_text))
                    tvStatusLabel.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.status_sent_bg)
                    )
                }
                MessageEntry.Status.PENDING -> {
                    dotStatus.setBackgroundResource(R.drawable.shape_dot_red)
                    tvStatusLabel.text = "待发"
                    tvStatusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.status_pending_text))
                    tvStatusLabel.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.status_pending_bg)
                    )
                }
                MessageEntry.Status.FAILED -> {
                    dotStatus.setBackgroundResource(R.drawable.shape_dot_red)
                    tvStatusLabel.text = "失败"
                    tvStatusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.status_failed_text))
                    tvStatusLabel.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.status_failed_bg)
                    )
                }
            }
        }
    }
}
