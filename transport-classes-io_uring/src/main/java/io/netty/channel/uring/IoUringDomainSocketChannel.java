package io.netty.channel.uring;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.IoRegistration;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.DomainSocketChannel;
import io.netty.channel.unix.DomainSocketChannelConfig;
import io.netty.channel.unix.DomainSocketReadMode;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.PeerCredentials;

import java.io.IOException;
import java.net.SocketAddress;

public class IoUringDomainSocketChannel extends AbstractIoUringStreamChannel implements DomainSocketChannel {

    private final IoUringDomainSocketChannelConfig config;

    private volatile DomainSocketAddress local;
    private volatile DomainSocketAddress remote;

    public IoUringDomainSocketChannel() {
        super(null, LinuxSocket.newSocketStream(), false);
        config = new IoUringDomainSocketChannelConfig(this);
    }

    IoUringDomainSocketChannel(Channel parent, FileDescriptor fd) {
        this(parent, new LinuxSocket(fd.intValue()));
    }

    IoUringDomainSocketChannel(Channel parent, LinuxSocket fd) {
        super(parent, fd, true);
        local = fd.localDomainSocketAddress();
        remote = fd.remoteDomainSocketAddress();
        config = new IoUringDomainSocketChannelConfig(this);
    }

    @Override
    public DomainSocketChannelConfig config() {
        return config;
    }

    @Override
    public DomainSocketAddress localAddress() {
        return local;
    }

    @Override
    public DomainSocketAddress remoteAddress() {
        return remote;
    }

    /**
     * Returns the unix credentials (uid, gid, pid) of the peer
     * <a href=https://man7.org/linux/man-pages/man7/socket.7.html>SO_PEERCRED</a>
     */
    public PeerCredentials peerCredentials() throws IOException {
        return socket.getPeerCredentials();
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof FileDescriptor) {
            return msg;
        }
        return super.filterOutboundMessage(msg);
    }

    @Override
    protected AbstractUringUnsafe newUnsafe() {
        return new IoUringDomainSocketUnsafe();
    }

    private final class IoUringDomainSocketUnsafe extends IoUringStreamUnsafe {

        private MsgHdrMemory writeMsgHdrMemory;
        private MsgHdrMemory readMsgHdrMemory;

        @Override
        protected int scheduleWriteSingle(Object msg) {
            if (msg instanceof FileDescriptor) {
                if (writeMsgHdrMemory == null) {
                    writeMsgHdrMemory = new MsgHdrMemory();
                }
                IoRegistration registration = registration();
                IoUringIoOps ioUringIoOps = prepSendFdIoOps((FileDescriptor) msg, writeMsgHdrMemory);
                writeId = registration.submit(ioUringIoOps);
                writeOpCode = Native.IORING_OP_SENDMSG;
                if (writeId == 0) {
                    MsgHdrMemory memory = writeMsgHdrMemory;
                    writeMsgHdrMemory = null;
                    memory.release();
                    return 0;
                }
                return 1;
            }
            return super.scheduleWriteSingle(msg);
        }

        @Override
        boolean writeComplete0(byte op, int res, int flags, short data, int outstanding) {
            if (op == Native.IORING_OP_SENDMSG) {
                writeId = 0;
                writeOpCode = 0;
                if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
                    return true;
                }
                try {
                    int nativeCallResult = res >= 0 ? res : Errors.ioResult("io_uring sendmsg", res);
                    if (nativeCallResult >= 0) {
                        ChannelOutboundBuffer channelOutboundBuffer = unsafe().outboundBuffer();
                        channelOutboundBuffer.remove();
                    }
                } catch (Throwable throwable) {
                   handleWriteError(throwable);
                }
                return true;
            }
            return super.writeComplete0(op, res, flags, data, outstanding);
        }

        private IoUringIoOps prepSendFdIoOps(FileDescriptor fileDescriptor, MsgHdrMemory msgHdrMemory) {
            msgHdrMemory.setScmRightsFd(fileDescriptor.intValue());
            return IoUringIoOps.newSendmsg(
                    fd().intValue(), (byte) 0, 0, msgHdrMemory.address(), msgHdrMemory.idx());
        }

        @Override
        protected int scheduleRead0(boolean first, boolean socketIsEmpty) {
            if (config.getReadMode() == DomainSocketReadMode.BYTES) {
                return super.scheduleRead0(first, socketIsEmpty);
            }

            if (config.getReadMode() == DomainSocketReadMode.FILE_DESCRIPTORS) {
                if (readMsgHdrMemory == null) {
                    readMsgHdrMemory = new MsgHdrMemory();
                }
                readMsgHdrMemory.set();
                IoRegistration registration = registration();
                IoUringIoOps ioUringIoOps = IoUringIoOps.newRecvmsg(
                        fd().intValue(), (byte) 0, 0, readMsgHdrMemory.address(), readMsgHdrMemory.idx());
                readId = registration.submit(ioUringIoOps);
                if (readId == 0) {
                    MsgHdrMemory memory = readMsgHdrMemory;
                    readMsgHdrMemory = null;
                    memory.release();
                    return 0;
                }
            }
            throw new Error();
        }

        @Override
        protected void readComplete0(byte op, int res, int flags, short data, int outstanding) {
            if (op == Native.IORING_OP_RECVMSG) {
                readId = 0;
                if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
                    return;
                }
                final IoUringRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
                final ChannelPipeline pipeline = pipeline();
                try {
                    int nativeCallResult = res >= 0 ? res : Errors.ioResult("io_uring recvmsg", res);
                    int nativeFd = readMsgHdrMemory.getScmRightsFd();
                    pipeline.fireChannelRead(new FileDescriptor(nativeFd));
                } catch (Throwable throwable) {
                    handleReadException(pipeline, null, throwable, false, allocHandle);
                }
                return;
            }
            super.readComplete0(op, res, flags, data, outstanding);
        }

        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            promise = promise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        local = localAddress != null ? (DomainSocketAddress) localAddress : socket.localDomainSocketAddress();
                        remote = (DomainSocketAddress) remoteAddress;
                    }
                }
            });
            super.connect(remoteAddress, localAddress, promise);
        }
    }

    @Override
    boolean isPollInFirst() {
        DomainSocketReadMode readMode = config.getReadMode();
        switch (readMode) {
            case BYTES:
                return super.isPollInFirst();
            case FILE_DESCRIPTORS:
                return false;
            default:
                throw new Error();
        }
    }
}
