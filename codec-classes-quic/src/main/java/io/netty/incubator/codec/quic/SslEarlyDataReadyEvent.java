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
package io.netty.incubator.codec.quic;


/**
 * Event which is fired once it's possible to send early data.
 * <p>
 * Users might call {@link io.netty.channel.Channel#write(Object)} to send early data. The data is automatically
 * flushed as part of the connection establishment.
 * Please be aware that early data may be replay-able and so may have other security concerns then other data.
 */
public final class SslEarlyDataReadyEvent {

    static final SslEarlyDataReadyEvent INSTANCE = new SslEarlyDataReadyEvent();

    private SslEarlyDataReadyEvent() { }
}
