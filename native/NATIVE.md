# Taxa nativo — app instalável (Android) via Capacitor

Este guia transforma o app web em um **APK real** que roda o mesmo app web por
cima de um **plugin nativo em Kotlin**, ganhando o que o navegador nunca teve:
corrente real (mA), temperatura, saúde da bateria, estado **real** da tela e
amostragem em **segundo plano** (com o celular bloqueado).

O app web na raiz do repositório continua intocado — ele é a interface. Rodando
no Chrome, funciona como sempre; empacotado no APK, ganha o motor nativo.

---

## Pré-requisitos (na sua máquina)

- **Node.js 18+** e **npm**
- **Android Studio** (instala o Android SDK e o Gradle automaticamente)
- Cabo USB para instalar no seu aparelho (ou gerar o APK e transferir)

> Não dá para gerar o APK sem o Android SDK — por isso o build roda na sua
> máquina, não no ambiente do repositório.

---

## Passo a passo

### 1. Instalar dependências
```bash
npm install
```

### 2. Gerar o projeto Android
```bash
npm run add:android
```
Isso copia o app web para `www/` e cria a pasta `android/` (projeto nativo).

### 3. Instalar os arquivos Kotlin do plugin
Copie os `.kt` de `native/` para dentro do pacote do app gerado:

```
android/app/src/main/java/com/joaobz14/taxa/
    ├── BatteryMonitorPlugin.kt
    ├── BatteryLog.kt
    └── BatteryLogService.kt
```

> Se a pasta `com/joaobz14/taxa` não existir, crie-a. O `appId` em
> `capacitor.config.json` (`com.joaobz14.taxa`) define esse caminho.

### 4. Registrar o plugin
Abra `android/app/src/main/java/com/joaobz14/taxa/MainActivity.kt` e registre
o plugin antes do `super.onCreate` **ou** via `registerPlugin`:

```kotlin
package com.joaobz14.taxa

import android.os.Bundle
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(BatteryMonitorPlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
```

### 5. Permissões e serviço no AndroidManifest
Em `android/app/src/main/AndroidManifest.xml`, dentro de `<manifest>` adicione
as permissões:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

E dentro de `<application>` declare o serviço:

```xml
<service
    android:name=".BatteryLogService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Registro de descarga de bateria para diagnóstico local" />
</service>
```

### 6. Compilar e instalar
Com o aparelho conectado (depuração USB ligada):
```bash
npm run open:android      # abre no Android Studio → botão Run
```
ou por linha de comando:
```bash
cd android && ./gradlew assembleDebug
# APK em android/app/build/outputs/apk/debug/app-debug.apk
```
Transfira o `app-debug.apk` para o celular e instale (permita "fontes
desconhecidas" para este instalador).

### Depois de editar o app web
Sempre que mudar `index.html`/`sw.js`/`native-bridge.js`:
```bash
npm run sync
```

---

## O que o plugin expõe ao app web

Via `window.NativeBattery` (definido em `native-bridge.js`):

| Método | Retorna |
|---|---|
| `available` | `true` só quando rodando nativo |
| `read()` | `{ level, charging, currentMa, currentRaw, chargeCounterUah, tempC, voltageMv, health, screenOn, ts }` |
| `startLogging(intervalSec)` | inicia o serviço em 2º plano |
| `stopLogging()` | para o serviço |
| `getLog()` | array de amostras gravadas com a tela bloqueada |
| `clearLog()` | limpa o log |
| `onScreen(cb)` | callback no estado **real** da tela (ligada/desligada) |

---

## Calibração importante: corrente (mA)

`BATTERY_PROPERTY_CURRENT_NOW` **não tem unidade padronizada** entre fabricantes:

- Muitos aparelhos: microamperes (µA) → dividir por 1000 dá mA (é o padrão em `currentMa`).
- Alguns **Samsung**: já reportam em **mA** e às vezes com **sinal invertido**
  (positivo enquanto descarrega).

Por isso o plugin devolve também `currentRaw` (valor cru). Ao testar no seu
S25 Ultra: descarregue a bateria e compare `currentRaw` com um valor plausível
(um telefone em uso puxa algo entre ~200 e ~1500 mA). Se `currentMa` vier na
casa de 0,x, o aparelho reporta em mA — use `currentRaw` direto (÷1) e o
módulo/valor absoluto para o sinal. Me avise o que aparece e eu ajusto a
conversão no app.

---

## Limitação que continua, mesmo nativo

O consumo **por app** (a tela "WhatsApp 6,9%") depende de `BATTERY_STATS`, uma
permissão privilegiada de sistema — indisponível para apps comuns sem root.
A aproximação viável é cruzar o **tempo de uso por app** (`UsageStatsManager`,
que você libera nas configurações) com a corrente medida. Fica como etapa
seguinte, se você quiser.
