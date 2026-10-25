<div align="center">

<img src="docs/discord/app-icon.png" alt="HugoSMP Enhanced" width="128">

# HugoSMP Enhanced

**Der Client-Mod für HugoSMP.net: Wirtschaft, Sicherheit und Komfort an einem Ort.**

[![Release](https://img.shields.io/github/v/release/Voidlesh/HugoSMPEnhanced?include_prereleases&label=Release)](https://github.com/Voidlesh/HugoSMPEnhanced/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/Voidlesh/HugoSMPEnhanced/build.yml?label=Build)](https://github.com/Voidlesh/HugoSMPEnhanced/actions)
[![Lizenz: MIT](https://img.shields.io/badge/Lizenz-MIT-blue.svg)](LICENSE)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11%20%7C%2026.3-62b47a)
![Fabric](https://img.shields.io/badge/Mod--Loader-Fabric-dbd0b4)

</div>

> **Alpha:** Die Mod ist in aktiver Entwicklung. Einige Erkennungen (z. B. Texte von Zahlungen, Aufträgen oder Privatnachrichten) sind noch nicht auf jeder Seite von HugoSMP bestätigt. Fehler bitte als [Issue](https://github.com/Voidlesh/HugoSMPEnhanced/issues) melden.
>
> Inoffizieller Community-Mod. Nicht von HugoSMP oder LetsHugo betrieben oder unterstützt.

## Überblick

HugoSMP Enhanced erweitert den Minecraft-Client um Werkzeuge, die auf HugoSMP.net den Alltag leichter machen: bessere Übersicht im Auktionshaus, Warnungen vor Scammern, ein Chat, der mitdenkt, und ein eigenes Einstellungsmenü.

Alle serverbezogenen Funktionen sind auf Verbindungen zu `hugosmp.net` beschränkt. Auf anderen Servern tut die Mod nichts. Sie läuft rein clientseitig, auf dem Server muss nichts installiert werden.

## Features

### Wirtschaft
- **Auktionshaus:** Preis pro Item bei Stapeln, eigene Suche mit Vorschlägen, Verlauf und Favoriten, HugoSMP-Spezialitems mit echtem Modell, bei limitierten Items Restlaufzeit und Preis pro Stunde mit Markierung des günstigsten Angebots.
- **Aufträge-Helfer:** Lieferbare Aufträge grün, der lukrativste gold umrandet, mit Tooltip zu Menge und Ertrag.
- **Geld-Tracker und Zahlungs-Log:** Kontostand, Verlauf als Kurve, Herkunft des Geldes und alle Zahlungen mit CSV-Export.
- **Farm-Tracker und Spawner-Lager:** Ertrag pro Stunde, Warnung bei vollem Inventar oder fast kaputtem Werkzeug, Erinnerung bevor ein Spawner voll ist, Spawner-Preise.
- **Verkaufs-Assistent und Chat-Rechner:** Rechnungen im Chat live auswerten, mit Stacks und Shulkern.

### Sicherheit
- **HugoGuard-Anbindung:** Scammer- und Trusted-Markierung über dem Kopf, im Chat und bei TPA-Anfragen, Warnung beim Bezahlen. Profilseite mit `/hg <Name>`.
- **Clan-Abzeichen:** Echte Clans werden markiert, ähnlich aussehende Fake-Clans gewarnt.
- **Anti-Leak:** Verbirgt oder verfälscht Koordinaten in F3, damit sie nicht versehentlich im Stream oder auf Bildern landen.
- **Streamer-Modus:** Blendet Namen und Skins aus, dazu Werkzeuge für Auktionen und Verlosungen mit Glücksrad.

### Chat und Komfort
- **Chat:** Filter nach Kategorie, Erwähnungs-Hervorhebung, Stichwort-Alarm, Nachrichten-Tab mit Unterhaltungen pro Spieler, zweiter Chat, Chat bleibt beim Weltwechsel erhalten.
- **Eigene Menüs:** Hauptmenü mit Schnell-Beitritt, Tastatur-Ansicht mit Doppelbelegungs-Erkennung, Screenshot-Galerie, Notizblock, Farbcode-Editor für `/rename` und `/sign`.
- **Inventar:** Eigene Knöpfe und mehrere speicherbare Layouts.
- **Anzeigen:** HUD-Editor, Entity-Heatmap für Farmen, kompakte Tabliste, Zoom, Fullbright, FPS-Anzeige.
- **Discord Rich Presence:** Ort, Geld, Kills und Spielzeit im Discord-Profil.
- **Updates:** Hinweis im Spiel, wenn ein neues Release erscheint, mit Changelog und Installation per Klick.

Eine Übersicht pro Version steht in den [Release-Notizen](release-notes/).

## Installation

1. [Fabric Loader](https://fabricmc.net/use/installer/) ab `0.19.5` für **Minecraft 1.21.11** oder **26.3** installieren.
2. [Fabric API](https://modrinth.com/mod/fabric-api) in den Ordner `mods` legen.
3. Von der [Releases-Seite](https://github.com/Voidlesh/HugoSMPEnhanced/releases) die Jar `hugosmpenhanced-<Version>.jar` nach `mods` legen. Es ist dieselbe Datei für Minecraft 1.21.11 und 26.3.
4. Optional: [Mod Menu](https://modrinth.com/mod/modmenu) für den Zugang über die Mods-Liste.
5. Minecraft starten und `hugosmp.net` beitreten, oder den neuen Knopf im Hauptmenü nutzen.

Voraussetzungen: Java 21 oder neuer (Minecraft 26.3 braucht Java 25).

### Updates

Die Mod prüft beim Start und danach alle sechs Stunden, ob auf GitHub ein neueres Release vorliegt. Dann erscheinen ein Hinweis im Hauptmenü und eine Chatzeile. Im Update-Fenster stehen die Änderungen, "Installieren" lädt die Jar herunter, prüft sie und tauscht sie beim Beenden des Spiels aus. Automatische Installation ist standardmäßig aus.

Manuell aktualisieren: neue Jar in `mods` legen und die alte löschen, denn der Dateiname enthält die Version.

## Konfiguration

Die Einstellungen öffnest du mit `/hugosmpenhanced` (Kurzform `/hse`) oder über Mod Menu. Sie sind nach Kategorien geordnet und durchsuchbar. Die Konfiguration liegt unter `config/hugosmpenhanced.json` im Minecraft-Verzeichnis, weitere Daten wie Notizen oder Zahlungen unter `config/hugosmpenhanced/`.

Nützliche Befehle: `/hse hud` (Anzeigen verschieben), `/hse geld`, `/hse zahlungen`, `/hse farm`, `/hse spawner`, `/hse nachrichten`, `/hse update`, `/hg <Name>`.

Tastenbelegungen findest du im Steuerungsmenü unter der Kategorie "HugoSMP Enhanced". Wenn etwas nicht erkannt wird, kopiert `Strg+Shift+C` in einem Menü oder `/hse debug` die Rohdaten für einen Fehlerbericht.

## Selbst bauen

Benötigt wird ein JDK 25 (die 1.21.11-Hälfte wird mit Java-21-Ziel gebaut).

```
./gradlew build
```

Die fertige Jar für beide Minecraft-Versionen liegt danach in `build/libs/hugosmpenhanced-<Version>.jar`. Beide Versionen entstehen aus denselben Quellen in `src/`, siehe [Mehrere Minecraft-Versionen](docs/MULTIVERSION.md). Einen Testclient startest du mit `./gradlew :mc1_21_11:runClient` oder `./gradlew :mc26_3:runClient`, die automatischen Client-Tests mit `./gradlew :mc1_21_11:runClientGameTest` (oder `:mc26_3:`).

`gradle.properties` enthält einen festen JDK-Pfad für Windows (`org.gradle.java.home`). Bei anderer Installation anpassen oder entfernen.

## Releases

Ändert sich `version` in `gradle.properties` auf `main`, baut eine GitHub Action die Mod und legt einen Release-Entwurf mit der Jar an. Der Text kommt aus `release-notes/<version>.md`. Mehr dazu in den [Release-Notizen](release-notes/README.md).

## Lizenz

[MIT](LICENSE) © 2026 Voidlesh
