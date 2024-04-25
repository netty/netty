/*
 * Copyright 2022 The Netty Project
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
import io.netty5.buffer.StandardAllocationTypes;
import io.netty5.channel.AbstractChannel;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.EventLoop;
import io.netty5.channel.ReadHandleFactory;
import io.netty5.channel.WriteHandleFactory;
import io.netty5.channel.socket.SocketProtocolFamily;
import io.netty5.channel.unix.Errors;
import io.netty5.channel.unix.FileDescriptor;
import io.netty5.channel.unix.UnixChannel;
import io.netty5.channel.unix.UnixChannelOption;
import io.netty5.util.Resource;
import io.netty5.util.collection.LongObjectHashMap;
import io.netty5.util.collection.LongObjectMap;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureContextListener;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.SilentDispose;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.NoRouteToHostException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ObjLongConsumer;

import static io.netty5.channel.unix.UnixChannelUtil.computeRemoteAddr;

abstract class AbstractIOUringChannel<P extends UnixChannel>
        extends AbstractChannel<P, SocketAddress, SocketAddress>
        implements UnixChannel {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractIOUringChannel.class);
    private static final int MAX_READ_AHEAD_PACKETS = 8;
    private static final AtomicInteger CONNECT_ID_COUNTER = new AtomicInteger();

    static final FutureContextListener<Buffer, Void> CLOSE_BUFFER = (b, f) -> SilentDispose.dispose(b, LOGGER);

    protected final LinuxSocket socket;
    protected final ObjectRing<Object> readsPending;
    protected final ObjectRing<Object> readsCompleted; // Either 'Failure', or a message (buffer, datagram, ...).
    protected final LongObjectMap<Object> cancelledReads;

    protected volatile boolean active;
    protected volatile SocketAddress local;
    protected volatile SocketAddress remote;

    protected SubmissionQueue submissionQueue;
    protected WriteSink writeSink;
    protected int currentCompletionResult;
    protected short currentCompletionData;

    private final Promise<Executor> prepareClosePromise;
    private final Runnable pendingRead;
    private final Runnable rdHupRead;

    private short lastReadId;
    private boolean readPendingRegister;
    private boolean readPendingConnect;
    private Buffer connectRemoteAddressMem;
    private boolean scheduledRdHup;
    private boolean receivedRdHup;
    private boolean submittedClose;

    private short lastConnectId;

    protected AbstractIOUringChannel(P parent, EventLoop eventLoop, boolean supportingDisconnect,
                                     ReadHandleFactory defaultReadHandleFactory,
                                     WriteHandleFactory defaultWriteHandleFactory,
                                     LinuxSocket socket, SocketAddress remote, boolean active) {
        super(parent, eventLoop, supportingDisconnect, defaultReadHandleFactory, defaultWriteHandleFactory);
        this.socket = socket;
        this.active = active;
        if (active) {
            // Directly cache local and remote addresses.
            local = socket.localAddress();
            this.remote = remote == null ? socket.remoteAddress() : remote;
        } else if (remote != null) {
            this.remote = remote;
        }
        prepareClosePromise = eventLoop.newPromise();
        pendingRead = this::submitReadForPending;
        rdHupRead = this::submitReadForRdHup;
        readsPending = new ObjectRing<>();
        readsCompleted = new ObjectRing<>();
        cancelledReads = new LongObjectHashMap<>(8);
    }

    @Override
    protected SocketAddress localAddress0() {
        return local;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return remote;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        if (local instanceof InetSocketAddress) {
            checkResolvable((InetSocketAddress) local);
        }
        socket.bind(localAddress); // Bind immediately, as AbstractChannel expects it to be done after this method call.
        if (fetchLocalAddress()) {
            local = socket.localAddress();
        } else {
            local = localAddress;
        }
        cacheAddresses(local, remoteAddress());
    }

    protected static void checkResolvable(InetSocketAddress addr) {
        if (addr.isUnresolved()) {
            throw new UnresolvedAddressException();
        }
    }

    protected final boolean fetchLocalAddress() {
        return socket.protocolFamily() != SocketProtocolFamily.UNIX;
    }

    @Override
    protected void doRead(boolean wasReadPendingAlready) throws Exception {
        // Schedule a read operation. When completed, we'll get a callback to readComplete.
        if (submissionQueue == null) {
            readPendingRegister = true;
            return;
        }
        if (!wasReadPendingAlready) {
            submitRead();
        }
    }

    private void submitRead() {
        // Submit reads until read handle says stop, we fill the submission queue, or hit max limit
        int maxPackets = Math.min(submissionQueue.remaining(), MAX_READ_AHEAD_PACKETS);
        int sumPackets = 0;
        int bufferSize = nextReadBufferSize();
        boolean morePackets = bufferSize > 0;

        while (morePackets) {
            Buffer readBuffer = readBufferAllocator().allocate(bufferSize);
            assert readBuffer.isDirect();
            assert readBuffer.countWritableComponents() == 1;
            sumPackets++;
            morePackets = sumPackets < maxPackets && (bufferSize = nextReadBufferSize()) > 0;
            submissionQueue.link(morePackets);
            short readId = ++lastReadId;
            submitReadForReadBuffer(readBuffer, readId, sumPackets > 1, readsPending);
        }
    }

    private void submitNonBlockingRead() {
        assert readsPending.isEmpty();
        int bufferSize = nextReadBufferSize();
        if (bufferSize == 0) {
            return;
        }
        Buffer readBuffer = readBufferAllocator().allocate(bufferSize);
        assert readBuffer.isDirect();
        assert readBuffer.countWritableComponents() == 1;
        short readId = ++lastReadId;
        submitReadForReadBuffer(readBuffer, readId, true, readsPending);
    }

    private void submitReadForPending() {
        if (active && readsPending.isEmpty()) {
            submitRead();
        }
    }

    private void submitReadForRdHup() {
        if (active && readsPending.isEmpty()) {
            submitNonBlockingRead();
        }
    }

    protected int nextReadBufferSize() {
        return readHandle().prepareRead();
    }

    protected void submitReadForReadBuffer(Buffer buffer, short readId, boolean nonBlocking,
                                             ObjLongConsumer<Object> pendingConsumer) {
        try (var itr = buffer.forEachComponent()) {
            var cmp = itr.firstWritable();
            assert cmp != null;
            long address = cmp.writableNativeAddress();
            int flags = nonBlocking ? Native.MSG_DONTWAIT : 0;
            long udata = submissionQueue.addRecv(
                    fd().intValue(), address, 0, cmp.writableBytes(), flags, readId);
            pendingConsumer.accept(buffer, udata);
        }
    }

    @Override
    protected void doClearScheduledRead() {
        // Using the lastReadId to differentiate our reads, means we avoid accidentally cancelling any future read.
        while (readsPending.poll()) {
            Object obj = readsPending.getPolledObject();
            long udata = readsPending.getPolledStamp();
            Resource.touch(obj, "read cancelled");
            cancelledReads.put(udata, obj);
            submissionQueue.addCancel(fd().intValue(), udata);
        }
    }

    void readComplete(int res, long udata) {
        assert executor().inEventLoop();
        if (res == Native.ERRNO_ECANCELED_NEGATIVE || res == Errors.ERRNO_EAGAIN_NEGATIVE) {
            Object obj = cancelledReads.remove(udata);
            if (obj == null) {
                obj = readsPending.remove(udata);
            }
            if (obj != null) {
                SilentDispose.dispose(obj, logger());
            }
            return;
        }

        final Object obj;
        if (readsPending.hasNextStamp(udata) && readsPending.poll()) {
            obj = readsPending.getPolledObject();
        } else {
            // Out-of-order read completion? Weird. Should this ever happen?
            obj = readsPending.remove(udata);
        }
        if (obj != null) {
            if (res >= 0) {
                Resource.touch(obj, "read completed");
                readsCompleted.push(prepareCompletedRead(obj, res), udata);
            } else {
                SilentDispose.dispose(obj, logger());
                readsCompleted.push(new Failure(res), udata);
            }
        }
    }

    protected Object prepareCompletedRead(Object obj, int result) {
        ((Buffer) obj).skipWritableBytes(result);
        return obj;
    }

    void ioLoopCompleted() {
        if (!readsCompleted.isEmpty()) {
            readNow(); // Will call back into doReadNow.
        }
        WriteSink writeSink = this.writeSink;
        if (writeSink != null) {
            writeSink.complete(0, 0, 0, false);
            writeSink.writeLoopContinue();
            writeSink.writeLoopEnd();
            this.writeSink = null;
        }
    }

    @Override
    protected boolean doReadNow(ReadSink readSink) throws Exception {
        while (readsCompleted.poll()) {
            Object completion = readsCompleted.getPolledObject();
            if (completion instanceof Failure) {
                throw Errors.newIOException("channel.read", ((Failure) completion).result);
            }
            if (processRead(readSink, completion)) {
                // Leave it to the sub-class to decide if this buffer is EOF or not.
                return true;
            }
        }
        // We have no more completed reads. Stop the read loop.
        readSink.processRead(0, 0, null);
        return false;
    }

    protected final BufferAllocator ioBufferAllocator() {
        BufferAllocator allocator = bufferAllocator();
        if (allocator.getAllocationType() == StandardAllocationTypes.OFF_HEAP) {
            return allocator;
        }
        return DefaultBufferAllocators.offHeapAllocator();
    }

    @Override
    protected final BufferAllocator readBufferAllocator() {
        return ioBufferAllocator();
    }

    /**
     * Process the given read.
     *
     * @return {@code true} if the channel should be closed, e.g. if a zero-readable buffer means EOF.
     */
    protected abstract boolean processRead(ReadSink readSink, Object read);

    @Override
    protected void readLoopComplete() {
        super.readLoopComplete();
        // If there are pending reads, or we received RDHUP (such that we want to drain inbound buffer),
        // then schedule a read to run later, after other tasks.
        // Those other tasks might issue their own reads, which we should not interferre with.
        // Another reason we need to schedule these to run later, is that the read-loop will cancel any unprocessed
        // reads after this method call.
        if (isReadPending()) {
            executor().execute(pendingRead);
        } else if (receivedRdHup) {
            executor().execute(rdHupRead);
        }
    }

    @NotNull
    protected Buffer intoDirectBuffer(Buffer buf, boolean dispose) {
        BufferAllocator allocator = ioBufferAllocator();
        Buffer copy = allocator.allocate(buf.readableBytes());
        copy.writeBytes(buf);
        if (dispose) {
            buf.close();
        }
        return copy;
    }

    @Override
    protected void writeLoop(WriteSink writeSink) {
        this.writeSink = writeSink;
        try {
            writeSink.writeLoopStep();
        } catch (Throwable e) {
            handleWriteError(e);
        }
    }

    @Override
    protected void doWriteNow(WriteSink writeSink) {
        submitAllWriteMessages(writeSink);
        // We *MUST* submit all our messages, since we'll be releasing the outbound buffers after the doWriteNow call.
        submissionQueue.submit();
    }

    protected abstract void submitAllWriteMessages(WriteSink writeSink);

    abstract void writeComplete(int result, long udata);

    /**
     * Connect to the remote peer
     */
    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Buffer initialData)
            throws Exception {
        if (localAddress instanceof InetSocketAddress) {
            checkResolvable((InetSocketAddress) localAddress);
        }

        InetSocketAddress remoteSocketAddr = remoteAddress instanceof InetSocketAddress
                ? (InetSocketAddress) remoteAddress : null;
        if (remoteSocketAddr != null) {
            checkResolvable(remoteSocketAddr);
        }

        if (localAddress != null) {
            socket.bind(localAddress);
        }

        submitConnect(remoteSocketAddr, initialData);
        return false;
    }

    protected void submitConnect(InetSocketAddress remoteSocketAddr, Buffer initialData) throws IOException {
        Buffer addrBuf = ioBufferAllocator().allocate(Native.SIZEOF_SOCKADDR_STORAGE);
        try (var itr = addrBuf.forEachComponent()) {
            var cmp = itr.firstWritable();
            SockaddrIn.write(socket.isIpv6(), cmp.writableNativeAddress(), remoteSocketAddr);
            lastConnectId = nextConnectId();
            submissionQueue.addConnect(fd().intValue(), cmp.writableNativeAddress(),
                    Native.SIZEOF_SOCKADDR_STORAGE, lastConnectId);
        }
        connectRemoteAddressMem = addrBuf;
    }

    private static short nextConnectId() {
        // Compute the next connect id, but skip 0, because that's the default value for lastConnectId.
        short result;
        do {
            result = (short) CONNECT_ID_COUNTER.incrementAndGet();
        } while (result == 0);
        return result;
    }

    void connectComplete(int res, long udata) {
        short connectId = UserData.decodeData(udata);
        if (connectId != lastConnectId) {
            // This can happen with file descriptor reuse, where the connect completion was for a now-closed channel.
            logger().debug("Ignoring connect completion for unrecognized connect call id: " +
                            "{} for fd {} (last connect id: {})",
                    connectId, fd().intValue(), lastConnectId);
            return;
        }
        currentCompletionResult = res;
        if (connectRemoteAddressMem != null) { // Can be null if we connected with TCP Fast Open.
            SilentDispose.dispose(connectRemoteAddressMem, logger());
            connectRemoteAddressMem = null;
        }
        finishConnect();
    }

    @Override
    protected boolean doFinishConnect(SocketAddress requestedRemoteAddress) throws Exception {
        int res = currentCompletionResult;
        currentCompletionResult = 0;
        currentCompletionData = 0;
        if (res < 0) {
            var nativeException = Errors.newIOException("connect", res);
            if (res == Errors.ERROR_ECONNREFUSED_NEGATIVE) {
                ConnectException refused = new ConnectException(nativeException.getMessage());
                refused.initCause(nativeException);
                throw refused;
            }
            if (res == Errors.ERROR_EHOSTUNREACH_NEGATIVE) { // EHOSTUNREACH: No route to host
                NoRouteToHostException unreach = new NoRouteToHostException(nativeException.getMessage());
                unreach.initCause(nativeException);
                throw unreach;
            }
            SocketException exception = new SocketException(nativeException.getMessage());
            exception.initCause(nativeException);
            throw exception;
        }
        if (fetchLocalAddress()) {
            local = socket.localAddress();
        }
        if (socket.finishConnect()) {
            active = true;
            if (requestedRemoteAddress instanceof InetSocketAddress) {
                remote = computeRemoteAddr((InetSocketAddress) requestedRemoteAddress,
                        (InetSocketAddress) socket.remoteAddress());
            } else {
                remote = requestedRemoteAddress;
            }
            submitPollRdHup();
            if (readPendingConnect) {
                submitRead();
                readPendingConnect = false;
            }
            return true;
        }
        return false;
    }

    private void submitPollRdHup() {
        submissionQueue.addPollRdHup(fd().intValue());
        scheduledRdHup = true;
    }

    void completeRdHup(int res) {
        if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
            return;
        }
        receivedRdHup = true;
        scheduledRdHup = false;
        if (active && readsPending.isEmpty()) {
            // Schedule a read to drain inbound buffer and notice the EOF.
            submitNonBlockingRead();
        }
    }

    void completeChannelRegister(SubmissionQueue submissionQueue) {
        this.submissionQueue = submissionQueue;
        if (active) {
            submitPollRdHup();
        }
        if (readPendingRegister) {
            readPendingRegister = false;
            read();
        }
    }

    @Override
    protected void doDisconnect() throws Exception {
        active = false;
    }

    @Override
    protected Future<Executor> prepareToClose() {
        // Prevent more operations from being submitted.
        active = false;
        // Cancel any pending connect.
        if (isConnectPending()) {
            submissionQueue.addCancel(fd().intValue(),
                    UserData.encode(fd().intValue(), Native.IORING_OP_CONNECT, lastConnectId));
        }
        // Cancel all pending reads.
        doClearScheduledRead();
        // Cancel any RDHUP poll
        if (scheduledRdHup) {
            submissionQueue.addPollRemove(fd().intValue(), Native.POLLRDHUP);
        }
        closeTransportNow();
        return prepareClosePromise.asFuture();
    }

    @Override
    protected void doClose() {
        tryDisposeAll(readsPending);
        tryDisposeAll(readsCompleted);
        if (connectRemoteAddressMem != null) {
            SilentDispose.trySilentDispose(connectRemoteAddressMem, logger());
            connectRemoteAddressMem = null;
        }
    }

    private void tryDisposeAll(ObjectRing<?> ring) {
        while (ring.poll()) {
            SilentDispose.trySilentDispose(ring.getPolledObject(), logger());
        }
    }

    void closeTransportNow() {
        if (!submittedClose) {
            submissionQueue.addClose(socket.intValue(), false, (short) 0);
            submittedClose = true;
        } else {
            logger().warn("Double-close attempted for {}", this);
        }
    }

    void closeComplete(int res, long udata) {
        if (socket.markClosed()) {
            prepareClosePromise.setSuccess(executor());
        }
    }

    @Override
    protected abstract void doShutdown(ChannelShutdownDirection direction) throws Exception;

    @Override
    public abstract boolean isShutdown(ChannelShutdownDirection direction);

    @Override
    public FileDescriptor fd() {
        return socket;
    }

    @Override
    public boolean isOpen() {
        return socket.isOpen();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(fd: " + socket.intValue() + ')' + super.toString();
    }

    protected abstract Logger logger();

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T getExtendedOption(ChannelOption<T> option) {
        if (option == ChannelOption.SO_BROADCAST) {
            return (T) Boolean.valueOf(isBroadcast());
        }
        if (option == ChannelOption.SO_RCVBUF) {
            return (T) Integer.valueOf(getReceiveBufferSize());
        }
        if (option == ChannelOption.SO_SNDBUF) {
            return (T) Integer.valueOf(getSendBufferSize());
        }
        if (option == ChannelOption.SO_LINGER) {
            return (T) Integer.valueOf(getSoLinger());
        }
        if (option == ChannelOption.SO_REUSEADDR) {
            return (T) Boolean.valueOf(isReuseAddress());
        }
        if (option == ChannelOption.IP_MULTICAST_LOOP_DISABLED) {
            return (T) Boolean.valueOf(isLoopbackModeDisabled());
        }
        if (option == ChannelOption.IP_MULTICAST_IF) {
            return (T) getNetworkInterface();
        }
        if (option == ChannelOption.IP_MULTICAST_TTL) {
            return (T) Integer.valueOf(getTimeToLive());
        }
        if (option == ChannelOption.IP_TOS) {
            return (T) Integer.valueOf(getTrafficClass());
        }
        if (option == UnixChannelOption.SO_REUSEPORT) {
            return (T) Boolean.valueOf(isReusePort());
        }
        return super.getExtendedOption(option);
    }

    @Override
    protected <T> void setExtendedOption(ChannelOption<T> option, T value) {
        if (option == ChannelOption.SO_BROADCAST) {
            setBroadcast((Boolean) value);
        } else if (option == ChannelOption.SO_RCVBUF) {
            setReceiveBufferSize((Integer) value);
        } else if (option == ChannelOption.SO_SNDBUF) {
            setSendBufferSize((Integer) value);
        } else if (option == ChannelOption.SO_LINGER) {
            setSoLinger((Integer) value);
        } else if (option == ChannelOption.SO_REUSEADDR) {
            setReuseAddress((Boolean) value);
        } else if (option == ChannelOption.IP_MULTICAST_LOOP_DISABLED) {
            setLoopbackModeDisabled((Boolean) value);
        } else if (option == ChannelOption.IP_MULTICAST_IF) {
            setNetworkInterface((NetworkInterface) value);
        } else if (option == ChannelOption.IP_MULTICAST_TTL) {
            setTimeToLive((Integer) value);
        } else if (option == ChannelOption.IP_TOS) {
            setTrafficClass((Integer) value);
        } else if (option == UnixChannelOption.SO_REUSEPORT) {
            setReusePort((Boolean) value);
        } else {
            super.setExtendedOption(option, value);
        }
    }

    @Override
    protected boolean isExtendedOptionSupported(ChannelOption<?> option) {
        if (option == ChannelOption.SO_BROADCAST ||
                option == ChannelOption.SO_RCVBUF ||
                option == ChannelOption.SO_SNDBUF ||
                option == ChannelOption.SO_LINGER ||
                option == ChannelOption.SO_REUSEADDR ||
                option == ChannelOption.IP_MULTICAST_LOOP_DISABLED ||
                option == ChannelOption.IP_MULTICAST_IF ||
                option == ChannelOption.IP_MULTICAST_TTL ||
                option == ChannelOption.IP_TOS ||
                option == UnixChannelOption.SO_REUSEPORT) {
            return true;
        }
        return super.isExtendedOptionSupported(option);
    }

    private int getSendBufferSize() {
        try {
            return socket.getSendBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSendBufferSize(int sendBufferSize) {
        try {
            socket.setSendBufferSize(sendBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getSoLinger() {
        try {
            return socket.getSoLinger();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSoLinger(int soLinger) {
        try {
            socket.setSoLinger(soLinger);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getReceiveBufferSize() {
        try {
            return socket.getReceiveBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReceiveBufferSize(int receiveBufferSize) {
        try {
            socket.setReceiveBufferSize(receiveBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getTrafficClass() {
        try {
            return socket.getTrafficClass();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTrafficClass(int trafficClass) {
        try {
            socket.setTrafficClass(trafficClass);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isReuseAddress() {
        try {
            return socket.isReuseAddress();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReuseAddress(boolean reuseAddress) {
        try {
            socket.setReuseAddress(reuseAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isBroadcast() {
        try {
            return socket.isBroadcast();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setBroadcast(boolean broadcast) {
        try {
            socket.setBroadcast(broadcast);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isLoopbackModeDisabled() {
        try {
            return socket.isLoopbackModeDisabled();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setLoopbackModeDisabled(boolean loopbackModeDisabled) {
        try {
            socket.setLoopbackModeDisabled(loopbackModeDisabled);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getTimeToLive() {
        try {
            return socket.getTimeToLive();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTimeToLive(int ttl) {
        try {
            socket.setTimeToLive(ttl);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    protected NetworkInterface getNetworkInterface() {
        try {
            return socket.getNetworkInterface();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setNetworkInterface(NetworkInterface networkInterface) {
        try {
            socket.setNetworkInterface(networkInterface);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if the SO_REUSEPORT option is set.
     */
    private boolean isReusePort() {
        try {
            return socket.isReusePort();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the SO_REUSEPORT option on the underlying Channel. This will allow to bind multiple
     * {@link IOUringDatagramChannel}s to the same port and so accept connections with multiple threads.
     * <p>
     * Be aware this method needs be called before {@link IOUringDatagramChannel#bind(java.net.SocketAddress)} to have
     * any affect.
     */
    private void setReusePort(boolean reusePort) {
        try {
            socket.setReusePort(reusePort);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    boolean isIpv6() {
        return socket.isIpv6();
    }

    private static final class Failure {
        final int result;

        private Failure(int result) {
            this.result = result;
        }
    }
}
