#!/bin/bash
# Mixin check of the merged jar on a real 26.x client (no GPU needed, it stops before the window opens).
#   tools/mixin-audit/run.sh 26.2                        versions/26.2/gradle.properties, jar build/libs/hugosmpenhanced-<version>.jar
#   tools/mixin-audit/run.sh 26.1.1 0.145.4+26.1.1 18.0.2   other patch versions: Minecraft, Fabric API, Mod Menu
# Needs JDK 25 (JAVA25_HOME, default /opt/jdk25) and xvfb-run. Prints HSE-FAIL per target class that does not take its mixins.
# BetterF3 and Sodium targets fail on purpose when those mods are not installed; the mod skips them then.
set -e
root="$(cd "$(dirname "$0")/../.." && pwd)"
mc="$1"
if [ -n "$2" ]; then
	fabric="$2"; modmenu="$3"
else
	props="$root/versions/$mc/gradle.properties"
	mc="$(grep '^minecraft_version=' "$props" | cut -d= -f2)"
	fabric="$(grep '^fabric_api_version=' "$props" | cut -d= -f2)"
	modmenu="$(grep '^modmenu_version=' "$props" | cut -d= -f2)"
fi
version="$(grep '^version=' "$root/gradle.properties" | cut -d= -f2 | tr -d '\r')"
jar="${MOD_JAR:-$root/build/libs/hugosmpenhanced-$version.jar}"
log="$(mktemp)"
set +e
xvfb-run -a -s "-screen 0 1280x720x24" bash "$root/gradlew" -p "$root/tools/mixin-audit" runClient \
	-Pmc="$mc" -Pfabric="$fabric" -Pmodmenu="$modmenu" -Pmodjar="$jar" \
	-Dorg.gradle.java.home="${JAVA25_HOME:-/opt/jdk25}" > "$log" 2>&1
grep -E "HSE-" "$log" | sed 's/^.*STDOUT\]: //' | cut -c1-400
rm -f "$log"
