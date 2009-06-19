/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel.xnio;

import static org.jboss.netty.channel.Channels.*;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.util.Queue;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.ReceiveBufferSizePredictor;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.channels.MultipointReadResult;
import org.jboss.xnio.channels.MultipointReadableMessageChannel;
import org.jboss.xnio.channels.MultipointWritableMessageChannel;
import org.jboss.xnio.channels.ReadableMessageChannel;
import org.jboss.xnio.channels.SuspendableReadChannel;
import org.jboss.xnio.channels.SuspendableWriteChannel;
import org.jboss.xnio.channels.WritableMessageChannel;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@SuppressWarnings("unchecked")
abstract class AbstractXnioChannelHandler implements IoHandler<java.nio.channels.Channel> {

    protected AbstractXnioChannelHandler() {
        super();
    }

    public void handleReadable(java.nio.channels.Channel channel) {
        BaseXnioChannel c = XnioChannelRegistry.getChannel(channel);

        boolean closed = false;

        ReceiveBufferSizePredictor predictor = c.getConfig().getReceiveBufferSizePredictor();
        ChannelBufferFactory bufferFactory = c.getConfig().getBufferFactory();
        ChannelBuffer buf = bufferFactory.getBuffer(predictor.nextReceiveBufferSize());

        SocketAddress remoteAddress = null;
        Throwable exception = null;
        if (channel instanceof ScatteringByteChannel) {
            try {
                while (buf.writable()) {
                    int readBytes = buf.writeBytes((ScatteringByteChannel) channel, buf.writableBytes());
                    if (readBytes == 0) {
                        break;
                    } else if (readBytes < 0) {
                        closed = true;
                        break;
                    }
                }
            } catch (IOException e) {
                exception = e;
                closed = true;
            }
        } else if (channel instanceof MultipointReadableMessageChannel) {
            ByteBuffer nioBuf = buf.toByteBuffer();
            try {
                MultipointReadResult res = ((MultipointReadableMessageChannel) channel).receive(nioBuf);
                if (res != null) {
                    buf = ChannelBuffers.wrappedBuffer(nioBuf);
                    remoteAddress = (SocketAddress) res.getSourceAddress();
                }
            } catch (IOException e) {
                exception = e;
                closed = true;
            }
        } else if (channel instanceof ReadableMessageChannel) {
            ByteBuffer nioBuf = buf.toByteBuffer();
            try {
                int readBytes = ((ReadableMessageChannel) channel).receive(nioBuf);
                if (readBytes > 0) {
                    buf = ChannelBuffers.wrappedBuffer(nioBuf);
                } else if (readBytes < 0) {
                    closed = true;
                }
            } catch (IOException e) {
                exception = e;
                closed = true;
            }
        }

        if (buf.readable()) {
            // Update the predictor.
            predictor.previousReceiveBufferSize(buf.readableBytes());

            // Fire the event.
            fireMessageReceived(c, buf, remoteAddress);
        }

        if (exception != null) {
            fireExceptionCaught(c, exception);
        }

        if (closed) {
            close(c);
        } else {
            resumeRead(channel);
        }
    }

    public void handleWritable(java.nio.channels.Channel channel) {
        BaseXnioChannel c = XnioChannelRegistry.getChannel(channel);
        int writtenBytes = 0;
        boolean open = true;
        boolean addOpWrite = false;
        MessageEvent evt;
        ChannelBuffer buf;
        int bufIdx;

        Queue<MessageEvent> writeBuffer = c.writeBuffer;
        synchronized (c.writeLock) {
            evt = c.currentWriteEvent;
            for (;;) {
                if (evt == null) {
                    evt = writeBuffer.poll();
                    if (evt == null) {
                        c.currentWriteEvent = null;
                        break;
                    }

                    buf = (ChannelBuffer) evt.getMessage();
                    bufIdx = buf.readerIndex();
                } else {
                    buf = (ChannelBuffer) evt.getMessage();
                    bufIdx = c.currentWriteIndex;
                }

                try {
                    final int writeSpinCount = c.getConfig().getWriteSpinCount();
                    boolean sent = false;
                    for (int i = writeSpinCount; i > 0; i --) {
                        if (channel instanceof GatheringByteChannel) {
                            int localWrittenBytes = buf.getBytes(
                                bufIdx,
                                (GatheringByteChannel) channel,
                                buf.writerIndex() - bufIdx);

                            if (localWrittenBytes != 0) {
                                bufIdx += localWrittenBytes;
                                writtenBytes += localWrittenBytes;
                                break;
                            }
                        } else if (channel instanceof MultipointWritableMessageChannel) {
                            ByteBuffer nioBuf = buf.toByteBuffer(bufIdx, buf.writerIndex() - bufIdx);
                            int nioBufSize = nioBuf.remaining();
                            SocketAddress remoteAddress = evt.getRemoteAddress();
                            if (remoteAddress == null) {
                                remoteAddress = c.getRemoteAddress();
                            }
                            sent = ((MultipointWritableMessageChannel) channel).send(remoteAddress, nioBuf);
                            if (sent) {
                                bufIdx += nioBufSize;
                                writtenBytes += nioBufSize;
                                break;
                            }
                        } else if (channel instanceof WritableMessageChannel) {
                            ByteBuffer nioBuf = buf.toByteBuffer(bufIdx, buf.writerIndex() - bufIdx);
                            int nioBufSize = nioBuf.remaining();
                            sent = ((WritableMessageChannel) channel).send(nioBuf);
                            if (sent) {
                                bufIdx += nioBufSize;
                                writtenBytes += nioBufSize;
                                break;
                            }
                        } else {
                            throw new IllegalArgumentException("Unsupported channel type: " + channel.getClass().getName());
                        }
                    }

                    if (bufIdx == buf.writerIndex() || sent) {
                        // Successful write - proceed to the next message.
                        evt.getFuture().setSuccess();
                        evt = null;
                    } else {
                        // Not written fully - perhaps the kernel buffer is full.
                        c.currentWriteEvent = evt;
                        c.currentWriteIndex = bufIdx;
                        addOpWrite = true;
                        break;
                    }
                } catch (AsynchronousCloseException e) {
                    // Doesn't need a user attention - ignore.
                } catch (Throwable t) {
                    evt.getFuture().setFailure(t);
                    evt = null;
                    fireExceptionCaught(c, t);
                    if (t instanceof IOException) {
                        open = false;
                        c.closeNow(succeededFuture(c));
                    }
                }
            }
        }

        if (writtenBytes > 0) {
            fireWriteComplete(c, writtenBytes);
        }

        if (open) {
            if (addOpWrite && channel instanceof SuspendableWriteChannel) {
                ((SuspendableWriteChannel) channel).resumeWrites();
            }
        }
    }

    public void handleClosed(java.nio.channels.Channel channel) {
        close(XnioChannelRegistry.getChannel(channel));
    }

    protected void resumeRead(java.nio.channels.Channel channel) {
        if (channel instanceof SuspendableReadChannel) {
            ((SuspendableReadChannel) channel).resumeReads();
        }
    }

    protected void close(BaseXnioChannel c) {
        if (c != null) {
            c.closeNow(c.getCloseFuture());
        }
    }
}
