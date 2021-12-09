#!/usr/bin/env bash
SCRIPT_HOME="$(cd "$(dirname "$0")"; pwd)"
cd "${SCRIPT_HOME}"

if [[ "$#" -ne 1 ]]; then
  echo "No arguments provided, serving from current directory"
  base_server_dir="$(pwd)"
else
  base_server_dir="$1"
  echo "Serving from ${base_server_dir}"
fi

cd "$SCRIPT_HOME"/dist

set -x
ARG_LINE="-Dconfig.file=application.conf -Dlogback.configurationFile=logback.xml -Dcom.nsantos.httpfileserver.base-path=${base_server_dir} "
java $ARG_LINE -jar simple-web-server-1.0-SNAPSHOT-jar-with-dependencies.jar
