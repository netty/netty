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
package io.netty.channel.embedded;

import io.netty.channel.ChannelId;
import io.netty.util.internal.MathUtil;

public class CustomChannelId implements ChannelId {

    private static final long serialVersionUID = 1L;

    private int id;

    CustomChannelId(int id) {
        this.id = id;
    }

    @Override
    public int compareTo(final ChannelId o) {
        if (o instanceof CustomChannelId) {
            return MathUtil.compare(id, ((CustomChannelId) o).id);
        }

        return asLongText().compareTo(o.asLongText());
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CustomChannelId && id == ((CustomChannelId) obj).id;
    }

    @Override
    public String toString() {
        return "CustomChannelId " + id;
    }

    @Override
    public String asShortText() {
        return toString();
    }

    @Override
    public String asLongText() {
        return toString();
    }

}
