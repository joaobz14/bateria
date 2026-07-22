package com.joaobz14.taxa

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.json.JSONObject

/**
 * Serviço em primeiro plano que amostra a bateria em intervalos fixos, mesmo
 * com a tela bloqueada e o app em segundo plano — a peça que o navegador nunca
 * pôde ter. Cada amostra registra nível, corrente real, estado da tela,
 * carregamento e temperatura, gravados via BatteryLog.
 */
class BatteryLogService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var intervalMs = 30_000L

    private val tick = object : Runnable {
        override fun run() {
            try {
                val s = BatteryMonitorPlugin.snapshot(applicationContext)
                val o = JSONObject().apply {
                    put("ts", s.getLong("ts"))
                    put("level", s.getInt("level"))
                    put("currentMa", s.getDouble("currentMa"))
                    put("currentRaw", s.getInt("currentRaw"))
                    put("screenOn", s.getBoolean("screenOn"))
                    put("charging", s.getBoolean("charging"))
                    if (s.has("tempC")) put("tempC", s.getDouble("tempC"))
                }
                BatteryLog.append(applicationContext, o)
            } catch (e: Exception) { /* ignora amostra com erro */ }
            handler.postDelayed(this, intervalMs)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sec = intent?.getIntExtra("intervalSec", 30) ?: 30
        intervalMs = (sec.coerceAtLeast(5) * 1000).toLong()
        startForeground(NOTIF_ID, buildNotification())
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Monitor de bateria", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return b
            .setContentTitle("Taxa — monitorando bateria")
            .setContentText("Registrando descarga em segundo plano")
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "bateria-log"
        private const val NOTIF_ID = 1001
    }
}
