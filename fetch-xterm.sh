#!/bin/sh
# Downloads the xterm.js files the terminal page needs into the app's assets.
# Run once from the project root. (Needs internet; afterwards the app is fully offline.)
set -e
A=app/src/main/assets
curl -fL -o $A/xterm.js       https://cdn.jsdelivr.net/npm/@xterm/xterm@5.5.0/lib/xterm.js
curl -fL -o $A/xterm.css      https://cdn.jsdelivr.net/npm/@xterm/xterm@5.5.0/css/xterm.css
curl -fL -o $A/addon-fit.js   https://cdn.jsdelivr.net/npm/@xterm/addon-fit@0.10.0/lib/addon-fit.js
echo "xterm.js files downloaded to $A"
