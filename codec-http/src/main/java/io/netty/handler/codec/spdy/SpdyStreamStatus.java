/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.spdy;

/**
 * The SPDY stream status code and its description.
 */
public class SpdyStreamStatus implements Comparable<SpdyStreamStatus> {

    /**
     * 1 Protocol Error
     */
    public static final SpdyStreamStatus PROTOCOL_ERROR =
        new SpdyStreamStatus(1, "PROTOCOL_ERROR");

    /**
     * 2 Invalid Stream
     */
    public static final SpdyStreamStatus INVALID_STREAM =
        new SpdyStreamStatus(2, "INVALID_STREAM");

    /**
     * 3 Refused Stream
     */
    public static final SpdyStreamStatus REFUSED_STREAM =
        new SpdyStreamStatus(3, "REFUSED_STREAM");

    /**
     * 4 Unsupported Version
     */
    public static final SpdyStreamStatus UNSUPPORTED_VERSION =
        new SpdyStreamStatus(4, "UNSUPPORTED_VERSION");

    /**
     * 5 Cancel
     */
    public static final SpdyStreamStatus CANCEL =
        new SpdyStreamStatus(5, "CANCEL");

    /**
     * 6 Internal Error
     */
    public static final SpdyStreamStatus INTERNAL_ERROR =
        new SpdyStreamStatus(6, "INTERNAL_ERROR");

    /**
     * 7 Flow Control Error
     */
    public static final SpdyStreamStatus FLOW_CONTROL_ERROR =
        new SpdyStreamStatus(7, "FLOW_CONTROL_ERROR");

    /**
     * 8 Stream In Use
     */
    public static final SpdyStreamStatus STREAM_IN_USE =
        new SpdyStreamStatus(8, "STREAM_IN_USE");

    /**
     * 9 Stream Already Closed
     */
    public static final SpdyStreamStatus STREAM_ALREADY_CLOSED =
        new SpdyStreamStatus(9, "STREAM_ALREADY_CLOSED");

    /**
     * 10 Invalid Credentials
     */
    public static final SpdyStreamStatus INVALID_CREDENTIALS =
        new SpdyStreamStatus(10, "INVALID_CREDENTIALS");

    /**
     * 11 Frame Too Large
     */
    public static final SpdyStreamStatus FRAME_TOO_LARGE =
        new SpdyStreamStatus(11, "FRAME_TOO_LARGE");

    /**
     * Returns the {@link SpdyStreamStatus} represented by the specified code.
     * If the specified code is a defined SPDY status code, a cached instance
     * will be returned.  Otherwise, a new instance will be returned.
     */
    public static SpdyStreamStatus valueOf(int code) {
        if (code == 0) {
            throw new IllegalArgumentException(
                    "0 is not a valid status code for a RST_STREAM");
        }

        switch (code) {
        case 1:
            return PROTOCOL_ERROR;
        case 2:
            return INVALID_STREAM;
        case 3:
            return REFUSED_STREAM;
        case 4:
            return UNSUPPORTED_VERSION;
        case 5:
            return CANCEL;
        case 6:
            return INTERNAL_ERROR;
        case 7:
            return FLOW_CONTROL_ERROR;
        case 8:
            return STREAM_IN_USE;
        case 9:
            return STREAM_ALREADY_CLOSED;
        case 10:
            return INVALID_CREDENTIALS;
        case 11:
            return FRAME_TOO_LARGE;
        }

        return new SpdyStreamStatus(code, "UNKNOWN (" + code + ')');
    }

    private final int code;

    private final String statusPhrase;

    /**
     * Creates a new instance with the specified {@code code} and its
     * {@code statusPhrase}.
     */
    public SpdyStreamStatus(int code, String statusPhrase) {
        if (code == 0) {
            throw new IllegalArgumentException(
                    "0 is not a valid status code for a RST_STREAM");
        }

        if (statusPhrase == null) {
            throw new NullPointerException("statusPhrase");
        }

        this.code = code;
        this.statusPhrase = statusPhrase;
    }

    /**
     * Returns the code of this status.
     */
    public int code() {
        return code;
    }

    /**
     * Returns the status phrase of this status.
     */
    public String statusPhrase() {
        return statusPhrase;
    }

    @Override
    public int hashCode() {
        return code();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SpdyStreamStatus)) {
            return false;
        }

        return code() == ((SpdyStreamStatus) o).code();
    }

    @Override
    public String toString() {
        return statusPhrase();
    }

    @Override
    public int compareTo(SpdyStreamStatus o) {
        return code() - o.code();
    }
}
