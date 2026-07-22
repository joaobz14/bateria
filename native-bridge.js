/* ------------------------------------------------------------------
   Ponte entre o app web e a camada nativa (plugin Kotlin BatteryMonitor).

   - No navegador: window.Capacitor não existe → available = false, e o app
     segue usando navigator.getBattery() normalmente (nada muda).
   - No app nativo (Capacitor): expõe leitura rica (corrente em mA, temperatura,
     saúde, estado REAL da tela) e o log em segundo plano gravado pelo serviço.

   Este arquivo é seguro de carregar em qualquer lugar — só ativa o que existe.
------------------------------------------------------------------ */
(function () {
  "use strict";

  const Cap = window.Capacitor;
  const isNative = !!(Cap && typeof Cap.isNativePlatform === "function" && Cap.isNativePlatform());

  let plugin = null;
  if (isNative && typeof Cap.registerPlugin === "function") {
    try { plugin = Cap.registerPlugin("BatteryMonitor"); } catch (e) { plugin = null; }
  }

  const screenSubs = [];
  if (plugin && typeof plugin.addListener === "function") {
    try {
      plugin.addListener("screen", ev => {
        const on = !!(ev && ev.on);
        screenSubs.forEach(cb => { try { cb(on); } catch (e) {} });
      });
    } catch (e) {}
  }

  window.NativeBattery = {
    available: !!plugin,

    /* Leitura instantânea rica. Devolve null se indisponível.
       { level, charging, currentMa, currentRaw, chargeCounterUah,
         tempC, voltageMv, health, screenOn, ts } */
    async read() {
      if (!plugin) return null;
      try { return await plugin.read(); } catch (e) { return null; }
    },

    /* Serviço em primeiro plano: registra amostras mesmo com a tela bloqueada. */
    async startLogging(intervalSec) {
      if (plugin && plugin.startLogging) {
        try { await plugin.startLogging({ intervalSec: intervalSec || 30 }); } catch (e) {}
      }
    },
    async stopLogging() {
      if (plugin && plugin.stopLogging) { try { await plugin.stopLogging(); } catch (e) {} }
    },
    /* Recupera o log gravado em segundo plano como array de amostras. */
    async getLog() {
      if (!plugin || !plugin.getLog) return [];
      try { const r = await plugin.getLog(); return JSON.parse((r && r.json) || "[]"); }
      catch (e) { return []; }
    },
    async clearLog() {
      if (plugin && plugin.clearLog) { try { await plugin.clearLog(); } catch (e) {} }
    },

    /* Assina o estado REAL da tela do aparelho (ligada/desligada). */
    onScreen(cb) { if (typeof cb === "function") screenSubs.push(cb); }
  };
})();
