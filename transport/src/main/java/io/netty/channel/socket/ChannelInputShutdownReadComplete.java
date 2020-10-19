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
package io.netty.channel.socket;

/**
 * User event that signifies the channel's input side is shutdown, and we tried to shut it down again. This typically
 * indicates that there is no more data to read.
 */
public final class ChannelInputShutdownReadComplete {
    public static final ChannelInputShutdownReadComplete INSTANCE = new ChannelInputShutdownReadComplete();

    private ChannelInputShutdownReadComplete() {
    }
}
