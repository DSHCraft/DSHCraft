#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
exec java -jar hmcl-ui/HMCL/build/libs/DSHCraft-1.3.0-SNAPSHOT.jar
