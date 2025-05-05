/*
 * Copyright 2020 The Netty Project
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

import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

/**
 * All QUIC error codes identified by Quiche.
 * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/src/lib.rs#L335-L380">Error</a>
 */
enum QuicheError {
    BUFFER_TOO_SHORT(Quiche.QUICHE_ERR_BUFFER_TOO_SHORT, "QUICHE_ERR_BUFFER_TOO_SHORT"),
    UNKNOWN_VERSION(Quiche.QUICHE_ERR_UNKNOWN_VERSION, "QUICHE_ERR_UNKNOWN_VERSION"),
    INVALID_FRAME(Quiche.QUICHE_ERR_INVALID_FRAME, "QUICHE_ERR_INVALID_FRAME"),
    INVALID_PACKET(Quiche.QUICHE_ERR_INVALID_PACKET, "QUICHE_ERR_INVALID_PACKET"),
    INVALID_STATE(Quiche.QUICHE_ERR_INVALID_STATE, "QUICHE_ERR_INVALID_STATE"),
    INVALID_STREAM_STATE(Quiche.QUICHE_ERR_INVALID_STREAM_STATE, "QUICHE_ERR_INVALID_STREAM_STATE"),
    INVALID_TRANSPORT_PARAM(Quiche.QUICHE_ERR_INVALID_TRANSPORT_PARAM, "QUICHE_ERR_INVALID_TRANSPORT_PARAM"),
    CRYPTO_FAIL(Quiche.QUICHE_ERR_CRYPTO_FAIL, "QUICHE_ERR_CRYPTO_FAIL"),
    TLS_FAIL(Quiche.QUICHE_ERR_TLS_FAIL, "QUICHE_ERR_TLS_FAIL"),
    FLOW_CONTROL(Quiche.QUICHE_ERR_FLOW_CONTROL, "QUICHE_ERR_FLOW_CONTROL"),
    STREAM_LIMIT(Quiche.QUICHE_ERR_STREAM_LIMIT, "QUICHE_ERR_STREAM_LIMIT"),
    FINAL_SIZE(Quiche.QUICHE_ERR_FINAL_SIZE, "QUICHE_ERR_FINAL_SIZE"),
    CONGESTION_CONTROL(Quiche.QUICHE_ERR_CONGESTION_CONTROL, "QUICHE_ERR_CONGESTION_CONTROL"),
    STREAM_RESET(Quiche.QUICHE_ERR_STREAM_RESET, "STREAM_RESET"),
    STREAM_STOPPED(Quiche.QUICHE_ERR_STREAM_STOPPED, "STREAM_STOPPED"),
    ID_LIMIT(Quiche.QUICHE_ERR_ID_LIMIT, "ID_LIMIT"),
    QUT_OF_IDENTIFIERS(Quiche.QUICHE_ERR_OUT_OF_IDENTIFIERS, "OUT_OF_IDENTIFIERS"),
    KEY_UPDATE(Quiche.QUICHE_ERR_KEY_UPDATE, "KEY_UPDATE"),
    CRYPTO_BUFFER_EXCEEDED(Quiche.QUICHE_ERR_CRYPTO_BUFFER_EXCEEDED, "QUICHE_ERR_CRYPTO_BUFFER_EXCEEDED");

    private static final IntObjectMap<QuicheError> ERROR_MAP = new IntObjectHashMap<>();

    static {
        for (QuicheError errorCode : QuicheError.values()) {
            ERROR_MAP.put(errorCode.code(), errorCode);
        }
    }

    private final int code;
    private final String message;

    QuicheError(int code, String message) {
        this.code = code;
        this.message = message;
    }

    final int code() {
        return code;
    }

    final String message() {
        return message;
    }

    @Override
    public final String toString() {
        return String.format("QuicError{code=%d, message=%s}", code, message);
    }

    static QuicheError valueOf(int code) {
        final QuicheError errorCode = ERROR_MAP.get(code);
        if (errorCode == null) {
            throw new IllegalArgumentException("unknown " + QuicheError.class.getSimpleName() + " code: " + code);
        }
        return errorCode;
    }
}
