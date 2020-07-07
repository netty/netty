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
package io.netty.channel.uring;

public class IOUringCqe {
    private final long eventId;
    private final int res;
    private final long flags;

    public IOUringCqe(long eventId, int res, long flags) {
        this.eventId = eventId;
        this.res = res;
        this.flags = flags;
    }

    public long getEventId() {
        return this.eventId;
    }

    public int getRes() {
        return this.res;
    }

    public long getFlags() {
        return this.flags;
    }
}
