package com.albek.currentactivity

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


@SuppressLint("AccessibilityPolicy")
class MyAccessibilityService : AccessibilityService() {

    companion object {
        private val _currentActivityFlow = MutableStateFlow<Pair<String?, String?>>(Pair(null, null))
        val currentActivityFlow: StateFlow<Pair<String?, String?>> get() = _currentActivityFlow
    }

    private var overlayRoot: LinearLayout? = null
    private var packageTextView: TextView? = null
    private var activityTextView: TextView? = null
    private var historyContainer: LinearLayout? = null
    private var windowManager: WindowManager? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private val recentActivities = ArrayDeque<Pair<String, String>>(10)
    private var currentPackageName: String = "Unknown"
    private var currentClassName: String = "Unknown"

    // متغيرات للحركة
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlay()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // إزالة Overlay إذا كان موجودًا مسبقًا
        overlayRoot?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e("Overlay", "Failed to remove existing overlay: ${e.message}")
            }
            overlayRoot = null
        }

        overlayRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC000000.toInt())
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }

        val titleTextView = TextView(this).apply {
            text = getString(R.string.overlay_title)
            textSize = 14f
            setTextColor(getColor(R.color.overlay_title_color))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }

        val closeTextView = TextView(this).apply {
            text = getString(R.string.overlay_close)
            textSize = 16f
            setTextColor(getColor(R.color.overlay_close_color))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener { removeOverlay() }
        }

        header.addView(titleTextView)
        header.addView(closeTextView)

        header.setOnTouchListener { _, event ->
            val params = overlayLayoutParams ?: return@setOnTouchListener false
            val root = overlayRoot ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(root, params)
                    true
                }

                else -> false
            }
        }

        packageTextView = TextView(this).apply {
            text = getString(R.string.package_text, currentPackageName)
            textSize = 13f
            setTextColor(getColor(R.color.overlay_package_color))
            setPadding(dp(6), dp(8), dp(6), dp(6))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setOnClickListener { copyToClipboard(currentPackageName) }
        }

        activityTextView = TextView(this).apply {
            text = getString(R.string.activity_text, currentClassName)
            textSize = 13f
            setTextColor(getColor(R.color.overlay_activity_color))
            setPadding(dp(6), dp(0), dp(6), dp(10))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setOnClickListener { copyToClipboard(currentClassName) }
        }

        val historyTitleTextView = TextView(this).apply {
            text = getString(R.string.overlay_recent_title)
            textSize = 12f
            setTextColor(getColor(R.color.overlay_history_title_color))
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        historyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(110)
            )
            addView(
                historyContainer,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        overlayRoot?.apply {
            addView(header)
            addView(createSeparator())
            addView(packageTextView)
            addView(createSeparator())
            addView(activityTextView)
            addView(createSeparator())
            addView(historyTitleTextView)
            addView(createSeparator())
            addView(scrollView)
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        overlayLayoutParams = WindowManager.LayoutParams(
            dp(280),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        try {
            windowManager?.addView(overlayRoot, overlayLayoutParams)
            refreshHistoryViews()
        } catch (e: Exception) {
            Log.e("Overlay", "Failed to add overlay view: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: "Unknown"
            val className = event.className?.toString() ?: "Unknown"
            _currentActivityFlow.value = Pair(packageName, className)
            currentPackageName = packageName
            currentClassName = className
            packageTextView?.text = getString(R.string.package_text, packageName)
            activityTextView?.text = getString(R.string.activity_text, className)
            addRecentActivity(packageName, className)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    fun removeOverlay() {
        overlayRoot?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e("Overlay", "Failed to remove overlay: ${e.message}")
            }
            overlayRoot = null
        }
    }

    // استقبال Intent لإدارة Overlay
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP_OVERLAY" -> removeOverlay()
            "START_OVERLAY" -> setupOverlay()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun copyToClipboard(value: String) {
        val text = value.trim()
        if (text.isEmpty() || text == "Unknown") return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
        Toast.makeText(this, getString(R.string.overlay_copied), Toast.LENGTH_SHORT).show()
    }

    private fun createSeparator(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                setMargins(dp(6), 0, dp(6), 0)
            }
            setBackgroundColor(getColor(R.color.overlay_separator_color))
        }
    }

    private fun addRecentActivity(packageName: String, className: String) {
        val name = className.trim()
        val pkg = packageName.trim()
        if (name.isEmpty() || name == "Unknown") return
        
        val newItem = Pair(pkg, name)
        if (recentActivities.firstOrNull() != newItem) {
            recentActivities.addFirst(newItem)
            while (recentActivities.size > 10) {
                recentActivities.removeLast()
            }
            refreshHistoryViews()
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = applicationContext.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun refreshHistoryViews() {
        val container = historyContainer ?: return
        container.removeAllViews()
        recentActivities.forEachIndexed { index, (pkg, activityName) ->
            if (index > 0) {
                container.addView(createSeparator())
            }
            
            val appName = getAppName(pkg)
            val appNameText = "${index + 1}. $appName"
            val fullText = "$appNameText\n$activityName"

            val spannable = SpannableString(fullText)

            // Color for App Name
            spannable.setSpan(
                ForegroundColorSpan(getColor(R.color.overlay_app_name_color)),
                0,
                appNameText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            val item = TextView(this).apply {
                text = spannable
                textSize = 12f
                setTextColor(getColor(R.color.overlay_activity_color_history))
                setPadding(dp(8), dp(6), dp(8), dp(6))
                setOnClickListener { copyToClipboard(activityName) }
            }
            container.addView(
                item,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }
}
