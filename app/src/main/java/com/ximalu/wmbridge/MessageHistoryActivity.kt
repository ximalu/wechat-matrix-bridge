package com.ximalu.wmbridge

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ximalu.wmbridge.service.BridgeProvider

/**
 * 消息记录查看器。
 * 通过 ContentProvider 从 [:bridge] 进程跨进程读取消息历史。
 */
class MessageHistoryActivity : AppCompatActivity() {

    private lateinit var adapter: MessageAdapter
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message_history)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "消息记录"

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
        val cursor = contentResolver.query(
            Uri.parse("content://${BridgeProvider.AUTHORITY}/history"),
            null, null, null, null
        ) ?: return

        val entries = mutableListOf<MessageHistoryItem>()
        val idCol = cursor.getColumnIndex("_id")
        val senderCol = cursor.getColumnIndex("sender")
        val contentCol = cursor.getColumnIndex("content")
        val groupCol = cursor.getColumnIndex("group")
        val timeCol = cursor.getColumnIndex("time")
        val statusCol = cursor.getColumnIndex("status")

        while (cursor.moveToNext()) {
            entries.add(MessageHistoryItem(
                id = if (idCol >= 0) cursor.getString(idCol) else "",
                sender = if (senderCol >= 0) cursor.getString(senderCol) else "",
                content = if (contentCol >= 0) cursor.getString(contentCol) else "",
                groupName = if (groupCol >= 0) cursor.getString(groupCol) else null,
                time = if (timeCol >= 0) cursor.getString(timeCol) else "",
                status = if (statusCol >= 0) cursor.getString(statusCol) else "pending"
            ))
        }
        cursor.close()

        adapter.submitList(entries)

        if (entries.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvSummary.text = "共 0 条"
        } else {
            tvEmpty.visibility = View.GONE
            val pending = entries.count { it.status == "pending" }
            val sent = entries.count { it.status == "sent" }
            val failed = entries.count { it.status == "failed" }
            tvSummary.text = "共 ${entries.size} 条 | 已发 $sent | 待发 $pending | 失败 $failed"
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("清空记录")
            .setMessage("确定要清空所有消息记录吗？此操作不可撤销。")
            .setPositiveButton("清空") { _, _ ->
                contentResolver.delete(
                    Uri.parse("content://${BridgeProvider.AUTHORITY}/clear_history"),
                    null, null
                )
                refresh()
                Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── Data class ──

    data class MessageHistoryItem(
        val id: String,
        val sender: String,
        val content: String,
        val groupName: String?,
        val time: String,
        val status: String  // "pending", "sent", "failed"
    )

    // ── Adapter ──

    private class MessageAdapter : RecyclerView.Adapter<MessageViewHolder>() {

        private var items = listOf<MessageHistoryItem>()

        fun submitList(list: List<MessageHistoryItem>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_entry, parent, false)
            return MessageViewHolder(view)
        }

        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size
    }

    private class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val dotStatus = itemView.findViewById<View>(R.id.dotStatus)
        private val tvSender = itemView.findViewById<TextView>(R.id.tvSender)
        private val tvContent = itemView.findViewById<TextView>(R.id.tvContent)
        private val tvGroup = itemView.findViewById<TextView>(R.id.tvGroup)
        private val tvTime = itemView.findViewById<TextView>(R.id.tvTime)
        private val tvStatusLabel = itemView.findViewById<TextView>(R.id.tvStatusLabel)

        fun bind(entry: MessageHistoryItem) {
            val ctx = itemView.context

            // Sender
            tvSender.text = if (entry.groupName != null && entry.groupName.isNotEmpty()) {
                "[${entry.groupName}] ${entry.sender}"
            } else {
                entry.sender
            }

            // Content
            tvContent.text = entry.content

            // Group name visibility
            tvGroup.visibility = if (entry.groupName != null && entry.groupName.isNotEmpty()) View.VISIBLE else View.GONE
            tvGroup.text = entry.groupName

            // Time
            tvTime.text = entry.time

            // Status
            when (entry.status) {
                "sent" -> {
                    dotStatus.setBackgroundResource(R.drawable.shape_dot_green)
                    tvStatusLabel.text = "已发"
                    tvStatusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.status_sent_text))
                    tvStatusLabel.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.status_sent_bg)
                    )
                }
                "pending" -> {
                    dotStatus.setBackgroundResource(R.drawable.shape_dot_red)
                    tvStatusLabel.text = "待发"
                    tvStatusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.status_pending_text))
                    tvStatusLabel.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.status_pending_bg)
                    )
                }
                "failed" -> {
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
