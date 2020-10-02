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

import io.netty.channel.unix.FileDescriptor;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import io.netty.buffer.ByteBuf;

public class NativeTest {

    @BeforeClass
    public static void loadJNI() {
        assumeTrue(IOUring.isAvailable());
    }

    @Test
    public void canWriteFile() throws Exception {
        ByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
        final ByteBuf writeEventByteBuf = allocator.directBuffer(100);
        final String inputString = "Hello World!";
        writeEventByteBuf.writeCharSequence(inputString, Charset.forName("UTF-8"));

        int fd = Native.createFile();

        RingBuffer ringBuffer = Native.createRingBuffer(32);
        IOUringSubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        IOUringCompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();

        assertNotNull(ringBuffer);
        assertNotNull(submissionQueue);
        assertNotNull(completionQueue);

        assertFalse(submissionQueue.addWrite(fd, writeEventByteBuf.memoryAddress(),
                writeEventByteBuf.readerIndex(), writeEventByteBuf.writerIndex(), (short) 0));
        submissionQueue.submit();

        completionQueue.ioUringWaitCqe();
        assertEquals(1, completionQueue.process(new IOUringCompletionQueueCallback() {
            @Override
            public void handle(int fd, int res, int flags, int op, short mask) {
                assertEquals(inputString.length(), res);
                writeEventByteBuf.release();
            }
        }));

        final ByteBuf readEventByteBuf = allocator.directBuffer(100);
        assertFalse(submissionQueue.addRead(fd, readEventByteBuf.memoryAddress(),
                                           readEventByteBuf.writerIndex(), readEventByteBuf.capacity(), (short) 0));
        submissionQueue.submit();

        completionQueue.ioUringWaitCqe();
        assertEquals(1, completionQueue.process(new IOUringCompletionQueueCallback() {
            @Override
            public void handle(int fd, int res, int flags, int op, short mask) {
                assertEquals(inputString.length(), res);
                readEventByteBuf.writerIndex(res);
            }
        }));
        byte[] dataRead = new byte[inputString.length()];
        readEventByteBuf.readBytes(dataRead);

        assertArrayEquals(inputString.getBytes(), dataRead);
        readEventByteBuf.release();

        ringBuffer.close();
    }

    @Test
    public void timeoutTest() throws Exception {

        RingBuffer ringBuffer = Native.createRingBuffer(32);
        IOUringSubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        final IOUringCompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();

        assertNotNull(ringBuffer);
        assertNotNull(submissionQueue);
        assertNotNull(completionQueue);

        Thread thread = new Thread() {
            @Override
            public void run() {
                completionQueue.ioUringWaitCqe();
                try {
                    completionQueue.process(new IOUringCompletionQueueCallback() {
                        @Override
                        public void handle(int fd, int res, int flags, int op, short mask) {
                            assertEquals(-62, res);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
        try {
            Thread.sleep(80);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        submissionQueue.addTimeout(0, (short) 0);
        submissionQueue.submit();

        thread.join();
        ringBuffer.close();
    }

    //Todo clean
    @Test
    public void eventfdTest() throws Exception {
        RingBuffer ringBuffer = Native.createRingBuffer(32);
        IOUringSubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        final IOUringCompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();

        assertNotNull(ringBuffer);
        assertNotNull(submissionQueue);
        assertNotNull(completionQueue);

        final FileDescriptor eventFd = Native.newBlockingEventFd();
        assertFalse(submissionQueue.addPollIn(eventFd.intValue()));
        submissionQueue.submit();

        new Thread() {
            @Override
            public void run() {
                Native.eventFdWrite(eventFd.intValue(), 1L);
            }
        }.start();

        completionQueue.ioUringWaitCqe();
        assertEquals(1, completionQueue.process(new IOUringCompletionQueueCallback() {
            @Override
            public void handle(int fd, int res, int flags, int op, short mask) {
                assertEquals(1, res);
            }
        }));
        try {
            eventFd.close();
        } finally {
            ringBuffer.close();
        }
    }

    //Todo clean
    //eventfd signal doesnt work when ioUringWaitCqe and eventFdWrite are executed in a thread
    //created this test to reproduce this "weird" bug
    @Test(timeout = 8000)
    public void eventfdNoSignal() throws Exception {

        RingBuffer ringBuffer = Native.createRingBuffer(32);
        IOUringSubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        final IOUringCompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();

        assertNotNull(ringBuffer);
        assertNotNull(submissionQueue);
        assertNotNull(completionQueue);

        Thread waitingCqe = new Thread() {
            @Override
            public void run() {
                completionQueue.ioUringWaitCqe();
                assertEquals(1, completionQueue.process(new IOUringCompletionQueueCallback() {
                    @Override
                    public void handle(int fd, int res, int flags, int op, short mask) {
                        assertEquals(1, res);
                    }
                }));
            }
        };
        waitingCqe.start();
        final FileDescriptor eventFd = Native.newBlockingEventFd();
        assertFalse(submissionQueue.addPollIn(eventFd.intValue()));
        submissionQueue.submit();

        new Thread() {
            @Override
            public void run() {
                Native.eventFdWrite(eventFd.intValue(), 1L);
            }
        }.start();

        waitingCqe.join();

        ringBuffer.close();
    }

    @Test
    public void ioUringExitTest() {
        RingBuffer ringBuffer = Native.createRingBuffer();
        ringBuffer.close();
    }

    @Test
    public void ioUringPollRemoveTest() throws Exception {
        RingBuffer ringBuffer = Native.createRingBuffer(32);
        IOUringSubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        final IOUringCompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();

        FileDescriptor eventFd = Native.newBlockingEventFd();
        submissionQueue.addPollIn(eventFd.intValue());
        submissionQueue.submit();
        submissionQueue.addPollRemove(eventFd.intValue(), Native.POLLIN, (short) 0);
        submissionQueue.submit();

        final AtomicReference<AssertionError> errorRef = new AtomicReference<AssertionError>();
        Thread waitingCqe = new Thread() {
            private final IOUringCompletionQueueCallback verifyCallback =
                    new IOUringCompletionQueueCallback() {
                @Override
                public void handle(int fd, int res, int flags, int op, short mask) {
                    if (op == Native.IORING_OP_POLL_ADD) {
                        assertEquals(Native.ERRNO_ECANCELED_NEGATIVE, res);
                    } else if (op == Native.IORING_OP_POLL_REMOVE) {
                        assertEquals(0, res);
                    } else {
                        fail("op " + op);
                    }
                }
            };

            @Override
            public void run() {
                try {
                    completionQueue.ioUringWaitCqe();
                    assertEquals(2, completionQueue.process(verifyCallback));
                } catch (AssertionError error) {
                    errorRef.set(error);
                }
            }
        };
        waitingCqe.start();
        waitingCqe.join();
        try {
            eventFd.close();
            AssertionError error = errorRef.get();
            if (error != null) {
                throw error;
            }
        } finally {
            ringBuffer.close();
        }
    }

    @Test
    public void testUserData() {
        RingBuffer ringBuffer = Native.createRingBuffer(32);
        IOUringSubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        final IOUringCompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();

        try {
            // Ensure userdata works with negative and positive values
            for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
                submissionQueue.addWrite(-1, -1, -1, -1, (short) i);
                assertEquals(1, submissionQueue.submitAndWait());
                final int expectedData = i;
                assertEquals(1, completionQueue.process(new IOUringCompletionQueueCallback() {
                    @Override
                    public void handle(int fd, int res, int flags, int op, short data) {
                        assertEquals(-1, fd);
                        assertTrue(res < 0);
                        assertEquals(Native.IORING_OP_WRITE, op);
                        assertEquals(expectedData, data);
                    }
                }));
            }
        } finally {
            ringBuffer.close();
        }
    }
}
