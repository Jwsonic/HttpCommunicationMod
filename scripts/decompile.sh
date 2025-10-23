#!/bin/bash

podman run --rm \
  -v "$(pwd)":/app \
  -w /app \
  maven:3.9-eclipse-temurin-8 \
  bash -c "export MAVEN_OPTS='-Xmx2g' && mvn exec:java -B \
    -Dexec.mainClass='org.benf.cfr.reader.Main' \
    -Dexec.classpathScope='test' \
    -Dexec.args='lib/desktop-1.0.jar --outputdir decompiled'"

sudo chown -R "$(id -u):$(id -g)" decompiled 2>/dev/null
