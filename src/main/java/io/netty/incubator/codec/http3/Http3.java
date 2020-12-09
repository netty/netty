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
package io.netty.incubator.codec.http3;

import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicCodecBuilder;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;

/**
 * Contains utility methods that help to bootstrap server / clients with HTTP3 support.
 */
public final class Http3 {

    private Http3() {  }

    private static final byte[] H3_PROTOS = new byte[] {
            0x05, 'h', '3', '-', '2', '9',
            0x05, 'h', '3', '-', '3', '0',
            0x05, 'h', '3', '-', '3', '1',
            0x05, 'h', '3', '-', '3', '2'
    };

    /**
     * Returns the supported protocols for H3.
     *
     * @return the supported protocols.
     */
    public static byte[] supportedApplicationProtocols() {
        return H3_PROTOS.clone();
    }

    /**
     * <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2">
     *     Minimum number max unidirectional streams</a>.
     */
    // control-stream, qpack decoder stream, qpack encoder stream
    public static final int MIN_INITIAL_MAX_STREAMS_UNIDIRECTIONAL = 3;

    /**
     * <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2">
     *     Minimum max data for unidirectional streams</a>.
     */
    public static final int MIN_INITIAL_MAX_STREAM_DATA_UNIDIRECTIONAL = 1024;

    /**
     * Returns a new {@link QuicServerCodecBuilder} that has preconfigured for HTTP3.
     *
     * @return a pre-configured builder for HTTP3.
     */
    public static QuicServerCodecBuilder newQuicServerCodecBuilder() {
        return configure(new QuicServerCodecBuilder());
    }

    /**
     * Returns a new {@link QuicClientCodecBuilder} that has preconfigured for HTTP3.
     *
     * @return a pre-configured builder for HTTP3.
     */
    public static QuicClientCodecBuilder newQuicClientCodecBuilder() {
        return configure(new QuicClientCodecBuilder());
    }

    private static <T extends QuicCodecBuilder<T>> T configure(T builder) {
        return builder.initialMaxStreamsUnidirectional(MIN_INITIAL_MAX_STREAMS_UNIDIRECTIONAL)
                .initialMaxStreamDataUnidirectional(MIN_INITIAL_MAX_STREAM_DATA_UNIDIRECTIONAL)
                .applicationProtocols(supportedApplicationProtocols());
    }
}
