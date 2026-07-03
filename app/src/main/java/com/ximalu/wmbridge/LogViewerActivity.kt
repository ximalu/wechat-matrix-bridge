package com.ximalu.wmbridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ximalu.wmbridge.service.BridgeProvider
import java.io.File

/**
 * 日志查看器。
 * 通过 ContentProvider 从 [:bridge] 进程读取 LogBuffer 数据。
 * 支持复制全部日志到剪贴板。
 */
class LogViewerActivity : AppCompatActivity() {

    private lateinit var adapter: LogAdapter
    private lateinit var rvLogs: RecyclerView
    private lateinit var tvLogCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "运行日志"

        rvLogs = findViewById(R.id.rvLogs)
        tvLogCount = findViewById(R.id.tvLogCount)

        rvLogs.layoutManager = LinearLayoutManager(this)
        adapter = LogAdapter()
        rvLogs.adapter = adapter

        findViewById<View>(R.id.btnShareLogs).setOnClickListener { shareLogs() }
        findViewById<View>(R.id.btnClearLogs).setOnClickListener { confirmClear() }

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
            UriFactory.logs(), null, null, null, null
        ) ?: return

        val entries = mutableListOf<LogEntry>()
        val idCol = cursor.getColumnIndex("_id")
        val timeCol = cursor.getColumnIndex("time")
        val levelCol = cursor.getColumnIndex("level")
        val tagCol = cursor.getColumnIndex("tag")
        val msgCol = cursor.getColumnIndex("msg")

        while (cursor.moveToNext()) {
            entries.add(LogEntry(
                id = if (idCol >= 0) cursor.getLong(idCol) else 0L,
                time = if (timeCol >= 0) cursor.getString(timeCol) else "",
                level = if (levelCol >= 0) cursor.getString(levelCol) else "",
                tag = if (tagCol >= 0) cursor.getString(tagCol) else "",
                msg = if (msgCol >= 0) cursor.getString(msgCol) else ""
            ))
        }
        cursor.close()

        adapter.submitList(entries)
        tvLogCount.text = "共 ${entries.size} 条日志"
    }

    private fun shareLogs() {
        val entries = adapter.items
        if (entries.isEmpty()) {
            Toast.makeText(this, "没有日志可分享", Toast.LENGTH_SHORT).show()
            return
        }
        val text = entries.joinToString("\n") { e ->
            "[${e.time}] [${e.level}/${e.tag}] ${e.msg}"
        }

        // Write to cache file
        val logDir = File(cacheDir, "logs")
        logDir.mkdirs()
        val logFile = File(logDir, "wmbridge_logs_${System.currentTimeMillis()}.txt")
        logFile.writeText(text)

        // Share via FileProvider
        val uri: Uri = FileProvider.getUriForFile(
            this, "${packageName}.fileprovider", logFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享日志"))
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("清空日志")
            .setMessage("确定要清空所有日志吗？")
            .setPositiveButton("清空") { _, _ ->
                contentResolver.update(UriFactory.clearLogs(), null, null, null)
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── Data class ──

    data class LogEntry(
        val id: Long,
        val time: String,
        val level: String,
        val tag: String,
        val msg: String
    )

    // ── Adapter ──

    private class LogAdapter : RecyclerView.Adapter<LogViewHolder>() {

        var items = listOf<LogEntry>()
            private set

        fun submitList(list: List<LogEntry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size
    }

    private class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tv1 = itemView.findViewById<TextView>(android.R.id.text1)
        private val tv2 = itemView.findViewById<TextView>(android.R.id.text2)

        fun bind(entry: LogEntry) {
            tv1.text = "[${entry.time}] [${entry.level}] ${entry.tag}"
            tv2.text = entry.msg
            tv1.textSize = 11f
            tv2.textSize = 12f

            // Color by level
            tv1.setTextColor(
                when (entry.level) {
                    "ERROR" -> 0xFFD32F2F.toInt()
                    "WARN" -> 0xFFF57C00.toInt()
                    else -> 0xFF757575.toInt()
                }
            )
        }
    }

    // ── URI Builder ──

    object UriFactory {
        private const val BASE = "content://${BridgeProvider.AUTHORITY}"

        fun logs() = android.net.Uri.parse("$BASE/logs")
        fun clearLogs() = android.net.Uri.parse("$BASE/clear_logs")
    }
}
