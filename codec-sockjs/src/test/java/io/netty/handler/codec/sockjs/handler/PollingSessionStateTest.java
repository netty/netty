/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.handler;


import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultEventLoop;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.SockJsService;
import io.netty.handler.codec.sockjs.SockJsSessionContext;
import io.netty.handler.codec.sockjs.protocol.HeartbeatFrame;
import org.junit.Test;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PollingSessionStateTest {

    @Test
    public void onOpen() {
        final SockJsService service = mock(SockJsService.class);
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").sessionTimeout(1).build();
        final SockJsSessionContext sessionContext = mock(SockJsSessionContext.class);
        final ChannelHandlerContext ctx = inactiveCtx();

        pollingSessionState(service, config, ctx).onConnect(ctx, sessionContext);
        verify(service).onOpen(sessionContext);
    }

    @Test
    public void onClose() throws InterruptedException {
        final SockJsService service = mock(SockJsService.class);
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").sessionTimeout(1).build();
        final ChannelHandlerContext ctx = inactiveCtx();

        pollingSessionState(service, config, ctx).onConnect(ctx, mock(SockJsSessionContext.class));
        Thread.sleep(10);
        verify(service).onClose();
    }

    @Test
    public void heartbeat() throws InterruptedException {
        final SockJsService service = mock(SockJsService.class);
        final SockJsConfig config = SockJsConfig.withPrefix("/echo")
                .sessionTimeout(100000)
                .heartbeatInterval(1)
                .build();
        final Channel channel = mock(Channel.class);
        final ChannelHandlerContext ctx = activeCtx(channel);

        pollingSessionState(service, config, ctx).onConnect(ctx, mock(SockJsSessionContext.class));
        Thread.sleep(50);
        verify(channel, atLeastOnce()).writeAndFlush(any(HeartbeatFrame.class));
    }

    private static PollingSessionState pollingSessionState(final SockJsService service,
                                                           final SockJsConfig config,
                                                           final ChannelHandlerContext ctx) {
        when(service.config()).thenReturn(config);
        final SockJsSession session = new SockJsSession("1234", service);
        session.setOpenContext(ctx);
        final ConcurrentHashMap<String, SockJsSession> sessions = new ConcurrentHashMap<String, SockJsSession>();
        sessions.put(session.sessionId(), session);
        return new PollingSessionState(sessions, session);
    }

    private static ChannelHandlerContext inactiveCtx() {
        final Channel channel = mock(Channel.class);
        when(channel.isActive()).thenReturn(false);
        when(channel.isRegistered()).thenReturn(false);
        return ctx(channel);
    }

    private static ChannelHandlerContext activeCtx(final Channel channel) {
        when(channel.isActive()).thenReturn(true);
        when(channel.isRegistered()).thenReturn(true);
        return ctx(channel);
    }

    private static ChannelHandlerContext ctx(final Channel channel) {
        final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        final DefaultEventLoop eventLoop = new DefaultEventLoop(Executors.newSingleThreadExecutor());
        when(ctx.executor()).thenReturn(eventLoop);
        when(ctx.channel()).thenReturn(channel);
        return ctx;
    }

}
