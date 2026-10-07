package com.novareader.piper_tts

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : android.app.Activity() {

    private companion object {
        const val ICON_REFRESH = "\uE5D5"
        const val ICON_SETTINGS = "\uE8B8"
        const val ICON_SEARCH = "\uE8B6"
        const val ICON_LOGS = "\uE873"
        const val ICON_DELETE = "\uE872"
    }

    private lateinit var repo: PiperVoiceRepository

    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var chipChecked: TextView
    private var onlyChecked = false
    private var voices = emptyList<PiperVoice>()
    private val executor = Executors.newSingleThreadExecutor()
    private var icons: Typeface? = null

    private val density get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private fun color(id: Int) = getColor(id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileLogger.init(this)
        try {
            repo = PiperVoiceRepository(this)
            FileLogger.log("MainActivity создана")
            icons = try {
                Typeface.createFromAsset(assets, "fonts/MaterialIcons-Regular.ttf")
            } catch (_: Exception) { null }
            buildUi()
            val cached = repo.loadCached()
            load(force = cached.isEmpty())
        } catch (e: Throwable) {
            FileLogger.error("MainActivity.onCreate FAILED", e)
            throw e
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.md_surface))
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        titleBlock.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(color(R.color.md_on_surface))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        titleBlock.addView(TextView(this).apply {
            text = getString(R.string.app_subtitle)
            setTextColor(color(R.color.md_on_surface_variant))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(2), 0, 0)
        })
        header.addView(titleBlock)
        header.addView(iconButton(ICON_REFRESH, getString(R.string.cd_refresh)) { load(true) })
        header.addView(space(dp(8)))
        header.addView(iconButton(ICON_SETTINGS, getString(R.string.cd_settings)) { openTtsSettings() })
        root.addView(header)

        root.addView(card {
            val howToBody = TextView(this@MainActivity).apply {
                text = getString(R.string.howto_body)
                setTextColor(color(R.color.md_on_surface_variant))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(6), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            }
            val howToArrow = TextView(this@MainActivity).apply {
                text = "⌃"
                setTextColor(color(R.color.md_on_surface_variant))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER
                setPadding(dp(8), 0, dp(2), 0)
            }
            val howToHeader = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val expanded = howToBody.visibility == View.VISIBLE
                    howToBody.visibility = if (expanded) View.GONE else View.VISIBLE
                    howToArrow.text = if (expanded) "⌄" else "⌃"
                }
            }
            howToHeader.addView(TextView(this@MainActivity).apply {
                text = getString(R.string.howto_title)
                setTextColor(color(R.color.md_on_surface))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            howToHeader.addView(howToArrow, LinearLayout.LayoutParams(dp(32), dp(32)))
            addView(howToHeader)
            addView(howToBody)
        })

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        status = TextView(this).apply {
            text = getString(R.string.status_loading)
            setTextColor(color(R.color.md_on_surface_variant))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        statusRow.addView(status)
        chipChecked = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(7), dp(14), dp(7))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onlyChecked = !onlyChecked
                updateChip()
                render()
            }
        }
        statusRow.addView(chipChecked)
        root.addView(statusRow)
        updateChip()

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.md_surface_card), dp(12))
            setPadding(dp(12), dp(4), dp(12), dp(4))
            elevation = 1f * density
        }
        searchRow.addView(TextView(this).apply {
            text = ICON_SEARCH
            typeface = icons
            setTextColor(color(R.color.md_on_surface_variant))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(0, 0, dp(8), 0)
        })
        search = EditText(this).apply {
            hint = getString(R.string.search_hint)
            setHintTextColor(color(R.color.md_on_surface_variant))
            setTextColor(color(R.color.md_on_surface))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setSingleLine(true)
            background = null
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setPadding(0, dp(10), 0, dp(10))
        }
        search.setOnEditorActionListener { _, _, _ -> render(); false }
        searchRow.addView(search)
        root.addView(searchRow, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(10)
            topMargin = dp(2)
        })

        val scroll = ScrollView(this).apply { isFillViewport = true }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        bottom.addView(textIconButton(ICON_LOGS, getString(R.string.logs)) { showLogs() })
        bottom.addView(space(dp(12)))
        bottom.addView(textIconButton(ICON_DELETE, getString(R.string.clear)) {
            FileLogger.clear()
            Toast.makeText(this@MainActivity, getString(R.string.logs_cleared), Toast.LENGTH_SHORT).show()
        })
        root.addView(bottom)

        setContentView(root)
    }

    private fun space(w: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(w, 1)
    }

    private fun rounded(fill: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.toFloat()
    }

    private fun card(build: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.md_surface_card), dp(14))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            elevation = 2f * density
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) }
            build()
        }
    }

    private fun iconButton(glyph: String, contentDesc: String, onClick: () -> Unit): TextView {
        val size = dp(42)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color(R.color.md_surface_card))
        }
        val ripple = RippleDrawable(
            ColorStateList.valueOf(color(R.color.md_primary) and 0x40FFFFFF or 0x40000000),
            bg,
            null
        )
        return TextView(this).apply {
            text = glyph
            typeface = icons
            setTextColor(color(R.color.md_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            contentDescription = contentDesc
            background = ripple
            layoutParams = LinearLayout.LayoutParams(size, size)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun textIconButton(glyph: String, label: String, onClick: () -> Unit): LinearLayout {
        val bg = rounded(color(R.color.md_surface_card), dp(20))
        val ripple = RippleDrawable(
            ColorStateList.valueOf(color(R.color.md_primary) and 0x33FFFFFF),
            bg,
            null
        )
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ripple
            setPadding(dp(14), dp(8), dp(16), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            addView(TextView(this@MainActivity).apply {
                text = glyph
                typeface = icons
                setTextColor(color(R.color.md_primary))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setPadding(0, 0, dp(6), 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = label
                setTextColor(color(R.color.md_on_surface))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
        }
    }

    private fun openTtsSettings() {
        try {
            startActivity(Intent("com.android.settings.TTS_SETTINGS"))
        } catch (_: Exception) {
            try {
                startActivity(Intent("android.settings.TTS_SETTINGS"))
            } catch (_: Exception) {
                status.text = getString(R.string.status_tts_settings_fail)
            }
        }
    }

    private fun showLogs() {
        val logContent = FileLogger.getLogContent()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }

        val writeFileCheck = CheckBox(this).apply {
            text = getString(R.string.logs_write_file)
            isChecked = FileLogger.isFileWritingEnabled()
            setOnCheckedChangeListener { _, checked ->
                FileLogger.setFileWritingEnabled(checked)
                Toast.makeText(
                    this@MainActivity,
                    if (checked) getString(R.string.logs_file_enabled)
                    else getString(R.string.logs_file_disabled),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        container.addView(writeFileCheck)

        val logView = TextView(this).apply {
            text = logContent
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(0, dp(8), 0, 0)
        }

        val scroll = ScrollView(this).apply {
            addView(logView)
        }
        container.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(360)
            )
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.logs_title))
            .setView(container)
            .setPositiveButton(getString(R.string.ok), null)
            .setNeutralButton(getString(R.string.logs_copy)) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Nova TTS Logs", FileLogger.getLogContent()))
                Toast.makeText(this, getString(R.string.logs_copied), Toast.LENGTH_SHORT).show()
            }
            .create()
        dialog.show()
    }

    private fun load(force: Boolean) {
        status.text = if (force) getString(R.string.status_updating) else getString(R.string.status_loading)
        FileLogger.log("Загрузка каталога Piper, force=$force")
        executor.execute {
            try {
                val data = kotlinx.coroutines.runBlocking { if (force) repo.refresh() else repo.loadCached() }
                runOnUiThread {
                    voices = data
                    render()
                    updateChip()
                    status.text = getString(R.string.status_count, data.size, repo.selected().size)
                }
            } catch (e: Throwable) {
                FileLogger.error("Ошибка при загрузке каталога Piper", e)
                runOnUiThread { status.text = getString(R.string.status_error, e.message ?: "") }
            }
        }
    }

    private fun updateChip() {
        val n = repo.selected().size
        chipChecked.text = getString(R.string.chip_checked, n)
        if (onlyChecked) {
            chipChecked.background = rounded(color(R.color.md_primary), dp(18))
            chipChecked.setTextColor(color(R.color.md_on_primary))
        } else {
            chipChecked.background = GradientDrawable().apply {
                setColor(color(R.color.md_surface_card))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), color(R.color.md_primary))
            }
            chipChecked.setTextColor(color(R.color.md_primary))
        }
    }

    private val subViews = HashMap<String, TextView>()
    private val modelManager by lazy { PiperModelManager(this) }

    private fun render() {
        list.removeAllViews()
        subViews.clear()
        val q = search.text.toString().trim().lowercase(Locale.ROOT)
        val selected = repo.selected()
        val filtered = voices.filter {
            (!onlyChecked || selected.contains(it.key)) &&
                    (q.isEmpty() ||
                            it.name.lowercase().contains(q) ||
                            it.locale.lowercase().contains(q) ||
                            it.friendlyName.lowercase().contains(q))
        }

        if (filtered.isEmpty()) {
            list.addView(TextView(this).apply {
                text = if (voices.isEmpty()) getString(R.string.status_empty_hint)
                else if (onlyChecked && q.isEmpty()) getString(R.string.status_none_checked)
                else getString(R.string.status_not_found)
                setTextColor(color(R.color.md_on_surface_variant))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, dp(24))
            })
            return
        }

        filtered.forEach { v ->
            val installed = modelManager.isInstalled(v)
            val isDownloading = VoiceDownloads.isActive(v.key)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(color(R.color.md_surface_card), dp(12))
                setPadding(dp(12), dp(8), dp(12), dp(8))
                elevation = 1f * density
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            }

            val check = CheckBox(this).apply {
                isEnabled = installed
                isChecked = installed && selected.contains(v.key)
                buttonTintList = ColorStateList.valueOf(
                    if (installed) color(R.color.md_primary) else color(R.color.md_on_surface_variant)
                )
                setOnCheckedChangeListener { _, checked ->
                    if (!installed) {
                        isChecked = false
                        return@setOnCheckedChangeListener
                    }
                    repo.setSelected(v.key, checked)
                    status.text = getString(R.string.status_count, voices.size, repo.selected().size)
                    updateChip()
                    FileLogger.log("Голос ${v.key}: ${if (checked) "выбран" else "снят"}")
                }
            }
            row.addView(check)

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                setPadding(dp(4), 0, 0, 0)
            }
            textCol.addView(TextView(this).apply {
                text = v.friendlyName
                setTextColor(color(R.color.md_on_surface))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            val sub = TextView(this).apply {
                text = when {
                    isDownloading -> downloadingText(v)
                    installed -> "${v.locale} · ${v.quality} · ✓ installed"
                    else -> "${v.locale} · ${v.quality}"
                }
                setTextColor(color(R.color.md_on_surface_variant))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            textCol.addView(sub)
            if (isDownloading) subViews[v.key] = sub
            row.addView(textCol)

            // Пол диктора: ♀ / ♂ (как в NovaEdgeTTS)
            VoiceGenders.of(this, v.name)?.let { g ->
                val female = g == VoiceGenders.Gender.FEMALE
                row.addView(TextView(this).apply {
                    text = if (female) "\u2640" else "\u2642"
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(if (female) 0xFFE91E63.toInt() else 0xFF42A5F5.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    gravity = Gravity.CENTER
                    contentDescription = if (female) "Female" else "Male"
                    setPadding(dp(8), 0, dp(4), 0)
                })
            }

            if (!installed) {
                val btn = TextView(this).apply {
                    text = if (isDownloading) "✕" else "↓"
                    setTextColor(color(R.color.md_primary))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setOnClickListener {
                        if (isDownloading) VoiceDownloadService.cancel(this@MainActivity, v.key)
                        else startDownload(v)
                    }
                }
                row.addView(btn)
            } else {
                val btn = TextView(this).apply {
                    text = "✕"
                    setTextColor(color(R.color.md_on_surface_variant))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setOnClickListener {
                        repo.setSelected(v.key, false)
                        modelManager.delete(v)
                        Toast.makeText(this@MainActivity, "Deleted ${v.friendlyName}", Toast.LENGTH_SHORT).show()
                        render()
                        updateChip()
                    }
                }
                row.addView(btn)
            }

            if (installed) {
                row.isClickable = true
                row.isFocusable = true
                row.setOnClickListener { check.isChecked = !check.isChecked }
            }

            list.addView(row)
        }
    }

    private fun downloadingText(v: PiperVoice): String {
        val p = VoiceDownloads.percent(v.key)
        return if (p in 0..100) "${v.locale} · $p%" else "${v.locale} · …"
    }

    private fun startDownload(v: PiperVoice) {
        if (VoiceDownloads.isActive(v.key) || modelManager.isInstalled(v)) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            // Загрузка работает и без разрешения, но уведомления с прогрессом не будет.
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        status.text = "Downloading ${v.friendlyName}…"
        VoiceDownloadService.start(this, v.key)   // строка перерисуется по колбэку, когда сервис поставит загрузку в очередь
    }

    private val downloadListener = object : VoiceDownloads.Listener {
        override fun onProgress(key: String, percent: Int) {
            val v = voices.firstOrNull { it.key == key } ?: return
            val sub = subViews[key]
            if (sub == null) { if (percent < 0) render(); return }   // загрузка только что встала в очередь
            sub.text = downloadingText(v)
            if (percent >= 0) status.text = "Downloading ${v.friendlyName}: $percent%"
        }

        override fun onFinished(key: String, error: String?) {
            val v = voices.firstOrNull { it.key == key }
            render()
            updateChip()
            status.text = getString(R.string.status_count, voices.size, repo.selected().size)
            if (v != null) {
                if (error == null) Toast.makeText(this@MainActivity, "Installed: ${v.friendlyName}", Toast.LENGTH_SHORT).show()
                else Toast.makeText(this@MainActivity, "${v.friendlyName}: $error", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        VoiceDownloads.add(downloadListener)
        if (voices.isNotEmpty()) { render(); updateChip() }   // подхватить то, что скачалось, пока экран был закрыт
    }

    override fun onStop() {
        VoiceDownloads.remove(downloadListener)
        super.onStop()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}