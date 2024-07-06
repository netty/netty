/*
 * Copyright 2020 The Netty Project
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
package io.netty5.channel.uring;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.channel.unix.FileDescriptor;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class NativeTest {
    @AutoClose("shutdown")
    private ExecutorService executor;

    @BeforeEach
    void createExecutor() {
        executor = Executors.newCachedThreadPool();
    }

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    /*
    @Test
    public void canWriteFile(@TempDir Path tmpDir) {
        BufferAllocator allocator = DefaultBufferAllocators.offHeapAllocator();
        final Buffer writeEventBuf = allocator.allocate(100);
        final String inputString = "Hello World!";
        writeEventBuf.writeCharSequence(inputString, StandardCharsets.UTF_8);

        Path file = tmpDir.resolve("io_uring.tmp");
        int fd = Native.createFile(file.toString());

        RingBuffer ringBuffer = Native.createRingBuffer(32);
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();

        assertNotNull(ringBuffer);
        assertNotNull(submissionQueue);
        assertNotNull(completionQueue);

        try (var itr = writeEventBuf.forEachComponent()) {
            var cmp = itr.firstReadable();
            assertNotNull(cmp);
            submissionQueue.addWrite(fd, cmp.readableNativeAddress(), 0, cmp.readableBytes(), (short) 0);
            submissionQueue.submit();
        }

        completionQueue.ioUringWaitCqe();
        assertEquals(1, completionQueue.process((fd1, res, flags, udata) -> {
            assertEquals(inputString.length(), res);
            writeEventBuf.close();
        }));

        final Buffer readEventBuf = allocator.allocate(100);
        try (var itr = readEventBuf.forEachComponent()) {
            var cmp = itr.firstWritable();
            submissionQueue.addRead(fd, cmp.writableNativeAddress(), 0, cmp.writableBytes(), (short) 0);
            submissionQueue.submit();
        }

        completionQueue.ioUringWaitCqe();
        assertEquals(1, completionQueue.process((fd12, res, flags, udata) -> {
            assertEquals(inputString.length(), res);
            readEventBuf.skipWritableBytes(res);
        }));
        byte[] dataRead = new byte[inputString.length()];
        readEventBuf.readBytes(dataRead, 0, inputString.length());

        assertArrayEquals(inputString.getBytes(), dataRead);
        readEventBuf.close();

        ringBuffer.close();
        file.toFile().delete();
    }

    @Test
    public void timeoutTest() throws Exception {

        RingBuffer ringBuffer = Native.createRingBuffer(32);
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        final CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();

        assertNotNull(ringBuffer);
        assertNotNull(submissionQueue);
        assertNotNull(completionQueue);

        var future = executor.submit(() -> {
            completionQueue.ioUringWaitCqe();
            completionQueue.process((fd, res, flags, udata) -> assertEquals(-62, res));
            return null;
        });

        try {
            Thread.sleep(80);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        submissionQueue.addTimeout(-1, 0, (short) 0);
        submissionQueue.submit();

        future.get();
        ringBuffer.close();
    }

    //Todo clean
    @Test
    public void eventfdTest() throws Exception {
        RingBuffer ringBuffer = Native.createRingBuffer(32);
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        final CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();

        assertNotNull(ringBuffer);
        assertNotNull(submissionQueue);
        assertNotNull(completionQueue);

        final FileDescriptor eventFd = Native.newBlockingEventFd();
        submissionQueue.addPollIn(eventFd.intValue());
        submissionQueue.submit();

        executor.submit(() -> Native.eventFdWrite(eventFd.intValue(), 1L));

        completionQueue.ioUringWaitCqe();
        assertEquals(1, completionQueue.process((fd, res, flags, udata) -> assertEquals(1, res)));
        try {
            eventFd.close();
        } finally {
            ringBuffer.close();
        }
    }

    //Todo clean
    //eventfd signal doesnt work when ioUringWaitCqe and eventFdWrite are executed in a thread
    //created this test to reproduce this "weird" bug
    @Test
    @Timeout(8)
    public void eventfdNoSignal() throws Exception {

        RingBuffer ringBuffer = Native.createRingBuffer(32);
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        final CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();

        assertNotNull(ringBuffer);
        assertNotNull(submissionQueue);
        assertNotNull(completionQueue);

        var waitingCqe = executor.submit(() -> {
            completionQueue.ioUringWaitCqe();
            assertEquals(1, completionQueue.process((fd, res, flags, udata) -> assertEquals(1, res)));
        });
        final FileDescriptor eventFd = Native.newBlockingEventFd();
        submissionQueue.addPollIn(eventFd.intValue());
        submissionQueue.submit();

        executor.submit(() -> Native.eventFdWrite(eventFd.intValue(), 1L));

        try {
            waitingCqe.get();
        } finally {
            ringBuffer.close();
        }
    }

    @Test
    public void ioUringExitTest() {
        RingBuffer ringBuffer = Native.createRingBuffer();
        ringBuffer.close();
    }

    @Test
    public void ioUringPollRemoveTest() throws Exception {
        RingBuffer ringBuffer = Native.createRingBuffer(32);
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        final CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();

        FileDescriptor eventFd = Native.newBlockingEventFd();
        submissionQueue.addPollIn(eventFd.intValue());
        submissionQueue.submit();
        submissionQueue.addPollRemove(eventFd.intValue(), Native.POLLIN);
        submissionQueue.submit();

        final CountDownLatch latch = new CountDownLatch(2);
        CompletionCallback verifyCallback = (fd, res, flags, udata) -> {
            byte op = UserData.decodeOp(udata);
            if (op == Native.IORING_OP_POLL_ADD) {
                assertEquals(Native.ERRNO_ECANCELED_NEGATIVE, res);
                latch.countDown();
            } else if (op == Native.IORING_OP_POLL_REMOVE) {
                assertEquals(0, res);
                latch.countDown();
            } else {
                fail("op " + op);
            }
        };
        var waitingCqe = executor.submit(() -> {
            completionQueue.ioUringWaitCqe();
            assertEquals(2, completionQueue.process(verifyCallback));
        });
        try {
            waitingCqe.get();
            latch.await();
            eventFd.close();
        } finally {
            ringBuffer.close();
        }
    }

    @Test
    public void testUserData() {
        RingBuffer ringBuffer = Native.createRingBuffer(32);
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        final CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();

        try {
            // Ensure userdata works with negative and positive values
            for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
                submissionQueue.addWrite(-1, -1, -1, -1, (short) i);
                assertEquals(1, submissionQueue.submitAndWait());
                final int expectedData = i;
                assertEquals(1, completionQueue.process((fd, res, flags, udata) -> {
                    byte op = UserData.decodeOp(udata);
                    short data = UserData.decodeData(udata);
                    assertEquals(-1, fd);
                    assertTrue(res < 0);
                    assertEquals(Native.IORING_OP_WRITE, op);
                    assertEquals(expectedData, data);
                }));
            }
        } finally {
            ringBuffer.close();
        }
    }
     */

    @Test
    public void parsingKernelVersionTest() {
        Native.checkKernelVersion("10.11.123");
        assertThrows(UnsupportedOperationException.class, () -> Native.checkKernelVersion("5.8.1-23"));

        Native.checkKernelVersion("5.100.1-1");
        Native.checkKernelVersion("5.9.1-1");
        Native.checkKernelVersion("5.9.100-1");

        assertThrows(UnsupportedOperationException.class, () -> Native.checkKernelVersion("5.5.67"));
        assertThrows(UnsupportedOperationException.class, () -> Native.checkKernelVersion("5.5.32"));
        assertThrows(UnsupportedOperationException.class, () -> Native.checkKernelVersion("4.16.20"));
    }
}
