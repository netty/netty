/*
 * Copyright 2016 The Netty Project
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
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty.util.internal;

import java.util.Queue;

/**
 * Forked from <a href="https://github.com/JCTools/JCTools">JCTools</a>.
 *
 * This is a tagging interface for the queues in this library which implement a subset of the {@link Queue}
 * interface sufficient for concurrent message passing.<br>
 * Message passing queues provide happens before semantics to messages passed through, namely that writes made
 * by the producer before offering the message are visible to the consuming thread after the message has been
 * polled out of the queue.
 *
 * @param <T>
 *            the event/message type
 */
public interface MessagePassingQueue<T> {
    int UNBOUNDED_CAPACITY = -1;

    interface Supplier<T> {
        /**
         * This method will return the next value to be written to the queue. As such the queue
         * implementations are commited to insert the value once the call is made.
         * <p>
         * Users should be aware that underlying queue implementations may upfront claim parts of the queue
         * for batch operations and this will effect the view on the queue from the supplier method. In
         * particular size and any offer methods may take the view that the full batch has already happened.
         *
         * @return new element, NEVER null
         */
        T get();
    }

    interface Consumer<T> {
        /**
         * This method will process an element already removed from the queue. This method is expected to
         * never throw an exception.
         * <p>
         * Users should be aware that underlying queue implementations may upfront claim parts of the queue
         * for batch operations and this will effect the view on the queue from the accept method. In
         * particular size and any poll/peek methods may take the view that the full batch has already
         * happened.
         *
         * @param e not null
         */
        void accept(T e);
    }

    interface WaitStrategy {
        /**
         * This method can implement static or dynamic backoff. Dynamic backoff will rely on the counter for
         * estimating how long the caller has been idling. The expected usage is:
         *
         * <pre>
         * <code>
         * int ic = 0;
         * while(true) {
         *   if(!isGodotArrived()) {
         *     ic = w.idle(ic);
         *     continue;
         *   }
         *   ic = 0;
         *   // party with Godot until he goes again
         * }
         * </code>
         * </pre>
         *
         * @param idleCounter idle calls counter, managed by the idle method until reset
         * @return new counter value to be used on subsequent idle cycle
         */
        int idle(int idleCounter);
    }

    interface ExitCondition {

        /**
         * This method should be implemented such that the flag read or determination cannot be hoisted out of
         * a loop which notmally means a volatile load, but with JDK9 VarHandles may mean getOpaque.
         *
         * @return true as long as we should keep running
         */
        boolean keepRunning();
    }

    /**
     * Called from a producer thread subject to the restrictions appropriate to the implementation and
     * according to the {@link Queue#offer(Object)} interface.
     *
     * @param e not null, will throw NPE if it is
     * @return true if element was inserted into the queue, false iff full
     */
    boolean offer(T e);

    /**
     * Called from the consumer thread subject to the restrictions appropriate to the implementation and
     * according to the {@link Queue#poll()} interface.
     *
     * @return a message from the queue if one is available, null iff empty
     */
    T poll();

    /**
     * Called from the consumer thread subject to the restrictions appropriate to the implementation and
     * according to the {@link Queue#peek()} interface.
     *
     * @return a message from the queue if one is available, null iff empty
     */
    T peek();

    /**
     * This method's accuracy is subject to concurrent modifications happening as the size is estimated and as
     * such is a best effort rather than absolute value. For some implementations this method may be O(n)
     * rather than O(1).
     *
     * @return number of messages in the queue, between 0 and {@link Integer#MAX_VALUE} but less or equals to
     *         capacity (if bounded).
     */
    int size();

    /**
     * Removes all items from the queue. Called from the consumer thread subject to the restrictions
     * appropriate to the implementation and according to the {@link Queue#clear()} interface.
     */
    void clear();

    /**
     * This method's accuracy is subject to concurrent modifications happening as the observation is carried
     * out.
     *
     * @return true if empty, false otherwise
     */
    boolean isEmpty();

    /**
     * @return the capacity of this queue or UNBOUNDED_CAPACITY if not bounded
     */
    int capacity();

    /**
     * Called from a producer thread subject to the restrictions appropriate to the implementation. As opposed
     * to {@link Queue#offer(Object)} this method may return false without the queue being full.
     *
     * @param e not null, will throw NPE if it is
     * @return true if element was inserted into the queue, false if unable to offer
     */
    boolean relaxedOffer(T e);

    /**
     * Called from the consumer thread subject to the restrictions appropriate to the implementation. As
     * opposed to {@link Queue#poll()} this method may return null without the queue being empty.
     *
     * @return a message from the queue if one is available, null if unable to poll
     */
    T relaxedPoll();

    /**
     * Called from the consumer thread subject to the restrictions appropriate to the implementation. As
     * opposed to {@link Queue#peek()} this method may return null without the queue being empty.
     *
     * @return a message from the queue if one is available, null if unable to peek
     */
    T relaxedPeek();

    /**
     * Remove all available item from the queue and hand to consume. This should be semantically similar to:
     * <pre><code>
     * M m;
     * while((m = relaxedPoll()) != null){
     *  c.accept(m);
     * }
     * </code></pre>
     * There's no strong commitment to the queue being empty at the end of a drain. Called from a
     * consumer thread subject to the restrictions appropriate to the implementation.
     *
     * @return the number of polled elements
     */
    int drain(Consumer<T> c);

    /**
     * Stuff the queue with elements from the supplier. Semantically similar to:
     * <pre><code>
     * while(relaxedOffer(s.get());
     * </code></pre>
     * There's no strong commitment to the queue being full at the end of a fill. Called from a
     * producer thread subject to the restrictions appropriate to the implementation.
     *
     * @return the number of offered elements
     */
    int fill(Supplier<T> s);

    /**
     * Remove up to <i>limit</i> elements from the queue and hand to consume. This should be semantically
     * similar to:
     *
     * <pre><code>
     *   M m;
     *   while((m = relaxedPoll()) != null){
     *     c.accept(m);
     *   }
     * </code></pre>
     *
     * There's no strong commitment to the queue being empty at the end of a drain. Called from a consumer
     * thread subject to the restrictions appropriate to the implementation.
     *
     * @return the number of polled elements
     */
    int drain(Consumer<T> c, int limit);

    /**
     * Stuff the queue with up to <i>limit</i> elements from the supplier. Semantically similar to:
     *
     * <pre>
     * <code>
     *   for(int i=0; i < limit && relaxedOffer(s.get(); i++);
     * </code>
     * </pre>
     *
     * There's no strong commitment to the queue being full at the end of a fill. Called from a producer
     * thread subject to the restrictions appropriate to the implementation.
     *
     * @return the number of offered elements
     */
    int fill(Supplier<T> s, int limit);

    /**
     * Remove elements from the queue and hand to consume forever. Semantically similar to:
     *
     * <pre>
     * <code>
     *  int idleCounter = 0;
     *  while (exit.keepRunning()) {
     *      E e = relaxedPoll();
     *      if(e==null){
     *          idleCounter = wait.idle(idleCounter);
     *          continue;
     *      }
     *      idleCounter = 0;
     *      c.accept(e);
     *  }
     * </code>
     * </pre>
     *
     * Called from a consumer thread subject to the restrictions appropriate to the implementation.
     *
     */
    void drain(Consumer<T> c, WaitStrategy wait, ExitCondition exit);

    /**
     * Stuff the queue with elements from the supplier forever. Semantically similar to:
     *
     * <pre>
     * <code>
     *  int idleCounter = 0;
     *  while (exit.keepRunning()) {
     *      E e = s.get();
     *      while (!relaxedOffer(e)) {
     *          idleCounter = wait.idle(idleCounter);
     *          continue;
     *      }
     *      idleCounter = 0;
     *  }
     * </code>
     * </pre>
     *
     * Called from a producer thread subject to the restrictions appropriate to the implementation.
     *
     */
    void fill(Supplier<T> s, WaitStrategy wait, ExitCondition exit);
}
