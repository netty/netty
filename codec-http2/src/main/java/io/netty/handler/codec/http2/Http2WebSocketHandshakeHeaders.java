/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.http.HttpHeaderValidationUtil;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtension;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName;
import io.netty.util.AsciiString;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpHeaderNames.ORIGIN;
import static io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_ACCEPT;
import static io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS;
import static io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_KEY;
import static io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL;
import static io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_VERSION;
import static io.netty.handler.codec.http.HttpHeaderValues.WEBSOCKET;
import static io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker.MAX_WINDOW_SIZE;
import static io.netty.util.AsciiString.contentEquals;
import static io.netty.util.internal.StringUtil.trimOws;

final class Http2WebSocketHandshakeHeaders {
    private static final AsciiString PROTOCOL = PseudoHeaderName.PROTOCOL.value();
    private static final String VERSION = WebSocketVersion.V13.toHttpHeaderValue();
    private static final String DEFLATE = "permessage-deflate";
    private static final String SERVER_WINDOW = "server_max_window_bits";
    private static final String CLIENT_WINDOW = "client_max_window_bits";

    private Http2WebSocketHandshakeHeaders() { }

    static PerMessageDeflateClientExtensionHandshaker clientCompression(int maxAllocation) {
        // Offering client_max_window_bits lets the peer choose 8, which the local encoder cannot use.
        return new PerMessageDeflateClientExtensionHandshaker(6, false, MAX_WINDOW_SIZE, true, false, maxAllocation);
    }

    static PerMessageDeflateServerExtensionHandshaker serverCompression(int maxAllocation) {
        return new PerMessageDeflateServerExtensionHandshaker(6, ZlibCodecFactory.isSupportingWindowSizeAndMemLevel(),
                MAX_WINDOW_SIZE, true, false, maxAllocation);
    }

    static boolean isWebSocketRequest(Http2Headers headers) {
        return HttpMethod.CONNECT.asciiName().contentEquals(headers.method()) &&
                WEBSOCKET.contentEquals(headers.get(PROTOCOL));
    }

    static Http2Headers newRequest(Http2Headers input, PerMessageDeflateClientExtensionHandshaker compression) {
        return newRequest(input, compression, false);
    }

    static Http2Headers newRequest(Http2Headers input, PerMessageDeflateClientExtensionHandshaker compression,
                                   boolean advertiseClientNoContext) {
        checkGenerated(input, PseudoHeaderName.METHOD.value(), HttpMethod.CONNECT.asciiName());
        checkGenerated(input, PROTOCOL, WEBSOCKET);
        checkGenerated(input, SEC_WEBSOCKET_VERSION, VERSION);
        checkApplicationHeaders(input);
        Http2Headers headers = copyOf(input)
                .method(HttpMethod.CONNECT.asciiName()).set(PROTOCOL, WEBSOCKET).set(SEC_WEBSOCKET_VERSION, VERSION);
        validateRequest(headers);
        if (compression != null) {
            WebSocketExtensionData offer = compression.newRequestData();
            if (offer.parameters().containsKey(CLIENT_WINDOW)) {
                throw new WebSocketHandshakeException(
                        "HTTP/2 permessage-deflate does not offer client_max_window_bits");
            }
            if (!advertiseClientNoContext) {
                // The default supports a server's client_no_context_takeover request without offering it.
                Map<String, String> parameters = new HashMap<String, String>(offer.parameters());
                parameters.remove("client_no_context_takeover");
                offer = new WebSocketExtensionData(offer.name(), parameters);
            }
            headers.set(SEC_WEBSOCKET_EXTENSIONS, extensionHeader(offer));
        }
        return headers;
    }

    static Http2Headers copyOf(Http2Headers headers) {
        try {
            return new DefaultHttp2Headers().set(headers);
        } catch (Exception cause) {
            // DefaultHttp2Headers may reject names or values from an unvalidated headers implementation.
            throw new WebSocketHandshakeException("invalid HTTP/2 handshake headers", cause);
        }
    }

    static List<WebSocketExtensionData> validateRequest(Http2Headers headers) {
        validateFields(headers, true);
        if (!isWebSocketRequest(headers) || !contentEquals(VERSION, single(headers, SEC_WEBSOCKET_VERSION))) {
            throw new WebSocketHandshakeException("expected an extended CONNECT request for WebSocket version 13");
        }
        CharSequence scheme = headers.scheme();
        CharSequence authority = headers.authority();
        CharSequence path = headers.path();
        if (!(HttpScheme.HTTP.name().contentEqualsIgnoreCase(scheme) ||
                HttpScheme.HTTPS.name().contentEqualsIgnoreCase(scheme)) ||
                authority == null || authority.length() == 0 || path == null || path.length() == 0 ||
                path.charAt(0) != '/') {
            throw new WebSocketHandshakeException("invalid WebSocket :scheme, :authority or :path");
        }
        try {
            URI uri = new URI(scheme + "://" + authority + path);
            String target = authority(uri, authority);
            if (uri.getRawFragment() != null) {
                throw new WebSocketHandshakeException("a WebSocket request target cannot contain a fragment");
            }
            CharSequence host = single(headers, HOST);
            if (host != null && !AsciiString.contentEqualsIgnoreCase(authority, host) &&
                    !target.equalsIgnoreCase(authority(new URI(scheme + "://" + host), host))) {
                throw new WebSocketHandshakeException("Host differs from :authority");
            }
        } catch (URISyntaxException e) {
            throw new WebSocketHandshakeException("invalid WebSocket request target", e);
        }
        single(headers, ORIGIN);
        subprotocols(headers);
        return deflateExtensions(headers, false);
    }

    private static String authority(URI uri, CharSequence field) {
        String raw = uri.getRawAuthority();
        if (!contentEquals(raw, field) || raw.indexOf('@') != -1) {
            throw new WebSocketHandshakeException("invalid WebSocket authority");
        }
        // URI also accepts registry-based authorities containing colons. HTTP permits a colon only
        // before a numeric port, apart from the colons inside a bracketed IP literal.
        int colon = raw.indexOf(':', raw.indexOf(']') + 1);
        if (colon < 0) {
            return raw;
        }
        if (colon == 0) {
            throw new WebSocketHandshakeException("empty WebSocket host");
        }
        int start = colon + 1;
        while (start + 1 < raw.length() && raw.charAt(start) == '0') {
            start++;
        }
        for (int i = start; i < raw.length(); ++i) {
            if (raw.charAt(i) < '0' || raw.charAt(i) > '9') {
                throw new WebSocketHandshakeException("invalid WebSocket port");
            }
        }
        String port = raw.substring(start);
        String defaultPort = HttpScheme.HTTPS.name().contentEqualsIgnoreCase(uri.getScheme()) ? "443" : "80";
        String host = raw.substring(0, colon);
        return port.isEmpty() || port.equals(defaultPort) ? host : host + ':' + port;
    }

    static WebSocketServerExtension negotiateServer(Http2Headers request, Http2Headers response,
                                                     PerMessageDeflateServerExtensionHandshaker compression,
                                                     List<WebSocketExtensionData> offers) {
        validateFields(response, false);
        checkGenerated(response, PseudoHeaderName.STATUS.value(), HttpResponseStatus.OK.codeAsText());
        checkApplicationHeaders(response);
        if (response.contains(CONTENT_LENGTH)) {
            throw new WebSocketHandshakeException("a successful CONNECT response cannot send content-length");
        }
        validateSelectedProtocol(request, response);
        response.status(HttpResponseStatus.OK.codeAsText());
        if (compression != null) {
            for (WebSocketExtensionData offer : offers) {
                WebSocketServerExtension extension = compression.handshakeExtension(offer);
                if (extension != null) {
                    WebSocketExtensionData selection = extension.newReponseData();
                    String value = extensionHeader(selection);
                    // A requested server window must be acknowledged, even when it is the default of 15.
                    if (offer.parameters().containsKey(SERVER_WINDOW) &&
                            !selection.parameters().containsKey(SERVER_WINDOW)) {
                        value += "; " + SERVER_WINDOW + "=15";
                    }
                    response.set(SEC_WEBSOCKET_EXTENSIONS, value);
                    return extension;
                }
            }
        }
        return null;
    }

    static WebSocketClientExtension validateResponse(Http2Headers request, Http2Headers response,
                                                      PerMessageDeflateClientExtensionHandshaker compression) {
        validateFields(response, false);
        CharSequence status = response.status();
        if (HttpStatusClass.valueOf(status) != HttpStatusClass.SUCCESS) {
            throw new WebSocketHandshakeException("expected a successful extended CONNECT response, got " + status);
        }
        validateSelectedProtocol(request, response);
        if (single(response, SEC_WEBSOCKET_EXTENSIONS) == null) {
            return null;
        }
        if (compression != null && !deflateExtensions(request, false).isEmpty()) {
            WebSocketExtensionData selection = deflateExtensions(response, true).get(0);
            WebSocketClientExtension extension = compression.handshakeExtensionWithClientNoContextSupport(selection);
            if (extension != null) {
                return extension;
            }
        }
        throw new WebSocketHandshakeException("unsupported WebSocket extension response");
    }

    private static void validateFields(Http2Headers headers, boolean request) {
        int seen = 0;
        for (Entry<CharSequence, CharSequence> field : headers) {
            CharSequence name = field.getKey();
            if (PseudoHeaderName.hasPseudoHeaderFormat(name)) {
                PseudoHeaderName pseudo = PseudoHeaderName.getPseudoHeader(name);
                if (pseudo == null || pseudo.isRequestOnly() != request || (seen & 1 << pseudo.ordinal()) != 0) {
                    throw new WebSocketHandshakeException("unexpected or duplicate pseudo-header: " + name);
                }
                seen |= 1 << pseudo.ordinal();
            } else if (HttpHeaderValidationUtil.isConnectionHeader(name, request) ||
                    HttpHeaderValidationUtil.isTeNotTrailers(name, field.getValue())) {
                throw new WebSocketHandshakeException("invalid HTTP/2 connection header: " + name);
            }
        }
    }

    private static void checkApplicationHeaders(Http2Headers headers) {
        if (headers.contains(SEC_WEBSOCKET_KEY) || headers.contains(SEC_WEBSOCKET_ACCEPT) ||
                headers.contains(SEC_WEBSOCKET_EXTENSIONS)) {
            throw new WebSocketHandshakeException("unexpected application-supplied WebSocket handshake field");
        }
    }

    private static void checkGenerated(Http2Headers headers, CharSequence name, CharSequence expected) {
        CharSequence value = single(headers, name);
        if (value != null && !contentEquals(value, expected)) {
            throw new WebSocketHandshakeException("unexpected value for " + name);
        }
    }

    private static CharSequence single(Http2Headers headers, CharSequence name) {
        Iterator<CharSequence> it = headers.valueIterator(name);
        if (!it.hasNext()) {
            return null;
        }
        CharSequence value = it.next();
        if (it.hasNext()) {
            throw new WebSocketHandshakeException("duplicate handshake header: " + name);
        }
        return value;
    }

    private static void validateSelectedProtocol(Http2Headers request, Http2Headers response) {
        CharSequence selected = single(response, SEC_WEBSOCKET_PROTOCOL);
        if (selected != null && !subprotocols(request).contains(selected.toString())) {
            throw new WebSocketHandshakeException("the selected WebSocket subprotocol was not offered");
        }
    }

    private static Set<String> subprotocols(Http2Headers request) {
        if (!request.contains(SEC_WEBSOCKET_PROTOCOL)) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<String>();
        for (CharSequence field : request.getAll(SEC_WEBSOCKET_PROTOCOL)) {
            for (String member : field.toString().split(",", -1)) {
                if (trimOws(member).length() == 0) {
                    continue;
                }
                String protocol = token(member);
                if (!result.add(protocol)) {
                    throw new WebSocketHandshakeException("duplicate WebSocket subprotocol");
                }
            }
        }
        if (result.isEmpty()) {
            throw new WebSocketHandshakeException("empty WebSocket subprotocol offer");
        }
        return result;
    }

    private static String token(String value) {
        String token = trimOws(value).toString();
        if (token.isEmpty() || HttpHeaderValidationUtil.validateToken(token) != -1) {
            throw new WebSocketHandshakeException("invalid token in WebSocket handshake");
        }
        return token;
    }

    private static String parameterValue(String raw) {
        String value = trimOws(raw).toString();
        if (value.isEmpty() || value.charAt(0) != '"') {
            return token(value);
        }
        // RFC 6455 section 9.1: even a quoted-string must decode to a single token.
        int end = value.length() - 1;
        if (end < 2 || value.charAt(end) != '"') {
            throw new WebSocketHandshakeException("invalid quoted WebSocket extension parameter");
        }
        StringBuilder unquoted = new StringBuilder(end - 1);
        for (int i = 1; i < end; i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                if (++i == end) {
                    throw new WebSocketHandshakeException("incomplete escape in WebSocket extension parameter");
                }
                c = value.charAt(i);
            }
            unquoted.append(c);
        }
        if (HttpHeaderValidationUtil.validateToken(unquoted) != -1) {
            throw new WebSocketHandshakeException("invalid quoted WebSocket extension token");
        }
        return unquoted.toString();
    }

    private static List<WebSocketExtensionData> deflateExtensions(Http2Headers headers, boolean response) {
        if (!headers.contains(SEC_WEBSOCKET_EXTENSIONS)) {
            return Collections.emptyList();
        }
        List<WebSocketExtensionData> deflates = new ArrayList<WebSocketExtensionData>(1);
        boolean sawExtension = false;
        for (CharSequence field : headers.getAll(SEC_WEBSOCKET_EXTENSIONS)) {
            // A comma or semicolon is never valid inside a quoted extension value's decoded token.
            for (String member : field.toString().split(",", -1)) {
                if (trimOws(member).length() == 0) {
                    continue;
                }
                sawExtension = true;
                String[] parts = member.split(";", -1);
                boolean deflate = DEFLATE.equals(token(parts[0]));
                Map<String, String> params = deflate && parts.length > 1 ?
                        new HashMap<String, String>() : Collections.<String, String>emptyMap();
                for (int i = 1; i < parts.length; i++) {
                    int equals = parts[i].indexOf('=');
                    String name = token(equals < 0 ? parts[i] : parts[i].substring(0, equals)).toLowerCase(Locale.ROOT);
                    String value = equals < 0 ? null : parameterValue(parts[i].substring(equals + 1));
                    if (deflate) {
                        // RFC 7692 section 7: decline this offer; later alternatives remain eligible.
                        deflate = !params.containsKey(name) && validDeflateParameter(name, value, response);
                        params.put(name, value);
                    }
                }
                if (response && (!deflate || !deflates.isEmpty())) {
                    throw new WebSocketHandshakeException("invalid permessage-deflate response");
                }
                if (deflate) {
                    deflates.add(new WebSocketExtensionData(DEFLATE, params));
                }
            }
        }
        if (!sawExtension) {
            throw new WebSocketHandshakeException("empty WebSocket extension list");
        }
        return deflates;
    }

    private static boolean validDeflateParameter(String name, String value, boolean response) {
        if ("server_no_context_takeover".equals(name) || "client_no_context_takeover".equals(name)) {
            return value == null;
        }
        if (!SERVER_WINDOW.equals(name) && !CLIENT_WINDOW.equals(name)) {
            return false;
        }
        if (value == null) {
            return !response && CLIENT_WINDOW.equals(name);
        }
        // The window size is a decimal integer from 8 to 15 without leading zeroes.
        return "8".equals(value) || "9".equals(value) ||
                value.length() == 2 && value.charAt(0) == '1' && value.charAt(1) >= '0' && value.charAt(1) <= '5';
    }

    private static String extensionHeader(WebSocketExtensionData data) {
        StringBuilder value = new StringBuilder(data.name());
        for (Entry<String, String> parameter : data.parameters().entrySet()) {
            value.append("; ").append(parameter.getKey());
            if (parameter.getValue() != null) {
                value.append('=').append(parameter.getValue());
            }
        }
        return value.toString();
    }
}
