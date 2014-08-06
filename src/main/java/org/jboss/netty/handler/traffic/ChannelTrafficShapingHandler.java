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
 * This implementation of the {@link AbstractTrafficShapingHandler} is for channel
 * traffic shaping, that is to say a per channel limitation of the bandwidth.<br><br>
 *
 * The general use should be as follow:<br>
 * <ul>
 * <li>Add in your pipeline a new ChannelTrafficShapingHandler, before a recommended {@link ExecutionHandler} (like
 * {@link OrderedMemoryAwareThreadPoolExecutor} or {@link MemoryAwareThreadPoolExecutor}).<br>
 * <tt>ChannelTrafficShapingHandler myHandler = new ChannelTrafficShapingHandler(timer);</tt><br>
 * timer could be created using <tt>HashedWheelTimer</tt><br>
 * <tt>pipeline.addLast("CHANNEL_TRAFFIC_SHAPING", myHandler);</tt><br><br>
 *
 * <b>Note that this handler has a Pipeline Coverage of "one" which means a new handler must be created
 * for each new channel as the counter cannot be shared among all channels.</b> For instance, if you have a
 * {@link ChannelPipelineFactory}, you should create a new ChannelTrafficShapingHandler in this
 * {@link ChannelPipelineFactory} each time getPipeline() method is called.<br><br>
 *
 * Other arguments can be passed like write or read limitation (in bytes/s where 0 means no limitation)
 * or the check interval (in millisecond) that represents the delay between two computations of the
 * bandwidth and so the call back of the doAccounting method (0 means no accounting at all).<br><br>
 *
 * A value of 0 means no accounting for checkInterval. If you need traffic shaping but no such accounting,
 * it is recommended to set a positive value, even if it is high since the precision of the
 * Traffic Shaping depends on the period where the traffic is computed. The highest the interval,
 * the less precise the traffic shaping will be. It is suggested as higher value something close
 * to 5 or 10 minutes.<br><br>
 *
 * maxTimeToWait, by default set to 15s, allows to specify an upper bound of time shaping.<br><br>
 * </li>
 * <li>When you shutdown your application, release all the external resources (except the timer internal itself)
 * by calling:<br>
 * <tt>myHandler.releaseExternalResources();</tt><br>
 * </li>
 * </ul><br>
 */
public class ChannelTrafficShapingHandler extends AbstractTrafficShapingHandler {
    private List<ToSend> messagesQueue = new LinkedList<ToSend>();
    private volatile Timeout writeTimeout;
    
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
        final long date;
        final MessageEvent toSend;

        private ToSend(final long delay, final MessageEvent toSend) {
            this.date = System.currentTimeMillis() + delay;
            this.toSend = toSend;
        }
    }

    @Override
    protected synchronized void submitWrite(final ChannelHandlerContext ctx, final MessageEvent evt, final long delay)
            throws Exception {
        if (delay == 0 && messagesQueue.isEmpty()) {
            internalSubmitWrite(ctx, evt);
            return;
        }
        if (timer == null) {
            // Sleep since no executor
            Thread.sleep(delay);
            internalSubmitWrite(ctx, evt);
            return;
        }
        final ToSend newToSend = new ToSend(delay, evt);
        messagesQueue.add(newToSend);
        writeTimeout = timer.newTimeout(new TimerTask() {
            public void run(Timeout timeout) throws Exception {
                sendAllValid(ctx);
            }
        }, delay+1, TimeUnit.MILLISECONDS);
    }

    private synchronized void sendAllValid(ChannelHandlerContext ctx) throws Exception {
        while (!messagesQueue.isEmpty()) {
            ToSend newToSend = messagesQueue.remove(0);
            if (newToSend.date <= System.currentTimeMillis()) {
                internalSubmitWrite(ctx, newToSend.toSend);
            } else {
                messagesQueue.add(0, newToSend);
                break;
            }
        }
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        if (trafficCounter != null) {
            trafficCounter.stop();
        }
        messagesQueue.clear();
        if (writeTimeout != null) {
            writeTimeout.cancel();
        }
        super.channelClosed(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        // readSuspended = true;
        ctx.setAttachment(Boolean.TRUE);
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
        // readSuspended = false;
        ctx.setAttachment(null);
        ctx.getChannel().setReadable(true);
        super.channelConnected(ctx, e);
    }

}
