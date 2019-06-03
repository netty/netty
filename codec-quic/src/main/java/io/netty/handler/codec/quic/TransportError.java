/*
 *
 *  * Copyright 2019 The Netty Project
 *  *
 *  * The Netty Project licenses this file to you under the Apache License,
 *  * version 2.0 (the "License"); you may not use this file except in compliance
 *  * with the License. You may obtain a copy of the License at:
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *
 */

package io.netty.handler.codec.quic;

//Section 20
public enum TransportError {

    NO_ERROR(0x0),
    INTERNAL_ERROR(0x1),
    SERVER_BUSY(0x2),
    FLOW_CONTROL_ERROR(0x3),
    STREAM_ID_ERROR(0x4),
    STREAM_STATE_ERROR(0x5),
    FINAL_OFFSET_ERROR(0x6),
    FRAME_ENCODING_ERROR(0x7),
    TRANSPORT_PARAMETER_ERROR(0x8),
    VERSION_NEGOTIATION_ERROR(0x9),
    PROTOCOL_VIOLATION(0xA);

    public static TransportError byErrorCode(byte errorCode) {
        for (TransportError transportError : TransportError.values()) {
            if (transportError.errorCode == errorCode) {
                return transportError;
            }
        }
        throw new UnsupportedOperationException("Error code " + errorCode + " not supported");
    }

    private byte errorCode;

    TransportError(int errorCode) {
        this.errorCode = (byte) errorCode;
    }

    public byte errorCode() {
        return errorCode;
    }
}
