package com.joaobz14.taxa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

/**
 * Plugin nativo que expõe ao app web o que o navegador nunca teve:
 * corrente instantânea (mA), temperatura, voltagem, saúde da célula e o
 * estado REAL da tela do aparelho, além de comandar o serviço de log em
 * segundo plano.
 */
@CapacitorPlugin(name = "BatteryMonitor")
class BatteryMonitorPlugin : Plugin() {

    private var screenReceiver: BroadcastReceiver? = null

    override fun load() {
        // Ouve o estado real da tela do aparelho (não só desta aba) e avisa o JS.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val on = intent?.action == Intent.ACTION_SCREEN_ON
                notifyListeners("screen", JSObject().put("on", on))
            }
        }
        // Android 14+ exige declarar a exportação de receivers de contexto.
        // SCREEN_ON/OFF são broadcasts protegidos do sistema → NOT_EXPORTED.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(screenReceiver, filter)
        }
    }

    override fun handleOnDestroy() {
        screenReceiver?.let { try { context.unregisterReceiver(it) } catch (e: Exception) {} }
    }

    @PluginMethod
    fun read(call: PluginCall) {
        call.resolve(snapshot(context))
    }

    @PluginMethod
    fun startLogging(call: PluginCall) {
        val intervalSec = call.getInt("intervalSec", 30) ?: 30
        val intent = Intent(context, BatteryLogService::class.java)
            .putExtra("intervalSec", intervalSec)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(intent)
        else
            context.startService(intent)
        call.resolve()
    }

    @PluginMethod
    fun stopLogging(call: PluginCall) {
        context.stopService(Intent(context, BatteryLogService::class.java))
        call.resolve()
    }

    @PluginMethod
    fun getLog(call: PluginCall) {
        call.resolve(JSObject().put("json", BatteryLog.readText(context)))
    }

    @PluginMethod
    fun clearLog(call: PluginCall) {
        BatteryLog.clear(context)
        call.resolve()
    }

    companion object {
        /** Snapshot instantâneo da bateria + estado da tela. */
        fun snapshot(ctx: Context): JSObject {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            // ATENÇÃO: a unidade e o sinal de CURRENT_NOW variam por fabricante.
            // Muitos aparelhos reportam em µA; alguns Samsung reportam em mA e com
            // sinal invertido. Devolvemos o valor bruto (currentRaw) para calibrar
            // no app, além de uma conversão padrão µA→mA em currentMa.
            val currentRaw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val counter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)

            val status = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempTenths = status?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val voltage = status?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            val health = status?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
            val st = status?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                           st == BatteryManager.BATTERY_STATUS_FULL

            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val screenOn = pm.isInteractive

            return JSObject().apply {
                put("level", level)
                put("charging", charging)
                put("currentRaw", currentRaw)
                put("currentMa", currentRaw / 1000.0)   // padrão µA→mA (calibrar por aparelho)
                put("chargeCounterUah", counter)
                if (tempTenths > 0) put("tempC", tempTenths / 10.0)
                if (voltage > 0) put("voltageMv", voltage)
                put("health", healthStr(health))
                put("screenOn", screenOn)
                put("ts", System.currentTimeMillis())
            }
        }

        private fun healthStr(h: Int) = when (h) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "boa"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "superaquecida"
            BatteryManager.BATTERY_HEALTH_DEAD -> "morta"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "sobretensao"
            BatteryManager.BATTERY_HEALTH_COLD -> "fria"
            else -> "desconhecida"
        }
    }
}
