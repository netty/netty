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

    public static final HttpResponseStatusCode HTTP_VERSION_NOT_SUPPORTED = new HttpResponseStatusCode(505, "HTTP Version Not Supported");


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
