#!/bin/bash

# Build the mod
./scripts/build.sh

# Fix ownership of build artifacts
sudo chown -R "$(id -u):$(id -g)" target

# Install to Slay the Spire mods directory
cp target/HttpCommunicationMod.jar ~/Library/Application\ Support/Steam/steamapps/common/SlayTheSpire/SlayTheSpire.app/Contents/Resources/mods/

echo "Installation complete!"
