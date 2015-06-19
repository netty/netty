/*
 * Copyright 2015 The Netty Project
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
package org.jboss.netty.handler.execution;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.util.ObjectSizeEstimator;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * This is a <b>fair</b> alternative of {@link OrderedDownstreamThreadPoolExecutor} .
 * <p> For more information about how the order is preserved
 * see {@link FairOrderedMemoryAwareThreadPoolExecutor}</p>
 */
public final class FairOrderedDownstreamThreadPoolExecutor extends FairOrderedMemoryAwareThreadPoolExecutor {

    /**
     * Creates a new instance.
     *
     * @param corePoolSize the maximum number of active threads
     * @noinspection unused
     */
    public FairOrderedDownstreamThreadPoolExecutor(int corePoolSize) {
        super(corePoolSize, 0L, 0L);
    }

    /**
     * Creates a new instance.
     *
     * @param corePoolSize the maximum number of active threads
     * @param keepAliveTime the amount of time for an inactive thread to shut itself down
     * @param unit the {@link TimeUnit} of {@code keepAliveTime}
     * @noinspection unused
     */
    public FairOrderedDownstreamThreadPoolExecutor(
            int corePoolSize, long keepAliveTime, TimeUnit unit) {
        super(corePoolSize, 0L, 0L, keepAliveTime, unit);
    }

    /**
     * Creates a new instance.
     *
     * @param corePoolSize the maximum number of active threads
     * @param keepAliveTime the amount of time for an inactive thread to shut itself down
     * @param unit the {@link TimeUnit} of {@code keepAliveTime}
     * @param threadFactory the {@link ThreadFactory} of this pool
     * @noinspection unused
     */
    public FairOrderedDownstreamThreadPoolExecutor(
            int corePoolSize, long keepAliveTime, TimeUnit unit, ThreadFactory threadFactory) {
        super(corePoolSize, 0L, 0L,
              keepAliveTime, unit, threadFactory);
    }

    /**
     * Return {@code null}
     */
    @Override
    public ObjectSizeEstimator getObjectSizeEstimator() {
        return null;
    }

    /**
     * Throws {@link UnsupportedOperationException} as there is not support for limit the memory size in this
     * implementation
     */
    @Override
    public void setObjectSizeEstimator(ObjectSizeEstimator objectSizeEstimator) {
        throw new UnsupportedOperationException("Not supported by this implementation");
    }

    /**
     * Returns {@code 0L}
     */
    @Override
    public long getMaxChannelMemorySize() {
        return 0L;
    }

    /**
     * Throws {@link UnsupportedOperationException} as there is not support for limit the memory size in this
     * implementation
     */
    @Override
    public void setMaxChannelMemorySize(long maxChannelMemorySize) {
        throw new UnsupportedOperationException("Not supported by this implementation");
    }

    /**
     * Returns {@code 0L}
     */
    @Override
    public long getMaxTotalMemorySize() {
        return 0L;
    }

    /**
     * Return {@code false} as we not need to count the memory in this implementation
     */
    @Override
    protected boolean shouldCount(Runnable task) {
        return false;
    }

    @Override
    public void execute(Runnable command) {
        // check if the Runnable was of an unsupported type
        if (command instanceof ChannelUpstreamEventRunnable) {
            throw new RejectedExecutionException("command must be enclosed with an downstream event.");
        }
        doExecute(command);
    }

    /**
     * Executes the specified task concurrently while maintaining the event order.
     */
    @Override
    protected void doExecute(Runnable task) {
        if (task instanceof ChannelEventRunnable) {
            ChannelEventRunnable eventRunnable = (ChannelEventRunnable) task;
            ChannelEvent event = eventRunnable.getEvent();
            EventTask newEventTask = new EventTask(eventRunnable);

            /*
             * e.g. Three event
             * "Channel A (Event A1)","Channel A (Event A2)","Channel A (Event A3)"
             * are submitted in sequence, then key "Channel A" is refer to the
             * value of "Event A3", and there is a linked list: "Event A3" ->
             * "Event A2" -> "Event A1" ( linked by the field "next" in
             * EventTask )
             */

            final Object key = getKey(event);
            EventTask previousEventTask = map.put(key, newEventTask);

            // try to setup "previousEventTask -> newEventTask"
            // if success, then "newEventTask" will be invoke by
            // "previousEventTask"
            if (previousEventTask != null) {
                if (compareAndSetNext(previousEventTask, null, newEventTask)) {
                    return;
                }
            } else {
                // register a listener so that the ChildExecutor will get removed once the channel was closed
                event.getChannel().getCloseFuture().addListener(new ChannelFutureListener() {

                    public void operationComplete(ChannelFuture future) throws Exception {
                        removeKey(key);
                    }
                });
            }

            // Two situation here:
            // 1. "newEventTask" is the header of linked list
            // 2. the "previousEventTask.next" is already END
            // At these two situations above, just execute "newEventTask"
            // immediately
            doUnorderedExecute(newEventTask);
        } else {
            doUnorderedExecute(task);
        }
    }
}
