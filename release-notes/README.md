# Release-Notizen

Ändert sich `version` in `gradle.properties` auf `main`, baut `.github/workflows/release.yml`
die Mod und legt das GitHub-Release `v<version>` mit der Jar an (kein Pre-release).

Der Text des Releases ist das Changelog, das die Mod im Spiel zeigt. Er kommt aus
`release-notes/<version>.md`, wenn es die Datei gibt (Markdown, Deutsch). Sonst baut der
Workflow ihn aus den Commit-Titeln seit dem letzten Tag.

Eine Version, für die es schon einen Tag gibt, wird nicht noch einmal veröffentlicht.
