# NAS Sync

File manager Android per il tuo NAS di casa via **WebDAV** (UGREEN, Synology, QNAP o qualsiasi server WebDAV),
con copia opzionale delle cartelle sul telefono per averle anche senza rete.

*Android file manager for your home NAS over WebDAV, with an optional offline copy of selected folders. The interface is in Italian.*

## Scarica

- **APK firmato**: [ultima release](https://github.com/enricofur86-eng/nas-sync-app/releases/latest)
- **Obtainium**: aggiungi `https://github.com/enricofur86-eng/nas-sync-app`
- **F-Droid**: in arrivo

Impronta SHA-256 del certificato di firma (APK di GitHub):
`C9:74:A2:0E:60:23:2E:76:86:BB:65:A3:93:0D:EA:55:CF:EB:8A:D7:C2:C0:38:39:10:50:C4:2B:F6:3B:B8:C1`

## Funzioni

- Sfoglia il NAS via WebDAV, anche da fuori casa
- Apre i file con le app del telefono e carica file sul NAS dal pulsante "Condividi"
- Crea cartelle, rinomina, elimina, condivide più file insieme
- Copia sul telefono (backup) opzionale delle cartelle scelte, sempre allineata al NAS
- Aggiornamento automatico in background, solo Wi-Fi o anche con i dati mobili
- Collegamenti alle cartelle sulla schermata Home
- Certificati autofirmati: mostra l'impronta e la ricorda
- Nessuna pubblicità, nessun tracciamento: l'app parla solo con il tuo NAS

## Compilare

```
./gradlew assembleFdroidRelease
```

L'APK (non firmato) si trova in `app/build/outputs/apk/fdroid/release/`.
La chiave di firma non è in questo repository: per firmare in proprio impostare le variabili
`NASSYNC_KEYSTORE`, `NASSYNC_KEYSTORE_PASSWORD`, `NASSYNC_KEY_ALIAS`, `NASSYNC_KEY_PASSWORD`.

Versioni:
- `fdroid` — con il pulsante "Sostieni lo sviluppo" nelle impostazioni
- `play` — per Google Play, senza link di donazione

## Sostieni lo sviluppo

Se l'app ti è utile puoi offrire un caffè: https://www.paypal.me/enricofurlan

## Licenza

GPL-3.0-or-later — vedi [LICENSE](LICENSE).
