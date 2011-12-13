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

import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import junit.framework.Assert;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelState;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.DownstreamMessageEvent;
import io.netty.channel.ExceptionEvent;
import io.netty.channel.UpstreamMessageEvent;

/**
 * Test utilities for Netty
 */
public class NettyTestUtils {

    public static ByteBuffer convertReadable(ChannelBuffer b) {
        int startIndex = b.readerIndex();
        ByteBuffer converted = ByteBuffer.allocate(b.readableBytes());
        b.readBytes(converted);
        b.readerIndex(startIndex);
        converted.flip();
        return converted;
    }

    public static void assertEquals(ChannelBuffer expected, ChannelBuffer actual) {
        if (expected.readableBytes() != actual.readableBytes()) {
            Assert.failNotEquals(
                    "channel buffers have differing readable sizes",
                    expected.readableBytes(), actual.readableBytes());
        }

        int startPositionExpected = expected.readerIndex();
        int startPositionActual = actual.readerIndex();
        int position = 0;
        while (expected.readable()) {
            byte expectedByte = expected.readByte();
            byte actualByte = actual.readByte();
            if (expectedByte != actualByte) {
                Assert.failNotEquals("channel buffers differ at position " +
                        position, expectedByte, actualByte);
            }

            position ++;
        }

        expected.readerIndex(startPositionExpected);
        actual.readerIndex(startPositionActual);
    }

    public static boolean checkEquals(ChannelBuffer expected,
            ChannelBuffer actual) {
        if (expected.readableBytes() != actual.readableBytes()) {
            return false;
        }

        while (expected.readable()) {
            byte expectedByte = expected.readByte();
            byte actualByte = actual.readByte();
            if (expectedByte != actualByte) {
                return false;
            }
        }

        return true;
    }

    public static List<ChannelBuffer> splitIntoChunks(int chunkSize,
            ChannelBuffer... buffers) {
        LinkedList<ChannelBuffer> chunks = new LinkedList<ChannelBuffer>();

        ArrayList<ChannelBuffer> sourceBuffers = new ArrayList<ChannelBuffer>();
        Collections.addAll(sourceBuffers, buffers);
        Iterator<ChannelBuffer> sourceIter = sourceBuffers.iterator();
        ChannelBuffer chunk = ChannelBuffers.buffer(chunkSize);
        while (sourceIter.hasNext()) {
            ChannelBuffer source = sourceIter.next();

            int index = source.readerIndex();
            while (source.writerIndex() > index) {
                int fragmentSize =
                        Math.min(source.writerIndex() - index,
                                chunk.writableBytes());
                chunk.writeBytes(source, index, fragmentSize);
                if (!chunk.writable()) {
                    chunks.add(chunk);
                    chunk = ChannelBuffers.buffer(chunkSize);
                }
                index += fragmentSize;
            }
        }

        if (chunk.readable()) {
            chunks.add(chunk);
        }

        return chunks;
    }

    public static ChannelBuffer createData(long containedNumber) {
        ChannelBuffer data = ChannelBuffers.dynamicBuffer();
        data.writeLong(containedNumber);
        return data;
    }

    public static void checkIsUpstreamMessageEventContainingData(
            ChannelEvent event, ChannelBuffer expectedData) {
        ChannelBuffer data =
                checkIsUpstreamMessageEvent(event, ChannelBuffer.class);
        assertEquals(expectedData, data);
    }

    public static <T> T checkIsUpstreamMessageEvent(ChannelEvent event,
            Class<T> expectedMessageType) {
        assertTrue(event instanceof UpstreamMessageEvent);
        UpstreamMessageEvent messageEvent = (UpstreamMessageEvent) event;
        assertTrue(expectedMessageType.isInstance(messageEvent.getMessage()));
        return expectedMessageType.cast(messageEvent.getMessage());
    }

    public static <T> T checkIsDownstreamMessageEvent(ChannelEvent event,
            Class<T> expectedMessageType) {
        assertTrue(event instanceof DownstreamMessageEvent);
        DownstreamMessageEvent messageEvent = (DownstreamMessageEvent) event;
        assertTrue(expectedMessageType.isInstance(messageEvent.getMessage()));
        return expectedMessageType.cast(messageEvent.getMessage());
    }

    public static InetSocketAddress createAddress(byte[] addr, int port) {
        try {
            return new InetSocketAddress(InetAddress.getByAddress(addr), port);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Bad address in test");
        }
    }

    public static Throwable checkIsExceptionEvent(ChannelEvent ev) {
        assertTrue(ev instanceof ExceptionEvent);
        ExceptionEvent exceptionEv = (ExceptionEvent) ev;
        return exceptionEv.getCause();
    }

    public static ChannelStateEvent checkIsStateEvent(ChannelEvent event,
            ChannelState expectedState, Object expectedValue) {
        assertTrue(event instanceof ChannelStateEvent);
        ChannelStateEvent stateEvent = (ChannelStateEvent) event;
        Assert.assertEquals(expectedState, stateEvent.getState());
        Assert.assertEquals(expectedValue, stateEvent.getValue());
        return stateEvent;
    }
}
