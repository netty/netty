#!/bin/bash -e
# ----------------------------------------------------------------------------
# Copyright 2021 The Netty Project
#
# The Netty Project licenses this file to you under the Apache License,
# version 2.0 (the "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at:
#
#   https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------
EXAMPLE_MAP=(
  'discard-client:io.netty5.example.discard.DiscardClient'
  'discard-server:io.netty5.example.discard.DiscardServer'
  'echo-client:io.netty5.example.echo.EchoClient'
  'echo-server:io.netty5.example.echo.EchoServer'
  'factorial-client:io.netty5.example.factorial.FactorialClient'
  'factorial-server:io.netty5.example.factorial.FactorialServer'
  'file-server:io.netty5.example.file.FileServer'
  'http-cors-server:io.netty5.example.http.cors.HttpCorsServer'
  'http-file-server:io.netty5.example.http.file.HttpStaticFileServer'
  'http-helloworld-server:io.netty5.example.http.helloworld.HttpHelloWorldServer'
  'http-snoop-client:io.netty5.example.http.snoop.HttpSnoopClient'
  'http-snoop-server:io.netty5.example.http.snoop.HttpSnoopServer'
  'http-upload-client:io.netty5.example.http.upload.HttpUploadClient'
  'http-upload-server:io.netty5.example.http.upload.HttpUploadServer'
  'websocket-client:io.netty5.example.http.websocketx.client.WebSocketClient'
  'websocket-server:io.netty5.example.http.websocketx.server.WebSocketServer'
  'http2-client:io.netty5.example.http2.helloworld.client.Http2Client'
  'http2-server:io.netty5.example.http2.helloworld.server.Http2Server'
  'http2-tiles:io.netty5.example.http2.tiles.Launcher'
  'http2-multiplex-server:io.netty5.example.http2.helloworld.multiplex.server.Http2Server'
  'worldclock-client:io.netty5.example.worldclock.WorldClockClient'
  'worldclock-server:io.netty5.example.worldclock.WorldClockServer'
  'objectecho-client:io.netty5.example.objectecho.ObjectEchoClient'
  'objectecho-server:io.netty5.example.objectecho.ObjectEchoServer'
  'quote-client:io.netty5.example.qotm.QuoteOfTheMomentClient'
  'quote-server:io.netty5.example.qotm.QuoteOfTheMomentServer'
  'securechat-client:io.netty5.example.securechat.SecureChatClient'
  'securechat-server:io.netty5.example.securechat.SecureChatServer'
  'telnet-client:io.netty5.example.telnet.TelnetClient'
  'telnet-server:io.netty5.example.telnet.TelnetServer'
  'proxy-server:io.netty5.example.proxy.HexDumpProxy'
  'uptime-client:io.netty5.example.uptime.UptimeClient'
  'uptime-server:io.netty5.example.uptime.UptimeServer'
  'localecho:io.netty5.example.localecho.LocalEcho'
  'udp-dns-client:io.netty5.example.dns.udp.DnsClient'
  'tcp-dns-client:io.netty5.example.dns.tcp.TcpDnsClient'
  'dot-dns-client:io.netty5.example.dns.dot.DoTClient'
)

EXAMPLE=''
EXAMPLE_CLASS=''
EXAMPLE_ARGS='-D_'
FORCE_NPN=''
I=0

while [[ $# -gt 0 ]]; do
  ARG="$1"
  shift
  if [[ "$ARG" =~ (^-.+) ]]; then
    EXAMPLE_ARGS="$EXAMPLE_ARGS $ARG"
  else
    EXAMPLE="$ARG"
    for E in "${EXAMPLE_MAP[@]}"; do
      KEY="${E%%:*}"
      VAL="${E##*:}"
      if [[ "$EXAMPLE" == "$KEY" ]]; then
        EXAMPLE_CLASS="$VAL"
        break
      fi
    done
    break
  fi
done

if [[ -z "$EXAMPLE" ]] || [[ -z "$EXAMPLE_CLASS" ]] || [[ $# -ne 0 ]]; then
  echo "  Usage: $0 [-D<name>[=<value>] ...] <example-name>" >&2
  echo "Example: $0 -Dport=8443 -Dssl http-server" >&2
  echo "         $0 -Dhost=127.0.0.1 -Dport=8009 echo-client" >&2
  echo "         $0 -DlogLevel=debug -Dhost=127.0.0.1 -Dport=8009 echo-client" >&2
  echo >&2
  echo "Available examples:" >&2
  echo >&2
  I=0
  for E in "${EXAMPLE_MAP[@]}"; do
    if [[ $I -eq 0 ]]; then
      echo -n '  '
    fi

    printf '%-24s' "${E%%:*}"
    ((I++)) || true

    if [[ $I -eq 2 ]]; then
      I=0
      echo
    fi
  done >&2
  if [[ $I -ne 0 ]]; then
    echo >&2
  fi
  echo >&2
  exit 1
fi

cd "`dirname "$0"`"/example
echo "[INFO] Running: $EXAMPLE ($EXAMPLE_CLASS $EXAMPLE_ARGS)"
exec mvn -q -nsu compile exec:exec -Dcheckstyle.skip=true -Dforbiddenapis.skip=true -Dforcenpn="$FORCE_NPN" -DargLine.example="$EXAMPLE_ARGS" -DexampleClass="$EXAMPLE_CLASS"
