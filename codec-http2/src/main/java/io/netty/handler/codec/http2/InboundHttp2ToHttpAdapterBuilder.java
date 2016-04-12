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

import io.netty.util.internal.UnstableApi;

/**
 * Builds an {@link InboundHttp2ToHttpAdapter}.
 */
@UnstableApi
public final class InboundHttp2ToHttpAdapterBuilder
        extends AbstractInboundHttp2ToHttpAdapterBuilder<InboundHttp2ToHttpAdapter, InboundHttp2ToHttpAdapterBuilder> {

    /**
     * Creates a new {@link InboundHttp2ToHttpAdapter} builder for the specified {@link Http2Connection}.
     *
     * @param connection the object which will provide connection notification events
     *                   for the current connection
     */
    public InboundHttp2ToHttpAdapterBuilder(Http2Connection connection) {
        super(connection);
    }

    @Override
    public InboundHttp2ToHttpAdapterBuilder maxContentLength(int maxContentLength) {
        return super.maxContentLength(maxContentLength);
    }

    @Override
    public InboundHttp2ToHttpAdapterBuilder validateHttpHeaders(boolean validate) {
        return super.validateHttpHeaders(validate);
    }

    @Override
    public InboundHttp2ToHttpAdapterBuilder propagateSettings(boolean propagate) {
        return super.propagateSettings(propagate);
    }

    @Override
    public InboundHttp2ToHttpAdapter build() {
        return super.build();
    }

    @Override
    protected InboundHttp2ToHttpAdapter build(Http2Connection connection,
                                              int maxContentLength,
                                              boolean validateHttpHeaders,
                                              boolean propagateSettings) throws Exception {

        return new InboundHttp2ToHttpAdapter(connection, maxContentLength,
                                             validateHttpHeaders, propagateSettings);
    }
}
