#!/usr/bin/env bash
# Shared environment for every Kratkoza build script.
#
# Sourced, never executed. Sets the JDK/SDK/Gradle that live in /mnt/workspace/.tooling and pins a
# Gradle user home for this project.

TOOLING_ROOT="/mnt/workspace/.tooling"
export JAVA_HOME="$TOOLING_ROOT/jdk"
export ANDROID_HOME="$TOOLING_ROOT/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# Worked out from where this file sits, not written down. Two people cannot work on two branches in
# one directory, so the second checkout is a git worktree somewhere else — and a script pinned to an
# absolute path would quietly build the *other* checkout's code from inside it.
export KRATKOZA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Logs are named after the checkout, for the same reason: two builds running at once would otherwise
# overwrite each other's log, and the failure you are reading would belong to somebody else.
export KRATKOZA_LOG_PREFIX="/tmp/kratkoza-$(basename "$KRATKOZA_DIR")"

# Gradle's native services and file locking misbehave on the Windows-mounted 9p filesystem, and the
# mount is slow enough that a cache living there dominates build time. Both go on the Linux disk.
#
# Deliberately shared between checkouts: Gradle locks it, so two builds at once take turns rather
# than corrupt anything, and sharing saves re-downloading every dependency per worktree. Set
# KRATKOZA_GRADLE_USER_HOME to split them if the waiting ever costs more than the download.
export GRADLE_USER_HOME="${KRATKOZA_GRADLE_USER_HOME:-/tmp/kratkoza-gradle-home}"
export GRADLE_TMP="/tmp/kratkoza-gradle-tmp"
export JAVA_OPTS="-Djava.io.tmpdir=$GRADLE_TMP ${JAVA_OPTS:-}"
export GRADLE_OPTS="-Dorg.gradle.cache.internal.locklistener=false -Djava.io.tmpdir=$GRADLE_TMP ${GRADLE_OPTS:-}"

mkdir -p "$GRADLE_USER_HOME" "$GRADLE_TMP"

export PATH="$TOOLING_ROOT/gradle/gradle-9.6.0/bin:$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"
