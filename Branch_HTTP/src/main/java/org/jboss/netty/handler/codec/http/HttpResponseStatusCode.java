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
public class HttpResponseStatusCode {
    public static final HttpResponseStatusCode CONTINUE = new HttpResponseStatusCode(100, "Continue");

    public static final HttpResponseStatusCode SWITCHING_PROTOCOLS = new HttpResponseStatusCode(101, "Switching Protocols");

    public static final HttpResponseStatusCode OK = new HttpResponseStatusCode(200, "OK");

    public static final HttpResponseStatusCode CREATED = new HttpResponseStatusCode(201, "Created");

    public static final HttpResponseStatusCode ACCEPTED = new HttpResponseStatusCode(202, "Accepted");

    public static final HttpResponseStatusCode NON_AUTHORITATIVE_INFORMATION = new HttpResponseStatusCode(203, "Non-Authoritative Information");

    public static final HttpResponseStatusCode NO_CONTENT = new HttpResponseStatusCode(204, "No Content");

    public static final HttpResponseStatusCode RESET_CONTENT = new HttpResponseStatusCode(205, "Reset Content");

    public static final HttpResponseStatusCode PARTIAL_CONTENT = new HttpResponseStatusCode(206, "Partial Content");

    public static final HttpResponseStatusCode MULTIPLE_CHOICES = new HttpResponseStatusCode(300, "Multiple Choices");

    public static final HttpResponseStatusCode MOVED_PERMANENTLY = new HttpResponseStatusCode(301, "Moved Permanently");

    public static final HttpResponseStatusCode FOUND = new HttpResponseStatusCode(302, "Found");

    public static final HttpResponseStatusCode SEE_OTHER = new HttpResponseStatusCode(303, "See Other");

    public static final HttpResponseStatusCode NOT_MODIFIED = new HttpResponseStatusCode(304, "Not Modified");

    public static final HttpResponseStatusCode USE_PROXY = new HttpResponseStatusCode(305, "Use Proxy");

    public static final HttpResponseStatusCode TEMPORARY_REDIRECT = new HttpResponseStatusCode(307, "Temporary Redirect");

    public static final HttpResponseStatusCode BAD_REQUEST = new HttpResponseStatusCode(400, "Bad Request");

    public static final HttpResponseStatusCode UNUATHORIZED = new HttpResponseStatusCode(401, "Unauthorized");

    public static final HttpResponseStatusCode PAYMENT_REQUIRED = new HttpResponseStatusCode(402, "Payment Required");

    public static final HttpResponseStatusCode FORBIDDEN = new HttpResponseStatusCode(403, "Forbidden");

    public static final HttpResponseStatusCode NOT_FOUND = new HttpResponseStatusCode(404, "Not Found");

    public static final HttpResponseStatusCode METHOD_NOT_ALLOWED = new HttpResponseStatusCode(405, "Method Not Allowed");

    public static final HttpResponseStatusCode NOT_ACCEPTABLE = new HttpResponseStatusCode(406, "Not Acceptable");

    public static final HttpResponseStatusCode PROXY_AUTHENTICATION_REQUIRED = new HttpResponseStatusCode(407, "Proxy Authentication Required");

    public static final HttpResponseStatusCode REQUEST_TIMEOUT = new HttpResponseStatusCode(408, "Request Timeout");

    public static final HttpResponseStatusCode CONFLICT = new HttpResponseStatusCode(409, "Conflict");

    public static final HttpResponseStatusCode GONE = new HttpResponseStatusCode(410, "Gone");

    public static final HttpResponseStatusCode LENGTH_REQUIRED = new HttpResponseStatusCode(411, "Length Required");

    public static final HttpResponseStatusCode PRECONDITION_FAILED = new HttpResponseStatusCode(412, "Precondition Failed");

    public static final HttpResponseStatusCode REQUEST_ENTITY_TOO_LARGE = new HttpResponseStatusCode(413, "Request Entity Too Large");

    public static final HttpResponseStatusCode REQUEST_URI_TOO_LONG = new HttpResponseStatusCode(414, "Request-URI Too Long");

    public static final HttpResponseStatusCode UNSUPPORTED_MEDIA_TYPE = new HttpResponseStatusCode(415, "Unsupported Media Type");

    public static final HttpResponseStatusCode REQUESTED_RANGE_NOT_SATISFIABLE = new HttpResponseStatusCode(416, "Requested Range Not Satisfiable");

    public static final HttpResponseStatusCode EXPECTATION_FAILED = new HttpResponseStatusCode(417, "Expectation Failed");

    public static final HttpResponseStatusCode INTERNAL_SERVER_ERROR = new HttpResponseStatusCode(500, "Internal Server Error");

    public static final HttpResponseStatusCode NOT_IMPLEMENTED = new HttpResponseStatusCode(501, "Not Implemented");

    public static final HttpResponseStatusCode BAD_GATEWAY = new HttpResponseStatusCode(502, "Bad Gateway");

    public static final HttpResponseStatusCode SERVICE_UNAVAILABLE = new HttpResponseStatusCode(503, "Service Unavailable");

    public static final HttpResponseStatusCode GATEWAY_TIMEOUT = new HttpResponseStatusCode(504, "Gateway Timeout");

    public static final HttpResponseStatusCode HTTP_VERSION_NOT_SUPPORTED = new HttpResponseStatusCode(505, "HTTP Version Not Supported");;

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

    private int code;

    private String description;

    public HttpResponseStatusCode(int code, String description) {
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
