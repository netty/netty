/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.handler.traffic;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.MemoryAwareThreadPoolExecutor;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.util.ObjectSizeEstimator;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

/**
 * <p>This implementation of the {@link AbstractTrafficShapingHandler} is for channel
 * traffic shaping, that is to say a per channel limitation of the bandwidth.</p>
 *
 * The general use should be as follow:<br>
 * <ul>
 * <li><p>Add in your pipeline a new ChannelTrafficShapingHandler, before a recommended {@link ExecutionHandler} (like
 * {@link OrderedMemoryAwareThreadPoolExecutor} or {@link MemoryAwareThreadPoolExecutor}).</p>
 * <p><tt>ChannelTrafficShapingHandler myHandler = new ChannelTrafficShapingHandler(timer);</tt></p>
 * <p>timer could be created using <tt>HashedWheelTimer</tt></p>
 * <p><tt>pipeline.addLast("CHANNEL_TRAFFIC_SHAPING", myHandler);</tt></p>
 *
 * <p><b>Note that this handler has a Pipeline Coverage of "one" which means a new handler must be created
 * for each new channel as the counter cannot be shared among all channels.</b> For instance, if you have a
 * {@link ChannelPipelineFactory}, you should create a new ChannelTrafficShapingHandler in this
 * {@link ChannelPipelineFactory} each time getPipeline() method is called.</p>
 *
 * <p>Other arguments can be passed like write or read limitation (in bytes/s where 0 means no limitation)
 * or the check interval (in millisecond) that represents the delay between two computations of the
 * bandwidth and so the call back of the doAccounting method (0 means no accounting at all).</p>
 *
 * <p>A value of 0 means no accounting for checkInterval. If you need traffic shaping but no such accounting,
 * it is recommended to set a positive value, even if it is high since the precision of the
 * Traffic Shaping depends on the period where the traffic is computed. The highest the interval,
 * the less precise the traffic shaping will be. It is suggested as higher value something close
 * to 5 or 10 minutes.</p>
 *
 * <p>maxTimeToWait, by default set to 15s, allows to specify an upper bound of time shaping.</p>
 * </li>
 * <li>When you shutdown your application, release all the external resources (except the timer internal itself)
 * by calling:<br>
 * <tt>myHandler.releaseExternalResources();</tt><br>
 * </li>
 * <li>In your handler, you should consider to use the <code>channel.isWritable()</code> and
 * <code>channelInterestChanged(ctx, event)</code> to handle writability, or through
 * <code>future.addListener(new ChannelFutureListener())</code> on the future returned by
 * <code>channel.write()</code>.</li>
 * <li><p>You shall also consider to have object size in read or write operations relatively adapted to
 * the bandwidth you required: for instance having 10 MB objects for 10KB/s will lead to burst effect,
 * while having 100 KB objects for 1 MB/s should be smoothly handle by this TrafficShaping handler.</p></li>
 * <li><p>Some configuration methods will be taken as best effort, meaning
 * that all already scheduled traffics will not be
 * changed, but only applied to new traffics.</p>
 * So the expected usage of those methods are to be used not too often,
 * accordingly to the traffic shaping configuration.</li>
 * </ul><br>
 */
public class ChannelTrafficShapingHandler extends AbstractTrafficShapingHandler {
    private final List<ToSend> messagesQueue = new LinkedList<ToSend>();
    private long queueSize;
    private volatile Timeout writeTimeout;
    private volatile ChannelHandlerContext ctx;

    public ChannelTrafficShapingHandler(Timer timer, long writeLimit,
            long readLimit, long checkInterval) {
        super(timer, writeLimit, readLimit, checkInterval);
    }

    public ChannelTrafficShapingHandler(Timer timer, long writeLimit,
            long readLimit, long checkInterval, long maxTime) {
        super(timer, writeLimit, readLimit, checkInterval, maxTime);
    }

    public ChannelTrafficShapingHandler(Timer timer, long writeLimit,
            long readLimit) {
        super(timer, writeLimit, readLimit);
    }

    public ChannelTrafficShapingHandler(Timer timer, long checkInterval) {
        super(timer, checkInterval);
    }

    public ChannelTrafficShapingHandler(Timer timer) {
        super(timer);
    }

    public ChannelTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long writeLimit, long readLimit, long checkInterval) {
        super(objectSizeEstimator, timer, writeLimit, readLimit,
                checkInterval);
    }

    public ChannelTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long writeLimit, long readLimit, long checkInterval, long maxTime) {
        super(objectSizeEstimator, timer, writeLimit, readLimit,
                checkInterval, maxTime);
    }

    public ChannelTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long writeLimit, long readLimit) {
        super(objectSizeEstimator, timer, writeLimit, readLimit);
    }

    public ChannelTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long checkInterval) {
        super(objectSizeEstimator, timer, checkInterval);
    }

    public ChannelTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer) {
        super(objectSizeEstimator, timer);
    }

    private static final class ToSend {
        final long relativeTimeAction;
        final MessageEvent toSend;

        private ToSend(final long delay, final MessageEvent toSend) {
            this.relativeTimeAction = delay;
            this.toSend = toSend;
        }
    }

    @Override
    void submitWrite(final ChannelHandlerContext ctx, final MessageEvent evt, final long size,
            final long delay, final long now) throws Exception {
        if (ctx == null) {
            this.ctx = ctx;
        }
        final ToSend newToSend;
        Channel channel = ctx.getChannel();
        synchronized (this) {
            if (delay == 0 && messagesQueue.isEmpty()) {
                if (! channel.isConnected()) {
                    // ignore
                    return;
                }
                if (trafficCounter != null) {
                    trafficCounter.bytesRealWriteFlowControl(size);
                }
                ctx.sendDownstream(evt);
                return;
            }
            if (timer == null) {
                // Sleep since no executor
                Thread.sleep(delay);
                if (! channel.isConnected()) {
                    // ignore
                    return;
                }
                if (trafficCounter != null) {
                    trafficCounter.bytesRealWriteFlowControl(size);
                }
                ctx.sendDownstream(evt);
                return;
            }
            if (! channel.isConnected()) {
                // ignore
                return;
            }
            newToSend = new ToSend(delay + now, evt);
            messagesQueue.add(newToSend);
            queueSize += size;
            checkWriteSuspend(ctx, delay, queueSize);
        }
        final long futureNow = newToSend.relativeTimeAction;
        writeTimeout = timer.newTimeout(new TimerTask() {
            public void run(Timeout timeout) throws Exception {
                sendAllValid(ctx, futureNow);
            }
        }, delay + 1, TimeUnit.MILLISECONDS);
    }

    private void sendAllValid(ChannelHandlerContext ctx, final long now) throws Exception {
        Channel channel = ctx.getChannel();
        if (! channel.isConnected()) {
            // ignore
            return;
        }
        synchronized (this) {
            while (!messagesQueue.isEmpty()) {
                ToSend newToSend = messagesQueue.remove(0);
                if (newToSend.relativeTimeAction <= now) {
                    long size = calculateSize(newToSend.toSend.getMessage());
                    if (trafficCounter != null) {
                        trafficCounter.bytesRealWriteFlowControl(size);
                    }
                    queueSize -= size;
                    if (! channel.isConnected()) {
                        // ignore
                        break;
                    }
                    ctx.sendDownstream(newToSend.toSend);
                } else {
                    messagesQueue.add(0, newToSend);
                    break;
                }
            }
            if (messagesQueue.isEmpty()) {
                releaseWriteSuspended(ctx);
            }
        }
    }

   /**
    * @return current size in bytes of the write buffer
    */
   public long queueSize() {
       return queueSize;
   }

   @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        if (trafficCounter != null) {
            trafficCounter.stop();
        }
        synchronized (this) {
            messagesQueue.clear();
        }
        if (writeTimeout != null) {
            writeTimeout.cancel();
        }
        super.channelClosed(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        this.ctx = ctx;
        // readSuspended = true;
        ReadWriteStatus rws = checkAttachment(ctx);
        rws.readSuspend = true;
        ctx.getChannel().setReadable(false);
        if (trafficCounter == null) {
            // create a new counter now
            if (timer != null) {
                trafficCounter = new TrafficCounter(this, timer, "ChannelTC" +
                        ctx.getChannel().getId(), checkInterval);
            }
        }
        if (trafficCounter != null) {
            trafficCounter.start();
        }
        rws.readSuspend = false;
        ctx.getChannel().setReadable(true);
        super.channelConnected(ctx, e);
    }

    @Override
    public void releaseExternalResources() {
        Channel channel = ctx.getChannel();
        synchronized (this) {
            if (ctx != null && ctx.getChannel().isConnected()) {
                for (ToSend toSend : messagesQueue) {
                    if (! channel.isConnected()) {
                        // ignore
                        break;
                    }
                    ctx.sendDownstream(toSend.toSend);
                }
            }
            messagesQueue.clear();
        }
        if (writeTimeout != null) {
            writeTimeout.cancel();
        }
        super.releaseExternalResources();
    }

}
