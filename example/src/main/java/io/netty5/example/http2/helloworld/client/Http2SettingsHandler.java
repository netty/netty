/*
 * Copyright 2014 The Netty Project
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
package io.netty5.example.http2.helloworld.client;

import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.handler.codec.http2.Http2Settings;
import io.netty5.util.concurrent.Promise;

import java.util.concurrent.TimeUnit;

/**
 * Reads the first {@link Http2Settings} object and notifies a {@link Promise}
 */
public class Http2SettingsHandler extends SimpleChannelInboundHandler<Http2Settings> {
    private final Promise<Void> promise;

    /**
     * Create new instance
     *
     * @param promise Promise object used to notify when first settings are received
     */
    public Http2SettingsHandler(Promise<Void> promise) {
        this.promise = promise;
    }

    /**
     * Wait for this handler to be added after the upgrade to HTTP/2, and for initial preface
     * handshake to complete.
     *
     * @param timeout Time to wait
     * @param unit {@link TimeUnit} for {@code timeout}
     * @throws Exception if timeout or other failure occurs
     */
    public void awaitSettings(long timeout, TimeUnit unit) throws Exception {
        if (!promise.asFuture().awaitUninterruptibly(timeout, unit)) {
            throw new IllegalStateException("Timed out waiting for settings");
        }
        if (promise.isFailed()) {
            throw new RuntimeException(promise.cause());
        }
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, Http2Settings msg) throws Exception {
        promise.setSuccess(null);

        // Only care about the first settings message
        ctx.pipeline().remove(this);
    }
}
