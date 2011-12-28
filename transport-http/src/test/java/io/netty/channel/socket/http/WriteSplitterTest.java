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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import org.junit.Test;

/**
 * Tests write splitting capabilities
 */
public class WriteSplitterTest {

    private static final int SPLIT_THRESHOLD = 1024;

    @Test
    public void testSplit_bufferUnderThreshold() {
        ChannelBuffer buffer = createBufferWithContents(800);
        List<ChannelBuffer> fragments =
                WriteSplitter.split(buffer, SPLIT_THRESHOLD);
        assertNotNull(fragments);
        assertEquals(1, fragments.size());
    }

    @Test
    public void testSplit_bufferMatchesThreshold() {
        ChannelBuffer buffer = createBufferWithContents(SPLIT_THRESHOLD);
        List<ChannelBuffer> fragments =
                WriteSplitter.split(buffer, SPLIT_THRESHOLD);
        assertNotNull(fragments);
        assertEquals(1, fragments.size());
    }

    @Test
    public void testSplit_bufferOverThreshold() {
        ChannelBuffer buffer =
                createBufferWithContents((int) (SPLIT_THRESHOLD * 1.5));
        List<ChannelBuffer> fragments =
                WriteSplitter.split(buffer, SPLIT_THRESHOLD);
        assertNotNull(fragments);
        assertEquals(2, fragments.size());

        ChannelBuffer fragment1 = fragments.get(0);
        checkMatches(buffer, fragment1);
        ChannelBuffer fragment2 = fragments.get(1);
        checkMatches(buffer, fragment2);
    }

    @Test
    public void testSplit_largeNumberOfFragments() {
        ChannelBuffer buffer = createBufferWithContents(SPLIT_THRESHOLD * 250);
        List<ChannelBuffer> fragments =
                WriteSplitter.split(buffer, SPLIT_THRESHOLD);
        assertNotNull(fragments);
        assertEquals(250, fragments.size());

        for (ChannelBuffer fragment: fragments) {
            checkMatches(buffer, fragment);
        }
    }

    private void checkMatches(ChannelBuffer mainBuffer, ChannelBuffer fragment) {
        assertTrue(mainBuffer.readableBytes() >= fragment.readableBytes());
        while (fragment.readable()) {
            assertEquals(mainBuffer.readByte(), fragment.readByte());
        }
    }

    private ChannelBuffer createBufferWithContents(int size) {
        byte[] contents = new byte[size];
        for (int i = 0; i < contents.length; i ++) {
            contents[i] = (byte) (i % 10);
        }

        return ChannelBuffers.copiedBuffer(contents);
    }

}
