#!/bin/sh
set -eu
cd "$(dirname "$0")"

if [ -f .env ]; then
    echo 'Existing .env retained. Setup does not rotate credentials.'
    exit 0
fi

command -v openssl >/dev/null
command -v node >/dev/null
umask 077
profile_dir=$(pwd)
setup_dir=$(mktemp -d "${TMPDIR:-/tmp}/mobile-foundry-supabase.XXXXXX")
trap 'rm -rf "$setup_dir"' 0
trap 'exit 1' HUP INT TERM
cp .env.example "$setup_dir/.env"
cp docker-compose.yml "$setup_dir/docker-compose.yml"
# Upstream generators print secrets; keep those out of setup logs.
(
    cd "$setup_dir"
    sh "$profile_dir/utils/generate-keys.sh" --update-env >/dev/null
    sh "$profile_dir/utils/add-new-auth-keys.sh" --update-env >/dev/null
)
mv "$setup_dir/.env" .env
echo 'Created local .env with generated credentials. Keep it private.'
