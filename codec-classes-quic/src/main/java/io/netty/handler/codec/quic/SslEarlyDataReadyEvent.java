/*
 * Copyright 2023 The Netty Project
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
package io.netty.handler.codec.quic;


/**
 * Event which is fired once it's possible to send early data on the client-side.
 * See <a href="https://www.rfc-editor.org/rfc/rfc8446#section-4.2.10">RFC8446 4.2.10 Early Data Indication</a>.
 * <p>
 * Users might call {@link io.netty.channel.Channel#writeAndFlush(Object)} or
 * {@link io.netty.channel.ChannelHandlerContext#writeAndFlush(Object)} to send early data.
 * Please be aware that early data may be replay-able and so may have other security concerns then other data.
 */
public final class SslEarlyDataReadyEvent {

    static final SslEarlyDataReadyEvent INSTANCE = new SslEarlyDataReadyEvent();

    private SslEarlyDataReadyEvent() { }
}
