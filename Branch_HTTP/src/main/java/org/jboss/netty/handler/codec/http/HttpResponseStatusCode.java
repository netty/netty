/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.codec.http;

import java.util.Map;
import java.util.HashMap;

/**
 * The response codes and descriptions to return in the response. see http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
 *
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public enum HttpResponseStatusCode {
    CONTINUE(100, "Continue"),

    SWITCHING_PROTOCOLS(101, "Switching Protocols"),

    OK(200, "OK"),

    CREATED(201, "Created"),

    ACCEPTED(202, "Accepted"),

    NON_AUTHORITATIVE_INFORMATION(203, "Non-Authoritative Information"),

    NO_CONTENT(204, "No Content"),

    RESET_CONTENT(205, "Reset Content"),

    PARTIAL_CONTENT(206, "Partial Content"),

    MULTIPLE_CHOICES(300, "Multiple Choices"),

    MOVED_PERMANENTLY(301, "Moved Permanently"),

    FOUND(302, "Found"),

    SEE_OTHER(303, "See Other"),

    NOT_MODIFIED(304, "Not Modified"),

    USE_PROXY(305, "Use Proxy"),

    TEMPORARY_REDIRECT(307, "Temporary Redirect"),

    BAD_REQUEST(400, "Bad Request"),

    UNUATHORIZED(401, "Unauthorized"),

    PAYMENT_REQUIRED(402, "Payment Required"),

    FORBIDDEN(403, "Forbidden"),

    NOT_FOUND(404, "Not Found"),

    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),

    NOT_ACCEPTABLE(406, "Not Acceptable"),

    PROXY_AUTHENTICATION_REQUIRED(407, "Proxy Authentication Required"),

    REQUEST_TIMEOUT(408, "Request Timeout"),

    CONFLICT(409, "Conflict"),

    GONE(410, "Gone"),

    LENGTH_REQUIRED(411, "Length Required"),

    PRECONDITION_FAILED(412, "Precondition Failed"),

    REQUEST_ENTITY_TOO_LARGE(413, "Request Entity Too Large"),

    REQUEST_URI_TOO_LONG(414, "Request-URI Too Long"),

    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),

    REQUESTED_RANGE_NOT_SATISFIABLE(416, "Requested Range Not Satisfiable"),

    EXPECTATION_FAILED(417, "Expectation Failed"),

    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),

    NOT_IMPLEMENTED(501, "Not Implemented"),

    BAD_GATEWAY(502, "Bad Gateway"),

    SERVICE_UNAVAILABLE(503, "Service Unavailable"),

    GATEWAY_TIMEOUT(504, "Gateway Timeout"),

    HTTP_VERSION_NOT_SUPPORTED(505, "HTTP Version Not Supported"),;

    private static Map<Integer, HttpResponseStatusCode> RESPONSE_MAP = new HashMap<Integer, HttpResponseStatusCode>();

    static {
        RESPONSE_MAP.put(CONTINUE.code, CONTINUE);
        RESPONSE_MAP.put(SWITCHING_PROTOCOLS.code, SWITCHING_PROTOCOLS);
        RESPONSE_MAP.put(OK.code, OK);
        RESPONSE_MAP.put(CREATED.code, CREATED);
        RESPONSE_MAP.put(ACCEPTED.code, ACCEPTED);
        RESPONSE_MAP.put(NON_AUTHORITATIVE_INFORMATION.code, NON_AUTHORITATIVE_INFORMATION);
        RESPONSE_MAP.put(NO_CONTENT.code, NO_CONTENT);
        RESPONSE_MAP.put(RESET_CONTENT.code, RESET_CONTENT);
        RESPONSE_MAP.put(PARTIAL_CONTENT.code, PARTIAL_CONTENT);
        RESPONSE_MAP.put(MULTIPLE_CHOICES.code, MULTIPLE_CHOICES);
        RESPONSE_MAP.put(MOVED_PERMANENTLY.code, MOVED_PERMANENTLY);
        RESPONSE_MAP.put(FOUND.code, FOUND);
        RESPONSE_MAP.put(SEE_OTHER.code, SEE_OTHER);
        RESPONSE_MAP.put(NOT_MODIFIED.code, NOT_MODIFIED);
        RESPONSE_MAP.put(USE_PROXY.code, USE_PROXY);
        RESPONSE_MAP.put(TEMPORARY_REDIRECT.code, TEMPORARY_REDIRECT);
        RESPONSE_MAP.put(BAD_REQUEST.code, BAD_REQUEST);
        RESPONSE_MAP.put(UNUATHORIZED.code, UNUATHORIZED);
        RESPONSE_MAP.put(PAYMENT_REQUIRED.code, PAYMENT_REQUIRED);
        RESPONSE_MAP.put(FORBIDDEN.code, FORBIDDEN);
        RESPONSE_MAP.put(NOT_FOUND.code, NOT_FOUND);
        RESPONSE_MAP.put(METHOD_NOT_ALLOWED.code, METHOD_NOT_ALLOWED);
        RESPONSE_MAP.put(NOT_ACCEPTABLE.code, NOT_ACCEPTABLE);
        RESPONSE_MAP.put(PROXY_AUTHENTICATION_REQUIRED.code, PROXY_AUTHENTICATION_REQUIRED);
        RESPONSE_MAP.put(REQUEST_TIMEOUT.code, REQUEST_TIMEOUT);
        RESPONSE_MAP.put(CONFLICT.code, CONFLICT);
        RESPONSE_MAP.put(GONE.code, GONE);
        RESPONSE_MAP.put(LENGTH_REQUIRED.code, LENGTH_REQUIRED);
        RESPONSE_MAP.put(PRECONDITION_FAILED.code, PRECONDITION_FAILED);
        RESPONSE_MAP.put(REQUEST_ENTITY_TOO_LARGE.code, REQUEST_ENTITY_TOO_LARGE);
        RESPONSE_MAP.put(REQUEST_URI_TOO_LONG.code, REQUEST_URI_TOO_LONG);
        RESPONSE_MAP.put(UNSUPPORTED_MEDIA_TYPE.code, UNSUPPORTED_MEDIA_TYPE);
        RESPONSE_MAP.put(REQUESTED_RANGE_NOT_SATISFIABLE.code, REQUESTED_RANGE_NOT_SATISFIABLE);
        RESPONSE_MAP.put(EXPECTATION_FAILED.code, EXPECTATION_FAILED);
        RESPONSE_MAP.put(INTERNAL_SERVER_ERROR.code, INTERNAL_SERVER_ERROR);
        RESPONSE_MAP.put(NOT_IMPLEMENTED.code, NOT_IMPLEMENTED);
        RESPONSE_MAP.put(BAD_GATEWAY.code, BAD_GATEWAY);
        RESPONSE_MAP.put(SERVICE_UNAVAILABLE.code, SERVICE_UNAVAILABLE);
        RESPONSE_MAP.put(GATEWAY_TIMEOUT.code, GATEWAY_TIMEOUT);
        RESPONSE_MAP.put(HTTP_VERSION_NOT_SUPPORTED.code, HTTP_VERSION_NOT_SUPPORTED);
    }

    public static HttpResponseStatusCode getResponseStatusCode(int id) {
        HttpResponseStatusCode code = RESPONSE_MAP.get(id);
        if (code == null) {
            throw new IllegalArgumentException("Invalid Response Status");
        }
        return code;
    }

    public static HttpResponseStatusCode getResponseStatusCode(int id, String reason) {
        HttpResponseStatusCode code = RESPONSE_MAP.get(id);
        if (code == null) {
            throw new IllegalArgumentException("Invalid Response Status");
        }
        code.description = reason;
        return code;
    }

    private int code;

    private String description;

    private HttpResponseStatusCode(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
