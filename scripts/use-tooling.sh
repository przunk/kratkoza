#!/usr/bin/env bash
#
# Environment for every build. Source it, do not execute it:
#
#     source scripts/use-tooling.sh
#
# Everything comes from the shared /mnt/workspace/.tooling install. Nothing here writes to that
# directory, and every mutable path (Gradle home, temp, logs) is namespaced by APP_SLUG, so a build
# here cannot disturb another project in the workspace.

# Worked out from where this file sits rather than written down. A second checkout is a git worktree
# elsewhere, and a script pinned to an absolute path would quietly build the *other* checkout.
export PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=./project.env
source "$PROJECT_DIR/scripts/project.env"

TOOLING_ROOT="${TOOLING_ROOT:-/mnt/workspace/.tooling}"

# Named after the checkout as well: two builds at once would otherwise overwrite each other's log,
# and the failure you are reading would belong to somebody else.
export LOG_PREFIX="/tmp/$APP_SLUG-$(basename "$PROJECT_DIR")"

export JAVA_HOME="${PROJECT_JAVA_HOME:-$TOOLING_ROOT/jdk}"
export ANDROID_HOME="$TOOLING_ROOT/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# Where Gradle puts the module's outputs; see MODULE_BUILD_DIR_RELATIVE in project.env.
export MODULE_BUILD_DIR="$PROJECT_DIR/$MODULE_BUILD_DIR_RELATIVE"

# Gradle's native services and file locking misbehave on the 9p mount behind /mnt/workspace, and a
# cache living there dominates build time. Both go on the Linux disk. Shared between checkouts on
# purpose: Gradle locks it, so two builds take turns rather than corrupt anything.
export GRADLE_USER_HOME="${PROJECT_GRADLE_USER_HOME:-/tmp/$APP_SLUG-gradle-home}"
export JAVA_OPTS="-Djava.io.tmpdir=/tmp/$APP_SLUG-gradle-tmp ${JAVA_OPTS:-}"
export GRADLE_OPTS="-Dorg.gradle.cache.internal.locklistener=false -Djava.io.tmpdir=/tmp/$APP_SLUG-gradle-tmp ${GRADLE_OPTS:-}"

mkdir -p "$GRADLE_USER_HOME" "/tmp/$APP_SLUG-gradle-tmp"

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"
true
