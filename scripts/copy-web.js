// Copia os arquivos web (que ficam na raiz, para o GitHub Pages continuar
// servindo) para www/, que é o webDir que o Capacitor empacota no APK.
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const out = path.join(root, "www");
fs.mkdirSync(out, { recursive: true });

const ARQUIVOS = ["index.html", "sw.js", "manifest.webmanifest", "native-bridge.js"];
let n = 0;
for (const f of ARQUIVOS) {
  const src = path.join(root, f);
  if (fs.existsSync(src)) { fs.copyFileSync(src, path.join(out, f)); n++; }
}
// ícones, se existirem
for (const f of fs.readdirSync(root)) {
  if (/\.(png|svg|ico)$/i.test(f)) { fs.copyFileSync(path.join(root, f), path.join(out, f)); n++; }
}
console.log(`web assets copiados para www/ (${n} arquivos)`);
