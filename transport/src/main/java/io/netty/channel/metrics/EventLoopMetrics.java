/*
 * Copyright 2014 The Netty Project
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

package io.netty.channel.metrics;

import io.netty.util.metrics.EventExecutorMetrics;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopScheduler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelHandler;
import io.netty.channel.nio.NioEventLoop;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;

/**
 * An {@link EventLoopMetrics} object is attached to an {@link EventLoop} and gathers information about it.
 *
 * @see EventLoopScheduler
 */
public interface EventLoopMetrics extends EventExecutorMetrics<EventLoop> {

    @Override
    void init(EventLoop eventLoop);

    /**
     * This method is called when the state of a {@link Channel} that is registered with the {@link EventLoop}
     * changes to active.
     */
    void channelActive();

    /**
     * This method is called when the state of a {@link Channel} that is registered with the {@link EventLoop}
     * changes to inactive.
     */
    void channelInactive();

    /**
     * This method is called when a {@link Channel} is registered with the {@link EventLoop}.
     */
    void channelRegistered();

    /**
     * This method is called when a {@link Channel} is deregistered from the {@link EventLoop}.
     */
    void channelUnregistered();

    /**
     * This method is called when a {@link Channel} that is registered with the {@link EventLoop} reads data.
     *
     * @param bytes number of bytes read.
     */
    void readBytes(long bytes);

    /**
     * This method is called when a {@link Channel} that is registered with the {@link EventLoop} writes data.
     * <p>
     * This method might be called concurrently from different threads and must therefore be be implemented
     * threadsafe.
     *
     * @param bytes number of bytes written.
     */
    void writeBytes(long bytes);

    /**
     * This method is called before any {@link Channel} of the {@link EventLoop} reads/writes data and before
     * any events are fired through the {@link ChannelPipeline}.
     * <p>
     * For example, in the case of the {@link NioEventLoop} it is called before the {@linkplain SelectionKey}s
     * after a call to {@link Selector#select()} are processed.
     * <p>
     * This method can be used (together with {@link #stopProcessIo()}) to measure the time the {@link EventLoop}
     * spends on processing I/O (incl. executing the {@linkplain ChannelHandler}s).
     */
    void startProcessIo();

    /**
     * This method is called after the {@link EventLoop} has finished with any I/O work as described in
     * {@link #startProcessIo()}. It is guaranteed to be called after every call to {@link #startProcessIo()}.
     */
    void stopProcessIo();

    /**
     * This method is called before the {@link EventLoop} starts processing tasks from its task queue.
     */
    void startExecuteTasks();

    /**
     * This method is called after the {@link EventLoop} has finished processing tasks from its task queue. It is
     * guaranteed to be called after every call to {@link #startExecuteTasks()}.
     */
    void stopExecuteTasks();
}
