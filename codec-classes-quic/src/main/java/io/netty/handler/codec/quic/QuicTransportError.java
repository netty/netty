/*
 * Copyright 2024 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.quic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <a href="https://www.rfc-editor.org/rfc/rfc9000.html#name-transport-error-codes">
 *     RFC9000 20.1. Transport Error Codes</a>
 */
public final class QuicTransportError {

    /**
     * An endpoint uses this with CONNECTION_CLOSE to signal that the connection is being closed abruptly in the
     * absence of any error.
     */
    public static final QuicTransportError NO_ERROR =
            new QuicTransportError(0x0, "NO_ERROR");

    /**
     * The endpoint encountered an internal error and cannot continue with the connection.
     */
    public static final QuicTransportError INTERNAL_ERROR =
            new QuicTransportError(0x1, "INTERNAL_ERROR");

    /**
     * The server refused to accept a new connection.
     */
    public static final QuicTransportError CONNECTION_REFUSED =
            new QuicTransportError(0x2, "CONNECTION_REFUSED");

    /**
     * An endpoint received more data than it permitted in its advertised data limits.
     */
    public static final QuicTransportError FLOW_CONTROL_ERROR =
            new QuicTransportError(0x3, "FLOW_CONTROL_ERROR");

    /**
     * An endpoint received a frame for a stream identifier that exceeded its advertised stream limit for the
     * corresponding stream type.
     */
    public static final QuicTransportError STREAM_LIMIT_ERROR =
            new QuicTransportError(0x4, "STREAM_LIMIT_ERROR");

    /**
     * An endpoint received a frame for a stream that was not in a state that permitted that frame.
     */
    public static final QuicTransportError STREAM_STATE_ERROR =
            new QuicTransportError(0x5, "STREAM_STATE_ERROR");

    /**
     * (1) An endpoint received a STREAM frame containing data that exceeded the previously established final size,
     * (2) an endpoint received a STREAM frame or a RESET_STREAM frame containing a final size that was lower than
     * the size of stream data that was already received, or (3) an endpoint received a STREAM frame or a RESET_STREAM
     * frame containing a different final size to the one already established.
     */
    public static final QuicTransportError FINAL_SIZE_ERROR =
            new QuicTransportError(0x6, "FINAL_SIZE_ERROR");

    /**
     * An endpoint received a frame that was badly formatted -- for instance, a frame of an unknown type or an ACK
     * frame that has more acknowledgment ranges than the remainder of the packet could carry.
     */
    public static final QuicTransportError FRAME_ENCODING_ERROR =
            new QuicTransportError(0x7, "FRAME_ENCODING_ERROR");

    /**
     * An endpoint received transport parameters that were badly formatted, included an invalid value, omitted a
     * mandatory transport parameter, included a forbidden transport parameter, or were otherwise in error.
     */
    public static final QuicTransportError TRANSPORT_PARAMETER_ERROR =
            new QuicTransportError(0x8, "TRANSPORT_PARAMETER_ERROR");

    /**
     * The number of connection IDs provided by the peer exceeds the advertised active_connection_id_limit.
     */
    public static final QuicTransportError CONNECTION_ID_LIMIT_ERROR =
            new QuicTransportError(0x9, "CONNECTION_ID_LIMIT_ERROR");

    /**
     * An endpoint detected an error with protocol compliance that was not covered by more specific error codes.
     */
    public static final QuicTransportError PROTOCOL_VIOLATION =
            new QuicTransportError(0xa, "PROTOCOL_VIOLATION");

    /**
     * A server received a client Initial that contained an invalid Token field.
     */
    public static final QuicTransportError INVALID_TOKEN =
            new QuicTransportError(0xb, "INVALID_TOKEN");

    /**
     * The application or application protocol caused the connection to be closed.
     */
    public static final QuicTransportError APPLICATION_ERROR =
            new QuicTransportError(0xc, "APPLICATION_ERROR");

    /**
     * An endpoint has received more data in CRYPTO frames than it can buffer.
     */
    public static final QuicTransportError CRYPTO_BUFFER_EXCEEDED =
            new QuicTransportError(0xd, "CRYPTO_BUFFER_EXCEEDED");

    /**
     * An endpoint detected errors in performing key updates.
     */
    public static final QuicTransportError KEY_UPDATE_ERROR =
            new QuicTransportError(0xe, "KEY_UPDATE_ERROR");

    /**
     * An endpoint has reached the confidentiality or integrity limit for the AEAD algorithm used by the given
     * connection.
     */
    public static final QuicTransportError AEAD_LIMIT_REACHED =
            new QuicTransportError(0xf, "AEAD_LIMIT_REACHED");

    /**
     * n endpoint has determined that the network path is incapable of supporting QUIC. An endpoint is unlikely to
     * receive a CONNECTION_CLOSE frame carrying this code except when the path does not support a large enough MTU.
     */
    public static final QuicTransportError NO_VIABLE_PATH =
            new QuicTransportError(0x10, "NO_VIABLE_PATH");

    private static final QuicTransportError[] INT_TO_ENUM_MAP;
    static {
        List<QuicTransportError> errorList = new ArrayList<>();
        errorList.add(NO_ERROR);
        errorList.add(INTERNAL_ERROR);
        errorList.add(CONNECTION_REFUSED);
        errorList.add(FLOW_CONTROL_ERROR);
        errorList.add(STREAM_LIMIT_ERROR);
        errorList.add(STREAM_STATE_ERROR);
        errorList.add(FINAL_SIZE_ERROR);
        errorList.add(FRAME_ENCODING_ERROR);
        errorList.add(TRANSPORT_PARAMETER_ERROR);
        errorList.add(CONNECTION_ID_LIMIT_ERROR);
        errorList.add(PROTOCOL_VIOLATION);
        errorList.add(INVALID_TOKEN);
        errorList.add(APPLICATION_ERROR);
        errorList.add(CRYPTO_BUFFER_EXCEEDED);
        errorList.add(KEY_UPDATE_ERROR);
        errorList.add(AEAD_LIMIT_REACHED);
        errorList.add(NO_VIABLE_PATH);

        // Crypto errors can have various codes.
        //
        // See https://www.rfc-editor.org/rfc/rfc9000.html#name-transport-error-codes:
        // The cryptographic handshake failed. A range of 256 values is reserved for carrying error codes specific to
        // the cryptographic handshake that is used. Codes for errors occurring when TLS is used for the cryptographic
        // handshake are described in Section 4.8 of [QUIC-TLS].
        for (int i = 0x0100; i <= 0x01ff; i++) {
            errorList.add(new QuicTransportError(i, "CRYPTO_ERROR"));
        }
        INT_TO_ENUM_MAP = errorList.toArray(new QuicTransportError[0]);
    }
    private final long code;
    private final String name;

    private QuicTransportError(long code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * Returns true if this is a {@code CRYPTO_ERROR}.
     */
    public boolean isCryptoError() {
        return code >= 0x0100 && code <= 0x01ff;
    }

    /**
     * Returns the name of the error as defined by RFC9000.
     *
     * @return name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the code for this error used on the wire as defined by RFC9000.
     */
    public long code() {
        return code;
    }

    public static QuicTransportError valueOf(long value) {
        if (value > 17) {
            value -= 0x0100;
        }

        if (value < 0 || value >= INT_TO_ENUM_MAP.length) {
            throw new IllegalArgumentException("Unknown error code value: " + value);
        }
        return INT_TO_ENUM_MAP[(int) value];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QuicTransportError quicError = (QuicTransportError) o;
        return code == quicError.code;
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }

    @Override
    public String toString() {
        return "QuicTransportError{" +
                "code=" + code +
                ", name='" + name + '\'' +
                '}';
    }
}
