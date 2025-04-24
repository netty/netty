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

/**
 * Generates and hands over the next stream id to use for a QUIC stream.
 */
final class QuicStreamIdGenerator {
    private long nextBidirectionalStreamId;
    private long nextUnidirectionalStreamId;

    QuicStreamIdGenerator(boolean server) {
        // See https://quicwg.org/base-drafts/rfc9000.html#name-stream-types-and-identifier
        nextBidirectionalStreamId = server ? 1 : 0;
        nextUnidirectionalStreamId = server ? 3 : 2;
    }

    long nextStreamId(boolean bidirectional) {
        if (bidirectional) {
            long stream = nextBidirectionalStreamId;
            nextBidirectionalStreamId += 4;
            return stream;
        }
        long stream = nextUnidirectionalStreamId;
        nextUnidirectionalStreamId += 4;
        return stream;
    }
}
