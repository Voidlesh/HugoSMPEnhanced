# HugoSMP Enhanced

Inoffizieller Community-Client-Mod für [HugoSMP.net](https://hugosmp.net) — visuelle und QoL-Erweiterungen für Fabric.
Nicht offiziell von HugoSMP oder LetsHugo affiliiert.

## Status

Früher Aufbau. Implementierte Features:

- **Preis pro Item** — im Auktionshaus-Tooltip wird bei Stapeln (>1 Item) zusätzlich der Preis pro Stück angezeigt (`src/client/.../feature/auction`)
- **HugoSMP-Items in der AH-Suche** — eigene Server-Items (Plugin-ID in den Item-Daten, eigenes Modell/Custom-Model-Data oder farbiger Server-Name bei Angeboten) werden aus AH, Orders und Inventar gemerkt (`config/hugosmpenhanced-items.json`) und in der Suche mit Name, Farbe und echtem Modell vorgeschlagen (`CustomItems`)
- **Limitierte Items: Preis pro Stunde** — erkennt die Restlaufzeit (Zeitstempel in den Item-Daten, Erstelldatum + Lebensdauer, Ablaufdatum oder Restzeit in der Lore; die AH-eigene Angebotslaufzeit wird ignoriert), zeigt sie und den Preis pro Stunde im Tooltip und rahmt das günstigste Angebot pro Stunde je Sorte gold ein (`LimitedItems`, `LimitedOffers`). Das echte Lore-Format von HugoSMP war beim Bau nicht bekannt
- **Mention-Highlighting** — bei Erwähnung des eigenen Namens (oder konfigurierter Zusatzbegriffe) im Chat: Sound + zusätzliche hervorgehobene Hinweiszeile (`src/client/.../feature/mentions`)
- **TPA-Toast** — bei `/tpa`- oder `/tpahere`-Anfragen erscheint ein eigenes Toast (Icon, Titel, Untertitel, Fortschrittsbalken) statt nur Chat-Text; per Tastenkombination (Steuerung-Menü, Kategorie "HugoSMP Enhanced", standardmäßig nicht belegt) sofort annehmbar, ohne `/tpaccept` zu tippen (`src/client/.../feature/tpa`, Mixin in `ClientPacketListener.handleSystemChat`)
- **Chat-Erhalt beim Weltwechsel** — HugoSMP wechselt zwischen seinen Welten intern per Reconnect, was Vanilla wie jeden Disconnect behandelt und den Chatverlauf löscht (`Hud.onDisconnected()`). Ein Mixin unterdrückt das gezielt nur für HugoSMP-Verbindungen (`src/client/.../mixin/PersistentChatMixin.java`)
- **Kompakte Tabliste** — skaliert die Tabliste (Halten von Tab) einstellbar herunter (`src/client/.../mixin/CompactTabListMixin.java`)
- **"HugoSMP.net beitreten"-Button im Hauptmenü** — verbindet direkt, ohne den Server manuell in der Multiplayer-Liste einzutragen (`src/client/.../mixin/TitleScreenQuickJoinMixin.java`, nutzt Vanillas eigenen `ConnectScreen`-Verbindungsablauf)
- **Eigenes Hauptmenü** — offizielles HugoSMP-Logo, eigene Splash-Texte mit Animation (Pulsieren, Wackeln, Regenbogen) und Farbe, Layout "Klassisch" oder "Seitenleiste" mit Server-Karte; oben rechts umschaltbar, in der Konfig abschaltbar (`src/client/.../feature/mainmenu/`)
- **Chat-Filter** — Öffentlicher Chat/Systemnachrichten/Private Nachrichten/Clan/Freunde einzeln ausblendbar, erkannt an den echten HugoSMP-Präfixen (`[HugoSMP]`, `[Nachricht]`, `[Clan]`, `[Freunde]`); eigene Nachrichten werden nie ausgeblendet (`src/client/.../feature/chatfilter`, Mixin in `ClientPacketListener.handleSystemChat/handlePlayerChat/handleDisguisedChat`)
- **Tastatur-Ansicht** — im Vanilla-Menü "Tastenbelegung" öffnet ein Button eine gezeichnete QWERTZ-Tastatur samt Maustasten: belegte Tasten grün, doppelt belegte rot (F3-Kombinationen und Zuschauer-Tasten zählen nicht als Doppelbelegung). Taste anklicken, rechts eine Aktion suchen und per Klick drauflegen oder mit ✕ entfernen (`src/client/.../feature/keyboard`)
- **Screenshot-Galerie** — Kamera-Symbol rechts neben "Zurück zum Spiel" im Pause-Menü (oder eigene Taste, standardmäßig nicht belegt): Vorschaubilder seitenweise, Klick zeigt das Bild groß mit Kopieren (in die Zwischenablage, über PowerShell/osascript/xclip), Löschen und Im-Ordner-Zeigen (`src/client/.../feature/gallery`)
- **HugoGuard-Scammer- und Trusted-Markierung** — lädt die Scammer- und Trusted-Liste von [hugoguard.net](https://hugoguard.net) (öffentliche API, alle 10 Minuten) und zeigt gelistete Spieler mit rotem "⚠ SCAMMER" bzw. grünem "✔ TRUSTED" über dem Kopf, vor ihren Chatnachrichten (Hover: Meldungen bzw. Reputation, Klick: Profil) und bei TPA-Anfragen. Dazu ein roter/grüner Schimmer um den Spieler, der wie jedes Modell von Wänden verdeckt wird (kein Durch-Wände-Sehen). Jede Stelle einzeln abschaltbar (`src/client/.../feature/hugoguard`)
- **`/hg <Name>`** — Profilseite mit drehbarem Skin, HugoGuard-Status, Reputation (▲/▼), Anzahl Reports, letztem Report und Button zum Profil auf hugoguard.net; ohne Name das eigene Profil
- **Chat-Rechner** — steht am Ende der Chat-Eingabe eine Rechnung (z. B. "verkaufe für 64*3,5"), erscheint das Ergebnis live darüber, mit Stacks/Shulkern; Tab ersetzt die Rechnung durch das Ergebnis. Versteht + - * x / : ^ %, Klammern, deutsche/englische Kommazahlen und k, m, st (Stack = 64) (`src/client/.../feature/calculator`)
- **Notizblock** — mehrere Notizen mit Titel, über ein Symbol im Pause-Menü oder eine eigene Taste (standardmäßig nicht belegt); angeheftete Notizen bleiben oben links im HUD sichtbar (`src/client/.../feature/notes`)
- **Discord Rich Presence** — zeigt im Discord-Profil Ort (SMP, Spawn, AFK-Welt, Duels, PvP), Geld, Kills, Tode und K/D samt Spielzeit, Logo, eigenem Kopf und Buttons; zwei Textzeilen mit Platzhaltern, alles in Einstellungen → Allgemein → Discord. Werte kommen aus Scoreboard, Tabliste, Weltname, Befehlen wie `/afk` und Chat-Antworten, Kills/Tode sonst aus der Vanilla-Statistik; `/hugosmpenhanced discord` zeigt, was erkannt wurde. Spricht lokal mit der Discord-App (`discord-ipc`), kein Login (`src/client/.../feature/discord`)

Alle Features (außer dem Hauptmenü-Button, der ja vor jeder Verbindung greift) sind über `ServerCheck` (`src/client/.../util/ServerCheck.java`) auf Verbindungen zu `hugosmp.net` beschränkt — sie tun nichts auf anderen Servern.

## Konfiguration

Richtige In-Game-Konfigurationsoberfläche (YACL), erreichbar über:
- **Befehl**: `/hugosmpenhanced` im Chat
- **Mod Menu**: falls installiert, über die Mods-Liste → HugoSMP Enhanced → Zahnrad-Symbol

Deckt alle Features ab (Mention-Highlighting inkl. Trigger-Liste, Preis pro Item, TPA-Toast, Tabliste, Chat-Erhalt, Chat-Filter, Hauptmenü-Button). Die TPA-Tastenkombination wird separat im normalen Steuerung-Menü unter der Kategorie "HugoSMP Enhanced" gebunden.

Gespeichert wird unter `config/hugosmpenhanced.json` (im Minecraft-Instanzverzeichnis).

## Ziel-Setup

- Minecraft `26.2`
- Fabric Loader `>=0.19.5`
- Fabric API `0.160.0+26.2`
- YACL `3.9.6+26.2-fabric` (Pflicht, für die Config-GUI)
- Mod Menu `20.0.2` (optional, nur für die Mods-Liste-Integration)
- Java `25`

Reiner Client-Mod (`"environment": "client"`) — läuft nicht auf dem Server, keine Serverseite nötig.

## Build

```
./gradlew build
```

Das gebaute Jar liegt danach unter `build/libs/`.

`gradle.properties` pinnt `org.gradle.java.home` aktuell auf einen JDK-25-Pfad dieser Maschine (`C:/Program Files/Eclipse Adoptium/jdk-25.0.1.8-hotspot`) — bei anderer JDK-Installation anpassen oder entfernen, falls Java 25 bereits Standard auf dem `PATH` ist.

## Lizenz

Aktuell `CC0-1.0` (vom Fabric-Template übernommen) — noch final zu entscheiden.
