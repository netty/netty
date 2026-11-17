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

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

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
         * <p>
         * This is typically used for tokens from Retry packets; see
         * {@link QuicTokenHandler#validateToken(ByteBuf, InetSocketAddress, ByteBuf)}.
         *
         * @param offset    the start index of the ODCID in the token.
         * @return          the validation result.
         */
        public static TokenValidationResult odcidFromToken(int offset) {
            return new TokenValidationResult(checkPositiveOrZero(offset, "offset"), false);
        }

        /**
         * Returns a result that indicates the token is valid and the ODCID should be taken from the current
         * destination connection id of the Initial packet.
         * <p>
         * This is typically used for tokens from NEW_TOKEN frames; see
         * {@link QuicTokenHandler#validateToken(ByteBuf, InetSocketAddress, ByteBuf)}.
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
     * Validate the token and return the offset, {@code -1} is returned if the token is not valid. The returned offset
     * identifies where the ODCID starts in the token. Implementations that support tokens from NEW_TOKEN frames should
     * override {@link #validateToken(ByteBuf, InetSocketAddress, ByteBuf)}.
     *
     * @param token     the {@link ByteBuf} that contains the token. The caller retains ownership of the buffer:
     *                  implementations must not release it and must retain, duplicate or copy it before using it after
     *                  this method returns.
     * @param address   the {@link InetSocketAddress} of the sender.
     * @return          the start index after the token or {@code -1} if the token was not valid.
     */
    int validateToken(ByteBuf token, InetSocketAddress address);

    /**
     * Validate the token and return a structured result that determines how the ODCID should be derived.
     * <p>
     * RFC 9000 distinguishes tokens sent in Retry packets from tokens sent in NEW_TOKEN frames, and requires token
     * construction to let the server identify how the token was provided to the client; see RFC 9000 Sections 8.1.1,
     * 8.1.2 and 8.1.3.
     * <p>
     * A token from a Retry packet, as described in RFC 9000 Section 8.1.2 and carried by the Retry packet in
     * Section 17.2.5, validates the same connection attempt after the server selected a new connection id. In this
     * case the result should identify the original destination connection id from the client's first Initial packet.
     * Use {@link TokenValidationResult#odcidFromToken(int)} if the token stores that connection id as a suffix, which
     * is the convention used by the legacy {@link #validateToken(ByteBuf, InetSocketAddress)} method.
     * <p>
     * A token from a NEW_TOKEN frame, as described in RFC 9000 Section 8.1.3, validates a future connection attempt.
     * No Retry packet has been sent for that new attempt, so the original destination connection id is the destination
     * connection id of the current Initial packet as described in RFC 9000 Section 7.2. Use
     * {@link TokenValidationResult#odcidFromDestinationConnectionId()} for this case.
     *
     * @param token     the {@link ByteBuf} that contains the token. The caller retains ownership of the buffer:
     *                  implementations must not release it and must retain, duplicate or copy it before using it after
     *                  this method returns.
     * @param address   the {@link InetSocketAddress} of the sender.
     * @param dcid      the destination connection id of the current Initial packet. The caller retains ownership of the
     *                  buffer: implementations must not release it and must retain, duplicate or copy it before using
     *                  it after this method returns.
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
