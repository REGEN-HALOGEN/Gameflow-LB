#!/bin/bash
# Runs Maven through a local forwarding proxy (127.0.0.1:8888) which adds
# a FRESH Proxy-Authorization per connection. Works around rotating
# egress-proxy credentials that break Maven's own proxy handling.
set -u

# 1. Refresh the auth file from the live env (fresh credentials).
PROXY_URL="${https_proxy:-${HTTPS_PROXY:-}}"
if [ -z "$PROXY_URL" ]; then echo "no https_proxy env" >&2; exit 1; fi
CREDS="$(echo "$PROXY_URL" | sed -E 's#http://([^@]+)@.*#\1#')"
printf 'Basic %s' "$(printf '%s' "$CREDS" | base64 -w0)" > /tmp/proxyauth.txt

# 2. Start the local proxy if needed.
if ! (exec 3<>/dev/tcp/127.0.0.1/8888) 2>/dev/null; then
  nohup python3 ~/workspace/gameflow-lb/backend/mvnproxy.py >/tmp/mvnproxy.out 2>&1 &
  for i in $(seq 1 20); do
    (exec 3<>/dev/tcp/127.0.0.1/8888) 2>/dev/null && break
    sleep 0.5
  done
fi

# 3. Point Maven at the local proxy (no auth needed there).
for HOME_CANDIDATE in /root /home/hatch; do
  mkdir -p "$HOME_CANDIDATE/.m2"
  cat > "$HOME_CANDIDATE/.m2/settings.xml" <<'EOF'
<settings>
  <proxies>
    <proxy>
      <id>local-egress</id><active>true</active>
      <protocol>http</protocol>
      <host>127.0.0.1</host><port>8888</port>
      <nonProxyHosts>localhost|127.0.0.1</nonProxyHosts>
    </proxy>
    <proxy>
      <id>local-egress-https</id><active>true</active>
      <protocol>https</protocol>
      <host>127.0.0.1</host><port>8888</port>
      <nonProxyHosts>localhost|127.0.0.1</nonProxyHosts>
    </proxy>
  </proxies>
</settings>
EOF
done

echo "local proxy ready; running mvn $*"
exec mvn "$@"
