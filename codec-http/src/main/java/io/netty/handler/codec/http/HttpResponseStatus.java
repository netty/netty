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
import io.netty.buffer.ByteBufUtil;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.ObjectUtil;

import static io.netty.handler.codec.http.HttpConstants.SP;
import static io.netty.util.ByteProcessor.FIND_ASCII_SPACE;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.lang.Integer.parseInt;

/**
 * The response code and its description of HTTP or its derived protocols, such as
 * <a href="http://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
 * <a href="http://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
 */
public class HttpResponseStatus implements Comparable<HttpResponseStatus> {

    /**
     * 100 Continue
     */
    public static final HttpResponseStatus CONTINUE = newStatus(100, "Continue");

    /**
     * 101 Switching Protocols
     */
    public static final HttpResponseStatus SWITCHING_PROTOCOLS = newStatus(101, "Switching Protocols");

    /**
     * 102 Processing (WebDAV, RFC2518)
     */
    public static final HttpResponseStatus PROCESSING = newStatus(102, "Processing");

    /**
     * 200 OK
     */
    public static final HttpResponseStatus OK = newStatus(200, "OK");

    /**
     * 201 Created
     */
    public static final HttpResponseStatus CREATED = newStatus(201, "Created");

    /**
     * 202 Accepted
     */
    public static final HttpResponseStatus ACCEPTED = newStatus(202, "Accepted");

    /**
     * 203 Non-Authoritative Information (since HTTP/1.1)
     */
    public static final HttpResponseStatus NON_AUTHORITATIVE_INFORMATION =
            newStatus(203, "Non-Authoritative Information");

    /**
     * 204 No Content
     */
    public static final HttpResponseStatus NO_CONTENT = newStatus(204, "No Content");

    /**
     * 205 Reset Content
     */
    public static final HttpResponseStatus RESET_CONTENT = newStatus(205, "Reset Content");

    /**
     * 206 Partial Content
     */
    public static final HttpResponseStatus PARTIAL_CONTENT = newStatus(206, "Partial Content");

    /**
     * 207 Multi-Status (WebDAV, RFC2518)
     */
    public static final HttpResponseStatus MULTI_STATUS = newStatus(207, "Multi-Status");

    /**
     * 300 Multiple Choices
     */
    public static final HttpResponseStatus MULTIPLE_CHOICES = newStatus(300, "Multiple Choices");

    /**
     * 301 Moved Permanently
     */
    public static final HttpResponseStatus MOVED_PERMANENTLY = newStatus(301, "Moved Permanently");

    /**
     * 302 Found
     */
    public static final HttpResponseStatus FOUND = newStatus(302, "Found");

    /**
     * 303 See Other (since HTTP/1.1)
     */
    public static final HttpResponseStatus SEE_OTHER = newStatus(303, "See Other");

    /**
     * 304 Not Modified
     */
    public static final HttpResponseStatus NOT_MODIFIED = newStatus(304, "Not Modified");

    /**
     * 305 Use Proxy (since HTTP/1.1)
     */
    public static final HttpResponseStatus USE_PROXY = newStatus(305, "Use Proxy");

    /**
     * 307 Temporary Redirect (since HTTP/1.1)
     */
    public static final HttpResponseStatus TEMPORARY_REDIRECT = newStatus(307, "Temporary Redirect");

    /**
     * 308 Permanent Redirect (RFC7538)
     */
    public static final HttpResponseStatus PERMANENT_REDIRECT = newStatus(308, "Permanent Redirect");

    /**
     * 400 Bad Request
     */
    public static final HttpResponseStatus BAD_REQUEST = newStatus(400, "Bad Request");

    /**
     * 401 Unauthorized
     */
    public static final HttpResponseStatus UNAUTHORIZED = newStatus(401, "Unauthorized");

    /**
     * 402 Payment Required
     */
    public static final HttpResponseStatus PAYMENT_REQUIRED = newStatus(402, "Payment Required");

    /**
     * 403 Forbidden
     */
    public static final HttpResponseStatus FORBIDDEN = newStatus(403, "Forbidden");

    /**
     * 404 Not Found
     */
    public static final HttpResponseStatus NOT_FOUND = newStatus(404, "Not Found");

    /**
     * 405 Method Not Allowed
     */
    public static final HttpResponseStatus METHOD_NOT_ALLOWED = newStatus(405, "Method Not Allowed");

    /**
     * 406 Not Acceptable
     */
    public static final HttpResponseStatus NOT_ACCEPTABLE = newStatus(406, "Not Acceptable");

    /**
     * 407 Proxy Authentication Required
     */
    public static final HttpResponseStatus PROXY_AUTHENTICATION_REQUIRED =
            newStatus(407, "Proxy Authentication Required");

    /**
     * 408 Request Timeout
     */
    public static final HttpResponseStatus REQUEST_TIMEOUT = newStatus(408, "Request Timeout");

    /**
     * 409 Conflict
     */
    public static final HttpResponseStatus CONFLICT = newStatus(409, "Conflict");

    /**
     * 410 Gone
     */
    public static final HttpResponseStatus GONE = newStatus(410, "Gone");

    /**
     * 411 Length Required
     */
    public static final HttpResponseStatus LENGTH_REQUIRED = newStatus(411, "Length Required");

    /**
     * 412 Precondition Failed
     */
    public static final HttpResponseStatus PRECONDITION_FAILED = newStatus(412, "Precondition Failed");

    /**
     * 413 Request Entity Too Large
     */
    public static final HttpResponseStatus REQUEST_ENTITY_TOO_LARGE =
            newStatus(413, "Request Entity Too Large");

    /**
     * 414 Request-URI Too Long
     */
    public static final HttpResponseStatus REQUEST_URI_TOO_LONG = newStatus(414, "Request-URI Too Long");

    /**
     * 415 Unsupported Media Type
     */
    public static final HttpResponseStatus UNSUPPORTED_MEDIA_TYPE = newStatus(415, "Unsupported Media Type");

    /**
     * 416 Requested Range Not Satisfiable
     */
    public static final HttpResponseStatus REQUESTED_RANGE_NOT_SATISFIABLE =
            newStatus(416, "Requested Range Not Satisfiable");

    /**
     * 417 Expectation Failed
     */
    public static final HttpResponseStatus EXPECTATION_FAILED = newStatus(417, "Expectation Failed");

    /**
     * 421 Misdirected Request
     *
     * @see <a href="https://tools.ietf.org/html/rfc7540#section-9.1.2">421 (Misdirected Request) Status Code</a>
     */
    public static final HttpResponseStatus MISDIRECTED_REQUEST = newStatus(421, "Misdirected Request");

    /**
     * 422 Unprocessable Entity (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus UNPROCESSABLE_ENTITY = newStatus(422, "Unprocessable Entity");

    /**
     * 423 Locked (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus LOCKED = newStatus(423, "Locked");

    /**
     * 424 Failed Dependency (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus FAILED_DEPENDENCY = newStatus(424, "Failed Dependency");

    /**
     * 425 Unordered Collection (WebDAV, RFC3648)
     */
    public static final HttpResponseStatus UNORDERED_COLLECTION = newStatus(425, "Unordered Collection");

    /**
     * 426 Upgrade Required (RFC2817)
     */
    public static final HttpResponseStatus UPGRADE_REQUIRED = newStatus(426, "Upgrade Required");

    /**
     * 428 Precondition Required (RFC6585)
     */
    public static final HttpResponseStatus PRECONDITION_REQUIRED = newStatus(428, "Precondition Required");

    /**
     * 429 Too Many Requests (RFC6585)
     */
    public static final HttpResponseStatus TOO_MANY_REQUESTS = newStatus(429, "Too Many Requests");

    /**
     * 431 Request Header Fields Too Large (RFC6585)
     */
    public static final HttpResponseStatus REQUEST_HEADER_FIELDS_TOO_LARGE =
            newStatus(431, "Request Header Fields Too Large");

    /**
     * 500 Internal Server Error
     */
    public static final HttpResponseStatus INTERNAL_SERVER_ERROR = newStatus(500, "Internal Server Error");

    /**
     * 501 Not Implemented
     */
    public static final HttpResponseStatus NOT_IMPLEMENTED = newStatus(501, "Not Implemented");

    /**
     * 502 Bad Gateway
     */
    public static final HttpResponseStatus BAD_GATEWAY = newStatus(502, "Bad Gateway");

    /**
     * 503 Service Unavailable
     */
    public static final HttpResponseStatus SERVICE_UNAVAILABLE = newStatus(503, "Service Unavailable");

    /**
     * 504 Gateway Timeout
     */
    public static final HttpResponseStatus GATEWAY_TIMEOUT = newStatus(504, "Gateway Timeout");

    /**
     * 505 HTTP Version Not Supported
     */
    public static final HttpResponseStatus HTTP_VERSION_NOT_SUPPORTED =
            newStatus(505, "HTTP Version Not Supported");

    /**
     * 506 Variant Also Negotiates (RFC2295)
     */
    public static final HttpResponseStatus VARIANT_ALSO_NEGOTIATES = newStatus(506, "Variant Also Negotiates");

    /**
     * 507 Insufficient Storage (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus INSUFFICIENT_STORAGE = newStatus(507, "Insufficient Storage");

    /**
     * 510 Not Extended (RFC2774)
     */
    public static final HttpResponseStatus NOT_EXTENDED = newStatus(510, "Not Extended");

    /**
     * 511 Network Authentication Required (RFC6585)
     */
    public static final HttpResponseStatus NETWORK_AUTHENTICATION_REQUIRED =
            newStatus(511, "Network Authentication Required");

    private static HttpResponseStatus newStatus(int statusCode, String reasonPhrase) {
        return new HttpResponseStatus(statusCode, reasonPhrase, true);
    }

    /**
     * Returns the {@link HttpResponseStatus} represented by the specified code.
     * If the specified code is a standard HTTP status code, a cached instance
     * will be returned.  Otherwise, a new instance will be returned.
     */
    public static HttpResponseStatus valueOf(int code) {
        HttpResponseStatus status = valueOf0(code);
        return status != null ? status : new HttpResponseStatus(code);
    }

    private static HttpResponseStatus valueOf0(int code) {
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
        case 308:
            return PERMANENT_REDIRECT;
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
        return null;
    }

    /**
     * Returns the {@link HttpResponseStatus} represented by the specified {@code code} and {@code reasonPhrase}.
     * If the specified code is a standard HTTP status {@code code} and {@code reasonPhrase}, a cached instance
     * will be returned. Otherwise, a new instance will be returned.
     * @param code The response code value.
     * @param reasonPhrase The response code reason phrase.
     * @return the {@link HttpResponseStatus} represented by the specified {@code code} and {@code reasonPhrase}.
     */
    public static HttpResponseStatus valueOf(int code, String reasonPhrase) {
        HttpResponseStatus responseStatus = valueOf0(code);
        return responseStatus != null && responseStatus.reasonPhrase().contentEquals(reasonPhrase) ? responseStatus :
                new HttpResponseStatus(code, reasonPhrase);
    }

    /**
     * Parses the specified HTTP status line into a {@link HttpResponseStatus}. The expected formats of the line are:
     * <ul>
     * <li>{@code statusCode} (e.g. 200)</li>
     * <li>{@code statusCode} {@code reasonPhrase} (e.g. 404 Not Found)</li>
     * </ul>
     *
     * @throws IllegalArgumentException if the specified status line is malformed
     */
    public static HttpResponseStatus parseLine(CharSequence line) {
        return (line instanceof AsciiString) ? parseLine((AsciiString) line) : parseLine(line.toString());
    }

    /**
     * Parses the specified HTTP status line into a {@link HttpResponseStatus}. The expected formats of the line are:
     * <ul>
     * <li>{@code statusCode} (e.g. 200)</li>
     * <li>{@code statusCode} {@code reasonPhrase} (e.g. 404 Not Found)</li>
     * </ul>
     *
     * @throws IllegalArgumentException if the specified status line is malformed
     */
    public static HttpResponseStatus parseLine(String line) {
        try {
            int space = line.indexOf(' ');
            return space == -1 ? valueOf(parseInt(line)) :
                    valueOf(parseInt(line.substring(0, space)), line.substring(space + 1));
        } catch (Exception e) {
            throw new IllegalArgumentException("malformed status line: " + line, e);
        }
    }

    /**
     * Parses the specified HTTP status line into a {@link HttpResponseStatus}. The expected formats of the line are:
     * <ul>
     * <li>{@code statusCode} (e.g. 200)</li>
     * <li>{@code statusCode} {@code reasonPhrase} (e.g. 404 Not Found)</li>
     * </ul>
     *
     * @throws IllegalArgumentException if the specified status line is malformed
     */
    public static HttpResponseStatus parseLine(AsciiString line) {
        try {
            int space = line.forEachByte(FIND_ASCII_SPACE);
            return space == -1 ? valueOf(line.parseInt()) : valueOf(line.parseInt(0, space), line.toString(space + 1));
        } catch (Exception e) {
            throw new IllegalArgumentException("malformed status line: " + line, e);
        }
    }

    private final int code;
    private final AsciiString codeAsText;
    private HttpStatusClass codeClass;

    private final String reasonPhrase;
    private final byte[] bytes;

    /**
     * Creates a new instance with the specified {@code code} and the auto-generated default reason phrase.
     */
    private HttpResponseStatus(int code) {
        this(code, HttpStatusClass.valueOf(code).defaultReasonPhrase() + " (" + code + ')', false);
    }

    /**
     * Creates a new instance with the specified {@code code} and its {@code reasonPhrase}.
     */
    public HttpResponseStatus(int code, String reasonPhrase) {
        this(code, reasonPhrase, false);
    }

    private HttpResponseStatus(int code, String reasonPhrase, boolean bytes) {
        checkPositiveOrZero(code, "code");
        ObjectUtil.checkNotNull(reasonPhrase, "reasonPhrase");

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
        String codeString = Integer.toString(code);
        codeAsText = new AsciiString(codeString);
        this.reasonPhrase = reasonPhrase;
        if (bytes) {
            this.bytes = (codeString + ' ' + reasonPhrase).getBytes(CharsetUtil.US_ASCII);
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
        return codeAsText;
    }

    /**
     * Returns the reason phrase of this {@link HttpResponseStatus}.
     */
    public String reasonPhrase() {
        return reasonPhrase;
    }

    /**
     * Returns the class of this {@link HttpResponseStatus}
     */
    public HttpStatusClass codeClass() {
        HttpStatusClass type = this.codeClass;
        if (type == null) {
            this.codeClass = type = HttpStatusClass.valueOf(code);
        }
        return type;
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
        return new StringBuilder(reasonPhrase.length() + 4)
            .append(codeAsText)
            .append(' ')
            .append(reasonPhrase)
            .toString();
    }

    void encode(ByteBuf buf) {
        if (bytes == null) {
            ByteBufUtil.copy(codeAsText, buf);
            buf.writeByte(SP);
            buf.writeCharSequence(reasonPhrase, CharsetUtil.US_ASCII);
        } else {
            buf.writeBytes(bytes);
        }
    }
}
