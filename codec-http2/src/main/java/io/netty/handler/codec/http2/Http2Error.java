/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

/**
 * All error codes identified by the HTTP/2 spec.
 */
public enum Http2Error {
    NO_ERROR(0x0),
    PROTOCOL_ERROR(0x1),
    INTERNAL_ERROR(0x2),
    FLOW_CONTROL_ERROR(0x3),
    SETTINGS_TIMEOUT(0x4),
    STREAM_CLOSED(0x5),
    FRAME_SIZE_ERROR(0x6),
    REFUSED_STREAM(0x7),
    CANCEL(0x8),
    COMPRESSION_ERROR(0x9),
    CONNECT_ERROR(0xA),
    ENHANCE_YOUR_CALM(0xB),
    INADEQUATE_SECURITY(0xC),
    HTTP_1_1_REQUIRED(0xD);

    private final long code;
    private static final Http2Error[] INT_TO_ENUM_MAP;
    static {
        Http2Error[] errors = values();
        Http2Error[] map = new Http2Error[errors.length];
        for (Http2Error error : errors) {
            map[(int) error.code()] = error;
        }
        INT_TO_ENUM_MAP = map;
    }

    Http2Error(long code) {
        this.code = code;
    }

    /**
     * Gets the code for this error used on the wire.
     */
    public long code() {
        return code;
    }

    public static Http2Error valueOf(long value) {
        return value >= INT_TO_ENUM_MAP.length || value < 0 ? null : INT_TO_ENUM_MAP[(int) value];
    }
}
