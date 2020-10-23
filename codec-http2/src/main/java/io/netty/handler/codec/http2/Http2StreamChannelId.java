/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.channel.ChannelId;

/**
 * ChannelId implementation which is used by our {@link Http2StreamChannel} implementation.
 */
final class Http2StreamChannelId implements ChannelId {
    private static final long serialVersionUID = -6642338822166867585L;

    private final int id;
    private final ChannelId parentId;

    Http2StreamChannelId(ChannelId parentId, int id) {
        this.parentId = parentId;
        this.id = id;
    }

    @Override
    public String asShortText() {
        return parentId.asShortText() + '/' + id;
    }

    @Override
    public String asLongText() {
        return parentId.asLongText() + '/' + id;
    }

    @Override
    public int compareTo(ChannelId o) {
        if (o instanceof Http2StreamChannelId) {
            Http2StreamChannelId otherId = (Http2StreamChannelId) o;
            int res = parentId.compareTo(otherId.parentId);
            if (res == 0) {
                return id - otherId.id;
            } else {
                return res;
            }
        }
        return parentId.compareTo(o);
    }

    @Override
    public int hashCode() {
        return id * 31 + parentId.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Http2StreamChannelId)) {
            return false;
        }
        Http2StreamChannelId otherId = (Http2StreamChannelId) obj;
        return id == otherId.id && parentId.equals(otherId.parentId);
    }

    @Override
    public String toString() {
        return asShortText();
    }
}
