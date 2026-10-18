# SecureVault v1.4 — Password Manager for Android

A secure, smart and modern personal password vault for Android (Kotlin + Jetpack Compose). Built by **Ichio**.

[![Download APK](https://img.shields.io/badge/Download-Releases-6C4CF1?logo=android&logoColor=white)](https://github.com/linggameidianza/SecureVault/releases)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-A64CF1?logo=kotlin&logoColor=white)
![Security](https://img.shields.io/badge/Encryption-AES--256--GCM-00BFA6)

## Infographic

![SecureVault infographic](docs/infografis/securevault-infografis.png)

The infographic above (in Indonesian) summarizes SecureVault's main functions and strengths:

| Section | Contents |
|---|---|
| **6 key strengths** | Anti-screenshot / screen recording (FLAG_SECURE), data-breach check via Have I Been Pwned, expiring-password reminders (D-2), AES-256-GCM encryption in the Android Keystore, biometric / device-credential lock, encrypted backup & restore |
| **App screens** | Generator & breach check, Tasks & subtasks, reminder notifications, and the screen protected against screenshots |
| **How the breach check works** | *k-Anonymity*: the password is SHA-1 hashed, only the first 5 characters of the hash are sent to HIBP, and matching is done on the phone |
| **Expiry lifecycle** | Save account → monitored automatically → D-2 notification → expired (red marker) → change password & new cycle |
| **Security layers & features** | 5 protection layers, 10 core features, and technical specs |

> **Note:** the screens in the infographic are illustrations recreated from the app's design — real screenshots cannot be taken because SecureVault blocks screenshots and screen recording.
>
> Files: [high-resolution PNG](docs/infografis/securevault-infografis.png) · [PDF](docs/infografis/securevault-infografis.pdf) · [HTML source](docs/infografis/securevault-infografis.html)

## Download & install

1. Download the APK from the **[Releases](https://github.com/linggameidianza/SecureVault/releases)** page.
2. Open the file on an Android 8.0+ phone; allow *"Install unknown apps"* if prompted.
3. On first launch, unlock with your fingerprint or your device screen lock.

An APK is also built automatically by GitHub Actions (`.github/workflows/build-apk.yml`) on every push — download it from the **Actions → Artifacts** tab.

## Features
- **Accounts & passwords**: store service name, username, password, URL, notes; search; copy username/password (clipboard is marked sensitive and auto-cleared after 30 seconds).
- **Password generator**: length 6–64, upper/lowercase, digits, symbols, avoid look-alike characters, strength indicator (SecureRandom).
- **Notes**: free-form notes with encrypted content.
- **Backup**: all accounts, passwords & notes → an encrypted `.svbk` file protected by a backup password (PBKDF2-SHA256, 210,000 iterations + AES-256-GCM). Save anywhere (local storage, Google Drive, etc. via the Storage Access Framework).
- **Restore**: from a `.svbk` file (same or another device), in *Merge* mode (duplicates skipped) or *Replace all*.
- **Password expiry**: a renewal interval (30–365 days, refreshed automatically when the password changes) or a specific date; orange/red markers and a "needs changing" filter.
- **Notifications**: automatic reminders 2 days before a password expires and once it has expired (WorkManager, checked every 6 hours).
- **Tasks**: a task list with subtasks (checklist), auto-completed when all subtasks are ticked, with optional notification reminders (AlarmManager, surviving reboot) and a "Mark done" action.
- **Data-breach check (Have I Been Pwned)**: check a password (Pwned Passwords, k-anonymity, free), audit all stored passwords, and check an email (subscription HIBP API key, stored encrypted).
- **Help & About**: a complete usage guide (Settings → Help) and app info (Settings → About).
- **App lock**: unlock with a strong biometric or your device screen lock (PIN/pattern/password). Unlocking releases the vault key, which is **bound to that authentication** — so it is a cryptographic lock, not just a UI gate. Auto-locks 30 seconds after going to the background.
- **Other security**: passwords & notes are encrypted with AES-256-GCM using an auth-bound Android Keystore key; the **entire database is encrypted with SQLCipher** (so metadata such as titles, usernames and URLs is protected too); `FLAG_SECURE` (blocks screenshots); `allowBackup=false`; the internet is used only for the data-breach check, at the user's request.

## Build from source
1. Open this folder in **Android Studio** (Ladybug 2024.2 or newer), JDK 17+.
2. Wait for the Gradle sync (it downloads Gradle 8.11.1 & dependencies automatically).
3. Run ▶ to a phone/emulator (Android 8.0 / API 26+), or `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
4. For a release: Build ▸ Generate Signed App Bundle / APK.

## Structure
```
app/src/main/java/id/pln/securevault/
├─ MainActivity.kt, SecureVaultApp.kt
├─ crypto/    CryptoManager (auth-bound Keystore AES-GCM), PasswordCrypto (PBKDF2, backup
│             encryption), LegacyCryptoManager & VaultMigration (one-time key migration)
├─ security/  AppLock (auto-lock), BiometricHelper
├─ data/      Room entities/DAO/database, DatabaseKey (SQLCipher passphrase), VaultRepository
├─ backup/    BackupManager (encrypted JSON export/import)
├─ util/      PasswordGenerator, ClipboardHelper
└─ ui/        VaultViewModel, AppRoot (navigation), screens/, theme/
```

## Important notes
- **A lost backup password cannot be recovered** — the backup file cannot be opened without it.
- On-device data is tied to the device's Keystore key; moving to a new phone or doing a factory reset **requires** Backup → Restore.
- If you can no longer unlock the app (for example the device screen lock was removed or changed and the key was invalidated): clear the app data (Settings ▸ Apps ▸ SecureVault ▸ Clear data), then restore from backup.
- Biometrics / device credential are the real unlock: a successful verification releases the cryptographic key that decrypts your data.
