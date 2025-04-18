/*
 * Copyright 2021 The Netty Project
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
package io.netty.incubator.codec.http3;

/**
 * State of encoding or decoding for a stream following the <a
 * href="https://quicwg.org/base-drafts/draft-ietf-quic-http.html#name-http-message-exchanges">
 * HTTP message exchange semantics</a>
 */
interface Http3RequestStreamCodecState {
    /**
     * An implementation of {@link Http3RequestStreamCodecState} that managed no state.
     */
    Http3RequestStreamCodecState NO_STATE = new Http3RequestStreamCodecState() {
        @Override
        public boolean started() {
            return false;
        }

        @Override
        public boolean receivedFinalHeaders() {
            return false;
        }

        @Override
        public boolean terminated() {
            return false;
        }
    };

    /**
     * If any {@link Http3HeadersFrame} or {@link Http3DataFrame} has been received/sent on this stream.
     *
     * @return {@code true} if any {@link Http3HeadersFrame} or {@link Http3DataFrame} has been received/sent on this
     * stream.
     */
    boolean started();

    /**
     * If a final {@link Http3HeadersFrame} has been received/sent before {@link Http3DataFrame} starts.
     *
     * @return {@code true} if a final {@link Http3HeadersFrame} has been received/sent before {@link Http3DataFrame}
     * starts
     */
    boolean receivedFinalHeaders();

    /**
     * If no more frames are expected on this stream.
     *
     * @return {@code true} if no more frames are expected on this stream.
     */
    boolean terminated();
}
