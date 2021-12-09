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

mvn compile

ARG_LINE="-Dcom.nsantos.httpfileserver.base-path=${base_server_dir}"
mvn exec:java "${ARG_LINE}"