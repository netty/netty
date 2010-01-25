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
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Amit Bhayani (amit.bhayani@gmail.com)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
public class RtspResponseStatus extends HttpResponseStatus {

    /**
     * 250 Low on Storage Space
     */
    public static final RtspResponseStatus LOW_STORAGE_SPACE = new RtspResponseStatus(
            250, "Low on Storage Space");

    /**
     * 302 Moved Temporarily
     */
    public static final RtspResponseStatus MOVED_TEMPORARILY = new RtspResponseStatus(
            302, "Moved Temporarily");
    /**
     * 451 Parameter Not Understood
     */
    public static final RtspResponseStatus PARAMETER_NOT_UNDERSTOOD = new RtspResponseStatus(
            451, "Parameter Not Understood");

    /**
     * 452 Conference Not Found
     */
    public static final RtspResponseStatus CONFERENCE_NOT_FOUND = new RtspResponseStatus(
            452, "Conference Not Found");

    /**
     * 453 Not Enough Bandwidth
     */
    public static final RtspResponseStatus NOT_ENOUGH_BANDWIDTH = new RtspResponseStatus(
            453, "Not Enough Bandwidth");

    /**
     * 454 Session Not Found
     */
    public static final RtspResponseStatus SESSION_NOT_FOUND = new RtspResponseStatus(
            454, "Session Not Found");

    /**
     * 455 Method Not Valid in This State
     */
    public static final RtspResponseStatus METHOD_NOT_VALID = new RtspResponseStatus(
            455, "Method Not Valid in This State");

    /**
     * 456 Header Field Not Valid for Resource
     */
    public static final RtspResponseStatus HEADER_FIELD_NOT_VALID = new RtspResponseStatus(
            456, "Header Field Not Valid for Resource");

    /**
     * 457 Invalid Range
     */
    public static final RtspResponseStatus INVALID_RANGE = new RtspResponseStatus(
            457, "Invalid Range");

    /**
     * 458 Parameter Is Read-Only
     */
    public static final RtspResponseStatus PARAMETER_IS_READONLY = new RtspResponseStatus(
            458, "Parameter Is Read-Only");

    /**
     * 459 Aggregate operation not allowed
     */
    public static final RtspResponseStatus AGGREGATE_OPERATION_NOT_ALLOWED = new RtspResponseStatus(
            459, "Aggregate operation not allowed");

    /**
     * 460 Only Aggregate operation allowed
     */
    public static final RtspResponseStatus ONLY_AGGREGATE_OPERATION_ALLOWED = new RtspResponseStatus(
            460, "Only Aggregate operation allowed");

    /**
     * 461 Unsupported transport
     */
    public static final RtspResponseStatus UNSUPPORTED_TRANSPORT = new RtspResponseStatus(
            461, "Unsupported transport");

    /**
     * 462 Destination unreachable
     */
    public static final RtspResponseStatus DESTINATION_UNREACHABLE = new RtspResponseStatus(
            462, "Destination unreachable");

    /**
     * 463 Destination unreachable
     */
    public static final RtspResponseStatus KEY_MANAGEMENT_FAILURE = new RtspResponseStatus(
            463, "Key management failure");

    /**
     * 505 RTSP Version not supported
     */
    public static final RtspResponseStatus RTSP_VERSION_NOT_SUPPORTED = new RtspResponseStatus(
            505, "RTSP Version not supported");

    /**
     * 551 Option not supported
     */
    public static final RtspResponseStatus OPTION_NOT_SUPPORTED = new RtspResponseStatus(
            551, "Option not supported");


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

    public RtspResponseStatus(int code, String reasonPhrase) {
        super(code, reasonPhrase);
    }
}
