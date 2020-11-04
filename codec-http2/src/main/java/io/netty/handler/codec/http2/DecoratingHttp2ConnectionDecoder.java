/*
 * Copyright 2015 The Netty Project
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

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.UnstableApi;

import java.util.List;

/**
 * Decorator around another {@link Http2ConnectionDecoder} instance.
 */
@UnstableApi
public class DecoratingHttp2ConnectionDecoder implements Http2ConnectionDecoder {
    private final Http2ConnectionDecoder delegate;

    public DecoratingHttp2ConnectionDecoder(Http2ConnectionDecoder delegate) {
        this.delegate = checkNotNull(delegate, "delegate");
    }

    @Override
    public void lifecycleManager(Http2LifecycleManager lifecycleManager) {
        delegate.lifecycleManager(lifecycleManager);
    }

    @Override
    public Http2Connection connection() {
        return delegate.connection();
    }

    @Override
    public Http2LocalFlowController flowController() {
        return delegate.flowController();
    }

    @Override
    public void frameListener(Http2FrameListener listener) {
        delegate.frameListener(listener);
    }

    @Override
    public Http2FrameListener frameListener() {
        return delegate.frameListener();
    }

    @Override
    public void decodeFrame(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Http2Exception {
        delegate.decodeFrame(ctx, in, out);
    }

    @Override
    public Http2Settings localSettings() {
        return delegate.localSettings();
    }

    @Override
    public boolean prefaceReceived() {
        return delegate.prefaceReceived();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
