/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.channel.socket.nio;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.gleamynode.netty.array.ByteArray;
import net.gleamynode.netty.array.HeapByteArray;
import net.gleamynode.netty.array.PartialByteArray;
import net.gleamynode.netty.channel.AbstractChannelPipelineSink;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelException;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.ChannelState;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.DefaultChannelStateEvent;
import net.gleamynode.netty.channel.DefaultMessageEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.pipeline.Pipeline;
import net.gleamynode.netty.util.NamePreservingRunnable;

class NioServerSocketPipelineSink extends AbstractChannelPipelineSink {

    static final Logger logger =
        Logger.getLogger(NioServerSocketPipelineSink.class.getName());
    private static final AtomicInteger nextId = new AtomicInteger();

    final int id = nextId.incrementAndGet();
    final Executor workerExecutor;
    final Worker[] workers;
    private final AtomicInteger workerIndex = new AtomicInteger();

    NioServerSocketPipelineSink(Executor workerExecutor, int workerCount) {
        this.workerExecutor = workerExecutor;
        workers = new Worker[workerCount];
        for (int i = 0; i < workers.length; i ++) {
            workers[i] = new Worker(i + 1);
        }
    }

    public void elementSunk(
            Pipeline<ChannelEvent> pipeline, ChannelEvent element) throws Exception {
        Channel channel = element.getChannel();
        if (channel instanceof NioServerSocketChannel) {
            handleServerSocket(element);
        } else if (channel instanceof NioAcceptedSocketChannel) {
            handleAcceptedSocket(element);
        }
    }

    private void handleServerSocket(ChannelEvent element) {
        if (!(element instanceof ChannelStateEvent)) {
            return;
        }

        ChannelStateEvent event = (ChannelStateEvent) element;
        NioServerSocketChannel channel =
            (NioServerSocketChannel) event.getChannel();
        ChannelFuture future = event.getFuture();
        ChannelState state = event.getState();
        Object value = event.getValue();

        switch (state) {
        case OPEN:
            if (Boolean.FALSE.equals(value)) {
                close(channel, future);
            }
            break;
        case BOUND:
            if (value != null) {
                bind(channel, future, (SocketAddress) value);
            } else {
                close(channel, future);
            }
            break;
        }
    }

    private void handleAcceptedSocket(ChannelEvent element) {
        if (element instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) element;
            NioAcceptedSocketChannel channel =
                (NioAcceptedSocketChannel) event.getChannel();
            ChannelFuture future = event.getFuture();
            ChannelState state = event.getState();
            Object value = event.getValue();

            switch (state) {
            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    close(channel, future);
                }
                break;
            case BOUND:
            case CONNECTED:
                if (value == null) {
                    close(channel, future);
                }
            }
        } else if (element instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) element;
            NioAcceptedSocketChannel channel =
                (NioAcceptedSocketChannel) event.getChannel();
            channel.writeBuffer.write(event);
            write(channel);
        }
    }

    private void bind(
            NioServerSocketChannel channel, ChannelFuture future,
            SocketAddress localAddress) {

        boolean bound = false;
        boolean bossStarted = false;
        try {
            channel.socket.socket().bind(localAddress, channel.getConfig().getBacklog());
            bound = true;

            channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                    channel, channel.succeededFuture,
                    ChannelState.BOUND, channel.getLocalAddress()));

            Executor bossExecutor =
                ((NioServerSocketChannelFactory) channel.getFactory()).bossExecutor;
            bossExecutor.execute(new NamePreservingRunnable(
                    new Boss(channel),
                    "New I/O server boss #" + id +" (channelId: " + channel.getId() +
                    ", " + channel.getLocalAddress() + ')'));
            bossStarted = true;

            future.setSuccess();
        } catch (IOException e) {
            future.setFailure(e);
        } finally {
            if (!bossStarted && bound) {
                close(channel, future);
            }
        }
    }

    private void close(NioServerSocketChannel channel, ChannelFuture future) {
        boolean bound = channel.isBound();
        try {
            channel.socket.close();
            if (channel.setClosed()) {
                if (bound) {
                    channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                            channel, channel.succeededFuture, ChannelState.BOUND, null));
                }
                channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                        channel, channel.succeededFuture, ChannelState.OPEN, Boolean.FALSE));
            }
            future.setSuccess();
        } catch (IOException e) {
            future.setFailure(e);
        }
    }

    void write(NioAcceptedSocketChannel channel) {
        if (channel.writeBuffer.empty() && channel.currentWriteEvent == null) {
            return;
        }

        boolean addOpWrite = false;
        boolean removeOpWrite = false;

        final int maxWrittenBytes;
        if (channel.getConfig().isReadWriteFair()) {
            // Set limitation for the number of written bytes for read-write
            // fairness.  I used maxReadBufferSize * 3 / 2, which yields best
            // performance in my experience while not breaking fairness much.
            int previousReceiveBufferSize =
                channel.getConfig().getReceiveBufferSizePredictor().nextReceiveBufferSize();
            maxWrittenBytes = previousReceiveBufferSize + previousReceiveBufferSize >>> 1;
        } else {
            maxWrittenBytes = Integer.MAX_VALUE;
        }
        int writtenBytes = 0;

        synchronized (channel.writeBuffer) {
            for (;;) {
                if (channel.writeBuffer.empty() && channel.currentWriteEvent == null) {
                    removeOpWrite = true;
                    break;
                }

                if (channel.currentWriteEvent == null) {
                    channel.currentWriteEvent = channel.writeBuffer.read();
                    channel.currentWriteIndex =
                        ((ByteArray) channel.currentWriteEvent.getMessage()).firstIndex();
                }

                ByteArray a = (ByteArray) channel.currentWriteEvent.getMessage();
                int localWrittenBytes = 0;
                try {
                    for (int i = channel.getConfig().getWriteSpinCount(); i > 0; i --) {
                        localWrittenBytes = a.copyTo(
                            channel.socket,
                            channel.currentWriteIndex,
                            Math.min(maxWrittenBytes - writtenBytes, a.length() - (channel.currentWriteIndex - a.firstIndex())));
                        if (localWrittenBytes != 0) {
                            break;
                        }
                    }
                } catch (IOException e) {
                    channel.currentWriteEvent.getFuture().setFailure(e);
                }

                writtenBytes += localWrittenBytes;
                channel.currentWriteIndex += localWrittenBytes;
                if (channel.currentWriteIndex == a.endIndex()) {
                    channel.currentWriteEvent.getFuture().setSuccess();
                    channel.currentWriteEvent = null;
                } else if (localWrittenBytes == 0 || writtenBytes < maxWrittenBytes) {
                    addOpWrite = true;
                    break;
                }
            }
        }

        if (addOpWrite) {
            setOpWrite(channel, true);
        } else if (removeOpWrite) {
            setOpWrite(channel, false);
        }
    }

    private void setOpWrite(NioAcceptedSocketChannel channel, boolean opWrite) {
        Selector selector = channel.worker.selector;
        SelectionKey key = channel.socket.keyFor(selector);
        if (opWrite) {
            if (Thread.currentThread() == channel.worker.thread) {
                int interestOps = key.interestOps();
                if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                    key.interestOps(interestOps | SelectionKey.OP_WRITE);
                }
            } else {
                synchronized (channel.worker.selectorGuard) {
                    selector.wakeup();
                    int interestOps = key.interestOps();
                    if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                        key.interestOps(interestOps | SelectionKey.OP_WRITE);
                    }
                }
            }
        } else {
            if (Thread.currentThread() == channel.worker.thread) {
                int interestOps = key.interestOps();
                if ((interestOps & SelectionKey.OP_WRITE) != 0) {
                    key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
                }
            } else {
                synchronized (channel.worker.selectorGuard) {
                    selector.wakeup();
                    int interestOps = key.interestOps();
                    if ((interestOps & SelectionKey.OP_WRITE) != 0) {
                        key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
                    }
                }
            }
        }
    }

    void close(NioAcceptedSocketChannel channel, ChannelFuture future) {
        Selector selector = channel.worker.selector;
        SelectionKey key = channel.socket.keyFor(selector);
        if (key != null) {
            key.cancel();
        }

        boolean connected = channel.isConnected();
        boolean bound = channel.isBound();
        try {
            channel.socket.close();
            if (channel.setClosed()) {
                if (connected) {
                    channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                            channel, channel.succeededFuture, ChannelState.CONNECTED, null));
                }
                if (bound) {
                    channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                            channel, channel.succeededFuture, ChannelState.BOUND, null));
                }
                channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                        channel, channel.succeededFuture, ChannelState.OPEN, Boolean.FALSE));
            }
            future.setSuccess();
        } catch (IOException e) {
            future.setFailure(e);
        }
    }

    Worker nextWorker() {
        return workers[Math.abs(
                workerIndex.getAndIncrement() % workers.length)];
    }

    private class Boss implements Runnable {
        private final NioServerSocketChannel channel;
        volatile boolean started;

        Boss(NioServerSocketChannel channel) {
            this.channel = channel;
        }

        public void run() {
            started = true;
            do {
                try {
                    SocketChannel acceptedSocket = channel.socket.accept();
                    try {
                        Pipeline<ChannelEvent> pipeline =
                            channel.getConfig().getPipelineFactory().getPipeline();
                        pipeline.setSink(channel.factory.sink);
                        Worker worker = nextWorker();
                        worker.register(new NioAcceptedSocketChannel(
                                        channel.factory, channel,
                                        acceptedSocket, worker, pipeline));
                    } catch (Exception e) {
                        logger.log(
                                Level.WARNING,
                                "Failed to initialize an accepted socket.",
                                e);
                        try {
                            acceptedSocket.close();
                        } catch (IOException e2) {
                            logger.log(
                                    Level.WARNING,
                                    "Failed to close a partially accepted socket.",
                                    e2);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // Thrown every second to stop when requested.
                } catch (IOException e) {
                    logger.log(
                            Level.WARNING,
                            "Failed to accept a connection.", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e1) {
                        // Ignore
                    }
                }
            } while (started);
        }
    }

    class Worker implements Runnable {

        private final int id;
        private final AtomicBoolean started = new AtomicBoolean();
        volatile Thread thread;
        volatile Selector selector;
        final Object selectorGuard = new Object();

        Worker(int id) {
            this.id = id;
        }

        void register(NioAcceptedSocketChannel channel) {
            boolean firstChannel = started.compareAndSet(false, true);
            Selector selector;
            if (firstChannel) {
                try {
                    this.selector = selector = Selector.open();
                } catch (IOException e) {
                    throw new ChannelException(
                            "Failed to create a selector.", e);
                }
            } else {
                selector = this.selector;
                if (selector == null) {
                    do {
                        Thread.yield();
                        selector = this.selector;
                    } while (selector == null);
                }
            }

            Pipeline<ChannelEvent> pipeline = channel.getPipeline();
            ChannelFuture succeededFuture = channel.succeededFuture;

            if (firstChannel) {
                try {
                    channel.socket.register(selector, SelectionKey.OP_READ, channel);
                } catch (ClosedChannelException e) {
                    throw new ChannelException(
                            "Failed to register a socket to the selector.", e);
                }

                pipeline.sendUpstream(new DefaultChannelStateEvent(
                        channel, succeededFuture, ChannelState.OPEN, Boolean.TRUE));
                pipeline.sendUpstream(new DefaultChannelStateEvent(
                        channel, succeededFuture, ChannelState.BOUND, channel.getLocalAddress()));
                pipeline.sendUpstream(new DefaultChannelStateEvent(
                        channel, succeededFuture, ChannelState.CONNECTED, channel.getRemoteAddress()));

                workerExecutor.execute(new NamePreservingRunnable(
                        this,
                        "New I/O server worker #" +
                        NioServerSocketPipelineSink.this.id + " (" +
                        id + " / " + workers.length + ')'));
            } else {
                synchronized (selectorGuard) {
                    selector.wakeup();
                    try {
                        channel.socket.register(selector, SelectionKey.OP_READ, channel);
                    } catch (ClosedChannelException e) {
                        throw new ChannelException(
                                "Failed to register a socket to the selector.", e);
                    }

                    pipeline.sendUpstream(new DefaultChannelStateEvent(
                            channel, succeededFuture, ChannelState.OPEN, Boolean.TRUE));
                    pipeline.sendUpstream(new DefaultChannelStateEvent(
                            channel, succeededFuture, ChannelState.BOUND, channel.getLocalAddress()));
                    pipeline.sendUpstream(new DefaultChannelStateEvent(
                            channel, succeededFuture, ChannelState.CONNECTED, channel.getRemoteAddress()));
                }
            }
        }

        public void run() {
            thread = Thread.currentThread();

            boolean shutdown = false;
            Selector selector = this.selector;
            for (;;) {
                synchronized (selectorGuard) {
                    // This empty synchronization block prevents the selector
                    // from acquiring its lock.
                }
                try {
                    int selectedKeyCount = selector.select(500);
                    if (selectedKeyCount > 0) {
                        processSelectedKeys(selector.selectedKeys());
                    }

                    if (selector.keys().isEmpty()) {
                        if (shutdown) {
                            try {
                                selector.close();
                            } catch (IOException e) {
                                logger.log(
                                        Level.WARNING,
                                        "Failed to close a selector.", e);
                            } finally {
                                this.selector = null;
                            }
                            started.set(false);
                            break;
                        } else {
                            // Give one more second.
                            shutdown = true;
                        }
                    }
                } catch (Throwable t) {
                    logger.log(
                            Level.WARNING,
                            "Unexpected exception in the selector loop.", t);

                    // Prevent possible consecutive immediate failures.
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore.
                    }
                }
            }
        }

        private void processSelectedKeys(Set<SelectionKey> selectedKeys) {
            for (Iterator<SelectionKey> i = selectedKeys.iterator(); i.hasNext();) {
                SelectionKey k = i.next();
                i.remove();

                if (!k.isValid()) {
                    close(k);
                    continue;
                }

                if (k.isReadable()) {
                    read(k);
                }

                if (!k.isValid()) {
                    close(k);
                    continue;
                }

                if (k.isWritable()) {
                    write(k);
                }
            }
        }

        private void read(SelectionKey k) {
            ReadableByteChannel ch = (ReadableByteChannel) k.channel();
            NioAcceptedSocketChannel channel = (NioAcceptedSocketChannel) k.attachment();

            ReceiveBufferSizePredictor predictor =
                channel.getConfig().getReceiveBufferSizePredictor();
            ByteBuffer buf = ByteBuffer.allocate(predictor.nextReceiveBufferSize());

            int ret = 0;
            int readBytes = 0;
            boolean failure = true;
            try {
                while ((ret = ch.read(buf)) > 0) {
                    readBytes += ret;
                    if (!buf.hasRemaining()) {
                        break;
                    }
                }
                failure = false;
            } catch (IOException e) {
                logger.log(
                        Level.WARNING,
                        "Failed to read from a socket.", e);
            }

            if (ret < 0 || failure) {
                close(k);
            } else if (readBytes > 0) {
                // Update the predictor.
                predictor.previousReceiveBufferSize(readBytes);

                // Fire the event.
                ByteArray array;
                if (readBytes == buf.capacity()) {
                    array = new HeapByteArray(buf.array());
                } else {
                    array = new PartialByteArray(new HeapByteArray(buf.array()), 0, readBytes);
                }
                channel.getPipeline().sendUpstream(new DefaultMessageEvent(
                        channel, channel.succeededFuture, array, null));
            }
        }

        private void write(SelectionKey k) {
            NioAcceptedSocketChannel ch = (NioAcceptedSocketChannel) k.attachment();
            NioServerSocketPipelineSink.this.write(ch);
        }

        private void close(SelectionKey k) {
            NioAcceptedSocketChannel ch = (NioAcceptedSocketChannel) k.attachment();
            NioServerSocketPipelineSink.this.close(ch, ch.succeededFuture);
        }
    }
}
