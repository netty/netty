/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.group;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.execution.ExecutionHandler;

/**
 * The result of an asynchronous {@link ChannelGroup} operation.
 * {@link ChannelGroupFuture} is composed of {@link ChannelFuture}s which
 * represent the outcome of the individual I/O operations that affect the
 * {@link Channel}s in the {@link ChannelGroup}.
 *
 * <p>
 * All I/O operations in {@link ChannelGroup} are asynchronous.  It means any
 * I/O calls will return immediately with no guarantee that the requested I/O
 * operations have been completed at the end of the call.  Instead, you will be
 * returned with a {@link ChannelGroupFuture} instance which tells you when the
 * requested I/O operations have succeeded, failed, or cancelled.
 * <p>
 * Various methods are provided to let you check if the I/O operations has been
 * completed, wait for the completion, and retrieve the result of the I/O
 * operation. It also allows you to add more than one
 * {@link ChannelGroupFutureListener} so you can get notified when the I/O
 * operation have been completed.
 *
 * <h3>Prefer {@link #addListener(ChannelGroupFutureListener)} to {@link #await()}</h3>
 *
 * It is recommended to prefer {@link #addListener(ChannelGroupFutureListener)} to
 * {@link #await()} wherever possible to get notified when I/O operations are
 * done and to do any follow-up tasks.
 * <p>
 * {@link #addListener(ChannelGroupFutureListener)} is non-blocking.  It simply
 * adds the specified {@link ChannelGroupFutureListener} to the
 * {@link ChannelGroupFuture}, and I/O thread will notify the listeners when
 * the I/O operations associated with the future is done.
 * {@link ChannelGroupFutureListener} yields the best performance and resource
 * utilization because it does not block at all, but it could be tricky to
 * implement a sequential logic if you are not used to event-driven programming.
 * <p>
 * By contrast, {@link #await()} is a blocking operation.  Once called, the
 * caller thread blocks until all I/O operations are done.  It is easier to
 * implement a sequential logic with {@link #await()}, but the caller thread
 * blocks unnecessarily until all I/O operations are done and there's relatively
 * expensive cost of inter-thread notification.  Moreover, there's a chance of
 * dead lock in a particular circumstance, which is described below.
 *
 * <h3>Do not call {@link #await()} inside {@link ChannelHandler}</h3>
 * <p>
 * The event handler methods in {@link ChannelHandler} is often called by
 * an I/O thread unless an {@link ExecutionHandler} is in the
 * {@link ChannelPipeline}.  If {@link #await()} is called by an event handler
 * method, which is called by the I/O thread, the I/O operation it is waiting
 * for might never be complete because {@link #await()} can block the I/O
 * operation it is waiting for, which is a dead lock.
 * <pre>
 * // BAD - NEVER DO THIS
 * {@code @Override}
 * public void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *     if (e.getMessage() instanceof ShutdownMessage) {
 *         {@link ChannelGroup} allChannels = MyServer.getAllChannels();
 *         {@link ChannelGroupFuture} future = allChannels.close();
 *         future.awaitUninterruptibly();
 *         // Perform post-shutdown operation
 *         // ...
 *     }
 * }
 *
 * // GOOD
 * {@code @Override}
 * public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
 *     if (e.getMessage() instanceof ShutdownMessage) {
 *         {@link ChannelGroup} allChannels = MyServer.getAllChannels();
 *         {@link ChannelGroupFuture} future = allChannels.close();
 *         future.addListener(new {@link ChannelGroupFutureListener}() {
 *             public void operationComplete({@link ChannelGroupFuture} future) {
 *                 // Perform post-closure operation
 *                 // ...
 *             }
 *         });
 *     }
 * }
 * </pre>
 * <p>
 * In spite of the disadvantages mentioned above, there are certainly the cases
 * where it is more convenient to call {@link #await()}. In such a case, please
 * make sure you do not call {@link #await()} in an I/O thread.  Otherwise,
 * {@link IllegalStateException} will be raised to prevent a dead lock.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2122 $, $Date: 2010-02-02 11:00:04 +0900 (Tue, 02 Feb 2010) $
 *
 * @apiviz.owns org.jboss.netty.channel.group.ChannelGroupFutureListener - - notifies
 */
public interface ChannelGroupFuture extends Iterable<ChannelFuture> {

    /**
     * Returns the {@link ChannelGroup} which is associated with this future.
     */
    ChannelGroup getGroup();

    /**
     * Returns the {@link ChannelFuture} of the individual I/O operation which
     * is associated with the {@link Channel} whose ID matches the specified
     * integer.
     *
     * @return the matching {@link ChannelFuture} if found.
     *         {@code null} otherwise.
     */
    ChannelFuture find(Integer channelId);

    /**
     * Returns the {@link ChannelFuture} of the individual I/O operation which
     * is associated with the specified {@link Channel}.
     *
     * @return the matching {@link ChannelFuture} if found.
     *         {@code null} otherwise.
     */
    ChannelFuture find(Channel channel);

    /**
     * Returns {@code true} if and only if this future is
     * complete, regardless of whether the operation was successful, failed,
     * or canceled.
     */
    boolean isDone();

    /**
     * Returns {@code true} if and only if all I/O operations associated with
     * this future were successful without any failure.
     */
    boolean isCompleteSuccess();

    /**
     * Returns {@code true} if and only if the I/O operations associated with
     * this future were partially successful with some failure.
     */
    boolean isPartialSuccess();

    /**
     * Returns {@code true} if and only if all I/O operations associated with
     * this future have failed without any success.
     */
    boolean isCompleteFailure();

    /**
     * Returns {@code true} if and only if the I/O operations associated with
     * this future have failed partially with some success.
     */
    boolean isPartialFailure();

    /**
     * Adds the specified listener to this future.  The
     * specified listener is notified when this future is
     * {@linkplain #isDone() done}.  If this future is already
     * completed, the specified listener is notified immediately.
     */
    void addListener(ChannelGroupFutureListener listener);

    /**
     * Removes the specified listener from this future.
     * The specified listener is no longer notified when this
     * future is {@linkplain #isDone() done}.  If this
     * future is already completed, this method has no effect
     * and returns silently.
     */
    void removeListener(ChannelGroupFutureListener listener);

    /**
     * Waits for this future to be completed.
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    ChannelGroupFuture await() throws InterruptedException;

    /**
     * Waits for this future to be completed without
     * interruption.  This method catches an {@link InterruptedException} and
     * discards it silently.
     */
    ChannelGroupFuture awaitUninterruptibly();

    /**
     * Waits for this future to be completed within the
     * specified time limit.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Waits for this future to be completed within the
     * specified time limit.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    boolean await(long timeoutMillis) throws InterruptedException;

    /**
     * Waits for this future to be completed within the
     * specified time limit without interruption.  This method catches an
     * {@link InterruptedException} and discards it silently.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     */
    boolean awaitUninterruptibly(long timeout, TimeUnit unit);

    /**
     * Waits for this future to be completed within the
     * specified time limit without interruption.  This method catches an
     * {@link InterruptedException} and discards it silently.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     */
    boolean awaitUninterruptibly(long timeoutMillis);

    /**
     * Returns the {@link Iterator} that enumerates all {@link ChannelFuture}s
     * which are associated with this future.  Please note that the returned
     * {@link Iterator} is is unmodifiable, which means a {@link ChannelFuture}
     * cannot be removed from this future.
     */
    Iterator<ChannelFuture> iterator();
}
