# Release-Notizen

Ändert sich `version` in `gradle.properties` auf `main`, baut `.github/workflows/release.yml`
die Mod und legt einen **Entwurf** für das GitHub-Release `v<version>` mit der Jar an. Live geht es erst,
wenn Levi den Entwurf unter Releases prüft und auf „Publish release“ klickt (nicht als Pre-release markieren).

Der Text des Releases ist das Changelog, das die Mod im Spiel zeigt. Er kommt aus
`release-notes/<version>.md`, wenn es die Datei gibt (Markdown, Deutsch). Sonst baut der
Workflow ihn aus den Commit-Titeln seit dem letzten Tag.

Weitere Pushes mit derselben Version aktualisieren den Entwurf (Jar und Text). Ist die Version schon
veröffentlicht (Tag existiert), passiert nichts mehr.
