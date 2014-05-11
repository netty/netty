/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

/**
 * Enumeration of all frame types defined by the HTTP/2 specification.
 */
public enum Http2FrameType {
    DATA((short) 0x0),
    HEADERS((short) 0x1),
    PRIORITY((short) 0x2),
    RST_STREAM((short) 0x3),
    SETTINGS((short) 0x4),
    PUSH_PROMISE((short) 0x5),
    PING((short) 0x6),
    GO_AWAY((short) 0x7),
    WINDOW_UPDATE((short) 0x8),
    CONTINUATION((short) 0x9),
    ALT_SVC((short) 0xA),
    BLOCKED((short) 0xB);

    /**
     * Create an array indexed by the frame type code for fast lookup of the enum value.
     */
    private static final Http2FrameType[] codeToTypeMap;
    static {
        int maxIndex = 0;
        for (Http2FrameType type : Http2FrameType.values()) {
            maxIndex = Math.max(maxIndex, type.typeCode());
        }
        codeToTypeMap = new Http2FrameType[maxIndex + 1];
        for (Http2FrameType type : Http2FrameType.values()) {
            codeToTypeMap[type.typeCode()] = type;
        }
    }

    private final short code;

    Http2FrameType(short code) {
        this.code = code;
    }

    /**
     * Gets the code used to represent this frame type on the wire.
     */
    public short typeCode() {
        return code;
    }

    /**
     * Looks up the frame type by it's type code.
     */
    public static Http2FrameType forTypeCode(short typeCode) {
        Http2FrameType type = null;
        if (typeCode >= 0 && typeCode < codeToTypeMap.length) {
            type = codeToTypeMap[typeCode];
        }

        if (type == null) {
            throw new IllegalArgumentException("Unsupported typeCode: " + typeCode);
        }
        return type;
    }
}
