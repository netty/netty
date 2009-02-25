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
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.channels.ConnectedChannel;
import org.jboss.xnio.channels.MultipointMessageChannel;
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
public class XnioChannelHandler implements IoHandler<java.nio.channels.Channel> {

    public void handleOpened(java.nio.channels.Channel channel) {
        // Get the parent channel
        XnioServerChannel parent = null;
        if (channel instanceof BoundChannel && !(channel instanceof ConnectedChannel)) {
            SocketAddress localAddress = (SocketAddress) ((BoundChannel) channel).getLocalAddress();
            parent = XnioChannelRegistry.getServerChannel(localAddress);
            if (parent == null) {
                // An accepted channel with no parent
                // probably a race condition or a port not bound by Netty.
                IoUtils.safeClose(channel);
                return;
            }
        }

        if (parent != null) {
            if (parent.xnioChannel instanceof MultipointMessageChannel) {
                // Multipoint channel
                XnioChannelRegistry.registerChannelMapping(parent);
            } else {
                // Accepted child channel
                try {
                    XnioChannel c = new XnioAcceptedChannel(
                            parent, parent.getFactory(),
                            parent.getConfig().getPipelineFactory().getPipeline(),
                            parent.getFactory().sink);
                    c.xnioChannel = channel;
                    fireChannelOpen(c);
                    if (c.isBound()) {
                        fireChannelBound(c, c.getLocalAddress());
                        if (c.isConnected()) {
                            fireChannelConnected(c, c.getRemoteAddress());
                        }
                    }
                    XnioChannelRegistry.registerChannelMapping(c);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        } else {
            // Connected channel
            XnioChannel c = XnioChannelRegistry.getChannel(channel);
            fireChannelOpen(c);
            if (c.isBound()) {
                fireChannelBound(c, c.getLocalAddress());
                if (c.isConnected()) {
                    fireChannelConnected(c, c.getRemoteAddress());
                }
            }
        }

        // Start to read.
        resumeRead(channel);
    }

    public void handleReadable(java.nio.channels.Channel channel) {
        XnioChannel c = XnioChannelRegistry.getChannel(channel);

        boolean closed = false;
        // TODO: Use ReceiveBufferSizePredictor
        ChannelBuffer buf = c.getConfig().getBufferFactory().getBuffer(2048);
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
        XnioChannel c = XnioChannelRegistry.getChannel(channel);
        if (channel instanceof GatheringByteChannel) {
            boolean open = true;
            boolean addOpWrite = false;

            MessageEvent evt;
            ChannelBuffer buf;
            int bufIdx;
            int writtenBytes = 0;

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
                        // TODO: Use configuration
                        final int writeSpinCount = 4;
                        for (int i = writeSpinCount; i > 0; i --) {
                            int localWrittenBytes = buf.getBytes(
                                bufIdx,
                                (GatheringByteChannel) channel,
                                buf.writerIndex() - bufIdx);

                            if (localWrittenBytes != 0) {
                                bufIdx += localWrittenBytes;
                                writtenBytes += localWrittenBytes;
                                break;
                            }
                        }

                        if (bufIdx == buf.writerIndex()) {
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

            fireWriteComplete(c, writtenBytes);

            if (open) {
                if (addOpWrite && channel instanceof SuspendableWriteChannel) {
                    ((SuspendableWriteChannel) channel).resumeWrites();
                }
            }
        } else if (channel instanceof MultipointWritableMessageChannel) {
            // TODO implement me
        } else if (channel instanceof WritableMessageChannel) {
            // TODO implement me
        }
    }

    public void handleClosed(java.nio.channels.Channel channel) {
        close(XnioChannelRegistry.getChannel(channel));
    }

    private void resumeRead(java.nio.channels.Channel channel) {
        if (channel instanceof SuspendableReadChannel) {
            ((SuspendableReadChannel) channel).resumeReads();
        }
    }

    private void close(XnioChannel c) {
        if (c != null) {
            c.closeNow(c.getCloseFuture());
        }
    }
}
