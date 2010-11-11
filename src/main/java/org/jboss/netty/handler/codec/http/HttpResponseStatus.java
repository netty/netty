/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http;

/**
 * The response code and its description of HTTP or its derived protocols, such as
 * <a href="http://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
 * <a href="http://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2286 $, $Date: 2010-05-27 21:34:44 +0900 (Thu, 27 May 2010) $
 *
 * @apiviz.exclude
 */
public class HttpResponseStatus implements Comparable<HttpResponseStatus> {

    /**
     * 100 Continue
     */
    public static final HttpResponseStatus CONTINUE = new HttpResponseStatus(100, "Continue");

    /**
     * 101 Switching Protocols
     */
    public static final HttpResponseStatus SWITCHING_PROTOCOLS = new HttpResponseStatus(101, "Switching Protocols");

    /**
     * 102 Processing (WebDAV, RFC2518)
     */
    public static final HttpResponseStatus PROCESSING = new HttpResponseStatus(102, "Processing");

    /**
     * 200 OK
     */
    public static final HttpResponseStatus OK = new HttpResponseStatus(200, "OK");

    /**
     * 201 Created
     */
    public static final HttpResponseStatus CREATED = new HttpResponseStatus(201, "Created");

    /**
     * 202 Accepted
     */
    public static final HttpResponseStatus ACCEPTED = new HttpResponseStatus(202, "Accepted");

    /**
     * 203 Non-Authoritative Information (since HTTP/1.1)
     */
    public static final HttpResponseStatus NON_AUTHORITATIVE_INFORMATION = new HttpResponseStatus(203, "Non-Authoritative Information");

    /**
     * 204 No Content
     */
    public static final HttpResponseStatus NO_CONTENT = new HttpResponseStatus(204, "No Content");

    /**
     * 205 Reset Content
     */
    public static final HttpResponseStatus RESET_CONTENT = new HttpResponseStatus(205, "Reset Content");

    /**
     * 206 Partial Content
     */
    public static final HttpResponseStatus PARTIAL_CONTENT = new HttpResponseStatus(206, "Partial Content");

    /**
     * 207 Multi-Status (WebDAV, RFC2518)
     */
    public static final HttpResponseStatus MULTI_STATUS = new HttpResponseStatus(207, "Multi-Status");

    /**
     * 300 Multiple Choices
     */
    public static final HttpResponseStatus MULTIPLE_CHOICES = new HttpResponseStatus(300, "Multiple Choices");

    /**
     * 301 Moved Permanently
     */
    public static final HttpResponseStatus MOVED_PERMANENTLY = new HttpResponseStatus(301, "Moved Permanently");

    /**
     * 302 Found
     */
    public static final HttpResponseStatus FOUND = new HttpResponseStatus(302, "Found");

    /**
     * 303 See Other (since HTTP/1.1)
     */
    public static final HttpResponseStatus SEE_OTHER = new HttpResponseStatus(303, "See Other");

    /**
     * 304 Not Modified
     */
    public static final HttpResponseStatus NOT_MODIFIED = new HttpResponseStatus(304, "Not Modified");

    /**
     * 305 Use Proxy (since HTTP/1.1)
     */
    public static final HttpResponseStatus USE_PROXY = new HttpResponseStatus(305, "Use Proxy");

    /**
     * 307 Temporary Redirect (since HTTP/1.1)
     */
    public static final HttpResponseStatus TEMPORARY_REDIRECT = new HttpResponseStatus(307, "Temporary Redirect");

    /**
     * 400 Bad Request
     */
    public static final HttpResponseStatus BAD_REQUEST = new HttpResponseStatus(400, "Bad Request");

    /**
     * 401 Unauthorized
     */
    public static final HttpResponseStatus UNAUTHORIZED = new HttpResponseStatus(401, "Unauthorized");

    /**
     * 402 Payment Required
     */
    public static final HttpResponseStatus PAYMENT_REQUIRED = new HttpResponseStatus(402, "Payment Required");

    /**
     * 403 Forbidden
     */
    public static final HttpResponseStatus FORBIDDEN = new HttpResponseStatus(403, "Forbidden");

    /**
     * 404 Not Found
     */
    public static final HttpResponseStatus NOT_FOUND = new HttpResponseStatus(404, "Not Found");

    /**
     * 405 Method Not Allowed
     */
    public static final HttpResponseStatus METHOD_NOT_ALLOWED = new HttpResponseStatus(405, "Method Not Allowed");

    /**
     * 406 Not Acceptable
     */
    public static final HttpResponseStatus NOT_ACCEPTABLE = new HttpResponseStatus(406, "Not Acceptable");

    /**
     * 407 Proxy Authentication Required
     */
    public static final HttpResponseStatus PROXY_AUTHENTICATION_REQUIRED = new HttpResponseStatus(407, "Proxy Authentication Required");

    /**
     * 408 Request Timeout
     */
    public static final HttpResponseStatus REQUEST_TIMEOUT = new HttpResponseStatus(408, "Request Timeout");

    /**
     * 409 Conflict
     */
    public static final HttpResponseStatus CONFLICT = new HttpResponseStatus(409, "Conflict");

    /**
     * 410 Gone
     */
    public static final HttpResponseStatus GONE = new HttpResponseStatus(410, "Gone");

    /**
     * 411 Length Required
     */
    public static final HttpResponseStatus LENGTH_REQUIRED = new HttpResponseStatus(411, "Length Required");

    /**
     * 412 Precondition Failed
     */
    public static final HttpResponseStatus PRECONDITION_FAILED = new HttpResponseStatus(412, "Precondition Failed");

    /**
     * 413 Request Entity Too Large
     */
    public static final HttpResponseStatus REQUEST_ENTITY_TOO_LARGE = new HttpResponseStatus(413, "Request Entity Too Large");

    /**
     * 414 Request-URI Too Long
     */
    public static final HttpResponseStatus REQUEST_URI_TOO_LONG = new HttpResponseStatus(414, "Request-URI Too Long");

    /**
     * 415 Unsupported Media Type
     */
    public static final HttpResponseStatus UNSUPPORTED_MEDIA_TYPE = new HttpResponseStatus(415, "Unsupported Media Type");

    /**
     * 416 Requested Range Not Satisfiable
     */
    public static final HttpResponseStatus REQUESTED_RANGE_NOT_SATISFIABLE = new HttpResponseStatus(416, "Requested Range Not Satisfiable");

    /**
     * 417 Expectation Failed
     */
    public static final HttpResponseStatus EXPECTATION_FAILED = new HttpResponseStatus(417, "Expectation Failed");

    /**
     * 422 Unprocessable Entity (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus UNPROCESSABLE_ENTITY = new HttpResponseStatus(422, "Unprocessable Entity");

    /**
     * 423 Locked (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus LOCKED = new HttpResponseStatus(423, "Locked");

    /**
     * 424 Failed Dependency (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus FAILED_DEPENDENCY = new HttpResponseStatus(424, "Failed Dependency");

    /**
     * 425 Unordered Collection (WebDAV, RFC3648)
     */
    public static final HttpResponseStatus UNORDERED_COLLECTION = new HttpResponseStatus(425, "Unordered Collection");

    /**
     * 426 Upgrade Required (RFC2817)
     */
    public static final HttpResponseStatus UPGRADE_REQUIRED = new HttpResponseStatus(426, "Upgrade Required");

    /**
     * 500 Internal Server Error
     */
    public static final HttpResponseStatus INTERNAL_SERVER_ERROR = new HttpResponseStatus(500, "Internal Server Error");

    /**
     * 501 Not Implemented
     */
    public static final HttpResponseStatus NOT_IMPLEMENTED = new HttpResponseStatus(501, "Not Implemented");

    /**
     * 502 Bad Gateway
     */
    public static final HttpResponseStatus BAD_GATEWAY = new HttpResponseStatus(502, "Bad Gateway");

    /**
     * 503 Service Unavailable
     */
    public static final HttpResponseStatus SERVICE_UNAVAILABLE = new HttpResponseStatus(503, "Service Unavailable");

    /**
     * 504 Gateway Timeout
     */
    public static final HttpResponseStatus GATEWAY_TIMEOUT = new HttpResponseStatus(504, "Gateway Timeout");

    /**
     * 505 HTTP Version Not Supported
     */
    public static final HttpResponseStatus HTTP_VERSION_NOT_SUPPORTED = new HttpResponseStatus(505, "HTTP Version Not Supported");

    /**
     * 506 Variant Also Negotiates (RFC2295)
     */
    public static final HttpResponseStatus VARIANT_ALSO_NEGOTIATES = new HttpResponseStatus(506, "Variant Also Negotiates");

    /**
     * 507 Insufficient Storage (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus INSUFFICIENT_STORAGE = new HttpResponseStatus(507, "Insufficient Storage");

    /**
     * 510 Not Extended (RFC2774)
     */
    public static final HttpResponseStatus NOT_EXTENDED = new HttpResponseStatus(510, "Not Extended");

    /**
     * Returns the {@link HttpResponseStatus} represented by the specified code.
     * If the specified code is a standard HTTP status code, a cached instance
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

    private final int code;

    private final String reasonPhrase;

    /**
     * Creates a new instance with the specified {@code code} and its
     * {@code reasonPhrase}.
     */
    public HttpResponseStatus(int code, String reasonPhrase) {
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
        this.reasonPhrase = reasonPhrase;
    }

    /**
     * Returns the code of this status.
     */
    public int getCode() {
        return code;
    }

    /**
     * Returns the reason phrase of this status.
     */
    public String getReasonPhrase() {
        return reasonPhrase;
    }

    @Override
    public int hashCode() {
        return getCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HttpResponseStatus)) {
            return false;
        }

        return getCode() == ((HttpResponseStatus) o).getCode();
    }

    public int compareTo(HttpResponseStatus o) {
        return getCode() - o.getCode();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(reasonPhrase.length() + 5);
        buf.append(code);
        buf.append(' ');
        buf.append(reasonPhrase);
        return buf.toString();
    }
}
