#!/usr/bin/env bash
# Fingerprint the local release keystore to check if it matches Play Console's
# expected upload key. Password is read silently — not stored in shell history.
#
# Usage:
#   bash scripts/fingerprint-release-keystore.sh [path/to/keystore]
#
# Default keystore path: release-keystore.jks (repo root)
set -euo pipefail

KEYSTORE="${1:-release-keystore.jks}"

if [ ! -f "$KEYSTORE" ]; then
  echo "Keystore not found: $KEYSTORE" >&2
  exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
    KEYTOOL="$JAVA_HOME/bin/keytool"
  elif [ -x "/c/Program Files/Android/Android Studio/jbr/bin/keytool" ]; then
    KEYTOOL="/c/Program Files/Android/Android Studio/jbr/bin/keytool"
  else
    echo "keytool not found on PATH. Export JAVA_HOME first." >&2
    exit 1
  fi
else
  KEYTOOL="keytool"
fi

read -r -s -p "Keystore password: " KS_PW
echo

if ! "$KEYTOOL" -list -keystore "$KEYSTORE" -storepass "$KS_PW" >/dev/null 2>&1; then
  echo "Password rejected by keytool." >&2
  exit 2
fi

echo
echo "=== $KEYSTORE ==="
"$KEYTOOL" -list -v -keystore "$KEYSTORE" -storepass "$KS_PW" 2>/dev/null \
  | grep -E "^Alias name:|^Owner:|^Issuer:|^Valid from:|SHA1:|SHA256:"
echo
echo "Play Console expects SHA1:"
echo "  05:6F:86:A3:39:41:1D:74:C8:59:84:46:95:F3:26:66:79:BD:B9:81"
echo
echo "If one of the SHA1 lines above matches, this IS your upload key."
