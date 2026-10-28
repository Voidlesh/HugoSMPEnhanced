# Mehrere Minecraft-Versionen, eine Jar

Die Mod läuft mit einer einzigen Jar auf Minecraft 1.21.11, 26.1 (26.1, 26.1.1, 26.1.2), 26.2 und 26.3. Es gibt keine weiteren Versionen dazwischen: nach 1.21.11 kam 26.1, die Namen 1.22 bis 1.99 gibt es nicht. Die Versionen haben verschiedene Namen im Spiel-Code (`GuiGraphics` gegen `GuiGraphicsExtractor`, `GLFW` gegen `InputConstants` und so weiter), 1.21.11 wird beim Bauen auf Intermediary umgeschrieben, 26.x nicht. Deshalb wird jede Version einzeln gebaut und beides danach zusammengesetzt.

## Aufbau
- `src/main`, `src/gametest`: **ein** Quellbaum für beide Versionen. Geschrieben ist er für 26.x. Stellen, die sich unterscheiden, stehen zwischen Direktiven (siehe unten).
- `versions/1.21.11`, `versions/26.1`, `versions/26.2`, `versions/26.3`: je ein Gradle-Projekt mit eigener Loom-Variante, Abhängigkeiten und `gradle.properties`. Das Projekt `26.1` baut gegen 26.1.2, läuft aber auch auf 26.1 und 26.1.1.
- `gradle/line.gradle`: gemeinsamer Teil beider Projekte. Er schreibt vor dem Kompilieren die Quellen der jeweiligen Version nach `versions/<v>/build/generated/` (`PreprocessTask`).
- `src/loader`: die Weiche. `Entry`, `ModMenuEntry` und `GatePlugin` laufen auf allen Versionen und wählen anhand der Minecraft-Version die passende Hälfte (`Line`: 1.21.x das normale Paket, 26.1 `mc26_1`, 26.2 `mc26_2`, 26.3 und alles Neuere `mc26_3`).
- `buildSrc`: der Vorverarbeiter (`Preprocessor`) und der Zusammenbau (`MergeJarTask`).
- `./gradlew mergedJar` (und `build`) schreibt `build/libs/hugosmpenhanced-<Version>.jar`.

## Direktiven im Quelltext
```java
//? if v263
Zeilen für 26.3
//? elif modern
Zeilen für 26.1 und 26.2
//? else
Zeilen für 1.21.11
//? end
```
- Bedingungen: `legacy` (nur 1.21.11), `modern` (ab 26.1), `v262` (ab 26.2), `v263` (ab 26.3), `pre262` (vor 26.2), `pre263` (vor 26.3). Der erste Zweig, der zutrifft, wird geschrieben.
- Meist reichen zwei Zweige: `if v263` / `else` (26.1 und 26.2 gehen dann mit 1.21.11), `if v262` / `else` (nur 26.1 geht mit 1.21.11) oder `if modern` / `else`.
- Steht `//? only <Bedingung>` in der ersten Zeile einer Datei, gibt es die Datei nur in diesen Versionen.
- Verschachteln geht nicht.
- Die Direktiven stehen auch in `fabric.mod.json` und den Mixin-Configs (JSON ohne Kommentare ist erst nach dem Vorverarbeiten gültig).

## Umbenennungen (`versions/swaps*.txt`)
Reine Umbenennungen stehen nicht als Blöcke im Code. In allen gemeinsamen Zeilen wird für ältere Versionen ersetzt, was in den Listen steht (`Regex<TAB>Ersatz`): `swaps.txt` für 1.21.11 (zum Beispiel `GuiGraphicsExtractor` → `GuiGraphics`), `swaps-26.2.txt` für 26.1 und 26.2 (zum Beispiel `InputConstants.Type.KEYBOARD` → `KEYSYM`), dazu `swaps-26.1.txt` nur für 26.1 (zum Beispiel `.gui.hud.` → `.gui.`). Innerhalb von Blöcken gelten die Listen nur für 26.1 und 26.2, nicht für 1.21.11. Schreibe neuen Code in der 26.3-Form. Gibt es für eine ältere Version etwas anderes, das die Listen nicht trifft, setze einen Block.

## Neues Feature einbauen
1. Code in `src/main/java` in der 26.x-Form schreiben, Unterschiede als Block.
2. `./gradlew build` baut alle Versionen und die Jar.
3. 1.21.11: `./gradlew :mc1_21_11:runClientGameTest` (Client-Tests, headless mit `xvfb-run`). 26.x lässt sich ohne Grafikkarte nicht ganz starten. Was geht: `tools/mixin-audit/run.sh 26.2` (auch 26.1, 26.3) startet den echten Client mit der Merge-Jar und lädt jede Klasse, die ein Mixin trifft. Schlägt ein Mixin fehl, steht `HSE-FAIL` im Ergebnis. BetterF3 und Sodium fehlen dort absichtlich (drei Treffer, optional). Alles andere muss im Spiel geprüft werden.

## Der Zusammenbau (`MergeJarTask`)
1. Basis ist die Jar von 1.21.11 (Manifest bleibt, sonst findet der Loader die Mixin-Ziele nicht).
2. Alle Klassen jeder 26.x-Jar werden nach `com.hugosmpenhanced.mc26_1.*`, `mc26_2.*` und `mc26_3.*` verschoben (ASM), damit alle Kopien nebeneinander liegen. Dateien, die keine Klassen sind (Texturen, Sprachen), liegen einmal in der Jar. Unterscheiden sie sich zwischen den Versionen, bricht der Bau ab.
3. Eine Mixin-Config je Kopie (`hugosmpenhanced.client.mixins.json`, `hugosmpenhanced.mc26_1.mixins.json` und so weiter) mit dem `GatePlugin`. Es lässt auf der falschen Version alle Mixins aus und lädt die Hälften der anderen Versionen nie (ihre Klassen sind für Java 25 gebaut).
4. `fabric.mod.json`: Einstieg und Mod Menu gehen über die Weiche, `minecraft` ist `["1.21.11", ">=26.1"]`.

Die Jar ist rund viermal so groß wie eine einzelne.

## Neue Minecraft-Version später
Weder Jar-in-Jar noch ein Versionsbereich wie `26.*` im Code ersetzen das: Eine neue Version braucht ein weiteres Projekt unter `versions/` (oder die vorhandene Hälfte läuft weiter, wenn sich nichts ändert; dann nur die `depends`-Angabe im Zusammenbau erweitern) und, falls sich die Namen ändern, weitere Direktiven. Neue Hälfte: Projekt anlegen, in `settings.gradle`, `build.gradle` (`modernProjects`) und `Line.of` eintragen, die Quellen per `tools/mixin-audit/run.sh` prüfen. Versionen nach 26.3 nimmt die 26.3-Hälfte, bis es eine eigene gibt; ob sie dort läuft, ist ungeprüft.
