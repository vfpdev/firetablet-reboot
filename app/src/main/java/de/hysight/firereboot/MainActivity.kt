package de.hysight.firereboot

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

class MainActivity : Activity() {

    private var advancedOpen = false

    private val bg get() = resources.getColor(R.color.bg, theme)
    private val card get() = resources.getColor(R.color.card, theme)
    private val cardBorder get() = resources.getColor(R.color.card_border, theme)
    private val accent get() = resources.getColor(R.color.accent, theme)
    private val ok get() = resources.getColor(R.color.ok, theme)
    private val warn get() = resources.getColor(R.color.warn, theme)
    private val textPrimary get() = resources.getColor(R.color.text_primary, theme)
    private val textSecondary get() = resources.getColor(R.color.text_secondary, theme)
    private val textTertiary get() = resources.getColor(R.color.text_tertiary, theme)
    private val monoBg get() = resources.getColor(R.color.mono_bg, theme)
    private val monoFg get() = resources.getColor(R.color.mono_fg, theme)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, HttpService::class.java))
        render()
    }

    private fun render() {
        val a11yOn = isAccessibilityEnabled()
        val adminOn = isDeviceAdminActive()
        val batteryOk = isIgnoringBatteryOptimizations()
        val ip = localIp()
        val port = BuildConfig.REBOOT_PORT

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        // Header
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.subtitle)
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(2), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = if (ip != null) "$ip:$port" else getString(R.string.ip_not_connected)
            setTextColor(if (ip != null) accent else textTertiary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, 0, 0, dp(20))
        })

        // Status card with gear icon in header
        val statusCard = makeCard()
        val statusHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        statusHeader.addView(TextView(this).apply {
            text = getString(R.string.section_status)
            setTextColor(textTertiary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        statusHeader.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_settings)
            setColorFilter(if (advancedOpen) accent else textSecondary)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setPadding(dp(6), dp(6), dp(6), dp(6))
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
            }
            setOnClickListener {
                advancedOpen = !advancedOpen
                render()
            }
        })
        statusCard.addView(statusHeader)
        val active = getString(R.string.state_active)
        val off = getString(R.string.state_off)
        val disabled = getString(R.string.state_disabled)
        statusCard.addView(statusRow(getString(R.string.status_a11y), a11yOn, if (a11yOn) active else off))
        statusCard.addView(statusRow(getString(R.string.status_admin), adminOn, if (adminOn) active else off))
        statusCard.addView(statusRow(getString(R.string.status_battery), batteryOk, if (batteryOk) disabled else active))
        statusCard.addView(statusRow(getString(R.string.status_http), true, getString(R.string.state_port_fmt, port)))
        root.addView(statusCard)

        // Advanced settings (toggled by gear)
        if (advancedOpen) {
            val advCard = makeCard()
            advCard.withTopMargin(dp(12))
            advCard.addView(sectionLabel(getString(R.string.section_all_toggles)))
            advCard.addView(toggleRow(
                R.drawable.ic_accessibility,
                getString(R.string.status_a11y),
                getString(R.string.toggle_a11y_sub),
                a11yOn
            ) {
                toggleAccessibility()
                scheduleRender()
            })
            advCard.addView(divider())
            advCard.addView(toggleRow(
                R.drawable.ic_shield,
                getString(R.string.status_admin),
                getString(R.string.toggle_admin_sub),
                adminOn
            ) {
                if (adminOn) {
                    Toast.makeText(this, getString(R.string.toast_admin_off_manual), Toast.LENGTH_LONG).show()
                    safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    })
                } else {
                    safeStart(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(
                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            ComponentName(this@MainActivity, AdminReceiver::class.java)
                        )
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            getString(R.string.admin_explanation)
                        )
                    })
                }
            })
            advCard.addView(divider())
            advCard.addView(toggleRow(
                R.drawable.ic_battery,
                getString(R.string.status_battery),
                if (batteryOk) getString(R.string.toggle_battery_sub_off) else getString(R.string.toggle_battery_sub_on),
                !batteryOk
            ) {
                if (batteryOk) {
                    safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } else {
                    safeStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                }
            })

            advCard.addView(View(this).apply {
                setBackgroundColor(cardBorder)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
                ).apply { topMargin = dp(12); bottomMargin = dp(4) }
            })

            advCard.addView(navRow(
                R.drawable.ic_settings,
                getString(R.string.nav_language)
            ) {
                showLanguageDialog()
            })
            advCard.addView(navRow(
                R.drawable.ic_settings,
                getString(R.string.nav_a11y_settings)
            ) {
                safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            advCard.addView(navRow(
                R.drawable.ic_settings,
                getString(R.string.nav_app_details)
            ) {
                safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
            })

            root.addView(advCard)
        }

        // Actions (only what needs doing)
        val needsAction = !a11yOn || !adminOn || !batteryOk
        if (needsAction) {
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(16), 0, 0)
            }
            actions.addView(sectionLabel(getString(R.string.section_actions)))
            if (!a11yOn) {
                actions.addView(primaryButton(getString(R.string.action_a11y_enable)) { toggleAccessibility() })
                actions.addView(secondaryButton(getString(R.string.action_a11y_via_settings)) {
                    safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                })
            }
            if (!adminOn) {
                actions.addView(primaryButton(getString(R.string.action_admin_enable)) {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(
                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            ComponentName(this@MainActivity, AdminReceiver::class.java)
                        )
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            getString(R.string.admin_explanation)
                        )
                    }
                    safeStart(intent)
                })
            }
            if (!batteryOk) {
                actions.addView(primaryButton(getString(R.string.action_battery_disable)) {
                    safeStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                })
            }
            root.addView(actions)
        }

        // Direct actions card (reboot + screen lock)
        val directCard = makeCard().withTopMargin(dp(16)) as LinearLayout
        directCard.addView(sectionLabel(getString(R.string.section_on_device)))
        val barRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, 0)
        }
        val rebootBtn = barButton(getString(R.string.btn_reboot), textColor = warn) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirm_reboot_title))
                .setMessage(getString(R.string.confirm_reboot_msg))
                .setPositiveButton(getString(R.string.btn_reboot)) { _, _ ->
                    val acc = RebootAccessibilityService.instance
                    if (acc == null) {
                        Toast.makeText(this, getString(R.string.toast_a11y_inactive), Toast.LENGTH_LONG).show()
                    } else {
                        acc.triggerReboot()
                    }
                }
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show()
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(6) }
        }
        val lockBtn = barButton(getString(R.string.btn_lock)) {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(this, AdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) {
                dpm.lockNow()
            } else {
                val acc = RebootAccessibilityService.instance
                if (acc != null) acc.lockScreen()
                else Toast.makeText(this, getString(R.string.toast_admin_or_a11y), Toast.LENGTH_LONG).show()
            }
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(6) }
        }
        barRow.addView(rebootBtn)
        barRow.addView(lockBtn)
        directCard.addView(barRow)
        root.addView(directCard)

        // Commands card
        val cmdCard = makeCard()
        cmdCard.addView(sectionLabel(getString(R.string.section_commands)))
        val base = if (ip != null) "http://$ip:$port" else "http://<ip>:$port"
        cmdCard.addView(commandBlock(getString(R.string.cmd_reboot), "curl -X POST $base/reboot"))
        cmdCard.addView(commandBlock(getString(R.string.cmd_screen_off), "curl -X POST $base/screen/off"))
        cmdCard.addView(commandBlock(getString(R.string.cmd_screen_on), "curl -X POST $base/screen/on"))
        root.addView(cmdCard.withTopMargin(dp(16)))

        // Footer
        root.addView(TextView(this).apply {
            text = getString(R.string.footer_version_fmt, BuildConfig.VERSION_NAME, port)
            setTextColor(textTertiary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(root, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        })
    }

    // ---- builders ----

    private fun makeCard(): LinearLayout {
        val gd = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(card)
            setStroke(dp(1), cardBorder)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gd
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }
    }

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(textTertiary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.15f
        setPadding(0, 0, 0, dp(10))
    }

    private fun statusRow(label: String, ok: Boolean, state: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (ok) this@MainActivity.ok else warn)
            }
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                rightMargin = dp(14)
            }
        }
        val labelView = TextView(this).apply {
            text = label
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val stateView = TextView(this).apply {
            text = state
            setTextColor(if (ok) this@MainActivity.ok else warn)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.05f
        }
        row.addView(dot)
        row.addView(labelView)
        row.addView(stateView)
        return row
    }

    private fun primaryButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(Color.BLACK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isAllCaps = false
        background = pillBg(accent, pressed = darken(accent, 0.85f))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        ).apply { topMargin = dp(8) }
        stateListAnimator = null
        elevation = 0f
        setOnClickListener { click() }
    }

    private fun toggleRow(
        iconRes: Int,
        label: String,
        sublabel: String,
        on: Boolean,
        click: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(12), dp(4), dp(12))
            background = pillBg(Color.TRANSPARENT, pressed = cardBorder)
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }
        row.addView(ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(if (on) accent else textSecondary)
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                rightMargin = dp(16)
            }
        })
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        labels.addView(TextView(this).apply {
            text = label
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        labels.addView(TextView(this).apply {
            text = sublabel
            setTextColor(textTertiary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(labels)
        row.addView(Switch(this).apply {
            isChecked = on
            isClickable = false
            isFocusable = false
        })
        return row
    }

    private fun navRow(iconRes: Int, label: String, click: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(14), dp(4), dp(14))
            background = pillBg(Color.TRANSPARENT, pressed = cardBorder)
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }
        row.addView(ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(textSecondary)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                rightMargin = dp(16)
            }
        })
        row.addView(TextView(this).apply {
            text = label
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron)
            setColorFilter(textTertiary)
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
        })
        return row
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(cardBorder)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        )
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_action_unavailable), Toast.LENGTH_LONG).show()
        }
    }

    private fun scheduleRender() {
        Handler(Looper.getMainLooper()).postDelayed({ render() }, 1500)
    }

    private fun barButton(label: String, textColor: Int = textPrimary, click: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isAllCaps = false
        background = pillBg(cardBorder, pressed = darken(cardBorder, 0.7f))
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(8), 0, dp(8), 0)
        stateListAnimator = null
        elevation = 0f
        setOnClickListener { click() }
    }

    private fun neutralButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(textPrimary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isAllCaps = false
        background = pillBg(cardBorder, pressed = darken(cardBorder, 0.7f))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
        ).apply { topMargin = dp(8) }
        stateListAnimator = null
        elevation = 0f
        setOnClickListener { click() }
    }

    private fun secondaryButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(textSecondary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        isAllCaps = false
        background = pillBg(card, pressed = cardBorder, strokeColor = cardBorder)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(42)
        ).apply { topMargin = dp(6) }
        stateListAnimator = null
        elevation = 0f
        setOnClickListener { click() }
    }

    private fun commandBlock(label: String, command: String): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        container.addView(TextView(this).apply {
            text = label
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, 0, dp(4))
        })
        val codeBg = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(monoBg)
        }
        container.addView(TextView(this).apply {
            text = command
            setTextColor(monoFg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            background = codeBg
            setPadding(dp(14), dp(12), dp(14), dp(12))
        })
        return container
    }

    private fun pillBg(color: Int, pressed: Int, strokeColor: Int? = null): RippleDrawable {
        val normal = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(color)
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }
        return RippleDrawable(ColorStateList.valueOf(pressed), normal, null)
    }

    private fun darken(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    private fun View.withTopMargin(margin: Int): View {
        val lp = (layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        lp.topMargin = margin
        layoutParams = lp
        return this
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---- state checks ----

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isDeviceAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (RebootAccessibilityService.instance != null) return true
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(packageName)
    }

    private fun toggleAccessibility() {
        val granted = checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Toast.makeText(
                this,
                getString(R.string.toast_grant_secure, packageName),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val target = "$packageName/${RebootAccessibilityService::class.java.name}"
        val current = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val isOn = current.contains(target)
        val next = if (isOn) {
            current.split(":").filter { it.isNotEmpty() && it != target }.joinToString(":")
        } else {
            if (current.isEmpty()) target else "$current:$target"
        }
        Settings.Secure.putString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next
        )
        Settings.Secure.putInt(
            contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, if (next.isEmpty()) 0 else 1
        )
        val written = (Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: "").contains(target)
        if (!isOn && !written) {
            Toast.makeText(this, getString(R.string.toast_write_blocked), Toast.LENGTH_LONG).show()
            safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        render()
        if (!isOn) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (RebootAccessibilityService.instance == null) {
                    Toast.makeText(this, getString(R.string.toast_fireos_no_bind), Toast.LENGTH_LONG).show()
                    safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else {
                    render()
                }
            }, 1500)
        }
    }

    private fun showLanguageDialog() {
        val labels = arrayOf(
            getString(R.string.lang_system),
            getString(R.string.lang_de),
            getString(R.string.lang_en)
        )
        val codes = arrayOf<String?>(null, "de", "en")
        val current = LocaleHelper.savedLang(this)
        val checked = codes.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.nav_language))
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                LocaleHelper.save(this, codes[which])
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun localIp(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
