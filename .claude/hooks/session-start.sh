#!/bin/bash
# Prepares a cloud session so ./gradlew test and ./gradlew build work.
#
# Two things stop them working out of the box:
#   1. The project targets Java 25; the container ships JDK 21.
#   2. Maven Central is rate-limited through the session proxy (HTTP 429). Both
#      repo.maven.apache.org and repo1.maven.org hit it, so a mirror alone is not a
#      cure - it is a second bucket. What gets through is retrying: each attempt
#      caches what it fetched, and the container image keeps that cache afterwards.
#
# Nothing here edits a tracked build file. The JDK choice and the mirror go into the
# Gradle user home, because a local workaround committed by accident changes how
# everyone else builds.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  echo "session-start: not a cloud session, nothing to do"
  exit 0
fi

PROJECT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
JDK_DIR="$HOME/.local/share/temurin-25"
JDK_URL="https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse"

# --- 1. Java 25 toolchain -----------------------------------------------------
if [ -x "$JDK_DIR/bin/javac" ]; then
  echo "session-start: JDK 25 already present at $JDK_DIR"
else
  echo "session-start: fetching a JDK 25 (container has $(java -version 2>&1 | grep -v JAVA_TOOL_OPTIONS | head -1))"
  tarball="$(mktemp -t jdk25-XXXXXX.tar.gz)"
  fetched=no
  for attempt in 1 2 3; do
    if curl -fsSL --max-time 600 -o "$tarball" "$JDK_URL"; then fetched=yes; break; fi
    echo "session-start: JDK download attempt $attempt failed, retrying"
    sleep $((attempt * 5))
  done
  if [ "$fetched" = yes ]; then
    mkdir -p "$JDK_DIR"
    tar xzf "$tarball" -C "$JDK_DIR" --strip-components=1
    echo "session-start: installed $("$JDK_DIR/bin/java" -version 2>&1 | grep -v JAVA_TOOL_OPTIONS | head -1)"
  else
    echo "session-start: WARNING could not download a JDK 25; gradle will fail on 'release version 25 not supported'"
  fi
  rm -f "$tarball"
fi

# --- 2. Point Gradle at it, from outside the repository -----------------------
mkdir -p "$HOME/.gradle"
if [ -x "$JDK_DIR/bin/javac" ]; then
  touch "$HOME/.gradle/gradle.properties"
  if grep -q '^org.gradle.java.home=' "$HOME/.gradle/gradle.properties"; then
    sed -i "s|^org.gradle.java.home=.*|org.gradle.java.home=$JDK_DIR|" "$HOME/.gradle/gradle.properties"
  else
    echo "org.gradle.java.home=$JDK_DIR" >> "$HOME/.gradle/gradle.properties"
  fi
  if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
    echo "export JAVA_HOME=\"$JDK_DIR\"" >> "$CLAUDE_ENV_FILE"
    echo "export PATH=\"$JDK_DIR/bin:\$PATH\"" >> "$CLAUDE_ENV_FILE"
  fi
fi

# --- 3. Central mirror as a Gradle init script, never a tracked file ----------
mkdir -p "$HOME/.gradle/init.d"
cat > "$HOME/.gradle/init.d/00-central-mirror.gradle" <<'GROOVY'
// A 429 aborts resolution rather than falling through to the next repository, so the
// mirror has to be tried FIRST, not merely be present.
def MIRROR = 'https://repo1.maven.org/maven2/'

static void prependMirror(repos, String url) {
    if (repos.findByName('centralMirror') != null) return
    def mirror = repos.maven { it.name = 'centralMirror'; it.url = url }
    repos.remove(mirror)
    repos.add(0, mirror)
}

beforeSettings { settings -> prependMirror(settings.pluginManagement.repositories, MIRROR) }
projectsLoaded { rootProject.allprojects { prependMirror(it.repositories, MIRROR) } }
GROOVY
echo "session-start: Central mirror installed in the Gradle user home"

# --- 4. Warm the dependency cache --------------------------------------------
# Rate limiting means the first attempts fail partway; each one caches what it got.
# This must never fail the session, so a warm-up that gives up still exits clean.
cd "$PROJECT"
warmed=no
for attempt in 1 2 3 4 5 6 7 8; do
  if ./gradlew --no-daemon testClasses >/tmp/gradle-warmup.log 2>&1; then
    warmed=yes
    echo "session-start: dependency cache warm after $attempt attempt(s)"
    break
  fi
  if grep -q "429" /tmp/gradle-warmup.log; then
    echo "session-start: attempt $attempt hit the Central rate limit, retrying"
  else
    echo "session-start: attempt $attempt failed for another reason:"
    tail -5 /tmp/gradle-warmup.log | sed 's/^/  /'
  fi
  sleep $((attempt * 10))
done
if [ "$warmed" != yes ]; then
  echo "session-start: WARNING cache not fully warm; ./gradlew may need a few retries"
fi
exit 0
