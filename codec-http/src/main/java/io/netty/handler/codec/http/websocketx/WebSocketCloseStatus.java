/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http.websocketx;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * WebSocket status codes specified in RFC-6455.
 * <pre>
 *
 * RFC-6455 The WebSocket Protocol, December 2011:
 * <a href="https://tools.ietf.org/html/rfc6455#section-7.4.1"
 *         >https://tools.ietf.org/html/rfc6455#section-7.4.1</a>
 *
 * WebSocket Protocol Registries, April 2019:
 * <a href="https://www.iana.org/assignments/websocket/websocket.xhtml#close-code-number"
 *         >https://www.iana.org/assignments/websocket/websocket.xhtml</a>
 *
 * 7.4.1.  Defined Status Codes
 *
 * Endpoints MAY use the following pre-defined status codes when sending
 * a Close frame.
 *
 * 1000
 *
 *    1000 indicates a normal closure, meaning that the purpose for
 *    which the connection was established has been fulfilled.
 *
 * 1001
 *
 *    1001 indicates that an endpoint is "going away", such as a server
 *    going down or a browser having navigated away from a page.
 *
 * 1002
 *
 *    1002 indicates that an endpoint is terminating the connection due
 *    to a protocol error.
 *
 * 1003
 *
 *    1003 indicates that an endpoint is terminating the connection
 *    because it has received a type of data it cannot accept (e.g., an
 *    endpoint that understands only text data MAY send this if it
 *    receives a binary message).
 *
 * 1004
 *
 *    Reserved. The specific meaning might be defined in the future.
 *
 * 1005
 *
 *    1005 is a reserved value and MUST NOT be set as a status code in a
 *    Close control frame by an endpoint. It is designated for use in
 *    applications expecting a status code to indicate that no status
 *    code was actually present.
 *
 * 1006
 *
 *    1006 is a reserved value and MUST NOT be set as a status code in a
 *    Close control frame by an endpoint. It is designated for use in
 *    applications expecting a status code to indicate that the
 *    connection was closed abnormally, e.g., without sending or
 *    receiving a Close control frame.
 *
 * 1007
 *
 *    1007 indicates that an endpoint is terminating the connection
 *    because it has received data within a message that was not
 *    consistent with the type of the message (e.g., non-UTF-8 [RFC3629]
 *    data within a text message).
 *
 * 1008
 *
 *    1008 indicates that an endpoint is terminating the connection
 *    because it has received a message that violates its policy. This
 *    is a generic status code that can be returned when there is no
 *    other more suitable status code (e.g., 1003 or 1009) or if there
 *    is a need to hide specific details about the policy.
 *
 * 1009
 *
 *    1009 indicates that an endpoint is terminating the connection
 *    because it has received a message that is too big for it to
 *    process.
 *
 * 1010
 *
 *    1010 indicates that an endpoint (client) is terminating the
 *    connection because it has expected the server to negotiate one or
 *    more extension, but the server didn't return them in the response
 *    message of the WebSocket handshake. The list of extensions that
 *    are needed SHOULD appear in the /reason/ part of the Close frame.
 *    Note that this status code is not used by the server, because it
 *    can fail the WebSocket handshake instead.
 *
 * 1011
 *
 *    1011 indicates that a server is terminating the connection because
 *    it encountered an unexpected condition that prevented it from
 *    fulfilling the request.
 *
 * 1012 (IANA Registry, Non RFC-6455)
 *
 *    1012 indicates that the service is restarted. a client may reconnect,
 *    and if it choses to do, should reconnect using a randomized delay
 *    of 5 - 30 seconds.
 *
 * 1013 (IANA Registry, Non RFC-6455)
 *
 *    1013 indicates that the service is experiencing overload. a client
 *    should only connect to a different IP (when there are multiple for the
 *    target) or reconnect to the same IP upon user action.
 *
 * 1014 (IANA Registry, Non RFC-6455)
 *
 *    The server was acting as a gateway or proxy and received an invalid
 *    response from the upstream server. This is similar to 502 HTTP Status Code.
 *
 * 1015
 *
 *    1015 is a reserved value and MUST NOT be set as a status code in a
 *    Close control frame by an endpoint. It is designated for use in
 *    applications expecting a status code to indicate that the
 *    connection was closed due to a failure to perform a TLS handshake
 *    (e.g., the server certificate can't be verified).
 *
 *
 * 7.4.2. Reserved Status Code Ranges
 *
 * 0-999
 *
 *    Status codes in the range 0-999 are not used.
 *
 * 1000-2999
 *
 *    Status codes in the range 1000-2999 are reserved for definition by
 *    this protocol, its future revisions, and extensions specified in a
 *    permanent and readily available public specification.
 *
 * 3000-3999
 *
 *    Status codes in the range 3000-3999 are reserved for use by
 *    libraries, frameworks, and applications. These status codes are
 *    registered directly with IANA. The interpretation of these codes
 *    is undefined by this protocol.
 *
 * 4000-4999
 *
 *    Status codes in the range 4000-4999 are reserved for private use
 *    and thus can't be registered. Such codes can be used by prior
 *    agreements between WebSocket applications. The interpretation of
 *    these codes is undefined by this protocol.
 * </pre>
 * <p>
 * While {@link WebSocketCloseStatus} is enum-like structure, its instances should NOT be compared by reference.
 * Instead, either {@link #equals(Object)} should be used or direct comparison of {@link #code()} value.
 */
public final class WebSocketCloseStatus implements Comparable<WebSocketCloseStatus> {

    public static final WebSocketCloseStatus NORMAL_CLOSURE =
        new WebSocketCloseStatus(1000, "Bye");

    public static final WebSocketCloseStatus ENDPOINT_UNAVAILABLE =
        new WebSocketCloseStatus(1001, "Endpoint unavailable");

    public static final WebSocketCloseStatus PROTOCOL_ERROR =
        new WebSocketCloseStatus(1002, "Protocol error");

    public static final WebSocketCloseStatus INVALID_MESSAGE_TYPE =
        new WebSocketCloseStatus(1003, "Invalid message type");

    public static final WebSocketCloseStatus INVALID_PAYLOAD_DATA =
        new WebSocketCloseStatus(1007, "Invalid payload data");

    public static final WebSocketCloseStatus POLICY_VIOLATION =
        new WebSocketCloseStatus(1008, "Policy violation");

    public static final WebSocketCloseStatus MESSAGE_TOO_BIG =
        new WebSocketCloseStatus(1009, "Message too big");

    public static final WebSocketCloseStatus MANDATORY_EXTENSION =
        new WebSocketCloseStatus(1010, "Mandatory extension");

    public static final WebSocketCloseStatus INTERNAL_SERVER_ERROR =
        new WebSocketCloseStatus(1011, "Internal server error");

    public static final WebSocketCloseStatus SERVICE_RESTART =
        new WebSocketCloseStatus(1012, "Service Restart");

    public static final WebSocketCloseStatus TRY_AGAIN_LATER =
        new WebSocketCloseStatus(1013, "Try Again Later");

    public static final WebSocketCloseStatus BAD_GATEWAY =
        new WebSocketCloseStatus(1014, "Bad Gateway");

    // 1004, 1005, 1006, 1015 are reserved and should never be used by user
    //public static final WebSocketCloseStatus SPECIFIC_MEANING = register(1004, "...");
    //public static final WebSocketCloseStatus EMPTY = register(1005, "Empty");
    //public static final WebSocketCloseStatus ABNORMAL_CLOSURE = register(1006, "Abnormal closure");
    //public static final WebSocketCloseStatus TLS_HANDSHAKE_FAILED(1015, "TLS handshake failed");

    private final int statusCode;
    private final String reasonText;
    private String text;

    public WebSocketCloseStatus(int statusCode, String reasonText) {
        if (!isValidStatusCode(statusCode)) {
            throw new IllegalArgumentException(
                "WebSocket close status code does NOT comply with RFC-6455: " + statusCode);
        }
        this.statusCode = statusCode;
        this.reasonText = checkNotNull(reasonText, "reasonText");
    }

    public int code() {
        return statusCode;
    }

    public String reasonText() {
        return reasonText;
    }

    /**
     * Order of {@link WebSocketCloseStatus} only depends on {@link #code()}.
     */
    @Override
    public int compareTo(WebSocketCloseStatus o) {
        return code() - o.code();
    }

    /**
     * Equality of {@link WebSocketCloseStatus} only depends on {@link #code()}.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }

        WebSocketCloseStatus that = (WebSocketCloseStatus) o;

        return statusCode == that.statusCode;
    }

    @Override
    public int hashCode() {
        return statusCode;
    }

    @Override
    public String toString() {
        String text = this.text;
        if (text == null) {
            // E.g.: "1000 Bye", "1009 Message too big"
            this.text = text = code() + " " + reasonText();
        }
        return text;
    }

    public static boolean isValidStatusCode(int code) {
        return code < 0 ||
            1000 <= code && code <= 1003 ||
            1007 <= code && code <= 1014 ||
            3000 <= code;
    }

    public static WebSocketCloseStatus valueOf(int code) {
        switch (code) {
            case 1000:
                return NORMAL_CLOSURE;
            case 1001:
                return ENDPOINT_UNAVAILABLE;
            case 1002:
                return PROTOCOL_ERROR;
            case 1003:
                return INVALID_MESSAGE_TYPE;
            case 1007:
                return INVALID_PAYLOAD_DATA;
            case 1008:
                return POLICY_VIOLATION;
            case 1009:
                return MESSAGE_TOO_BIG;
            case 1010:
                return MANDATORY_EXTENSION;
            case 1011:
                return INTERNAL_SERVER_ERROR;
            case 1012:
                return SERVICE_RESTART;
            case 1013:
                return TRY_AGAIN_LATER;
            case 1014:
                return BAD_GATEWAY;
            default:
                return new WebSocketCloseStatus(code, "Close status #" + code);
        }
    }

}
