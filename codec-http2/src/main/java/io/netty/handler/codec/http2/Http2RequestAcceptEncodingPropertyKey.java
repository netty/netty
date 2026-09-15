/*
 * Copyright 2026 The Netty Project
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
 * A shared {@link Http2Connection.PropertyKey} used to capture and retrieve the {@code accept-encoding}
 * header of a request on its {@link Http2Stream}, so that it can be read again when the response is
 * written on the same stream.
 * <p>
 * A single instance of this class must be shared between the inbound frame listener that captures the
 * value and the outbound encoder that reads it, so that both sides agree on the same
 * {@link Http2Connection.PropertyKey}.
 */
public final class Http2RequestAcceptEncodingPropertyKey {

    private final Http2Connection.PropertyKey key;

    /**
     * Create a new instance.
     *
     * @param connection the connection whose streams will carry the captured {@code accept-encoding} value
     */
    public Http2RequestAcceptEncodingPropertyKey(Http2Connection connection) {
        this.key = connection.newKey();
        connection.addListener(new Http2ConnectionAdapter() {
            @Override
            public void onStreamRemoved(Http2Stream stream) {
                stream.removeProperty(key);
            }
        });
    }

    /**
     * Returns the {@code accept-encoding} value previously captured for the given stream, or {@code null}
     * if none was captured (or {@code stream} is {@code null}).
     */
    CharSequence acceptEncoding(Http2Stream stream) {
        return stream == null ? null : (CharSequence) stream.getProperty(key);
    }

    /**
     * Stores the {@code accept-encoding} value for the given stream.
     */
    void setAcceptEncoding(Http2Stream stream, CharSequence acceptEncoding) {
        stream.setProperty(key, acceptEncoding);
    }
}
