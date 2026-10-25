# Mehrere Minecraft-Versionen, eine Jar

Die Mod läuft mit einer einzigen Jar auf Minecraft 1.21.11 und 26.3. Die beiden Versionen haben verschiedene Namen im Spiel-Code (`GuiGraphics` gegen `GuiGraphicsExtractor`, `GLFW` gegen `InputConstants` und so weiter), 1.21.11 wird beim Bauen auf Intermediary umgeschrieben, 26.x nicht. Deshalb wird jede Version einzeln gebaut und beides danach zusammengesetzt.

## Aufbau
- `src/main`, `src/gametest`: **ein** Quellbaum für beide Versionen. Geschrieben ist er für 26.x. Stellen, die sich unterscheiden, stehen zwischen Direktiven (siehe unten).
- `versions/1.21.11`, `versions/26.3`: je ein Gradle-Projekt mit eigener Loom-Variante, Abhängigkeiten und `gradle.properties`.
- `gradle/line.gradle`: gemeinsamer Teil beider Projekte. Er schreibt vor dem Kompilieren die Quellen der jeweiligen Version nach `versions/<v>/build/generated/` (`PreprocessTask`).
- `src/loader`: die Weiche. `Entry`, `ModMenuEntry` und `GatePlugin` laufen auf beiden Versionen und wählen anhand der Minecraft-Version die passende Hälfte.
- `buildSrc`: der Vorverarbeiter (`Preprocessor`) und der Zusammenbau (`MergeJarTask`).
- `./gradlew mergedJar` (und `build`) schreibt `build/libs/hugosmpenhanced-<Version>.jar`.

## Direktiven im Quelltext
```java
//? if modern
neue Zeilen (26.x)
//? else
alte Zeilen (1.21.11)
//? end
```
- `//? if legacy` ... `//? end` gilt nur für 1.21.11.
- Steht `//? only modern` (oder `legacy`) in der ersten Zeile einer Datei, gibt es die Datei nur in dieser Version.
- Verschachteln geht nicht.
- Die Direktiven stehen auch in `fabric.mod.json` und den Mixin-Configs (JSON ohne Kommentare ist erst nach dem Vorverarbeiten gültig).

## Umbenennungen (`versions/swaps.txt`)
Reine Umbenennungen stehen nicht als Blöcke im Code. In allen gemeinsamen Zeilen wird für 1.21.11 ersetzt, was in `versions/swaps.txt` steht (`Regex<TAB>Ersatz`), zum Beispiel `GuiGraphicsExtractor` → `GuiGraphics` oder `.text(` → `.drawString(`. Schreibe neuen Code in der 26.x-Form. Gibt es für 1.21.11 etwas anderes, das die Liste nicht trifft, setze einen `if modern`-Block.

## Neues Feature einbauen
1. Code in `src/main/java` in der 26.x-Form schreiben, Unterschiede als Block.
2. `./gradlew build` baut beide Versionen und die Jar.
3. 1.21.11: `./gradlew :mc1_21_11:runClientGameTest` (Client-Tests, headless mit `xvfb-run`). 26.3 lässt sich ohne Grafikkarte nicht starten, dort muss im Spiel geprüft werden.

## Der Zusammenbau (`MergeJarTask`)
1. Basis ist die Jar von 1.21.11 (Manifest bleibt, sonst findet der Loader die Mixin-Ziele nicht).
2. Alle Klassen der 26.x-Jar werden nach `com.hugosmpenhanced.mc26.*` verschoben (ASM), damit beide Kopien nebeneinander liegen.
3. Zwei Mixin-Configs (`hugosmpenhanced.client.mixins.json`, `hugosmpenhanced.mc26.mixins.json`) mit dem `GatePlugin`. Es lässt auf der falschen Version alle Mixins aus und lädt die Hälfte der anderen Version nie (ihre Klassen sind für Java 25 gebaut).
4. `fabric.mod.json`: Einstieg und Mod Menu gehen über die Weiche, `minecraft` ist `["1.21.11", ">=26.3"]`.

Die Jar ist rund doppelt so groß wie eine einzelne.

## Neue Minecraft-Version später
Weder Jar-in-Jar noch ein Versionsbereich wie `26.*` im Code ersetzen das: Eine neue Version braucht ein weiteres Projekt unter `versions/` (oder die vorhandene Hälfte läuft weiter, wenn sich nichts ändert; dann nur die `depends`-Angabe im Zusammenbau erweitern) und, falls sich die Namen ändern, weitere Direktiven.
