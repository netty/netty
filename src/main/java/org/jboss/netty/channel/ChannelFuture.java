/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.channel;

import java.util.concurrent.TimeUnit;

import org.jboss.netty.handler.execution.ExecutionHandler;

/**
 * The result of an asynchronous {@link Channel} I/O operation.
 * <p>
 * All I/O operations in Netty are asynchronous.  It means any I/O calls will
 * return immediately with no guarantee that the requested I/O operation has
 * been completed at the end of the call.  Instead, you will be returned with
 * a {@link ChannelFuture} instance which tells you when the requested I/O
 * operation has succeeded, failed, or cancelled.
 * <p>
 * Various methods are provided to let you check if the I/O operation has been
 * completed, wait for the completion, and retrieve the result of the I/O
 * operation. It also allows you to add more than one {@link ChannelFutureListener}
 * so you can get notified when the I/O operation has been completed.
 *
 * <h3>Prefer {@link #addListener(ChannelFutureListener)} to {@link #await()}</h3>
 *
 * It is recommended to prefer {@link #addListener(ChannelFutureListener)} to
 * {@link #await()} wherever possible to get notified when an I/O operation is
 * done and to do any follow-up tasks.
 * <p>
 * {@link #addListener(ChannelFutureListener)} is non-blocking.  It simply adds
 * the specified {@link ChannelFutureListener} to the {@link ChannelFuture}, and
 * I/O thread will notify the listeners when the I/O operation associated with
 * the future is done.  {@link ChannelFutureListener} yields the best
 * performance and resource utilization because it does not block at all, but
 * it could be tricky to implement a sequential logic if you are not used to
 * event-driven programming.
 * <p>
 * By contrast, {@link #await()} is a blocking operation.  Once called, the
 * caller thread blocks until the operation is done.  It is easier to implement
 * a sequential logic with {@link #await()}, but the caller thread blocks
 * unnecessarily until the I/O operation is done and there's relatively
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
 * public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
 *     if (e.getMessage() instanceof GoodByeMessage) {
 *         ChannelFuture future = e.getChannel().close();
 *         future.awaitUninterruptibly();
 *         // Perform post-closure operation
 *         // ...
 *     }
 * }
 *
 * // GOOD
 * public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
 *     if (e.getMessage() instanceof GoodByeMessage) {
 *         ChannelFuture future = e.getChannel().close();
 *         future.addListener(new ChannelFutureListener() {
 *             public void operationComplete(ChannelFuture future) {
 *                 // Perform post-closure operation
 *                 // ...
 *             }
 *         });
 *     }
 * }
 * </pre>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.owns org.jboss.netty.channel.ChannelFutureListener - - notifies
 */
public interface ChannelFuture {

    /**
     * Returns a channel where the I/O operation associated with this
     * future takes place.
     */
    Channel getChannel();

    /**
     * Returns {@code true} if and only if this future is
     * complete, regardless of whether the operation was successful, failed,
     * or cancelled.
     */
    boolean isDone();

    /**
     * Returns {@code true} if and only if this future was
     * cancelled by a {@link #cancel()} method.
     */
    boolean isCancelled();

    /**
     * Returns {@code true} if and only if the I/O operation was completed
     * successfully.
     */
    boolean isSuccess();

    /**
     * Returns the cause of the failed I/O operation if the I/O operation has
     * failed.
     *
     * @return the cause of the failure.
     *         {@code null} if succeeded or this future is not
     *         completed yet.
     */
    Throwable getCause();

    /**
     * Cancels the I/O operation associated with this future
     * and notifies all listeners if canceled successfully.
     *
     * @return {@code true} if and only if the operation has been canceled.
     *         {@code false} if the operation can't be canceled or is already
     *         completed.
     */
    boolean cancel();

    /**
     * Marks this future as a success and notifies all
     * listeners.
     *
     * @return {@code true} if and only if successfully marked this future as
     *         a success. Otherwise {@code false} because this future is
     *         already marked as either a success or a failure.
     */
    boolean setSuccess();

    /**
     * Marks this future as a failure and notifies all
     * listeners.
     *
     * @return {@code true} if and only if successfully marked this future as
     *         a failure. Otherwise {@code false} because this future is
     *         already marked as either a success or a failure.
     */
    boolean setFailure(Throwable cause);

    /**
     * Adds the specified listener to this future.  The
     * specified listener is notified when this future is
     * {@linkplain #isDone() done}.  If this future is already
     * completed, the specified listener is notified immediately.
     */
    void addListener(ChannelFutureListener listener);

    /**
     * Removes the specified listener from this future.
     * The specified listener is no longer notified when this
     * future is {@linkplain #isDone() done}.  If this
     * future is already completed, this method has no effect
     * and returns silently.
     */
    void removeListener(ChannelFutureListener listener);

    /**
     * Waits for this future to be completed.
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    ChannelFuture await() throws InterruptedException;

    /**
     * Waits for this future to be completed without
     * interruption.  This method catches an {@link InterruptedException} and
     * discards it silently.
     */
    ChannelFuture awaitUninterruptibly();

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
}
