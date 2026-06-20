#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v java >/dev/null 2>&1; then
  echo "Java is required. Install JDK 17+ and add it to PATH."
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven is required. Install Maven 3.9+ and add it to PATH."
  exit 1
fi

echo "Java version:"
java -version

echo "Maven version:"
mvn -version

echo "Building Java server..."
cd "$ROOT_DIR"
mvn -q clean package

echo "Build successful."
echo "Run with: java -jar $ROOT_DIR/target/restprovider-java-server-1.0.0.jar"
