#!/usr/bin/env bash
# Creates the permanent signing key once and prints the four values to store as GitHub
# repository secrets (Settings > Secrets and variables > Actions). Every build signed with
# this key can update the previous one without uninstalling. Keep the .jks file and the
# passwords somewhere safe: a lost key means one more uninstall for every user.
set -euo pipefail
out="${1:-naqaa-release.jks}"
alias_name="naqaa"
password="$(python3 -c 'import secrets; print(secrets.token_urlsafe(24))')"
keytool -genkeypair -keystore "$out" -alias "$alias_name" -keyalg RSA -keysize 4096 \
  -validity 10000 -dname "CN=yswef, O=Naqaa" -storepass "$password" -keypass "$password"
echo
echo "NAQAA_STORE_BASE64  = $(base64 -w0 "$out")"
echo "NAQAA_STORE_PASSWORD= $password"
echo "NAQAA_KEY_ALIAS     = $alias_name"
echo "NAQAA_KEY_PASSWORD  = $password"
echo
echo "With the GitHub CLI:  gh secret set NAQAA_STORE_BASE64 < <(base64 -w0 $out)  (and the other three)"
