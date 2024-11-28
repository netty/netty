/*
 * Copyright 2024 The Netty Project
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

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.FileRegion;
import io.netty.channel.unix.FileDescriptor;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

final class IoUringFileRegion implements FileRegion {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(IoUringFileRegion.class);

    private static final short SPLICE_TO_PIPE = 1;
    private static final short SPLICE_TO_SOCKET = 2;

    final DefaultFileRegion fileRegion;
    private FileDescriptor[] pipe;
    private int transferred;
    private int pipeLen = -1;

    IoUringFileRegion(DefaultFileRegion fileRegion) {
        this.fileRegion = fileRegion;
    }

    void open() throws IOException {
        fileRegion.open();
        if (pipe == null) {
            pipe = FileDescriptor.pipe();
        }
    }

    IoUringIoOps splice(int fd) {
        if (pipeLen == -1) {
            return spliceToPipe();
        }
        return spliceToSocket(fd, pipeLen);
    }

    IoUringIoOps spliceToPipe() {
        int fileRegionFd = Native.getFd(fileRegion);
        int len = (int) (count() - transferred());
        int offset =  (int) (position() + transferred());
        return IoUringIoOps.newSplice(
                fileRegionFd, offset,
                pipe[1].intValue(), -1L,
                len, Native.SPLICE_F_MOVE, SPLICE_TO_PIPE);
    }

    private IoUringIoOps spliceToSocket(int socket, int len) {
        return IoUringIoOps.newSplice(
                pipe[0].intValue(), -1L,
                socket, -1L,
                len, Native.SPLICE_F_MOVE, SPLICE_TO_SOCKET);
    }

    /**
     * Handle splice result
     *
     * @param result    the result
     * @param data      the data that was submitted as part of the SPLICE.
     * @return          the number of bytes that should be considered to be transferred.
     */
    int handleResult(int result, short data) {
        assert result >= 0;
        if (data == SPLICE_TO_PIPE) {
            // This is the result for spliceToPipe
            transferred += result;
            pipeLen = result;
            return 0;
        }
        if (data == SPLICE_TO_SOCKET) {
            // This is the result for spliceToSocket
            pipeLen -= result;
            assert pipeLen >= 0;
            if (pipeLen == 0) {
                if (transferred() >= count()) {
                    // We transferred the whole file
                    return -1;
                }
                pipeLen = -1;
            }
            return result;
        }
        throw new IllegalArgumentException("Unknown data: " + data);
    }

    @Override
    public long position() {
        return fileRegion.position();
    }

    @Override
    public long transfered() {
        return transferred;
    }

    @Override
    public long transferred() {
        return transferred;
    }

    @Override
    public long count() {
        return fileRegion.count();
    }

    @Override
    public long transferTo(WritableByteChannel target, long position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileRegion retain() {
        fileRegion.retain();
        return this;
    }

    @Override
    public FileRegion retain(int increment) {
        fileRegion.retain(increment);
        return this;
    }

    @Override
    public FileRegion touch() {
        fileRegion.touch();
        return this;
    }

    @Override
    public FileRegion touch(Object hint) {
        fileRegion.touch(hint);
        return this;
    }

    @Override
    public int refCnt() {
        return fileRegion.refCnt();
    }

    @Override
    public boolean release() {
        if (fileRegion.release()) {
            closePipeIfNeeded();
            return true;
        }
        return false;
    }

    @Override
    public boolean release(int decrement) {
        if (fileRegion.release(decrement)) {
            closePipeIfNeeded();
            return true;
        }
        return false;
    }

    private void closePipeIfNeeded() {
        if (pipe != null) {
            closeSilently(pipe[0]);
            closeSilently(pipe[1]);
        }
    }

    private static void closeSilently(FileDescriptor fd) {
        try {
            fd.close();
        } catch (IOException e) {
            logger.debug("Error while closing a pipe", e);
        }
    }
}
