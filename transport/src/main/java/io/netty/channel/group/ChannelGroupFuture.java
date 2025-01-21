/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.group;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.Iterator;

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
 * <h3>Prefer {@link #addListener(GenericFutureListener)} to {@link #await()}</h3>
 *
 * It is recommended to prefer {@link #addListener(GenericFutureListener)} to
 * {@link #await()} wherever possible to get notified when I/O operations are
 * done and to do any follow-up tasks.
 * <p>
 * {@link #addListener(GenericFutureListener)} is non-blocking.  It simply
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
 * an I/O thread.  If {@link #await()} is called by an event handler
 * method, which is called by the I/O thread, the I/O operation it is waiting
 * for might never be complete because {@link #await()} can block the I/O
 * operation it is waiting for, which is a dead lock.
 * <pre>
 * // BAD - NEVER DO THIS
 * {@code @Override}
 * public void messageReceived({@link ChannelHandlerContext} ctx, ShutdownMessage msg) {
 *     {@link ChannelGroup} allChannels = MyServer.getAllChannels();
 *     {@link ChannelGroupFuture} future = allChannels.close();
 *     future.awaitUninterruptibly();
 *     // Perform post-shutdown operation
 *     // ...
 *
 * }
 *
 * // GOOD
 * {@code @Override}
 * public void messageReceived(ChannelHandlerContext ctx, ShutdownMessage msg) {
 *     {@link ChannelGroup} allChannels = MyServer.getAllChannels();
 *     {@link ChannelGroupFuture} future = allChannels.close();
 *     future.addListener(new {@link ChannelGroupFutureListener}() {
 *         public void operationComplete({@link ChannelGroupFuture} future) {
 *             // Perform post-closure operation
 *             // ...
 *         }
 *     });
 * }
 * </pre>
 * <p>
 * In spite of the disadvantages mentioned above, there are certainly the cases
 * where it is more convenient to call {@link #await()}. In such a case, please
 * make sure you do not call {@link #await()} in an I/O thread.  Otherwise,
 * {@link IllegalStateException} will be raised to prevent a dead lock.
 */
public interface ChannelGroupFuture extends Future<Void>, Iterable<ChannelFuture> {

    /**
     * Returns the {@link ChannelGroup} which is associated with this future.
     */
    ChannelGroup group();

    /**
     * Returns the {@link ChannelFuture} of the individual I/O operation which
     * is associated with the specified {@link Channel}.
     *
     * @return the matching {@link ChannelFuture} if found.
     *         {@code null} otherwise.
     */
    ChannelFuture find(Channel channel);

    /**
     * Returns {@code true} if and only if all I/O operations associated with
     * this future were successful without any failure.
     */
    @Override
    boolean isSuccess();

    @Override
    ChannelGroupException cause();

    /**
     * Returns {@code true} if and only if the I/O operations associated with
     * this future were partially successful with some failure.
     */
    boolean isPartialSuccess();

    /**
     * Returns {@code true} if and only if the I/O operations associated with
     * this future have failed partially with some success.
     */
    boolean isPartialFailure();

    @Override
    ChannelGroupFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelGroupFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelGroupFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelGroupFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelGroupFuture await() throws InterruptedException;

    @Override
    ChannelGroupFuture awaitUninterruptibly();

    @Override
    ChannelGroupFuture syncUninterruptibly();

    @Override
    ChannelGroupFuture sync() throws InterruptedException;

    /**
     * Returns the {@link Iterator} that enumerates all {@link ChannelFuture}s
     * which are associated with this future.  Please note that the returned
     * {@link Iterator} is unmodifiable, which means a {@link ChannelFuture}
     * cannot be removed from this future.
     */
    @Override
    Iterator<ChannelFuture> iterator();
}
