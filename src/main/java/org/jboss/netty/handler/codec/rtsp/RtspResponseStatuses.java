/*
 * Copyright 2010 Red Hat, Inc.
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
package org.jboss.netty.handler.codec.rtsp;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * The status code and its description of a RTSP response.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://amitbhayani.blogspot.com/">Amit Bhayani</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2243 $, $Date: 2010-04-16 14:01:55 +0900 (Fri, 16 Apr 2010) $
 *
 * @apiviz.exclude
 */
public final class RtspResponseStatuses {

    /**
     * 100 Continue
     */
    public static final HttpResponseStatus CONTINUE = HttpResponseStatus.CONTINUE;

    /**
     * 200 OK
     */
    public static final HttpResponseStatus OK = HttpResponseStatus.OK;

    /**
     * 201 Created
     */
    public static final HttpResponseStatus CREATED = HttpResponseStatus.CREATED;

    /**
     * 250 Low on Storage Space
     */
    public static final HttpResponseStatus LOW_STORAGE_SPACE = new HttpResponseStatus(
            250, "Low on Storage Space");

    /**
     * 300 Multiple Choices
     */
    public static final HttpResponseStatus MULTIPLE_CHOICES = HttpResponseStatus.MULTIPLE_CHOICES;

    /**
     * 301 Moved Permanently
     */
    public static final HttpResponseStatus MOVED_PERMANENTLY = HttpResponseStatus.MOVED_PERMANENTLY;

    /**
     * 302 Moved Temporarily
     */
    public static final HttpResponseStatus MOVED_TEMPORARILY = new HttpResponseStatus(
            302, "Moved Temporarily");
    /**
     * 304 Not Modified
     */
    public static final HttpResponseStatus NOT_MODIFIED = HttpResponseStatus.NOT_MODIFIED;

    /**
     * 305 Use Proxy
     */
    public static final HttpResponseStatus USE_PROXY = HttpResponseStatus.USE_PROXY;

    /**
     * 400 Bad Request
     */
    public static final HttpResponseStatus BAD_REQUEST = HttpResponseStatus.BAD_REQUEST;

    /**
     * 401 Unauthorized
     */
    public static final HttpResponseStatus UNAUTHORIZED = HttpResponseStatus.UNAUTHORIZED;

    /**
     * 402 Payment Required
     */
    public static final HttpResponseStatus PAYMENT_REQUIRED = HttpResponseStatus.PAYMENT_REQUIRED;

    /**
     * 403 Forbidden
     */
    public static final HttpResponseStatus FORBIDDEN = HttpResponseStatus.FORBIDDEN;

    /**
     * 404 Not Found
     */
    public static final HttpResponseStatus NOT_FOUND = HttpResponseStatus.NOT_FOUND;

    /**
     * 405 Method Not Allowed
     */
    public static final HttpResponseStatus METHOD_NOT_ALLOWED = HttpResponseStatus.METHOD_NOT_ALLOWED;

    /**
     * 406 Not Acceptable
     */
    public static final HttpResponseStatus NOT_ACCEPTABLE = HttpResponseStatus.NOT_ACCEPTABLE;

    /**
     * 407 Proxy Authentication Required
     */
    public static final HttpResponseStatus PROXY_AUTHENTICATION_REQUIRED = HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED;

    /**
     * 408 Request Timeout
     */
    public static final HttpResponseStatus REQUEST_TIMEOUT = HttpResponseStatus.REQUEST_TIMEOUT;

    /**
     * 410 Gone
     */
    public static final HttpResponseStatus GONE = HttpResponseStatus.GONE;

    /**
     * 411 Length Required
     */
    public static final HttpResponseStatus LENGTH_REQUIRED = HttpResponseStatus.LENGTH_REQUIRED;

    /**
     * 412 Precondition Failed
     */
    public static final HttpResponseStatus PRECONDITION_FAILED = HttpResponseStatus.PRECONDITION_FAILED;

    /**
     * 413 Request Entity Too Large
     */
    public static final HttpResponseStatus REQUEST_ENTITY_TOO_LARGE = HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;

    /**
     * 414 Request-URI Too Long
     */
    public static final HttpResponseStatus REQUEST_URI_TOO_LONG = HttpResponseStatus.REQUEST_URI_TOO_LONG;

    /**
     * 415 Unsupported Media Type
     */
    public static final HttpResponseStatus UNSUPPORTED_MEDIA_TYPE = HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE;

    /**
     * 451 Parameter Not Understood
     */
    public static final HttpResponseStatus PARAMETER_NOT_UNDERSTOOD = new HttpResponseStatus(
            451, "Parameter Not Understood");

    /**
     * 452 Conference Not Found
     */
    public static final HttpResponseStatus CONFERENCE_NOT_FOUND = new HttpResponseStatus(
            452, "Conference Not Found");

    /**
     * 453 Not Enough Bandwidth
     */
    public static final HttpResponseStatus NOT_ENOUGH_BANDWIDTH = new HttpResponseStatus(
            453, "Not Enough Bandwidth");

    /**
     * 454 Session Not Found
     */
    public static final HttpResponseStatus SESSION_NOT_FOUND = new HttpResponseStatus(
            454, "Session Not Found");

    /**
     * 455 Method Not Valid in This State
     */
    public static final HttpResponseStatus METHOD_NOT_VALID = new HttpResponseStatus(
            455, "Method Not Valid in This State");

    /**
     * 456 Header Field Not Valid for Resource
     */
    public static final HttpResponseStatus HEADER_FIELD_NOT_VALID = new HttpResponseStatus(
            456, "Header Field Not Valid for Resource");

    /**
     * 457 Invalid Range
     */
    public static final HttpResponseStatus INVALID_RANGE = new HttpResponseStatus(
            457, "Invalid Range");

    /**
     * 458 Parameter Is Read-Only
     */
    public static final HttpResponseStatus PARAMETER_IS_READONLY = new HttpResponseStatus(
            458, "Parameter Is Read-Only");

    /**
     * 459 Aggregate operation not allowed
     */
    public static final HttpResponseStatus AGGREGATE_OPERATION_NOT_ALLOWED = new HttpResponseStatus(
            459, "Aggregate operation not allowed");

    /**
     * 460 Only Aggregate operation allowed
     */
    public static final HttpResponseStatus ONLY_AGGREGATE_OPERATION_ALLOWED = new HttpResponseStatus(
            460, "Only Aggregate operation allowed");

    /**
     * 461 Unsupported transport
     */
    public static final HttpResponseStatus UNSUPPORTED_TRANSPORT = new HttpResponseStatus(
            461, "Unsupported transport");

    /**
     * 462 Destination unreachable
     */
    public static final HttpResponseStatus DESTINATION_UNREACHABLE = new HttpResponseStatus(
            462, "Destination unreachable");

    /**
     * 463 Key management failure
     */
    public static final HttpResponseStatus KEY_MANAGEMENT_FAILURE = new HttpResponseStatus(
            463, "Key management failure");

    /**
     * 500 Internal Server Error
     */
    public static final HttpResponseStatus INTERNAL_SERVER_ERROR = HttpResponseStatus.INTERNAL_SERVER_ERROR;

    /**
     * 501 Not Implemented
     */
    public static final HttpResponseStatus NOT_IMPLEMENTED = HttpResponseStatus.NOT_IMPLEMENTED;

    /**
     * 502 Bad Gateway
     */
    public static final HttpResponseStatus BAD_GATEWAY = HttpResponseStatus.BAD_GATEWAY;

    /**
     * 503 Service Unavailable
     */
    public static final HttpResponseStatus SERVICE_UNAVAILABLE = HttpResponseStatus.SERVICE_UNAVAILABLE;

    /**
     * 504 Gateway Timeout
     */
    public static final HttpResponseStatus GATEWAY_TIMEOUT = HttpResponseStatus.GATEWAY_TIMEOUT;

    /**
     * 505 RTSP Version not supported
     */
    public static final HttpResponseStatus RTSP_VERSION_NOT_SUPPORTED = new HttpResponseStatus(
            505, "RTSP Version not supported");

    /**
     * 551 Option not supported
     */
    public static final HttpResponseStatus OPTION_NOT_SUPPORTED = new HttpResponseStatus(
            551, "Option not supported");


    /**
     * Returns the {@link HttpResponseStatus} represented by the specified code.
     * If the specified code is a standard RTSP status code, a cached instance
     * will be returned.  Otherwise, a new instance will be returned.
     */
    public static HttpResponseStatus valueOf(int code) {
        switch (code) {
        case 250: return LOW_STORAGE_SPACE;
        case 302: return MOVED_TEMPORARILY;
        case 451: return PARAMETER_NOT_UNDERSTOOD;
        case 452: return CONFERENCE_NOT_FOUND;
        case 453: return NOT_ENOUGH_BANDWIDTH;
        case 454: return SESSION_NOT_FOUND;
        case 455: return METHOD_NOT_VALID;
        case 456: return HEADER_FIELD_NOT_VALID;
        case 457: return INVALID_RANGE;
        case 458: return PARAMETER_IS_READONLY;
        case 459: return AGGREGATE_OPERATION_NOT_ALLOWED;
        case 460: return ONLY_AGGREGATE_OPERATION_ALLOWED;
        case 461: return UNSUPPORTED_TRANSPORT;
        case 462: return DESTINATION_UNREACHABLE;
        case 463: return KEY_MANAGEMENT_FAILURE;
        case 505: return RTSP_VERSION_NOT_SUPPORTED;
        case 551: return OPTION_NOT_SUPPORTED;
        default:  return HttpResponseStatus.valueOf(code);
        }
    }

    private RtspResponseStatuses() {
        super();
    }
}
