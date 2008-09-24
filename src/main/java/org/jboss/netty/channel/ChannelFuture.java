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

/**
 * The result of an asynchronous {@link Channel} I/O operation.  Methods are
 * provided to check if the I/O operation is complete, to wait for its
 * completion, and to retrieve the result of the I/O operation. It also allows
 * you to add more than one {@link ChannelFutureListener} so you can get
 * notified when the I/O operation is complete.
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
     * {@link ChannelFuture} takes place.
     */
    Channel getChannel();

    /**
     * Returns {@code true} if and only if this {@link ChannelFuture} is
     * complete, regardless of whether the operation was successful, failed,
     * or canceled.
     */
    boolean isDone();

    /**
     * Returns {@code true} if and only if this {@link ChannelFuture} was
     * canceled by a {@link #cancel()} method.
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
     *         {@code null} if succeeded or this {@link ChannelFuture} is not
     *         completed yet.
     */
    Throwable getCause();

    /**
     * Cancels the I/O operation associated with this {@link ChannelFuture}
     * and notifies all listeners if canceled successfully.
     *
     * @return {@code true} if and only if the operation has been canceled.
     *         {@code false} if the operation can't be canceled or is already
     *         completed.
     */
    boolean cancel();

    /**
     * Marks this {@link ChannelFuture} as a success and notifies all
     * listeners.
     */
    void setSuccess();

    /**
     * Marks this {@link ChannelFuture} as a failure and notifies all
     * listeners.
     */
    void setFailure(Throwable cause);

    /**
     * Adds the specified listener to this {@link ChannelFuture}.  The
     * specified listener is notified when this {@link ChannelFuture} is
     * {@linkplain #isDone() done}.  If this {@link ChannelFuture} is already
     * completed, the specified listener is notified immediately.
     */
    void addListener(ChannelFutureListener listener);

    /**
     * Removes the specified listener from this {@link ChannelFuture}.
     * The specified listener is no longer notified when this
     * {@link ChannelFuture} is {@linkplain #isDone() done}.  If this
     * {@link ChannelFuture} is already completed, this method has no effect
     * and returns silently.
     */
    void removeListener(ChannelFutureListener listener);

    /**
     * Waits for this {@link ChannelFuture} to be completed.
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    ChannelFuture await() throws InterruptedException;

    /**
     * Waits for this {@link ChannelFuture} to be completed without
     * interruption.  This method catches an {@link InterruptedException} and
     * discards it silently.
     */
    ChannelFuture awaitUninterruptibly();

    /**
     * Waits for this {@link ChannelFuture} to be completed within the
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
     * Waits for this {@link ChannelFuture} to be completed within the
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
     * Waits for this {@link ChannelFuture} to be completed within the
     * specified time limit without interruption.  This method catches an
     * {@link InterruptedException} and discards it silently.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     */
    boolean awaitUninterruptibly(long timeout, TimeUnit unit);

    /**
     * Waits for this {@link ChannelFuture} to be completed within the
     * specified time limit without interruption.  This method catches an
     * {@link InterruptedException} and discards it silently.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     */
    boolean awaitUninterruptibly(long timeoutMillis);
}
