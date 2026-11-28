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

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtension;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.netty.handler.codec.http.HttpHeaderNames.COOKIE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpHeaderNames.ORIGIN;
import static io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_ACCEPT;
import static io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS;
import static io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_KEY;
import static io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL;
import static io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_VERSION;
import static io.netty.handler.codec.http2.Http2WebSocketHandshakeHeaders.clientCompression;
import static io.netty.handler.codec.http2.Http2WebSocketHandshakeHeaders.isWebSocketRequest;
import static io.netty.handler.codec.http2.Http2WebSocketHandshakeHeaders.negotiateServer;
import static io.netty.handler.codec.http2.Http2WebSocketHandshakeHeaders.newRequest;
import static io.netty.handler.codec.http2.Http2WebSocketHandshakeHeaders.serverCompression;
import static io.netty.handler.codec.http2.Http2WebSocketHandshakeHeaders.validateRequest;
import static io.netty.handler.codec.http2.Http2WebSocketHandshakeHeaders.validateResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Http2WebSocketHandshakeHeadersTest {
    private static Http2Headers target() {
        return new DefaultHttp2Headers().scheme("https").authority("[::1]:8443").path("/chat?room=1");
    }

    private static Http2Headers response() {
        return new DefaultHttp2Headers().status("200");
    }

    private static WebSocketServerExtension accept(Http2Headers request, Http2Headers response,
                                                   PerMessageDeflateServerExtensionHandshaker compression) {
        return negotiateServer(request, response, compression, validateRequest(request));
    }

    @Test
    void createsExtendedConnectAndPreservesApplicationHeaders() {
        Http2Headers input = target().add(ORIGIN, "https://example.com").add(COOKIE, "sid=123")
                .add(SEC_WEBSOCKET_PROTOCOL, "chat, telemetry").add(SEC_WEBSOCKET_PROTOCOL, "Chat");
        Http2Headers request = newRequest(input, null);
        assertThat(input.method()).isNull();
        assertThat(isWebSocketRequest(input)).isFalse();
        assertThat(isWebSocketRequest(target().method("GET").set(":protocol", "websocket"))).isFalse();
        assertThat(isWebSocketRequest(target().method("CONNECT").set(":protocol", "WebSocket"))).isFalse();
        assertThat(isWebSocketRequest(request)).isTrue();
        assertThat(request.get(ORIGIN)).hasToString("https://example.com");
        assertThat(request.get(COOKIE)).hasToString("sid=123");
        assertThat(request.get(SEC_WEBSOCKET_VERSION)).hasToString("13");
        assertThat(request.contains(SEC_WEBSOCKET_KEY)).isFalse();
        assertThat(request.contains(SEC_WEBSOCKET_EXTENSIONS)).isFalse();

        Http2Headers accepted = new DefaultHttp2Headers().set(SEC_WEBSOCKET_PROTOCOL, "Chat").set("set-cookie", "x=1");
        assertThat(accept(request, accepted, null)).isNull();
        assertThat(accepted.status()).hasToString("200");
        assertThat(accepted.get("set-cookie")).hasToString("x=1");
        assertThat(validateResponse(request, accepted, null)).isNull();
        assertThat(validateResponse(request, response().status("299"), null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { ":scheme", ":authority", ":path", ":method", ":protocol", "sec-websocket-version" })
    void rejectsMissingRequestFields(String field) {
        Http2Headers request = newRequest(target(), null);
        request.remove(field);
        assertThatThrownBy(() -> validateRequest(request)).isInstanceOf(WebSocketHandshakeException.class);
    }

    @ParameterizedTest
    @CsvSource({ ":scheme,wss", ":authority,example.org/room", ":authority,user@host",
            ":authority,localhost:abc", ":authority,:8080", ":authority,host:80:81", ":path,chat",
            ":path,/chat#fragment", ":method,GET", ":protocol,WebSocket",
            "sec-websocket-version,8", "connection,Upgrade", "upgrade,websocket", "te,gzip", ":status,200" })
    void rejectsInvalidRequestFields(String field, String value) {
        Http2Headers request = newRequest(target(), null).set(field, value);
        assertThatThrownBy(() -> validateRequest(request)).isInstanceOf(WebSocketHandshakeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { ":method", ":scheme", ":authority", ":path", ":protocol", "sec-websocket-version" })
    void rejectsDuplicateWireFieldsEvenIfOuterHeaderValidationWasDisabled(String field) {
        Http2Headers request = new DefaultHttp2Headers(false).set(newRequest(target(), null));
        request.add(field, request.get(field));
        assertThatThrownBy(() -> validateRequest(request)).isInstanceOf(WebSocketHandshakeException.class);
    }

    @ParameterizedTest
    @CsvSource({ "example.org,example.org:443", "[::1]:8443,[::1]:08443",
            "service_name:0443,service_name", "%65xample.com:80,%65xample.com:0080", "service_name:,service_name" })
    void acceptsRegisteredNamesIpLiteralsAndEquivalentPorts(String authority, String host) {
        Http2Headers input = target().authority(authority).set(HOST, host);
        assertThat(newRequest(input, null).authority()).hasToString(authority);
    }

    @Test
    void rejectsConflictingAuthorityAndHost() {
        Http2Headers input = target().authority("[::1]:8443").set(HOST, "[::1]:443");
        assertThatThrownBy(() -> newRequest(input, null)).isInstanceOf(WebSocketHandshakeException.class);
    }

    @Test
    void preservesTheWireRulesWhenCopyingUnvalidatedApplicationHeaders() {
        Http2Headers input = new DefaultHttp2Headers(false).set(target()).add("invalid name", "value");
        assertThatThrownBy(() -> newRequest(input, null)).isInstanceOf(WebSocketHandshakeException.class);
        assertThatThrownBy(() -> Http2WebSocketHandshakeHeaders.copyOf(input))
                .isInstanceOf(WebSocketHandshakeException.class);
    }

    @Test
    void ignoresEmptyHttpListMembersAroundAnOffer() {
        Http2Headers request = newRequest(target(), null).add(SEC_WEBSOCKET_EXTENSIONS, ", permessage-deflate,")
                .add(SEC_WEBSOCKET_PROTOCOL, "chat, ,echo");
        Http2Headers accepted = response().add(SEC_WEBSOCKET_PROTOCOL, "echo");
        assertThat(accept(request, accepted, serverCompression(65536))).isNotNull();
        request.set(SEC_WEBSOCKET_EXTENSIONS, ",,");
        assertThatThrownBy(() -> validateRequest(request)).isInstanceOf(WebSocketHandshakeException.class);
    }

    @Test
    void ignoresHttp1NonceFieldsOnReceiptAndNeverSendsThem() {
        Http2Headers request = newRequest(target(), null).set(SEC_WEBSOCKET_KEY, "unused");
        Http2Headers received = response().set(SEC_WEBSOCKET_ACCEPT, "unused");
        validateRequest(request);
        assertThat(validateResponse(request, received, null)).isNull();
        assertThatThrownBy(() -> newRequest(target().set(SEC_WEBSOCKET_KEY, "unused"), null))
                .isInstanceOf(WebSocketHandshakeException.class);
        assertThatThrownBy(() -> accept(request, received, null)).isInstanceOf(WebSocketHandshakeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "chat room", "chat, chat", "a/b", "chat,\u0001bad" })
    void rejectsMalformedOrDuplicateOfferedSubprotocols(String protocols) {
        Http2Headers request = new DefaultHttp2Headers(false).set(newRequest(target(), null))
                .set(SEC_WEBSOCKET_PROTOCOL, protocols);
        assertThatThrownBy(() -> validateRequest(request)).isInstanceOf(WebSocketHandshakeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "chat", "Chat, echo", "echo2", "Chat, Chat" })
    void rejectsSelectionsOtherThanOneCaseSensitiveOfferedToken(String selected) {
        Http2Headers request = newRequest(target().set(SEC_WEBSOCKET_PROTOCOL, "Chat, echo"), null);
        Http2Headers response = response().set(SEC_WEBSOCKET_PROTOCOL, selected);
        assertThatThrownBy(() -> accept(request, response, null)).isInstanceOf(WebSocketHandshakeException.class);
        assertThatThrownBy(() -> validateResponse(request, response, null))
                .isInstanceOf(WebSocketHandshakeException.class);
    }

    @ParameterizedTest
    @CsvSource({ "sec-websocket-protocol,chat", "sec-websocket-extensions,permessage-deflate", ":status,200" })
    void rejectsMultipleResponseHeaders(String name, String value) {
        PerMessageDeflateClientExtensionHandshaker compression = clientCompression(65536);
        Http2Headers request = newRequest(target().set(SEC_WEBSOCKET_PROTOCOL, "chat"), compression);
        Http2Headers response = new DefaultHttp2Headers(false).set(response()).set(name, value).add(name, value);
        assertThatThrownBy(() -> validateResponse(request, response, compression))
                .isInstanceOf(WebSocketHandshakeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "101", "403", "20x", "2000" })
    void requiresAFinalSuccessStatus(String status) {
        Http2Headers request = newRequest(target(), null);
        assertThatThrownBy(() -> validateResponse(request, response().status(status), null))
                .isInstanceOf(WebSocketHandshakeException.class);
    }

    @Test
    void doesNotLetApplicationsAdvertiseAnUninstalledExtension() {
        Http2Headers input = target().set(SEC_WEBSOCKET_EXTENSIONS, "my-compression");
        assertThatThrownBy(() -> newRequest(input, clientCompression(65536)))
                .isInstanceOf(WebSocketHandshakeException.class);
        Http2Headers request = newRequest(target(), null);
        Http2Headers response = response().set(SEC_WEBSOCKET_EXTENSIONS, "my-compression");
        assertThatThrownBy(() -> accept(request, response, serverCompression(65536)))
                .isInstanceOf(WebSocketHandshakeException.class);
    }

    @Test
    void negotiatesTheFirstSupportedOfferAcrossMultipleFieldLines() {
        Http2Headers request = newRequest(target(), null)
                .add(SEC_WEBSOCKET_EXTENSIONS, "unknown; flag=token, permessage-deflate; unknown")
                .add(SEC_WEBSOCKET_EXTENSIONS, "permessage-deflate; server_no_context_takeover, permessage-deflate");
        Http2Headers response = response();
        assertThat(accept(request, response, serverCompression(65536))).isNotNull();
        assertThat(response.get(SEC_WEBSOCKET_EXTENSIONS))
                .hasToString("permessage-deflate; server_no_context_takeover");
        Http2Headers declined = response();
        assertThat(accept(request, declined, null)).isNull();
        assertThat(declined.contains(SEC_WEBSOCKET_EXTENSIONS)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "permessage-deflate; server_max_window_bits",
            "permessage-deflate; client_max_window_bits=07", "permessage-deflate; server_max_window_bits=16",
            "permessage-deflate; client_no_context_takeover=1",
            "permessage-deflate; client_max_window_bits; CLIENT_MAX_WINDOW_BITS=10" })
    void declinesAnInvalidDeflateOfferAndContinuesWithOtherOffers(String offer) {
        Http2Headers request = newRequest(target(), null).set(SEC_WEBSOCKET_EXTENSIONS, offer);
        Http2Headers uncompressed = response();
        assertThat(accept(request, uncompressed, serverCompression(65536))).isNull();
        assertThat(uncompressed.contains(SEC_WEBSOCKET_EXTENSIONS)).isFalse();
        request.add(SEC_WEBSOCKET_EXTENSIONS, "permessage-deflate");
        assertThat(accept(request, response(), serverCompression(65536))).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "bad extension", "permessage-deflate;", "x; key=\"unterminated",
            "x; key=\"not a token\"", "permessage-deflate, bad; =value", "x; n=\"tok;en\"" })
    void rejectsMalformedExtensionGrammarEvenForUnusedOffers(String header) {
        Http2Headers request = newRequest(target(), null).set(SEC_WEBSOCKET_EXTENSIONS, header);
        assertThatThrownBy(() -> validateRequest(request)).isInstanceOf(WebSocketHandshakeException.class);
    }

    @Test
    void skipsAnOfferRequiringAnEightBitServerEncoder() {
        Http2Headers request = newRequest(target(), null).set(SEC_WEBSOCKET_EXTENSIONS,
                "permessage-deflate; server_max_window_bits=8");
        Http2Headers accepted = response();
        assertThat(accept(request, accepted, serverCompression(65536))).isNull();
        assertThat(accepted.contains(SEC_WEBSOCKET_EXTENSIONS)).isFalse();

        request.add(SEC_WEBSOCKET_EXTENSIONS, "permessage-deflate; server_max_window_bits=9; client_max_window_bits=8");
        WebSocketServerExtension extension = accept(request, accepted, serverCompression(65536));
        assertThat(accepted.get(SEC_WEBSOCKET_EXTENSIONS).toString())
                .contains("server_max_window_bits=9", "client_max_window_bits=8");
        assertCanWriteCompressedFrame(extension);
    }

    @Test
    void negotiatesWithAServerThatPrefersAnEightBitClientWindow() {
        PerMessageDeflateClientExtensionHandshaker client = clientCompression(65536);
        PerMessageDeflateServerExtensionHandshaker server =
                new PerMessageDeflateServerExtensionHandshaker(6, false, 8, false, false, 65536);
        Http2Headers request = newRequest(target(), client);
        Http2Headers response = response();
        assertCanWriteCompressedFrame(accept(request, response, server));
        assertCanWriteCompressedFrame(validateResponse(request, response, client));
        assertThat(request.get(SEC_WEBSOCKET_EXTENSIONS)).hasToString("permessage-deflate");
        assertThat(response.get(SEC_WEBSOCKET_EXTENSIONS)).hasToString("permessage-deflate");
    }

    @Test
    void acceptsAnEightBitServerWindowWithoutOfferingAClientWindow() {
        PerMessageDeflateClientExtensionHandshaker client = clientCompression(65536);
        Http2Headers request = newRequest(target(), client);
        Http2Headers selected = response().set(SEC_WEBSOCKET_EXTENSIONS,
                "permessage-deflate; server_max_window_bits=8");
        assertCanWriteCompressedFrame(validateResponse(request, selected, client));
    }

    @ParameterizedTest
    @ValueSource(ints = { 8, 9, 15 })
    void rejectsAnUnsolicitedClientWindow(int window) {
        PerMessageDeflateClientExtensionHandshaker client = clientCompression(65536);
        Http2Headers request = newRequest(target(), client);
        Http2Headers selected = response().set(SEC_WEBSOCKET_EXTENSIONS,
                "permessage-deflate; client_max_window_bits=" + window);
        assertThatThrownBy(() -> validateResponse(request, selected, client))
                .isInstanceOf(WebSocketHandshakeException.class);
    }

    private static void assertCanWriteCompressedFrame(WebSocketExtension extension) {
        EmbeddedChannel channel = new EmbeddedChannel(extension.newExtensionEncoder());
        try {
            assertThat(channel.writeOutbound(new TextWebSocketFrame("hello"))).isTrue();
            TextWebSocketFrame compressed = channel.readOutbound();
            try {
                assertThat(compressed.rsv()).isEqualTo(WebSocketExtension.RSV1);
            } finally {
                compressed.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void acceptsQuotedParameterValuesAndAcknowledgesExplicitServerWindow() {
        Http2Headers request = newRequest(target(), null)
                .set(SEC_WEBSOCKET_EXTENSIONS, "permessage-deflate; server_max_window_bits=\"1\\5\"");
        PerMessageDeflateServerExtensionHandshaker compression =
                new PerMessageDeflateServerExtensionHandshaker(6, true, 15, true, false, 65536);
        Http2Headers accepted = response();
        assertThat(accept(request, accepted, compression)).isNotNull();
        assertThat(accepted.get(SEC_WEBSOCKET_EXTENSIONS)).hasToString("permessage-deflate; server_max_window_bits=15");
    }

    @ParameterizedTest
    @ValueSource(strings = { "client_no_context_takeover", "server_no_context_takeover",
            "client_no_context_takeover; server_no_context_takeover" })
    void acceptsServerRequestedContextLimitsWithoutMakingAnUnconditionalClientPromise(String parameters) {
        PerMessageDeflateClientExtensionHandshaker compression = clientCompression(65536);
        Http2Headers request = newRequest(target(), compression);
        assertThat(request.get(SEC_WEBSOCKET_EXTENSIONS).toString()).doesNotContain("no_context_takeover");
        Http2Headers accepted = response().set(SEC_WEBSOCKET_EXTENSIONS, "permessage-deflate; " + parameters);
        assertThat(validateResponse(request, accepted, compression)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "unknown", "permessage-deflate, permessage-deflate", "permessage-deflate; unknown",
            "permessage-deflate; client_max_window_bits", "permessage-deflate; client_max_window_bits=+9",
            "permessage-deflate; client_max_window_bits=09", "permessage-deflate; server_no_context_takeover=1",
            "permessage-deflate; client_no_context_takeover; CLIENT_NO_CONTEXT_TAKEOVER" })
    void rejectsUnsupportedOrMalformedExtensionResponses(String selected) {
        PerMessageDeflateClientExtensionHandshaker compression = clientCompression(65536);
        Http2Headers request = newRequest(target(), compression);
        Http2Headers response = response().set(SEC_WEBSOCKET_EXTENSIONS, selected);
        assertThatThrownBy(() -> validateResponse(request, response, compression))
                .isInstanceOf(WebSocketHandshakeException.class);
    }

    @Test
    void rejectsUnsolicitedExtensionAndAcceptsAnUncompressedResponse() {
        PerMessageDeflateClientExtensionHandshaker compression = clientCompression(65536);
        Http2Headers response = response().set(SEC_WEBSOCKET_EXTENSIONS, "permessage-deflate");
        Http2Headers plainRequest = newRequest(target(), null);
        assertThatThrownBy(() -> validateResponse(plainRequest, response, null))
                .isInstanceOf(WebSocketHandshakeException.class);
        assertThat(validateResponse(newRequest(target(), compression), response(), compression)).isNull();
    }
}
