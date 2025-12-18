/*
 * Copyright 2024 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;

/**
 * WebTransportObserver 的空实现，所有方法不做任何操作。
 */
public class NoopWebTransportObserver implements WebTransportObserver {

    public static final NoopWebTransportObserver INSTANCE = new NoopWebTransportObserver();

    protected NoopWebTransportObserver() {}

    @Override
    public void onSessionCreated(WebTransportSession session) {}

    @Override
    public void onSessionClosed(WebTransportSession session, Future<Void> future) {}

    @Override
    public void onStreamCreated(WebTransportStream stream) {}

    @Override
    public void onStreamClosed(WebTransportStream stream, Future<Void> future) {}

    @Override
    public void onDatagramReceived(WebTransportSession session, WebTransportDatagram datagram) {}

    @Override
    public void onDatagramSent(WebTransportSession session, WebTransportDatagram datagram) {}

    @Override
    public void onError(Channel channel, Throwable cause) {}
}
