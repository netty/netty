/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Http2StreamChannelBootstrapTest {

    @Test
    public void open0FailsPromiseOnHttp2MultiplexHandlerError() {
        Http2StreamChannelBootstrap bootstrap = new Http2StreamChannelBootstrap(mock(Channel.class));

        Http2MultiplexHandler handler = new Http2MultiplexHandler(mock(ChannelHandler.class));
        EventExecutor executor = mock(EventExecutor.class);
        when(executor.inEventLoop()).thenReturn(true);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.executor()).thenReturn(executor);
        when(ctx.handler()).thenReturn(handler);

        Promise<Http2StreamChannel> promise = new DefaultPromise(mock(EventExecutor.class));
        bootstrap.open0(ctx, promise);
        assertThat(promise.isDone(), is(true));
        assertThat(promise.cause(), is(instanceOf(IllegalStateException.class)));
    }
}
