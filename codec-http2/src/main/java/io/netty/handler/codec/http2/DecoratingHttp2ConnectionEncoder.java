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

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A decorator around another {@link Http2ConnectionEncoder} instance.
 */
@UnstableApi
public class DecoratingHttp2ConnectionEncoder extends DecoratingHttp2FrameWriter implements Http2ConnectionEncoder {
    private final Http2ConnectionEncoder delegate;

    public DecoratingHttp2ConnectionEncoder(Http2ConnectionEncoder delegate) {
        super(delegate);
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
    public Http2RemoteFlowController flowController() {
        return delegate.flowController();
    }

    @Override
    public Http2FrameWriter frameWriter() {
        return delegate.frameWriter();
    }

    @Override
    public Http2Settings pollSentSettings() {
        return delegate.pollSentSettings();
    }

    @Override
    public void remoteSettings(Http2Settings settings) throws Http2Exception {
        delegate.remoteSettings(settings);
    }
}
