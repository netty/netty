/*
 * JBoss, Home of Professional Open Source Copyright 2009, Red Hat Middleware
 * LLC, and individual contributors by the @authors tag. See the copyright.txt
 * in the distribution for a full listing of individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.jboss.netty.channel.socket.nio;

import static org.jboss.netty.channel.Channels.fireChannelBound;
import static org.jboss.netty.channel.Channels.fireChannelClosed;
import static org.jboss.netty.channel.Channels.fireChannelConnected;
import static org.jboss.netty.channel.Channels.fireChannelDisconnected;
import static org.jboss.netty.channel.Channels.fireChannelInterestChanged;
import static org.jboss.netty.channel.Channels.fireChannelOpen;
import static org.jboss.netty.channel.Channels.fireChannelUnbound;
import static org.jboss.netty.channel.Channels.fireExceptionCaught;
import static org.jboss.netty.channel.Channels.fireMessageReceived;
import static org.jboss.netty.channel.Channels.fireWriteComplete;
import static org.jboss.netty.channel.Channels.succeededFuture;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.LinkedTransferQueue;
import org.jboss.netty.util.ThreadRenamingRunnable;

/**
 * A NioUdpWorker thread performs non-blocking read and write operations for one
 * of more NioDatagram Channels in non-blocking mode. 
 * <p/> 
 * 
 * @author <a href="mailto:dbevenius@jboss.com">Daniel Bevenius</a>
 * 
 */
public class NioUdpWorker implements Runnable
{
    private static final int CONSTRAINT_LEVEL = NioProviderMetadata.CONSTRAINT_LEVEL;
    private static int MAX_PACKET_SIZE = 65507;

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioUdpWorker.class);

    private final int bossId;
    private final int id;
    private final Executor executor;

    private final Object startStopLock = new Object();
    private boolean started;
    private final AtomicBoolean wakenUp = new AtomicBoolean();

    private volatile Selector selector;
    private final ReadWriteLock selectorGuard = new ReentrantReadWriteLock();

    private volatile Thread thread;

    final Queue<Runnable> registerTaskQueue = new LinkedTransferQueue<Runnable>();
    final Queue<Runnable> writeTaskQueue = new LinkedTransferQueue<Runnable>();

    public NioUdpWorker(int bossId, int id, Executor workerExecutor)
    {
        this.bossId = bossId;
        this.id = id;
        this.executor = workerExecutor;
    }
    
    /**
     * Registers the passed in channel with a selector.
     * 
     * @param channel The channel to register.
     * @param future
     */
    void register(final NioDatagramChannel channel, final ChannelFuture future)
    {
        Runnable registerTask = new RegisterTask(channel, future);
        Selector selector;

        synchronized (startStopLock)
        {
            if (!started)
            {
                // Open a selector if this worker didn't start yet.
                this.selector = selector = openSelector();
                
                boolean success = false;
                try
                {
                    // Start the worker thread with the new Selector.
                    ThreadRenamingRunnable workerThread = new ThreadRenamingRunnable(this, String.format("New I/O server worker #%d'-'%d", bossId, id));
                    executor.execute(workerThread);
                    success = true;
                } 
                finally
                {
                    if (!success)
                    {
                        try
                        {
                            // Release the Selector if the execution fails.
                            selector.close();
                        } 
                        catch (final Throwable t)
                        {
                            logger.warn("Failed to close a selector.", t);
                        }
                        this.selector = selector = null;
                        // The method will return to the caller at this point.
                    }
                }
            } 
            else
            {
                // Use the existing selector if this worker has been started.
                selector = this.selector;
            }

            assert selector != null && selector.isOpen();

            started = true;
            boolean offered = registerTaskQueue.offer(registerTask);
            assert offered;
        }

        if (wakenUp.compareAndSet(false, true))
        {
            selector.wakeup();
        }
    }

    /**
     * Main selector loop. This will loop forever and call select.
     */
    public void run()
    {
        thread = Thread.currentThread();

        boolean shutdown = false;
        Selector selector = this.selector;
        for (;;)
        {
            wakenUp.set(false);

            try
            {
                int selectedKeyCount = selector.select(500);

                // Wake up immediately in the next turn if someone might
                // have waken up the selector between 'wakenUp.set(false)'
                // and 'selector.select(...)'.
                if (wakenUp.get())
                {
                    selector.wakeup();
                }

                processRegisterTaskQueue();
                processWriteTaskQueue();

                if (selectedKeyCount > 0)
                {
                    processSelectedKeys(selector.selectedKeys());
                }

                // Exit the loop when there's nothing to handle.
                // The shutdown flag is used to delay the shutdown of this
                // loop to avoid excessive Selector creation when
                // connections are registered in a one-by-one manner instead of
                // concurrent manner.
                if (selector.keys().isEmpty())
                {
                    if (shutdown || executor instanceof ExecutorService && ((ExecutorService) executor).isShutdown())
                    {
                        synchronized (startStopLock)
                        {
                            if (registerTaskQueue.isEmpty() && selector.keys().isEmpty())
                            {
                                started = false;
                                try
                                {
                                    selector.close();
                                } 
                                catch (final IOException e)
                                {
                                    logger.warn("Failed to close a selector.", e);
                                } 
                                finally
                                {
                                    this.selector = null;
                                }
                                break;
                            } 
                            else
                            {
                                shutdown = false;
                            }
                        }
                    } 
                    else
                    {
                        // Give one more second.
                        shutdown = true;
                    }
                } 
                else
                {
                    shutdown = false;
                }
            } 
            catch (final Throwable t)
            {
                logger.warn("Unexpected exception in the selector loop.", t);

                // Prevent possible consecutive immediate failures that lead to
                // excessive CPU consumption.
                try
                {
                    Thread.sleep(1000);
                } 
                catch (final InterruptedException ignore)
                {
                    //Thread.currentThread().interrupt();
                }
            }
        }
    }

    private Selector openSelector()
    {
        try
        {
            return Selector.open();
        } 
        catch (final Throwable t)
        {
            throw new ChannelException("Failed to create a selector.", t);
        }
    }

    private void processRegisterTaskQueue()
    {
        for (;;)
        {
            final Runnable task = registerTaskQueue.poll();
            if (task == null)
            {
                break;
            }

            task.run();
        }
    }

    private void processWriteTaskQueue()
    {
        for (;;)
        {
            final Runnable task = writeTaskQueue.poll();
            if (task == null)
            {
                break;
            }

            task.run();
        }
    }

    private static void processSelectedKeys(final Set<SelectionKey> selectedKeys)
    {
        for (Iterator<SelectionKey> i = selectedKeys.iterator(); i.hasNext();)
        {
            SelectionKey key = i.next();
            try
            {
                if (key.isReadable())
                {
                    boolean read = read(key);
                    if (read)
                    {
                        i.remove();
                    }
                }
                else
                {
                    i.remove();
                }
            } 
            catch (final CancelledKeyException e)
            {
                close(key);
            }
        }
    }

    static void write(final NioDatagramChannel channel, final ChannelFuture future, final Object message, final SocketAddress remoteAddress, final boolean mightNeedWakeup)
    {
        try 
        {
            ChannelBuffer buffer = (ChannelBuffer) message;
            int length = buffer.readableBytes();
            channel.getDatagramChannel().send(buffer.toByteBuffer(), remoteAddress);
            fireWriteComplete(channel, length);
            future.setSuccess();
        } 
        catch (Throwable t) 
        {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    /**
     * Read is called when a Selector has been notified that the underlying channel
     * was something to be read. The channel would previously have registered its interest 
     * in read operations.
     * 
     * @param key The selection key which contains the Selector registration information.
     */
    private static boolean read(final SelectionKey key)
    {
        final DatagramChannel datagramChannel = (DatagramChannel) key.channel();
        final NioDatagramChannel nioDatagramChannel = (NioDatagramChannel) key.attachment();
        try 
        {
            // Allocating a non-direct buffer with a max udp packge size.
            // Would using a direct buffer be more efficient or would this negatively 
            // effect performance, as direct buffer allocation has a higher upfront cost
            // where as a ByteBuffer is heap allocated.
            final ByteBuffer byteBuffer = ByteBuffer.allocate(MAX_PACKET_SIZE);
            
            // Recieve from the channel in a non blocking mode. We have already been notified that
            // the channel is ready to receive. 
            final SocketAddress remoteAddress = datagramChannel.receive(byteBuffer);
            if (remoteAddress == null)
            {
                return false;
            }
            
            // Flip the buffer so that we can wrap it.
            byteBuffer.flip();
            // Create a Netty ChannelByffer by wrapping the ByteBuffer.
            final ChannelBuffer channelBuffer = ChannelBuffers.wrappedBuffer(byteBuffer);
            
            logger.debug("ChannelBuffer : " + channelBuffer + ", remoteAdress: " + remoteAddress);
            
            // Notify the interested parties about the newly arrived message (channelBuffer).
            fireMessageReceived(nioDatagramChannel, channelBuffer, remoteAddress);
        } 
        catch (final Throwable t) 
        {
            if (!nioDatagramChannel.getDatagramChannel().socket().isClosed()) 
            {
                fireExceptionCaught(nioDatagramChannel, t);
            }
        } 
        return true;
    }

    private static void close(final SelectionKey key)
    {
        NioDatagramChannel ch = (NioDatagramChannel) key.attachment();
        close(ch, succeededFuture(ch));
    }

    static void close(final NioDatagramChannel channel, final ChannelFuture future)
    {
        NioUdpWorker worker = channel.worker;
        Selector selector = worker.selector;
        SelectionKey key = channel.getDatagramChannel().keyFor(selector);
        if (key != null)
        {
            key.cancel();
        }

        boolean connected = channel.isConnected();
        boolean bound = channel.isBound();
        try
        {
            channel.getDatagramChannel().socket().close();
            if (channel.setClosed())
            {
                future.setSuccess();
                if (connected)
                {
                    fireChannelDisconnected(channel);
                }
                if (bound)
                {
                    fireChannelUnbound(channel);
                }

                fireChannelClosed(channel);
            } else
            {
                future.setSuccess();
            }
        } catch (final Throwable t)
        {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    static void setInterestOps(NioDatagramChannel channel, ChannelFuture future, int interestOps)
    {
        NioUdpWorker worker = channel.worker;
        Selector selector = worker.selector;
        SelectionKey key = channel.getDatagramChannel().keyFor(selector);
        if (key == null || selector == null)
        {
            Exception cause = new NotYetConnectedException();
            future.setFailure(cause);
            fireExceptionCaught(channel, cause);
        }
        
        int ops = interestOps;
        

        boolean changed = false;
        try
        {
            // interestOps can change at any time and at any thread.
            // Acquire a lock to avoid possible race condition.
            synchronized (channel.interestOpsLock)
            {
                // Override OP_WRITE flag - a user cannot change this flag.
                ops &= ~Channel.OP_WRITE;
                ops |= channel.getRawInterestOps() & Channel.OP_WRITE;

                switch (CONSTRAINT_LEVEL)
                {
                case 0:
                    if (channel.getRawInterestOps() != interestOps)
                    {
                        key.interestOps(interestOps);
                        if (Thread.currentThread() != worker.thread && worker.wakenUp.compareAndSet(false, true))
                        {
                            selector.wakeup();
                        }
                        changed = true;
                    }
                    break;
                case 1:
                case 2:
                    if (channel.getRawInterestOps() != interestOps)
                    {
                        if (Thread.currentThread() == worker.thread)
                        {
                            key.interestOps(interestOps);
                            changed = true;
                        } else
                        {
                            worker.selectorGuard.readLock().lock();
                            try
                            {
                                if (worker.wakenUp.compareAndSet(false, true))
                                {
                                    selector.wakeup();
                                }
                                key.interestOps(interestOps);
                                changed = true;
                            } finally
                            {
                                worker.selectorGuard.readLock().unlock();
                            }
                        }
                    }
                    break;
                default:
                    throw new Error();
                }
            }

            future.setSuccess();
            if (changed)
            {
                channel.setRawInterestOpsNow(interestOps);
                fireChannelInterestChanged(channel);
            }
        } catch (Throwable t)
        {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    /**
     * RegisterTask is a task responsible for registering a channel with a
     * selector.
     * 
     * @author <a href="mailto:dbevenius@jboss.com">Daniel Bevenius</a>
     * 
     */
    private final class RegisterTask implements Runnable
    {
        private final NioDatagramChannel channel;
        private final ChannelFuture future;

        RegisterTask(final NioDatagramChannel channel, final ChannelFuture future)
        {
            this.channel = channel;
            this.future = future;
        }

        /**
         * This runnable's task. Does the actual registering by calling the
         * underlying DatagramChannels peer DatagramSocket register method.
         * 
         */
        public void run()
        {
            SocketAddress localAddress = channel.getLocalAddress();
            if (localAddress == null)
            {
                if (future != null)
                {
                    future.setFailure(new ClosedChannelException());
                }
                close(channel, succeededFuture(channel));
                return;
            }

            try
            {
                // Register interest in both reads and writes.
                channel.getDatagramChannel().register(selector, SelectionKey.OP_READ, channel);
                if (future != null)
                {
                    future.setSuccess();
                }
            } 
            catch (final ClosedChannelException e)
            {
                if (future != null)
                {
                    future.setFailure(e);
                }
                close(channel, succeededFuture(channel));
                throw new ChannelException("Failed to register a socket to the selector.", e);
            }

            fireChannelOpen(channel);
            fireChannelBound(channel, localAddress);
            fireChannelConnected(channel, localAddress);
        }
    }

}
