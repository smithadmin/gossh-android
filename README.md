# GoSSH for Android

SSH + Telnet client with user-definable command buttons, tabs, prompting buttons
and per-session colors - the Android counterpart of the desktop [GoSSH](https://github.com/smithadmin/gossh).

Kotlin + Jetpack Compose. SSH via JSch (mwiede fork), telnet implemented in-app,
terminal rendering by xterm.js inside a WebView.

## Build
1. Download the xterm.js files once (needs internet; the app is offline afterwards):

       ./fetch-xterm.sh

   (Or put xterm.js, xterm.css and addon-fit.js from the npm packages
   @xterm/xterm 5.5 and @xterm/addon-fit 0.10 into app/src/main/assets/.)
2. Open this folder in Android Studio (recent version), let it sync, and Run.
   Studio may offer to upgrade the Gradle/AGP/Kotlin versions - accepting is fine.
   Command line: run `gradle wrapper` once, then `./gradlew assembleDebug`;
   the APK is app/build/outputs/apk/debug/app-debug.apk.

Requires Android 8.0 (API 26) or newer.

## Use
- "+" opens the connection form: SSH or Telnet, host, port, user, password or an
  imported private key (Import key... copies it into app-private storage; the
  password field doubles as the key passphrase), and a color scheme.
  "Save session" keeps everything except the password.
- Each connection is a tab. Tabs keep running in the background: a foreground
  service with a notification keeps the connections alive.
- Button bar: tap to send. Edit via the menu (the three dots) -> Edit buttons.
  - {ctrl-c} {ctrl-d} {ctrl-z} {esc} {tab} {enter} send control keys
  - {{prompt:Name}}, {{prompt:Name=default}}, {{secret:Name}} ask when tapped
  - multi-line commands are sent line by line (optional delay)
  - "Ask before sending", and "only for saved session" are per button
- Extra-keys row under the buttons: Esc, Tab, sticky Ctrl, arrows, | ~ - / \ _,
  Paste, and a keyboard button.
- Colors: Session colors in the menu edits the current tab live (presets, or
  #RRGGBB for background/text/accent; save your own presets). Accent colors the
  banner above the terminal and the dot on the tab. "Default" is plain black.
- Config lives in the app's private storage (config.json, same layout as the
  desktop client; key-file paths are device-local).

## Notes
- SSH host keys: trust on first use; a changed key is refused.
- Telnet: no auto-login, relies on server echo, unencrypted.
- Disable battery optimization for the app if your phone kills long-idle sessions.
