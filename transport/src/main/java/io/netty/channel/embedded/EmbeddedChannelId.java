/*
 * Copyright 2014 The Netty Project
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

package io.netty.channel.embedded;

import io.netty.channel.ChannelId;

/**
 * A dummy {@link ChannelId} implementation.
 */
final class EmbeddedChannelId implements ChannelId {

    private static final long serialVersionUID = -251711922203466130L;

    static final ChannelId INSTANCE = new EmbeddedChannelId();

    private EmbeddedChannelId() { }

    @Override
    public String asShortText() {
        return toString();
    }

    @Override
    public String asLongText() {
        return toString();
    }

    @Override
    public int compareTo(final ChannelId o) {
        if (o instanceof EmbeddedChannelId) {
            return 0;
        }

        return asLongText().compareTo(o.asLongText());
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof EmbeddedChannelId;
    }

    @Override
    public String toString() {
        return "embedded";
    }
}
