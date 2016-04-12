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
 * Builds an {@link InboundHttp2ToHttpPriorityAdapter}.
 */
@UnstableApi
public final class InboundHttp2ToHttpPriorityAdapterBuilder
        extends AbstractInboundHttp2ToHttpAdapterBuilder<InboundHttp2ToHttpPriorityAdapter,
                                                         InboundHttp2ToHttpPriorityAdapterBuilder> {

    /**
     * Creates a new {@link InboundHttp2ToHttpPriorityAdapter} builder for the specified
     * {@link Http2Connection}.
     *
     * @param connection the object which will provide connection notification events
     *                   for the current connection
     */
    public InboundHttp2ToHttpPriorityAdapterBuilder(Http2Connection connection) {
        super(connection);
    }

    @Override
    public InboundHttp2ToHttpPriorityAdapterBuilder maxContentLength(int maxContentLength) {
        return super.maxContentLength(maxContentLength);
    }

    @Override
    public InboundHttp2ToHttpPriorityAdapterBuilder validateHttpHeaders(boolean validate) {
        return super.validateHttpHeaders(validate);
    }

    @Override
    public InboundHttp2ToHttpPriorityAdapterBuilder propagateSettings(boolean propagate) {
        return super.propagateSettings(propagate);
    }

    @Override
    public InboundHttp2ToHttpPriorityAdapter build() {
        return super.build();
    }

    @Override
    protected InboundHttp2ToHttpPriorityAdapter build(Http2Connection connection,
                                                      int maxContentLength,
                                                      boolean validateHttpHeaders,
                                                      boolean propagateSettings) throws Exception {

        return new InboundHttp2ToHttpPriorityAdapter(connection, maxContentLength,
                                                     validateHttpHeaders, propagateSettings);
    }
}
