
mvn package -DskipTests || exit

cp target/CommunicationMod.jar ~/Library/Application\ Support/Steam/steamapps/common/SlayTheSpire/SlayTheSpire.app/Contents/Resources/mods/
