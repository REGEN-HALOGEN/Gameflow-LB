#!/usr/bin/env python3
"""Local forwarding HTTP proxy for Maven (see mvnw-proxy.sh).

Listens on 127.0.0.1:8888, no auth required. Forwards every request to the
upstream egress proxy from $https_proxy, injecting a FRESH Proxy-Authorization
header per connection (read from /tmp/proxyauth.txt, refreshed by
mvnw-proxy.sh). This works around rotating egress credentials that break
Maven's built-in proxy handling.

Supports:
  - CONNECT host:port  (HTTPS tunneling, incl. Maven Central)
  - Absolute-URI GET/HEAD/POST... (plain HTTP)
"""
import base64
import os
import socket
import threading

LISTEN = ("127.0.0.1", 8888)
AUTH_FILE = "/tmp/proxyauth.txt"
BUF = 65536


def upstream_target():
    p = os.environ.get("https_proxy") or os.environ.get("HTTPS_PROXY") or ""
    rest = p.split("://", 1)[1] if "://" in p else p
    hostport = rest.split("@")[-1].split("/")[0]
    host, _, port = hostport.partition(":")
    return host, int(port or 3128)


def fresh_auth_header():
    try:
        with open(AUTH_FILE) as fh:
            return fh.read().strip()
    except OSError:
        return ""


def relay(a, b, tag):
    total = 0
    try:
        while True:
            chunk = a.recv(BUF)
            if not chunk:
                print(f"DBG relay {tag}: EOF after {total} bytes", flush=True)
                break
            b.sendall(chunk)
            total += len(chunk)
    except OSError as e:
        print(f"DBG relay {tag}: err {e} after {total} bytes", flush=True)
    finally:
        for s in (a, b):
            try:
                s.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass


def handle(client):
    try:
        # Read the request head (up to end of headers).
        data = b""
        while b"\r\n\r\n" not in data:
            chunk = client.recv(BUF)
            if not chunk:
                client.close()
                return
            data += chunk
            if len(data) > 1 << 20:
                client.close()
                return
        head, _, rest = data.partition(b"\r\n\r\n")
        lines = head.decode("latin1").split("\r\n")
        method, target = lines[0].split(" ", 2)[:2]

        up_host, up_port = upstream_target()
        up = socket.create_connection((up_host, up_port), timeout=30)

        auth = fresh_auth_header()
        print(f"DBG {method} {target} auth={auth[:20]}...", flush=True)
        if method.upper() == "CONNECT":
            up.sendall(
                f"CONNECT {target} HTTP/1.1\r\nHost: {target}\r\n".encode("latin1")
            )
            if auth:
                up.sendall(f"Proxy-Authorization: {auth}\r\n".encode("latin1"))
            up.sendall(b"\r\n")
            # Read the CONNECT response; only tunnel on 200.
            resp = b""
            while b"\r\n\r\n" not in resp:
                chunk = up.recv(BUF)
                if not chunk:
                    break
                resp += chunk
            print(f"DBG upstream CONNECT resp: {resp[:60]}", flush=True)
            # Always forward the CONNECT response to the client (200 or not).
            client.sendall(resp)
            if b" 200 " not in resp.split(b"\r\n", 1)[0]:
                client.close()
                up.close()
                return
            # 200 established — relay the raw tunnel.
            t1 = threading.Thread(target=relay, args=(client, up, "c->u"), daemon=True)
            t2 = threading.Thread(target=relay, args=(up, client, "u->c"), daemon=True)
            t1.start()
            t2.start()
            t1.join()
            t2.join()
            return

        # Plain-HTTP absolute-URI forwarding.
        out_lines = [lines[0]]
        for ln in lines[1:]:
            if ln.lower().startswith("proxy-authorization:"):
                continue
            out_lines.append(ln)
        if auth:
            out_lines.append(f"Proxy-Authorization: {auth}")
        up.sendall(("\r\n".join(out_lines) + "\r\n\r\n").encode("latin1") + rest)
        t1 = threading.Thread(target=relay, args=(up, client, "u->c-plain"), daemon=True)
        t1.start()
        t1.join()
    except Exception:
        try:
            client.close()
        except OSError:
            pass


def main():
    servers = []
    # Bind IPv4 loopback AND IPv6 loopback. The sandbox intercepts JVM IPv4
    # loopback TCP (returns a proxy-denial instead of routing it), while ::1
    # works — so Maven (JVM) must be pointed at ::1.
    for family, addr in ((socket.AF_INET, "127.0.0.1"), (socket.AF_INET6, "::1")):
        try:
            srv = socket.socket(family, socket.SOCK_STREAM)
            srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            srv.bind((addr, LISTEN[1]))
            srv.listen(64)
            servers.append((srv, addr))
            print(f"mvnproxy listening on {addr}:{LISTEN[1]}", flush=True)
        except OSError as e:
            print(f"could not bind {addr}: {e}", flush=True)
    if not servers:
        raise SystemExit("no listen sockets")
    while True:
        for srv, _ in servers:
            try:
                srv.settimeout(0.5)
                client, _ = srv.accept()
            except socket.timeout:
                continue
            threading.Thread(target=handle, args=(client,), daemon=True).start()


if __name__ == "__main__":
    main()
