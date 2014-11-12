/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.AsciiString;
import io.netty.util.CharsetUtil;

import static io.netty.handler.codec.http.HttpConstants.*;

/**
 * The response code and its description of HTTP or its derived protocols, such as
 * <a href="http://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
 * <a href="http://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
 */
public class HttpResponseStatus implements Comparable<HttpResponseStatus> {

    /**
     * 100 Continue
     */
    public static final HttpResponseStatus CONTINUE = httpResponseStatus(100, "Continue");

    /**
     * 101 Switching Protocols
     */
    public static final HttpResponseStatus SWITCHING_PROTOCOLS = httpResponseStatus(101, "Switching Protocols");

    /**
     * 102 Processing (WebDAV, RFC2518)
     */
    public static final HttpResponseStatus PROCESSING = httpResponseStatus(102, "Processing");

    /**
     * 200 OK
     */
    public static final HttpResponseStatus OK = httpResponseStatus(200, "OK");

    /**
     * 201 Created
     */
    public static final HttpResponseStatus CREATED = httpResponseStatus(201, "Created");

    /**
     * 202 Accepted
     */
    public static final HttpResponseStatus ACCEPTED = httpResponseStatus(202, "Accepted");

    /**
     * 203 Non-Authoritative Information (since HTTP/1.1)
     */
    public static final HttpResponseStatus NON_AUTHORITATIVE_INFORMATION =
            httpResponseStatus(203, "Non-Authoritative Information");

    /**
     * 204 No Content
     */
    public static final HttpResponseStatus NO_CONTENT = httpResponseStatus(204, "No Content");

    /**
     * 205 Reset Content
     */
    public static final HttpResponseStatus RESET_CONTENT = httpResponseStatus(205, "Reset Content");

    /**
     * 206 Partial Content
     */
    public static final HttpResponseStatus PARTIAL_CONTENT = httpResponseStatus(206, "Partial Content");

    /**
     * 207 Multi-Status (WebDAV, RFC2518)
     */
    public static final HttpResponseStatus MULTI_STATUS = httpResponseStatus(207, "Multi-Status");

    /**
     * 300 Multiple Choices
     */
    public static final HttpResponseStatus MULTIPLE_CHOICES = httpResponseStatus(300, "Multiple Choices");

    /**
     * 301 Moved Permanently
     */
    public static final HttpResponseStatus MOVED_PERMANENTLY = httpResponseStatus(301, "Moved Permanently");

    /**
     * 302 Found
     */
    public static final HttpResponseStatus FOUND = httpResponseStatus(302, "Found");

    /**
     * 303 See Other (since HTTP/1.1)
     */
    public static final HttpResponseStatus SEE_OTHER = httpResponseStatus(303, "See Other");

    /**
     * 304 Not Modified
     */
    public static final HttpResponseStatus NOT_MODIFIED = httpResponseStatus(304, "Not Modified");

    /**
     * 305 Use Proxy (since HTTP/1.1)
     */
    public static final HttpResponseStatus USE_PROXY = httpResponseStatus(305, "Use Proxy");

    /**
     * 307 Temporary Redirect (since HTTP/1.1)
     */
    public static final HttpResponseStatus TEMPORARY_REDIRECT = httpResponseStatus(307, "Temporary Redirect");

    /**
     * 400 Bad Request
     */
    public static final HttpResponseStatus BAD_REQUEST = httpResponseStatus(400, "Bad Request");

    /**
     * 401 Unauthorized
     */
    public static final HttpResponseStatus UNAUTHORIZED = httpResponseStatus(401, "Unauthorized");

    /**
     * 402 Payment Required
     */
    public static final HttpResponseStatus PAYMENT_REQUIRED = httpResponseStatus(402, "Payment Required");

    /**
     * 403 Forbidden
     */
    public static final HttpResponseStatus FORBIDDEN = httpResponseStatus(403, "Forbidden");

    /**
     * 404 Not Found
     */
    public static final HttpResponseStatus NOT_FOUND = httpResponseStatus(404, "Not Found");

    /**
     * 405 Method Not Allowed
     */
    public static final HttpResponseStatus METHOD_NOT_ALLOWED = httpResponseStatus(405, "Method Not Allowed");

    /**
     * 406 Not Acceptable
     */
    public static final HttpResponseStatus NOT_ACCEPTABLE = httpResponseStatus(406, "Not Acceptable");

    /**
     * 407 Proxy Authentication Required
     */
    public static final HttpResponseStatus PROXY_AUTHENTICATION_REQUIRED =
            httpResponseStatus(407, "Proxy Authentication Required");

    /**
     * 408 Request Timeout
     */
    public static final HttpResponseStatus REQUEST_TIMEOUT = httpResponseStatus(408, "Request Timeout");

    /**
     * 409 Conflict
     */
    public static final HttpResponseStatus CONFLICT = httpResponseStatus(409, "Conflict");

    /**
     * 410 Gone
     */
    public static final HttpResponseStatus GONE = httpResponseStatus(410, "Gone");

    /**
     * 411 Length Required
     */
    public static final HttpResponseStatus LENGTH_REQUIRED = httpResponseStatus(411, "Length Required");

    /**
     * 412 Precondition Failed
     */
    public static final HttpResponseStatus PRECONDITION_FAILED = httpResponseStatus(412, "Precondition Failed");

    /**
     * 413 Request Entity Too Large
     */
    public static final HttpResponseStatus REQUEST_ENTITY_TOO_LARGE =
            httpResponseStatus(413, "Request Entity Too Large");

    /**
     * 414 Request-URI Too Long
     */
    public static final HttpResponseStatus REQUEST_URI_TOO_LONG = httpResponseStatus(414, "Request-URI Too Long");

    /**
     * 415 Unsupported Media Type
     */
    public static final HttpResponseStatus UNSUPPORTED_MEDIA_TYPE = httpResponseStatus(415, "Unsupported Media Type");

    /**
     * 416 Requested Range Not Satisfiable
     */
    public static final HttpResponseStatus REQUESTED_RANGE_NOT_SATISFIABLE =
            httpResponseStatus(416, "Requested Range Not Satisfiable");

    /**
     * 417 Expectation Failed
     */
    public static final HttpResponseStatus EXPECTATION_FAILED = httpResponseStatus(417, "Expectation Failed");

    /**
     * 421 Misdirected Request
     *
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-http2-15#section-9.1.2">421 Status Code</a>
     */
    public static final HttpResponseStatus MISDIRECTED_REQUEST = httpResponseStatus(421, "Misdirected Request");

    /**
     * 422 Unprocessable Entity (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus UNPROCESSABLE_ENTITY = httpResponseStatus(422, "Unprocessable Entity");

    /**
     * 423 Locked (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus LOCKED = httpResponseStatus(423, "Locked");

    /**
     * 424 Failed Dependency (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus FAILED_DEPENDENCY = httpResponseStatus(424, "Failed Dependency");

    /**
     * 425 Unordered Collection (WebDAV, RFC3648)
     */
    public static final HttpResponseStatus UNORDERED_COLLECTION = httpResponseStatus(425, "Unordered Collection");

    /**
     * 426 Upgrade Required (RFC2817)
     */
    public static final HttpResponseStatus UPGRADE_REQUIRED = httpResponseStatus(426, "Upgrade Required");

    /**
     * 428 Precondition Required (RFC6585)
     */
    public static final HttpResponseStatus PRECONDITION_REQUIRED = httpResponseStatus(428, "Precondition Required");

    /**
     * 429 Too Many Requests (RFC6585)
     */
    public static final HttpResponseStatus TOO_MANY_REQUESTS = httpResponseStatus(429, "Too Many Requests");

    /**
     * 431 Request Header Fields Too Large (RFC6585)
     */
    public static final HttpResponseStatus REQUEST_HEADER_FIELDS_TOO_LARGE =
            httpResponseStatus(431, "Request Header Fields Too Large");

    /**
     * 500 Internal Server Error
     */
    public static final HttpResponseStatus INTERNAL_SERVER_ERROR = httpResponseStatus(500, "Internal Server Error");

    /**
     * 501 Not Implemented
     */
    public static final HttpResponseStatus NOT_IMPLEMENTED = httpResponseStatus(501, "Not Implemented");

    /**
     * 502 Bad Gateway
     */
    public static final HttpResponseStatus BAD_GATEWAY = httpResponseStatus(502, "Bad Gateway");

    /**
     * 503 Service Unavailable
     */
    public static final HttpResponseStatus SERVICE_UNAVAILABLE = httpResponseStatus(503, "Service Unavailable");

    /**
     * 504 Gateway Timeout
     */
    public static final HttpResponseStatus GATEWAY_TIMEOUT = httpResponseStatus(504, "Gateway Timeout");

    /**
     * 505 HTTP Version Not Supported
     */
    public static final HttpResponseStatus HTTP_VERSION_NOT_SUPPORTED =
            httpResponseStatus(505, "HTTP Version Not Supported");

    /**
     * 506 Variant Also Negotiates (RFC2295)
     */
    public static final HttpResponseStatus VARIANT_ALSO_NEGOTIATES = httpResponseStatus(506, "Variant Also Negotiates");

    /**
     * 507 Insufficient Storage (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus INSUFFICIENT_STORAGE = httpResponseStatus(507, "Insufficient Storage");

    /**
     * 510 Not Extended (RFC2774)
     */
    public static final HttpResponseStatus NOT_EXTENDED = httpResponseStatus(510, "Not Extended");

    /**
     * 511 Network Authentication Required (RFC6585)
     */
    public static final HttpResponseStatus NETWORK_AUTHENTICATION_REQUIRED =
            httpResponseStatus(511, "Network Authentication Required");

    /**
     * Returns the {@link HttpResponseStatus} represented by the specified code.
     * If the specified code is a standard HTTP getStatus code, a cached instance
     * will be returned.  Otherwise, a new instance will be returned.
     */
    public static HttpResponseStatus valueOf(int code) {
        switch (code) {
        case 100:
            return CONTINUE;
        case 101:
            return SWITCHING_PROTOCOLS;
        case 102:
            return PROCESSING;
        case 200:
            return OK;
        case 201:
            return CREATED;
        case 202:
            return ACCEPTED;
        case 203:
            return NON_AUTHORITATIVE_INFORMATION;
        case 204:
            return NO_CONTENT;
        case 205:
            return RESET_CONTENT;
        case 206:
            return PARTIAL_CONTENT;
        case 207:
            return MULTI_STATUS;
        case 300:
            return MULTIPLE_CHOICES;
        case 301:
            return MOVED_PERMANENTLY;
        case 302:
            return FOUND;
        case 303:
            return SEE_OTHER;
        case 304:
            return NOT_MODIFIED;
        case 305:
            return USE_PROXY;
        case 307:
            return TEMPORARY_REDIRECT;
        case 400:
            return BAD_REQUEST;
        case 401:
            return UNAUTHORIZED;
        case 402:
            return PAYMENT_REQUIRED;
        case 403:
            return FORBIDDEN;
        case 404:
            return NOT_FOUND;
        case 405:
            return METHOD_NOT_ALLOWED;
        case 406:
            return NOT_ACCEPTABLE;
        case 407:
            return PROXY_AUTHENTICATION_REQUIRED;
        case 408:
            return REQUEST_TIMEOUT;
        case 409:
            return CONFLICT;
        case 410:
            return GONE;
        case 411:
            return LENGTH_REQUIRED;
        case 412:
            return PRECONDITION_FAILED;
        case 413:
            return REQUEST_ENTITY_TOO_LARGE;
        case 414:
            return REQUEST_URI_TOO_LONG;
        case 415:
            return UNSUPPORTED_MEDIA_TYPE;
        case 416:
            return REQUESTED_RANGE_NOT_SATISFIABLE;
        case 417:
            return EXPECTATION_FAILED;
        case 421:
            return MISDIRECTED_REQUEST;
        case 422:
            return UNPROCESSABLE_ENTITY;
        case 423:
            return LOCKED;
        case 424:
            return FAILED_DEPENDENCY;
        case 425:
            return UNORDERED_COLLECTION;
        case 426:
            return UPGRADE_REQUIRED;
        case 428:
            return PRECONDITION_REQUIRED;
        case 429:
            return TOO_MANY_REQUESTS;
        case 431:
            return REQUEST_HEADER_FIELDS_TOO_LARGE;
        case 500:
            return INTERNAL_SERVER_ERROR;
        case 501:
            return NOT_IMPLEMENTED;
        case 502:
            return BAD_GATEWAY;
        case 503:
            return SERVICE_UNAVAILABLE;
        case 504:
            return GATEWAY_TIMEOUT;
        case 505:
            return HTTP_VERSION_NOT_SUPPORTED;
        case 506:
            return VARIANT_ALSO_NEGOTIATES;
        case 507:
            return INSUFFICIENT_STORAGE;
        case 510:
            return NOT_EXTENDED;
        case 511:
            return NETWORK_AUTHENTICATION_REQUIRED;
        }

        final String reasonPhrase;

        if (code < 100) {
            reasonPhrase = "Unknown Status";
        } else if (code < 200) {
            reasonPhrase = "Informational";
        } else if (code < 300) {
            reasonPhrase = "Successful";
        } else if (code < 400) {
            reasonPhrase = "Redirection";
        } else if (code < 500) {
            reasonPhrase = "Client Error";
        } else if (code < 600) {
            reasonPhrase = "Server Error";
        } else {
            reasonPhrase = "Unknown Status";
        }

        return new HttpResponseStatus(code, reasonPhrase + " (" + code + ')');
    }

    /**
     * Parses the specified HTTP status line into a {@link HttpResponseStatus}.  The expected formats of the line are:
     * <ul>
     * <li>{@code statusCode} (e.g. 200)</li>
     * <li>{@code statusCode} {@code reasonPhrase} (e.g. 404 Not Found)</li>
     * </ul>
     *
     * @throws IllegalArgumentException if the specified status line is malformed
     */
    public static HttpResponseStatus parseLine(CharSequence line) {
        String status = line.toString();
        try {
            int space = status.indexOf(' ');
            if (space == -1) {
                return valueOf(Integer.parseInt(status));
            } else {
                int code = Integer.parseInt(status.substring(0, space));
                String reasonPhrase = status.substring(space + 1);
                HttpResponseStatus responseStatus = valueOf(code);
                if (responseStatus.reasonPhrase().equals(reasonPhrase)) {
                    return responseStatus;
                } else {
                    return new HttpResponseStatus(code, reasonPhrase);
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("malformed status line: " + status, e);
        }
    }

    private final int code;
    private final AsciiString codeAsString;

    private final String reasonPhrase;
    private final byte[] bytes;

    /**
     * Creates a new instance with the specified {@code code} and its
     * {@code reasonPhrase}.
     */
    public HttpResponseStatus(int code, String reasonPhrase) {
        this(code, reasonPhrase, false);
    }

    private HttpResponseStatus(int code, String reasonPhrase, boolean bytes) {
        if (code < 0) {
            throw new IllegalArgumentException(
                    "code: " + code + " (expected: 0+)");
        }

        if (reasonPhrase == null) {
            throw new NullPointerException("reasonPhrase");
        }

        for (int i = 0; i < reasonPhrase.length(); i ++) {
            char c = reasonPhrase.charAt(i);
            // Check prohibited characters.
            switch (c) {
                case '\n': case '\r':
                    throw new IllegalArgumentException(
                            "reasonPhrase contains one of the following prohibited characters: " +
                                    "\\r\\n: " + reasonPhrase);
            }
        }

        this.code = code;
        codeAsString = new AsciiString(Integer.toString(code));
        this.reasonPhrase = reasonPhrase;
        if (bytes) {
            this.bytes = (code + " " + reasonPhrase).getBytes(CharsetUtil.US_ASCII);
        } else {
            this.bytes = null;
        }
    }

    /**
     * Returns the code of this {@link HttpResponseStatus}.
     */
    public int code() {
        return code;
    }

    /**
     * Returns the status code as {@link AsciiString}.
     */
    public AsciiString codeAsText() {
        return codeAsString;
    }

    /**
     * Returns the reason phrase of this {@link HttpResponseStatus}.
     */
    public String reasonPhrase() {
        return reasonPhrase;
    }

    /**
     * Determine if the status code is of the informational class (1xx)
     */
    public boolean isInformational() {
        return code >= 100 && code < 200;
    }

    @Override
    public int hashCode() {
        return code();
    }

    /**
     * Equality of {@link HttpResponseStatus} only depends on {@link #code()}. The
     * reason phrase is not considered for equality.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HttpResponseStatus)) {
            return false;
        }

        return code() == ((HttpResponseStatus) o).code();
    }

    /**
     * Equality of {@link HttpResponseStatus} only depends on {@link #code()}. The
     * reason phrase is not considered for equality.
     */
    @Override
    public int compareTo(HttpResponseStatus o) {
        return code() - o.code();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(reasonPhrase.length() + 5);
        buf.append(code);
        buf.append(' ');
        buf.append(reasonPhrase);
        return buf.toString();
    }

    void encode(ByteBuf buf) {
        if (bytes == null) {
            HttpHeaderUtil.encodeAscii0(String.valueOf(code()), buf);
            buf.writeByte(SP);
            HttpHeaderUtil.encodeAscii0(String.valueOf(reasonPhrase()), buf);
        } else {
            buf.writeBytes(bytes);
        }
    }

    private static HttpResponseStatus httpResponseStatus(int statusCode, String reasonPhrase) {
        return new HttpResponseStatus(statusCode, reasonPhrase, true);
    }
}
