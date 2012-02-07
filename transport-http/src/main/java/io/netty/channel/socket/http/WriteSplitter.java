/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.http;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;

/**
 * Provides functionality to split a provided ChannelBuffer into multiple fragments which fit
 * under a specified size threshold.
 */
final class WriteSplitter {

    public static List<ChannelBuffer> split(ChannelBuffer buffer,
            int splitThreshold) {
        int listSize = (int) ((float) buffer.readableBytes() / splitThreshold);
        ArrayList<ChannelBuffer> fragmentList =
                new ArrayList<ChannelBuffer>(listSize);

        if (buffer.readableBytes() > splitThreshold) {
            int slicePosition = buffer.readerIndex();
            while (slicePosition < buffer.writerIndex()) {
                int chunkSize =
                        Math.min(splitThreshold, buffer.writerIndex() -
                                slicePosition);
                ChannelBuffer chunk = buffer.slice(slicePosition, chunkSize);
                fragmentList.add(chunk);
                slicePosition += chunkSize;
            }
        } else {
            fragmentList.add(ChannelBuffers.wrappedBuffer(buffer));
        }

        return fragmentList;
    }

    private WriteSplitter() {
        // Unused
    }
}
