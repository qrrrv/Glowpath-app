package com.musicplayer

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Показывает текст ошибки после вылета приложения (работает в отдельном процессе ":crash").
 * Обычные View, без Compose — чтобы сам экран ошибок не мог упасть вместе с приложением.
 */
class CrashReportActivity : Activity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra(EXTRA_TRACE).orEmpty().ifBlank { "(пустой отчёт)" }
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val title = TextView(this).apply {
            text = "Приложение вылетело"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }

        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("Копировать") {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", trace))
                Toast.makeText(this@CrashReportActivity, "Скопировано", Toast.LENGTH_SHORT).show()
            })
            addView(button("Отправить") {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, trace)
                }
                startActivity(Intent.createChooser(send, "Отправить отчёт"))
            })
            addView(button("Закрыть") { finishAndRemoveTask() })
        }

        val body = TextView(this).apply {
            text = trace
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(title)
            addView(buttons)
            addView(
                ScrollView(this@CrashReportActivity).apply { addView(body) },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            )
            // Android 15+ рисует приложения под системными панелями — отодвигаем контент
            setOnApplyWindowInsetsListener { v, insets ->
                v.setPadding(
                    dp(16) + insets.systemWindowInsetLeft,
                    dp(16) + insets.systemWindowInsetTop,
                    dp(16) + insets.systemWindowInsetRight,
                    dp(16) + insets.systemWindowInsetBottom
                )
                insets
            }
        }
        setContentView(root)
        root.requestApplyInsets()
    }

    companion object {
        const val EXTRA_TRACE = "trace"
    }
}
