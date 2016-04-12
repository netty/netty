/*
 * Copyright 2015 The Netty Project
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

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.UnstableApi;

/**
 * Provides an extensibility point for users to define the validity of push requests.
 * @see <a href="https://tools.ietf.org/html/draft-ietf-httpbis-http2-17#section-8.2">[RFC http2], Section 8.2</a>.
 */
@UnstableApi
public interface Http2PromisedRequestVerifier {
    /**
     * Determine if a {@link Http2Headers} are authoritative for a particular {@link ChannelHandlerContext}.
     * @param ctx The context on which the {@code headers} where received on.
     * @param headers The headers to be verified.
     * @return {@code true} if the {@code ctx} is authoritative for the {@code headers}, {@code false} otherwise.
     * @see
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-http2-17#section-10.1">[RFC http2], Section 10.1</a>.
     */
    boolean isAuthoritative(ChannelHandlerContext ctx, Http2Headers headers);

    /**
     * Determine if a request is cacheable.
     * @param headers The headers for a push request.
     * @return {@code true} if the request associated with {@code headers} is known to be cacheable,
     * {@code false} otherwise.
     * @see <a href="https://tools.ietf.org/html/rfc7231#section-4.2.3">[RFC 7231], Section 4.2.3</a>.
     */
    boolean isCacheable(Http2Headers headers);

    /**
     * Determine if a request is safe.
     * @param headers The headers for a push request.
     * @return {@code true} if the request associated with {@code headers} is known to be safe,
     * {@code false} otherwise.
     * @see <a href="https://tools.ietf.org/html/rfc7231#section-4.2.1">[RFC 7231], Section 4.2.1</a>.
     */
    boolean isSafe(Http2Headers headers);

    /**
     * A default implementation of {@link Http2PromisedRequestVerifier} which always returns positive responses for
     * all verification challenges.
     */
    Http2PromisedRequestVerifier ALWAYS_VERIFY = new Http2PromisedRequestVerifier() {
        @Override
        public boolean isAuthoritative(ChannelHandlerContext ctx, Http2Headers headers) {
            return true;
        }

        @Override
        public boolean isCacheable(Http2Headers headers) {
            return true;
        }

        @Override
        public boolean isSafe(Http2Headers headers) {
            return true;
        }
    };
}
