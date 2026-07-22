package com.joaobz14.taxa

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Armazenamento simples do log de bateria em um arquivo JSON no diretório
 * privado do app. É o que permite recuperar, do app web, as amostras que o
 * serviço gravou enquanto o telefone esteve bloqueado.
 */
object BatteryLog {
    private const val FILE = "battery-log.json"
    private const val MAX = 5000

    @Synchronized
    fun append(ctx: Context, sample: JSONObject) {
        val arr = load(ctx)
        arr.put(sample)
        val out = if (arr.length() > MAX) {
            val n = JSONArray()
            for (i in (arr.length() - MAX) until arr.length()) n.put(arr.get(i))
            n
        } else arr
        File(ctx.filesDir, FILE).writeText(out.toString())
    }

    fun readText(ctx: Context): String {
        val f = File(ctx.filesDir, FILE)
        return if (f.exists()) f.readText() else "[]"
    }

    fun clear(ctx: Context) {
        File(ctx.filesDir, FILE).delete()
    }

    private fun load(ctx: Context): JSONArray {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return JSONArray()
        return try { JSONArray(f.readText()) } catch (e: Exception) { JSONArray() }
    }
}
