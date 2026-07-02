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

import io.netty.buffer.ByteBuf;

import java.net.InetSocketAddress;

/**
 * Handle token related operations.
 */
public interface QuicTokenHandler {

    /**
     * The result of a token validation.
     */
    final class TokenValidationResult {
        private static final TokenValidationResult INVALID_TOKEN = new TokenValidationResult(-1, false);
        private static final TokenValidationResult ODCID_FROM_DESTINATION_CONNECTION_ID =
                new TokenValidationResult(-1, true);

        private final int tokenOffset;
        private final boolean odcidFromDestinationConnectionId;

        private TokenValidationResult(int tokenOffset, boolean odcidFromDestinationConnectionId) {
            this.tokenOffset = tokenOffset;
            this.odcidFromDestinationConnectionId = odcidFromDestinationConnectionId;
        }

        /**
         * Returns a result that indicates the token is invalid.
         *
         * @return  the invalid token result.
         */
        public static TokenValidationResult invalidToken() {
            return INVALID_TOKEN;
        }

        /**
         * Returns a result that indicates the token is valid and the ODCID should be taken from the token suffix
         * starting at the specified offset.
         *
         * @param offset    the start index of the ODCID in the token.
         * @return          the validation result.
         */
        public static TokenValidationResult odcidFromToken(int offset) {
            if (offset < 0) {
                throw new IllegalArgumentException("offset must be >= 0");
            }
            return new TokenValidationResult(offset, false);
        }

        /**
         * Returns a result that indicates the token is valid and the ODCID should be taken from the current
         * destination connection id of the Initial packet.
         *
         * @return  the validation result.
         */
        public static TokenValidationResult odcidFromDestinationConnectionId() {
            return ODCID_FROM_DESTINATION_CONNECTION_ID;
        }

        /**
         * Returns {@code true} if the token is valid.
         *
         * @return  {@code true} if the token is valid.
         */
        public boolean isValid() {
            return this != INVALID_TOKEN;
        }

        /**
         * Returns {@code true} if the ODCID should be taken from the current destination connection id.
         *
         * @return  {@code true} if the ODCID should be taken from the current destination connection id.
         */
        public boolean usesDestinationConnectionIdAsOdcid() {
            return odcidFromDestinationConnectionId;
        }

        ByteBuf originalDestinationConnectionId(ByteBuf token, ByteBuf dcid) {
            if (!isValid()) {
                throw new IllegalStateException("token is not valid");
            }
            if (odcidFromDestinationConnectionId) {
                return dcid.slice();
            }
            if (tokenOffset > token.readableBytes()) {
                throw new IllegalArgumentException("offset " + tokenOffset + " exceeds token length "
                        + token.readableBytes());
            }
            return token.slice(tokenOffset, token.readableBytes() - tokenOffset);
        }
    }

    /**
     * Generate a new token for the given destination connection id and address. This token is written to {@code out}.
     * If no token should be generated and so no token validation should take place at all this method should return
     * {@code false}.
     *
     * @param out       {@link ByteBuf} into which the token will be written.
     * @param dcid      the destination connection id. The {@link ByteBuf#readableBytes()} will be at most
     *                  {@link Quic#MAX_CONN_ID_LEN}.
     * @param address   the {@link InetSocketAddress} of the sender.
     * @return          {@code true} if a token was written and so validation should happen, {@code false} otherwise.
     */
    boolean writeToken(ByteBuf out, ByteBuf dcid, InetSocketAddress address);

    /**
     * Validate the token and return the offset, {@code -1} is returned if the token is not valid.
     *
     * @param token     the {@link ByteBuf} that contains the token. The ownership is not transferred.
     * @param address   the {@link InetSocketAddress} of the sender.
     * @return          the start index after the token or {@code -1} if the token was not valid.
     */
    int validateToken(ByteBuf token, InetSocketAddress address);

    /**
     * Validate the token and return a structured result that determines how the ODCID should be derived.
     *
     * @param token     the {@link ByteBuf} that contains the token. The ownership is not transferred.
     * @param address   the {@link InetSocketAddress} of the sender.
     * @param dcid      the destination connection id of the current Initial packet.
     * @return          the validation result.
     */
    default TokenValidationResult validateToken(ByteBuf token, InetSocketAddress address, ByteBuf dcid) {
        int offset = validateToken(token, address);
        return offset == -1 ? TokenValidationResult.invalidToken() : TokenValidationResult.odcidFromToken(offset);
    }

    /**
     * Return the maximal token length.
     *
     * @return the maximal supported token length.
     */
    int maxTokenLength();
}
