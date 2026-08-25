#!/usr/bin/env bash
# Shared environment for every Kratkoza build script.
#
# Sourced, never executed. Sets the JDK/SDK/Gradle that live in /mnt/workspace/.tooling and pins a
# Gradle user home for this project.

TOOLING_ROOT="/mnt/workspace/.tooling"
export JAVA_HOME="$TOOLING_ROOT/jdk"
export ANDROID_HOME="$TOOLING_ROOT/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

export KRATKOZA_DIR="/mnt/workspace/Kratkoza"

# Gradle's native services and file locking misbehave on the Windows-mounted 9p filesystem, and the
# mount is slow enough that a cache living there dominates build time. Both go on the Linux disk.
export GRADLE_USER_HOME="${KRATKOZA_GRADLE_USER_HOME:-/tmp/kratkoza-gradle-home}"
export GRADLE_TMP="/tmp/kratkoza-gradle-tmp"
export JAVA_OPTS="-Djava.io.tmpdir=$GRADLE_TMP ${JAVA_OPTS:-}"
export GRADLE_OPTS="-Dorg.gradle.cache.internal.locklistener=false -Djava.io.tmpdir=$GRADLE_TMP ${GRADLE_OPTS:-}"

mkdir -p "$GRADLE_USER_HOME" "$GRADLE_TMP"

export PATH="$TOOLING_ROOT/gradle/gradle-9.6.0/bin:$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"
