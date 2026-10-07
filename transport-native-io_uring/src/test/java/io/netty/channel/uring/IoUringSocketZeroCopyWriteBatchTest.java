/*
 * Copyright 2026 The Netty Project
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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies that the decision to use a zero-copy write is taken for the whole flushed batch and not only for the
 * first buffer, so that a small prefix (for example an HTTP response header) does not push the buffers behind it
 * into a separate writev submitted in a later event loop iteration.
 *
 * <p>See https://github.com/netty/netty/issues/17632.
 */
public class IoUringSocketZeroCopyWriteBatchTest {

    private static final int THRESHOLD = 1024;

    @BeforeAll
    static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
        assumeTrue(IoUring.isSendmsgZcSupported());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void smallHeaderFollowedByLargeBodyIsSubmittedAsOneSendmsgZc() throws Exception {
        runOnEventLoop(channel -> {
            channel.config().setOption(IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD, THRESHOLD);
            // A header below the threshold followed by a body at or above it: the whole batch must go out through
            // a single SENDMSG_ZC, not a WRITEV for the header and a SEND_ZC for the body.
            writeFlushed(channel,
                    channel.alloc().buffer(208).writeZero(208),
                    channel.alloc().buffer(4096).writeZero(4096));
            assertEquals(Native.IORING_OP_SENDMSG_ZC, channel.writeOpCode);
        });
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void batchWithoutZeroCopyCandidateIsSubmittedAsWritev() throws Exception {
        runOnEventLoop(channel -> {
            channel.config().setOption(IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD, THRESHOLD);
            writeFlushed(channel,
                    channel.alloc().buffer(208).writeZero(208),
                    channel.alloc().buffer(208).writeZero(208));
            assertEquals(Native.IORING_OP_WRITEV, channel.writeOpCode);
        });
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void fileRegionStopsTheZeroCopyScan() throws Exception {
        File file = File.createTempFile("netty-iouring", ".tmp");
        file.deleteOnExit();
        Files.write(file.toPath(), new byte[16]);
        DefaultFileRegion region = new DefaultFileRegion(file, 0, 16);
        runOnEventLoop(channel -> {
            channel.config().setOption(IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD, THRESHOLD);
            AbstractIoUringChannel.AbstractUringUnsafe unsafe =
                    (AbstractIoUringChannel.AbstractUringUnsafe) channel.unsafe();
            ChannelOutboundBuffer in = unsafe.outboundBuffer();
            channel.write(channel.alloc().buffer(208).writeZero(208));
            channel.write(region);
            channel.write(channel.alloc().buffer(4096).writeZero(4096));
            in.addFlush();
            channel.doWrite(in);
            // The gather stops at the FileRegion, so the qualifying buffer behind it must not select the
            // zero-copy path: otherwise only the small buffer would actually be submitted through sendmsg_zc.
            assertEquals(Native.IORING_OP_WRITEV, channel.writeOpCode);
        });
    }

    private static void writeFlushed(IoUringSocketChannel channel, ByteBuf... buffers) {
        ChannelOutboundBuffer in = ((AbstractIoUringChannel.AbstractUringUnsafe) channel.unsafe()).outboundBuffer();
        assert in != null;
        for (ByteBuf buffer : buffers) {
            in.addMessage(buffer, buffer.readableBytes(), channel.newPromise());
        }
        in.addFlush();
        // Drive the write path directly, mirroring doWrite(ChannelOutboundBuffer): the completion of the submitted
        // op is not processed until the task returns to the event loop, so writeOpCode is still observable here.
        channel.doWrite(in);
    }

    private interface Task {
        void run(IoUringSocketChannel channel);
    }

    private static void runOnEventLoop(Task task) throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        IoUringSocketChannel channel = new IoUringSocketChannel();
        try {
            group.register(channel).sync();
            channel.eventLoop().submit(() -> task.run(channel)).sync();
        } finally {
            channel.close().syncUninterruptibly();
            group.shutdownGracefully().syncUninterruptibly();
        }
    }
}
