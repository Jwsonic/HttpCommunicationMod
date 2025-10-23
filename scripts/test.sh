#!/bin/bash

podman run --rm \
  -v "$(pwd)":/app \
  -w /app \
  maven:3.9-eclipse-temurin-8 mvn test -B

sudo chown -R "$(id -u):$(id -g)" target 2>/dev/null
