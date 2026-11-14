#!/bin/sh
# Creates a self-signed certificate for the host in FREEZER_URL, unless one is mounted at /etc/nginx/certs.
# Browsers warn once about it; HTTPS is still needed because login uses the Web Crypto API.
set -eu
dir=/etc/nginx/certs
host=$(echo "$FREEZER_URL" | sed -E 's#^[a-z]+://([^:/]+).*#\1#')
marker="$dir/generated-for"

if [ -f "$dir/tls.crt" ] && [ ! -f "$marker" ]; then
    echo "certificate: using the mounted one"
    exit 0
fi
if [ -f "$marker" ] && [ "$(cat "$marker")" = "$host" ]; then
    exit 0
fi

case "$host" in
    *[!0-9.]*) san="DNS:$host" ;;
    *) san="IP:$host" ;;
esac
mkdir -p "$dir"
openssl req -x509 -newkey rsa:2048 -nodes -days 825 -subj "/CN=$host" \
    -addext "subjectAltName=$san,DNS:localhost,IP:127.0.0.1" \
    -keyout "$dir/tls.key" -out "$dir/tls.crt" 2>/dev/null
echo "$host" > "$marker"
echo "certificate: self-signed for $host"
