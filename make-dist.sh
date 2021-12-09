#!/usr/bin/env bash
SCRIPT_HOME="$(cd "$(dirname "$0")"; pwd)"
cd "${SCRIPT_HOME}"

mvn package -Dmaven.test.skip=true

echo "Copying assembly ./target/simple-web-server-1.0-SNAPSHOT-jar-with-dependencies.jar to ./dist directory"
cp -f "${SCRIPT_HOME}"/target/simple-web-server-1.0-SNAPSHOT-jar-with-dependencies.jar "${SCRIPT_HOME}"/dist
