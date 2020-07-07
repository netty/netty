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

import org.junit.Test;

import java.io.FileInputStream;

import java.io.File;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledUnsafeDirectByteBuf;
import static org.junit.Assert.*;

public class NativeTest {

    @Test
    public void canWriteFile() {
        //Todo add read operation test
        final long eventId = 1;

        ByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
        UnpooledUnsafeDirectByteBuf directByteBufPooled = new UnpooledUnsafeDirectByteBuf(allocator, 500, 1000);
        String inputString = "Hello World!";
        byte[] byteArrray = inputString.getBytes();
        directByteBufPooled.writeBytes(byteArrray);

        int fd = (int) Native.createFile();
        System.out.println("Filedescriptor: " + fd);

        RingBuffer ringBuffer = Native.createRingBuffer(32);
        IOUringSubmissionQueue submissionQueue = ringBuffer.getIoUringSubmissionQueue();
        IOUringCompletionQueue completionQueue = ringBuffer.getIoUringCompletionQueue();

        assertNotNull(ringBuffer);
        assertNotNull(submissionQueue);
        assertNotNull(completionQueue);

        assertTrue(submissionQueue.add(eventId, EventType.WRITE, fd, directByteBufPooled.memoryAddress(),
        directByteBufPooled.readerIndex(), directByteBufPooled.writerIndex()));
        submissionQueue.submit();

        IOUringCqe ioUringCqe = completionQueue.ioUringWaitCqe();

        assertNotNull(ioUringCqe);
        assertEquals(inputString.length(), ioUringCqe.getRes());
        assertEquals(1, ioUringCqe.getEventId());
    }
}
