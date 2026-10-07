#!/bin/sh
set -eu

case "${FIREBASE_PROJECT_ID:-demo-mobile-foundry}" in
    demo-*) ;;
    *) echo 'The local stack requires a demo- project ID.' >&2; exit 1 ;;
esac

set -- emulators:start --non-interactive \
    --project "${FIREBASE_PROJECT_ID:-demo-mobile-foundry}" \
    --only auth,firestore,storage --export-on-exit=/data/export

if [ -f /data/export/firebase-export-metadata.json ]; then
    set -- "$@" --import=/data/export
fi

exec firebase "$@"
