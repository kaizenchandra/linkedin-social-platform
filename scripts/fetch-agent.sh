#!/bin/sh
set -eu
mkdir -p .local/tools
curl -fsSL https://repo.maven.apache.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/2.31.1/opentelemetry-javaagent-2.31.1.jar -o .local/tools/opentelemetry-javaagent.jar
printf '%s  %s\n' 'bbf83c151b6400709e2f225bdd07a04f839d9d13b8b93464241333fd25d3e3ba' '.local/tools/opentelemetry-javaagent.jar' | shasum -a 256 -c -
