/*
 * Copyright 2016 The Netty Project
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

import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

/**
 * The default {@link Http2PingFrame} implementation.
 */
@UnstableApi
public class DefaultHttp2PingFrame implements Http2PingFrame {

    private final long content;
    private final boolean ack;

    public DefaultHttp2PingFrame(long content) {
        this(content, false);
    }

    public DefaultHttp2PingFrame(long content, boolean ack) {
        this.content = content;
        this.ack = ack;
    }

    @Override
    public boolean ack() {
        return ack;
    }

    @Override
    public String name() {
        return "PING";
    }

    @Override
    public long content() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Http2PingFrame)) {
            return false;
        }
        Http2PingFrame other = (Http2PingFrame) o;
        return ack == other.ack() &&  content == other.content();
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = hash * 31 + (ack ? 1 : 0);
        return hash;
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(content=" + content + ", ack=" + ack + ')';
    }
}
