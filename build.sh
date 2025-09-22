podman run --rm \
  -v "$(pwd)":/app \
  -w /app \
  maven:3.9-eclipse-temurin-8 mvn package -DskipTests

sudo chown -R "$(id -u):$(id -g)" target

cp target/HttpCommunicationMod.jar ~/Library/Application\ Support/Steam/steamapps/common/SlayTheSpire/SlayTheSpire.app/Contents/Resources/mods/
