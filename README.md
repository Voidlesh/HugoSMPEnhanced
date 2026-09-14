# HugoSMP Enhanced

Inoffizieller Community-Client-Mod für [HugoSMP.net](https://hugosmp.net) — visuelle und QoL-Erweiterungen für Fabric.
Nicht offiziell von HugoSMP oder LetsHugo affiliiert.

## Status

Früher Aufbau. Implementierte Features:

- **Preis pro Item** — im Auktionshaus-Tooltip wird bei Stapeln (>1 Item) zusätzlich der Preis pro Stück angezeigt (`src/client/.../feature/auction`)
- **Mention-Highlighting** — bei Erwähnung des eigenen Namens (oder konfigurierter Zusatzbegriffe) im Chat: Sound + zusätzliche hervorgehobene Hinweiszeile (`src/client/.../feature/mentions`)
- **TPA-Toast** — bei `/tpa`- oder `/tpahere`-Anfragen erscheint ein eigenes Toast (Icon, Titel, Untertitel, Fortschrittsbalken) statt nur Chat-Text; per Tastenkombination (Steuerung-Menü, Kategorie "HugoSMP Enhanced", standardmäßig nicht belegt) sofort annehmbar, ohne `/tpaccept` zu tippen (`src/client/.../feature/tpa`, Mixin in `ClientPacketListener.handleSystemChat`)
- **Chat-Erhalt beim Weltwechsel** — HugoSMP wechselt zwischen seinen Welten intern per Reconnect, was Vanilla wie jeden Disconnect behandelt und den Chatverlauf löscht (`Hud.onDisconnected()`). Ein Mixin unterdrückt das gezielt nur für HugoSMP-Verbindungen (`src/client/.../mixin/PersistentChatMixin.java`)
- **Kompakte Tabliste** — skaliert die Tabliste (Halten von Tab) einstellbar herunter (`src/client/.../mixin/CompactTabListMixin.java`), plus Toggle, um den eigenen Namen immer oben anzuzeigen (`SelfOnTopTabListMixin.java`)
- **"HugoSMP.net beitreten"-Button im Hauptmenü** — verbindet direkt, ohne den Server manuell in der Multiplayer-Liste einzutragen (`src/client/.../mixin/TitleScreenQuickJoinMixin.java`, nutzt Vanillas eigenen `ConnectScreen`-Verbindungsablauf)
- **Chat-Filter** — Öffentlicher Chat/Systemnachrichten/Private Nachrichten/Clan/Freunde einzeln ausblendbar, erkannt an den echten HugoSMP-Präfixen (`[HugoSMP]`, `[Nachricht]`, `[Clan]`, `[Freunde]`); eigene Nachrichten werden nie ausgeblendet (`src/client/.../feature/chatfilter`, Mixin in `ClientPacketListener.handleSystemChat/handlePlayerChat/handleDisguisedChat`)

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
