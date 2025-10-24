#!/bin/bash

# Create named volume on first run (idempotent - won't error if exists)
podman volume create maven-repo 2>/dev/null || true

podman run --rm \
  -v "$(pwd)":/app \
  -v maven-repo:/root/.m2 \
  -w /app \
  maven:3.9-eclipse-temurin-8 mvn package -DskipTests -B -q

echo "Build complete!"
