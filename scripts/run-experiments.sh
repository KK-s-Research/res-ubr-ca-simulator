#!/usr/bin/env sh
set -eu

MODE="${1:-full}"
OUTPUT="${2:-output}"
mvn clean package
java -Djava.awt.headless=true -jar target/ubr-ca-simulator-1.0.0.jar \
  "--${MODE}" --output "${OUTPUT}"
