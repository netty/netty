/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * {@link ChannelConnector} implementation which uses a {@link Bootstrap}.
 */
public final class BootstrapChannelConnector implements ChannelConnector {
    private static final ChannelHandler DUMMY_HANDLER = new DummyHandler();

    /**
     * This is package-private so we can support {@link SimpleChannelPool#connectChannel(Bootstrap)}. This will removed
     * once the API can be changed in later releases.
     */
    final Bootstrap bootstrap;

    public BootstrapChannelConnector(Bootstrap bootstrap) {
        // Clone the original Bootstrap as we want to set our own handler
        this.bootstrap = checkNotNull(bootstrap, "bootstrap").clone();
        // Add a dummy handler
        this.bootstrap.handler(DUMMY_HANDLER);
        this.bootstrap.option(ChannelOption.AUTO_READ, false);
    }

    @Override
    public Future<Channel> connect() {
        final Promise<Channel> promise = group().next().newPromise();
        ChannelFuture future = bootstrap.connect();
        if (future.isDone()) {
            notifyPromise(future, promise);
        } else {
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    notifyPromise(future, promise);
                }
            });
        }
        return promise;
    }

    private static void notifyPromise(ChannelFuture future, Promise<Channel> promise) {
        if (future.isSuccess()) {
            promise.setSuccess(future.channel());
        } else {
            promise.setFailure(future.cause());
        }
    }

    @Override
    public EventLoopGroup group() {
        return bootstrap.group();
    }

    @ChannelHandler.Sharable
    private static final class DummyHandler extends ChannelHandlerAdapter { }
}
