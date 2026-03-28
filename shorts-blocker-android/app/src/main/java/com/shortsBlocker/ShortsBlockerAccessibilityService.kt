package com.shortsBlocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.core.app.NotificationCompat

class ShortsBlockerAccessibilityService : AccessibilityService() {

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val NOTIFICATION_CHANNEL_ID = "shorts_blocker_channel"
        const val NOTIFICATION_ID = 1001
        const val SESSION_NOTIFICATION_ID = 1002

        var instance: ShortsBlockerAccessibilityService? = null
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var sessionTimer: CountDownTimer? = null
    private var isOverlayShowing = false
    private var isShortsScreen = false
    private var notificationManager: NotificationManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        showForegroundNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        removeOverlay()
        sessionTimer?.cancel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != YOUTUBE_PACKAGE) {
            if (isOverlayShowing) removeOverlay()
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                checkAndBlockShorts(event)
            }
            else -> {}
        }
    }

    private fun checkAndBlockShorts(event: AccessibilityEvent) {
        val shortsDetected = isShortsPage(event)
        isShortsScreen = shortsDetected

        if (shortsDetected && SessionManager.shouldBlockShorts(this)) {
            showOverlay()
        } else {
            removeOverlay()
            if (shortsDetected && SessionManager.isSessionActive(this)) {
                // Session is active, ensure timer is running
                if (sessionTimer == null) startSessionTimer()
            }
        }
    }

    private fun isShortsPage(event: AccessibilityEvent): Boolean {
        // Method 1: Check window title / class
        val className = event.className?.toString() ?: ""
        val text = event.text?.joinToString(" ")?.lowercase() ?: ""

        if (text.contains("shorts")) return true

        // Method 2: Traverse accessibility tree to find Shorts indicators
        val root = rootInActiveWindow ?: return false
        return containsShortsNode(root)
    }

    private fun containsShortsNode(node: AccessibilityNodeInfo?): Boolean {
        node ?: return false
        try {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            val resId = node.viewIdResourceName ?: ""

            if (desc.contains("shorts") || text == "shorts" ||
                resId.contains("shorts") || resId.contains("reel")) {
                return true
            }
            // Recurse children (limited depth for performance)
            for (i in 0 until minOf(node.childCount, 20)) {
                val child = node.getChild(i) ?: continue
                if (containsShortsNode(child)) {
                    child.recycle()
                    return true
                }
                child.recycle()
            }
        } catch (e: Exception) {
            // ignore
        }
        return false
    }

    fun showOverlay() {
        if (isOverlayShowing) return
        if (!android.provider.Settings.canDrawOverlays(this)) return

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_blocker, null)

        val unlockBtn = view.findViewById<View>(R.id.btn_unlock)
        val msgText = view.findViewById<TextView>(R.id.tv_message)

        val remaining = SessionManager.getSessionsRemainingToday(this)
        if (remaining <= 0) {
            msgText.text = "Daily limit reached!\nYou've used all 5 sessions today.\nCome back tomorrow."
        } else {
            msgText.text = "YouTube Shorts is blocked.\n$remaining session(s) remaining today.\nEnter password for 10 min access."
        }

        unlockBtn.setOnClickListener {
            val intent = Intent(this, PasswordDialogActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        windowManager?.addView(view, params)
        overlayView = view
        isOverlayShowing = true
    }

    fun removeOverlay() {
        if (!isOverlayShowing) return
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) { }
        overlayView = null
        isOverlayShowing = false
    }

    fun startSession() {
        SessionManager.startSession(this)
        removeOverlay()
        startSessionTimer()
        showSessionActiveNotification()
    }

    private fun startSessionTimer() {
        sessionTimer?.cancel()
        val remaining = SessionManager.getRemainingSessionTimeMs(this)
        if (remaining <= 0) {
            endSession()
            return
        }
        sessionTimer = object : CountDownTimer(remaining, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                updateSessionNotification(millisUntilFinished)
                // Broadcast remaining time to MainActivity
                val intent = Intent("com.shortsBlocker.SESSION_TICK").apply {
                    putExtra("remaining_ms", millisUntilFinished)
                }
                sendBroadcast(intent)
            }
            override fun onFinish() {
                endSession()
            }
        }.start()
    }

    private fun endSession() {
        sessionTimer?.cancel()
        sessionTimer = null
        SessionManager.endSession(this)
        notificationManager?.cancel(SESSION_NOTIFICATION_ID)
        if (isShortsScreen) showOverlay()
        // Notify MainActivity
        val intent = Intent("com.shortsBlocker.SESSION_ENDED")
        sendBroadcast(intent)
    }

    override fun onInterrupt() {
        removeOverlay()
        sessionTimer?.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Shorts Blocker",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shorts Blocker running"
            setShowBadge(false)
        }
        notificationManager?.createNotificationChannel(channel)
    }

    private fun showForegroundNotification() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Shorts Blocker Active")
            .setContentText("Monitoring YouTube Shorts")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun showSessionActiveNotification() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Shorts Session Active")
            .setContentText("10:00 remaining")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager?.notify(SESSION_NOTIFICATION_ID, notification)
    }

    private fun updateSessionNotification(remainingMs: Long) {
        val minutes = (remainingMs / 1000 / 60).toInt()
        val seconds = (remainingMs / 1000 % 60).toInt()
        val timeStr = String.format("%02d:%02d", minutes, seconds)
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Shorts Session Active")
            .setContentText("$timeStr remaining")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager?.notify(SESSION_NOTIFICATION_ID, notification)
    }
}
