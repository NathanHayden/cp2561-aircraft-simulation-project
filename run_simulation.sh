#!/usr/bin/env bash
# Build and run the aircraft simulation on Linux or macOS.
# Pass any program args after the script name, e.g. ./run_simulation.sh --inject-failures
set -e
cd "$(dirname "$0")/src"
javac *.java
java -Dswing.aatext=true -Dsun.java2d.opengl=true Main "$@"
