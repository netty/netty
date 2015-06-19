/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.jboss.netty.handler.execution;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.util.ObjectSizeEstimator;
import org.jboss.netty.util.internal.ConcurrentIdentityWeakKeyHashMap;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * This is a <b>fair</b> alternative of {@link OrderedMemoryAwareThreadPoolExecutor} .
 *
 * <h3>Unfair of {@link OrderedMemoryAwareThreadPoolExecutor}</h3>
 * The task executed in {@link OrderedMemoryAwareThreadPoolExecutor} is unfair in some situations.
 * For example, let's say there is only one executor thread that handle the events from the two channels, and events
 * are submitted in sequence:
 * <pre>
 *           Channel A (Event A1) , Channel B (Event B), Channel A (Event A2) , ... , Channel A (Event An)
 * </pre>
 * Then the events maybe executed in this unfair order:
 * <pre>
 *          ----------------------------------------&gt; Timeline --------------------------------&gt;
 *           Channel A (Event A1) , Channel A (Event A2) , ... , Channel A (Event An), Channel B (Event B)
 * </pre>
 * As we see above, Channel B (Event B) maybe executed unfairly late.
 * Even more, if there are too much events come in Channel A, and one-by-one closely, then Channel B (Event B) would be
 * waiting for a long while and become "hungry".
 *
 * <h3>Fair of FairOrderedMemoryAwareThreadPoolExecutor</h3>
 * In the same case above ( one executor thread and two channels ) , this implement will guarantee execution order as:
 * <pre>
 *          ----------------------------------------&gt; Timeline --------------------------------&gt;
 *           Channel A (Event A1) , Channel B (Event B), Channel A (Event A2) , ... , Channel A (Event An),
 * </pre>
 *
 * <b>NOTE</b>: For convenience the case above use <b>one single executor thread</b>, but the fair mechanism is suitable
 * for <b>multiple executor threads</b> situations.
 */
public class FairOrderedMemoryAwareThreadPoolExecutor extends MemoryAwareThreadPoolExecutor {

    // end sign
    private final EventTask end = new EventTask(null);

    private final AtomicReferenceFieldUpdater<EventTask, EventTask> fieldUpdater =
            AtomicReferenceFieldUpdater.newUpdater(EventTask.class, EventTask.class, "next");

    protected final ConcurrentMap<Object, EventTask> map = newMap();

    /**
     * Creates a new instance.
     *
     * @param corePoolSize the maximum number of active threads
     * @param maxChannelMemorySize the maximum total size of the queued events per channel. Specify {@code 0} to
     * disable.
     * @param maxTotalMemorySize the maximum total size of the queued events for this pool Specify {@code 0} to
     * disable.
     * @noinspection unused
     */
    public FairOrderedMemoryAwareThreadPoolExecutor(int corePoolSize, long maxChannelMemorySize,
                                                    long maxTotalMemorySize) {
        super(corePoolSize, maxChannelMemorySize, maxTotalMemorySize);
    }

    /**
     * Creates a new instance.
     *
     * @param corePoolSize the maximum number of active threads
     * @param maxChannelMemorySize the maximum total size of the queued events per channel. Specify {@code 0} to
     * disable.
     * @param maxTotalMemorySize the maximum total size of the queued events for this pool Specify {@code 0} to
     * disable.
     * @param keepAliveTime the amount of time for an inactive thread to shut itself down
     * @param unit the {@link TimeUnit} of {@code keepAliveTime}
     * @noinspection unused
     */
    public FairOrderedMemoryAwareThreadPoolExecutor(int corePoolSize, long maxChannelMemorySize,
                                                    long maxTotalMemorySize, long keepAliveTime, TimeUnit unit) {
        super(corePoolSize, maxChannelMemorySize, maxTotalMemorySize, keepAliveTime, unit);
    }

    /**
     * Creates a new instance.
     *
     * @param corePoolSize the maximum number of active threads
     * @param maxChannelMemorySize the maximum total size of the queued events per channel. Specify {@code 0} to
     * disable.
     * @param maxTotalMemorySize the maximum total size of the queued events for this pool Specify {@code 0} to
     * disable.
     * @param keepAliveTime the amount of time for an inactive thread to shut itself down
     * @param unit the {@link TimeUnit} of {@code keepAliveTime}
     * @param threadFactory the {@link ThreadFactory} of this pool
     * @noinspection unused
     */
    public FairOrderedMemoryAwareThreadPoolExecutor(int corePoolSize, long maxChannelMemorySize,
                                                    long maxTotalMemorySize, long keepAliveTime, TimeUnit unit,
                                                    ThreadFactory threadFactory) {
        super(corePoolSize, maxChannelMemorySize, maxTotalMemorySize, keepAliveTime, unit,
              threadFactory);
    }

    /**
     * Creates a new instance.
     *
     * @param corePoolSize the maximum number of active threads
     * @param maxChannelMemorySize the maximum total size of the queued events per channel. Specify {@code 0} to
     * disable.
     * @param maxTotalMemorySize the maximum total size of the queued events for this pool Specify {@code 0} to
     * disable.
     * @param keepAliveTime the amount of time for an inactive thread to shut itself down
     * @param unit the {@link TimeUnit} of {@code keepAliveTime}
     * @param threadFactory the {@link ThreadFactory} of this pool
     * @param objectSizeEstimator the {@link ObjectSizeEstimator} of this pool
     * @noinspection unused
     */
    public FairOrderedMemoryAwareThreadPoolExecutor(int corePoolSize, long maxChannelMemorySize,
                                                    long maxTotalMemorySize, long keepAliveTime, TimeUnit unit,
                                                    ObjectSizeEstimator objectSizeEstimator,
                                                    ThreadFactory threadFactory) {
        super(corePoolSize, maxChannelMemorySize, maxTotalMemorySize, keepAliveTime, unit,
              objectSizeEstimator, threadFactory);
    }

    /** @noinspection WeakerAccess*/
    protected ConcurrentMap<Object, EventTask> newMap() {
        return new ConcurrentIdentityWeakKeyHashMap<Object, EventTask>();
    }

    /**
     * Executes the specified task concurrently while maintaining the event order.
     */
    @Override
    protected void doExecute(Runnable task) {
        if (task instanceof ChannelEventRunnable) {
            ChannelEventRunnable eventRunnable = (ChannelEventRunnable) task;
            EventTask newEventTask = new EventTask(eventRunnable);
            /*
              * e.g. Three event "Channel A (Event A1)","Channel A (Event A2)","Channel A (Event A3)" are
              * submitted in sequence, then key "Channel A" is refer to the value of "Event A3", and there
              * is a linked list: "Event A3" -> "Event A2" -> "Event A1" ( linked by the field "next" in
              * EventTask )
              */
            Object key = getKey(eventRunnable.getEvent());
            EventTask previousEventTask = map.put(key, newEventTask);
            // Remove the entry when the channel closes.
            removeIfClosed(eventRunnable, key);
            // try to setup "previousEventTask -> newEventTask"
            // if success, then "newEventTask" will be invoke by "previousEventTask"
            if (previousEventTask != null) {
                if (compareAndSetNext(previousEventTask, null, newEventTask)) {
                    return;
                }
            }
            // Two situation here:
            // 1. "newEventTask" is the header of linked list
            // 2. the "previousEventTask.next" is already END
            // At these two situations above, just execute "newEventTask" immediately
            doUnorderedExecute(newEventTask);
        } else {
            doUnorderedExecute(task);
        }
    }

    private void removeIfClosed(ChannelEventRunnable eventRunnable, Object key) {
        ChannelEvent event = eventRunnable.getEvent();
        if (event instanceof ChannelStateEvent) {
            ChannelStateEvent se = (ChannelStateEvent) event;
            if (se.getState() == ChannelState.OPEN && !event.getChannel().isOpen()) {
                removeKey(key);
            }
        }
    }

    /**
     * call removeKey(Object key) when the life cycle of the key ends, such as when the channel is closed
     */
    protected boolean removeKey(Object key) {
        return map.remove(key) != null;
    }

    protected Object getKey(ChannelEvent e) {
        return e.getChannel();
    }

    @Override
    protected boolean shouldCount(Runnable task) {
        return !(task instanceof EventTask) && super.shouldCount(task);
    }

    protected final boolean compareAndSetNext(EventTask eventTask, EventTask expect, EventTask update) {
        // because the "next" field is modified by method "doExecute()" and
        // method "EventTask.run()", so use CAS for thread-safe
        return fieldUpdater.compareAndSet(eventTask, expect, update);
    }

    protected final class EventTask implements Runnable {
        /** @noinspection unused*/
        volatile EventTask next;
        private final ChannelEventRunnable runnable;

        EventTask(ChannelEventRunnable runnable) {
            this.runnable = runnable;
        }

        public void run() {
            try {
                runnable.run();
            } finally {
                // if "next" is not null, then trigger "next" to execute;
                // else if "next" is null, set "next" to END, means end this linked list
                if (!compareAndSetNext(this, null, end)) {
                    doUnorderedExecute(next);
                }
            }
        }
    }
}
