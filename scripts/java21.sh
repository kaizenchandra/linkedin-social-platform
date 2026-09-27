#!/bin/sh
set -eu
if [ -x /usr/libexec/java_home ]; then export JAVA_HOME="$(/usr/libexec/java_home -v 21)"; fi
exec ./mvnw "$@"
