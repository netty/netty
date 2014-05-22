#!/bin/bash -e
EXAMPLE_MAP=(
  'discard-client:org.jboss.netty.example.discard.DiscardClient'
  'discard-server:org.jboss.netty.example.discard.DiscardServer'
  'echo-client:org.jboss.netty.example.echo.EchoClient'
  'echo-server:org.jboss.netty.example.echo.EchoServer'
  'factorial-client:org.jboss.netty.example.factorial.FactorialClient'
  'factorial-server:org.jboss.netty.example.factorial.FactorialServer'
  'http-snoop-client:org.jboss.netty.example.http.snoop.HttpSnoopClient'
  'http-snoop-server:org.jboss.netty.example.http.snoop.HttpSnoopServer'
  'http-file-server:org.jboss.netty.example.http.file.HttpStaticFileServer'
  'http-helloworld-server:org.jboss.netty.example.http.helloworld.HttpHelloWorldServer'
  'http-upload-client:org.jboss.netty.example.http.upload.HttpUploadClient'
  'http-upload-server:org.jboss.netty.example.http.upload.HttpUploadServer'
  'websocket-client:org.jboss.netty.example.websocketx.client.WebSocketClient'
  'websocket-server:org.jboss.netty.example.websocketx.server.WebSocketServer'
  'localtime-client:org.jboss.netty.example.localtime.LocalTimeClient'
  'localtime-server:org.jboss.netty.example.localtime.LocalTimeServer'
  'objectecho-client:org.jboss.netty.example.objectecho.ObjectEchoClient'
  'objectecho-server:org.jboss.netty.example.objectecho.ObjectEchoServer'
  'quote-client:org.jboss.netty.example.qotm.QuoteOfTheMomentClient'
  'quote-server:org.jboss.netty.example.qotm.QuoteOfTheMomentServer'
  'securechat-client:org.jboss.netty.example.securechat.SecureChatClient'
  'securechat-server:org.jboss.netty.example.securechat.SecureChatServer'
  'telnet-client:org.jboss.netty.example.telnet.TelnetClient'
  'telnet-server:org.jboss.netty.example.telnet.TelnetServer'
  'proxy-server:org.jboss.netty.example.proxy.HexDumpProxy'
  'uptime-client:org.jboss.netty.example.uptime.UptimeClient'
  'local-transport:org.jboss.netty.example.local.LocalExample'
)

EXAMPLE=''
EXAMPLE_CLASS=''
EXAMPLE_ARGS='-D_'
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

cd "`dirname "$0"`"
echo "[INFO] Running: $EXAMPLE ($EXAMPLE_CLASS $EXAMPLE_ARGS)"
exec mvn -nsu compile antrun:run -Dcheckstyle.skip=true -Dantfile=src/antrun/run-example.xml -DargLine.example="$EXAMPLE_ARGS" -DexampleClass="$EXAMPLE_CLASS"

