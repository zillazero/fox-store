# FOX Store – Phase 1 (12.10.2026)
Basis: Droid-ify (GPL-3.0), Commit ff1453ed (Release 0.7.8, 13.09.2026). Debug-Build OK (JDK 17 Temurin ~/jdk17, SDK ~/android-sdk).
Build: export JAVA_HOME=~/jdk17 ANDROID_HOME=~/android-sdk; ./gradlew --no-daemon -Dorg.gradle.jvmargs="-Xmx3g" assembleDebug

## Umsetzung
1. Rebranding: applicationId/namespace site.pk187.foxstore (Kotlin-Package com.looker.droidify kann bleiben = weniger Diff), app_name "FOX Store" in allen values-*, Launcher-/TV-Banner aus assets/logo.png (rund, dunkler Grund, Schutzraum 1/4), F-Droid-/Droid-ify-Namen+Logos raus. LICENSE + Copyright bleiben, NOTICE/About: "Basiert auf Droid-ify (GPL-3.0), modifiziert von pk187", Quellcode-Link (öffentliches Repo nötig).
2. Themes: datastore/model/Theme.kt ersetzen durch FoxTheme(foxhole|magenta|amber|gruen|polizei, Standard foxhole), Farben generiert aus tokens.json (Gradle-Task -> Kotlin-Objekt + values/colors.xml für die View-Teile). Switcher-Kacheln in Einstellungen, sofort wirksam, "Animationen aus". Fonts (Orbitron/Rajdhani/Inter) als res/font (woff2 -> ttf konvertieren). Speicherung pro Gerät in DataStore.
3. Feste Hauptquelle: model/Repository.kt defaultRepositories ersetzen: FOX-Repo https://apps.pk187.site/fdroid/repo, Fingerprint als Konstante FOX_REPO_FINGERPRINT = "TODO_VON_GROK_BOT"; nicht löschbar/deaktivierbar, "Repo hinzufügen", Deeplinks (fdroidrepos://) und Repo-Editor entfernen.
4. "Weitere freie Apps": zweite, untergeordnete Sektion (eigener Tab/Filter nach Repo-Gruppe), feste kuratierte Liste, standardmäßig an/aus nach Entscheidung.
5. TV: Manifest hat bereits LEANBACK_LAUNCHER + leanback/touchscreen required=false; ergänzen: android:banner (320x180), Fokus-Rahmen 2 px Akzent auf allen Listen/Buttons (focusable, nextFocus*, Compose focusable()+onFocusChanged), D-Pad-Navigation testen auf TV-Emulator; ggf. eigenes TV-Layout (Grid statt Liste).
6. Selbst-Update: FOX Store als App im eigenen Repo; Updates kommen automatisch über die normale Update-Logik (gleiche applicationId + gleicher Signierschlüssel). Ggf. "Self-Update"-Hinweis oben.
7. Release: Signierschlüssel erst nach Freigabe erzeugen.
