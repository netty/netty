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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.IoEvent;
import io.netty.channel.IoEventLoop;
import io.netty.channel.IoRegistration;
import io.netty.channel.unix.Errors;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;

import java.nio.charset.StandardCharsets;

/**
 * A file that uses {@code io_uring} for all the io operations and so is fully non-blocking.
 */
public final class IoUringFile {
    final IoUringIoHandle handle = new IoUringIoHandle() {
        @Override
        public void handle(IoRegistration registration, IoEvent ioEvent) {
            IoUringIoEvent event  = (IoUringIoEvent) ioEvent;
            int opCode = event.opcode();
            short data = event.data();
            int res = event.res();
            if (opCode == Native.IORING_OP_OPENAT) {
                cString.release();
                cString = null;
                assert data == 0;
                if (res >= 0) {
                    IoUringFile.this.fd = res;
                    promise.setSuccess(IoUringFile.this);
                } else {
                    promise.setFailure(Errors.newIOException("open(...)", res));
                }
                return;
            }
            if (opCode == Native.IORING_OP_CLOSE) {
                assert data == 0;
                if (res == 0) {
                    IoUringFile.this.fd = -1;
                    closePromise.setSuccess(null);
                } else {
                    closePromise.setFailure(Errors.newIOException("close(...)", res));
                }
            }
        }

        @Override
        public void close() {
            if (cString != null) {
                cString.release();
                cString = null;
            }
        }
    };
    private final IoEventLoop eventLoop;
    private Promise<IoUringFile> promise;
    private IoUringIoRegistration registration;
    private int fd;
    private ByteBuf cString;
    private Promise<Void> closePromise;

    IoUringFile(IoEventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    void open(IoUringIoRegistration registration, String path, int oFlags, int mode, Promise<IoUringFile> promise) {
        assert eventLoop.inEventLoop();
        this.registration = registration;
        this.promise = promise;

        // We need to create a null-terminated c-style string so we can pass it to io_uring.
        byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
        cString = Unpooled.directBuffer(pathBytes.length + 1);
        cString.writeBytes(pathBytes);
        cString.writeByte('\0');
        registration.submit(new IoUringIoOps(Native.IORING_OP_OPENAT, oFlags, (short) 0, /* AT_FDCWD */ -100, 0,
                cString.memoryAddress(), mode, 0, (short) 0));
    }

    public Future<Void> write(ByteBuf buffer) {
        buffer.release();
        return eventLoop.newFailedFuture(new UnsupportedOperationException());
    }

    public Future<ByteBuf> read(int numBytes) {
        return eventLoop.newFailedFuture(new UnsupportedOperationException());
    }

    public Future<Void> close() {
        assert this.registration != null;
        Promise<Void> closePromise = eventLoop.newPromise();
        if (eventLoop.inEventLoop()) {
            close0(closePromise);
        } else {
            eventLoop.submit(() -> close0(closePromise));
        }
        return closePromise;
    }

    private void close0(Promise<Void> promise) {
        if (closePromise == null) {
            closePromise = promise;
            registration.submit(IoUringIoOps.newClose(fd, 0, (short) 0));
        } else {
            PromiseNotifier.cascade(closePromise, promise);
        }
    }

    /**
     * Open a {@link IoUringFile} which is tied to an {@link IoEventLoop} that can work with {@link IoUringIoHandle}s.
     *
     * @param eventLoop     the {@link IoEventLoop} which will handle all IO.
     * @param path          the file to the path to open.
     * @param oFlags        the oFlags.
     * @param mode          the mode.
     * @return              a {@link Future} that is notified once the operation completes.
     */
    public static Future<IoUringFile> open(IoEventLoop eventLoop, String path, int oFlags, int mode) {
        Promise<IoUringFile> promise = eventLoop.newPromise();
        IoUringFile file = new IoUringFile(eventLoop);

        eventLoop.register(file.handle).addListener((FutureListener<IoRegistration>) future -> {
            if (future.isSuccess()) {
                IoUringIoRegistration registration = (IoUringIoRegistration) future.getNow();
                file.open(registration, path, oFlags, mode, promise);
            } else {
                promise.setFailure(future.cause());
            }
        });
        return promise;
    }
}
