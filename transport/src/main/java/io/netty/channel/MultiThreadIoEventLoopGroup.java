/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel;

import io.netty.util.concurrent.EventExecutorChooserFactory;
import io.netty.util.internal.EmptyArrays;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * {@link IoEventLoopGroup} implementation that will handle its tasks with multiple threads.
 */
public class MultiThreadIoEventLoopGroup extends MultithreadEventLoopGroup implements IoEventLoopGroup {

    /**
     * Creates a new instance of the {@link MultiThreadIoEventLoopGroup} using the default number
     * of threads and default {@link ThreadFactory}.
     */
    public MultiThreadIoEventLoopGroup(IoHandlerFactory ioHandlerFactory) {
        this(0, ioHandlerFactory);
    }

    /**
     /**
     * Creates a new instance of the {@link MultiThreadIoEventLoopGroup} using the default {@link ThreadFactory}.
     *
     * @param nThreads          the number of threads and so {@link EventLoop}s that are created.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} that will be used to create {@link IoHandler} for handling
     *                          IO.
     */
    public MultiThreadIoEventLoopGroup(int nThreads, IoHandlerFactory ioHandlerFactory) {
        this(nThreads, (Executor) null, ioHandlerFactory);
    }

    /**
     * Create a new instance using the default number of thread.
     *
     * @param threadFactory     the {@link ThreadFactory} that is used.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} that will be used to create {@link IoHandler} for handling
     *                          IO.
     */
    public MultiThreadIoEventLoopGroup(ThreadFactory threadFactory, IoHandlerFactory ioHandlerFactory) {
        this(0, threadFactory, ioHandlerFactory);
    }

    /**
     * Creates a new instance of the {@link MultiThreadIoEventLoopGroup} using the default number
     * of threads.
     *
     * @param executor          the {@link Executor} that is used.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} that will be used to create {@link IoHandler} for handling
     *                          IO.
     */
    public MultiThreadIoEventLoopGroup(Executor executor,
                                       IoHandlerFactory ioHandlerFactory) {
        super(0, executor, ioHandlerFactory);
    }

    /**
     * Creates a new instance of the {@link MultiThreadIoEventLoopGroup}.
     *
     * @param nThreads          the number of threads and so {@link EventLoop}s that are created.
     * @param executor          the {@link Executor} that is used.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} that will be used to create {@link IoHandler} for handling
     *                          IO.
     */
    public MultiThreadIoEventLoopGroup(int nThreads, Executor executor,
                                       IoHandlerFactory ioHandlerFactory) {
        super(nThreads, executor, ioHandlerFactory);
    }

    /**
     * Creates a new instance of the {@link MultiThreadIoEventLoopGroup}.
     *
     * @param nThreads          the number of threads and so {@link EventLoop}s that are created.
     * @param threadFactory     the {@link ThreadFactory} that is used.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} that will be used to create {@link IoHandler} for handling
     *                          IO.
     */
    public MultiThreadIoEventLoopGroup(int nThreads, ThreadFactory threadFactory,
                                       IoHandlerFactory ioHandlerFactory) {
        super(nThreads, threadFactory, ioHandlerFactory);
    }

    /**
     * Creates a new instance of the {@link MultiThreadIoEventLoopGroup}.
     *
     * @param nThreads          the number of threads and so {@link EventLoop}s that are created.
     * @param executor          the {@link Executor} that is used.
     * @param chooserFactory    the {@link EventExecutorChooserFactory} that is used to choose the
     *                          {@link IoEventLoop} when {@link MultiThreadIoEventLoopGroup#next()} is
     *                          called.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} that will be used to create {@link IoHandler} for handling
     *                          IO.
     */
    public MultiThreadIoEventLoopGroup(int nThreads, Executor executor,
                                       EventExecutorChooserFactory chooserFactory,
                                       IoHandlerFactory ioHandlerFactory) {
        super(nThreads, executor, chooserFactory, ioHandlerFactory);
    }

    /**
     * Creates a new instance of the {@link MultiThreadIoEventLoopGroup}.
     *
     * @param nThreads          the number of threads and so {@link EventLoop}s that are created.
     * @param executor          the {@link Executor} that is used.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} that will be used to create {@link IoHandler} for handling
     *                          IO.
     * @param args              extra args that are passed to {@link #newChild(Executor, Object...)} method.
     */
    protected MultiThreadIoEventLoopGroup(int nThreads, Executor executor,
                                          IoHandlerFactory ioHandlerFactory, Object... args) {
        super(nThreads, executor, combine(ioHandlerFactory, args));
    }

    /**
     * Creates a new instance of the {@link MultiThreadIoEventLoopGroup}.
     *
     * @param nThreads          the number of threads and so {@link EventLoop}s that are created.
     * @param threadFactory     the {@link ThreadFactory} that is used.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} that will be used to create {@link IoHandler} for handling
     *                          IO.
     * @param args              extra args that are passed to {@link #newChild(Executor, Object...)} method.
     */
    protected MultiThreadIoEventLoopGroup(int nThreads, ThreadFactory threadFactory,
                                          IoHandlerFactory ioHandlerFactory, Object... args) {
        super(nThreads, threadFactory, combine(ioHandlerFactory, args));
    }

    /**
     * Creates a new instance of the {@link MultiThreadIoEventLoopGroup}.
     *
     * @param nThreads          the number of threads and so {@link EventLoop}s that are created.
     * @param threadFactory     the {@link ThreadFactory} that is used.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} that will be used to create {@link IoHandler} for handling
     *                          IO.
     * @param chooserFactory    the {@link EventExecutorChooserFactory} that is used to choose the
     * @param args              extra args that are passed to {@link #newChild(Executor, Object...)} method.
     */
    protected MultiThreadIoEventLoopGroup(int nThreads, ThreadFactory threadFactory,
                                          IoHandlerFactory ioHandlerFactory,
                                          EventExecutorChooserFactory chooserFactory,
                                          Object... args) {
        super(nThreads, threadFactory, chooserFactory, combine(ioHandlerFactory, args));
    }

    /**
     * Creates a new instance of the {@link MultiThreadIoEventLoopGroup}.
     *
     * @param nThreads          the number of threads and so {@link EventLoop}s that are created.
     * @param executor          the {@link Executor} that is used.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} that will be used to create {@link IoHandler} for handling
     *                          IO.
     * @param chooserFactory    the {@link EventExecutorChooserFactory} that is used to choose the
     * @param args              extra args that are passed to {@link #newChild(Executor, Object...)} method.
     */
    protected MultiThreadIoEventLoopGroup(int nThreads, Executor executor,
                                          IoHandlerFactory ioHandlerFactory,
                                          EventExecutorChooserFactory chooserFactory,
                                          Object... args) {
        super(nThreads, executor, chooserFactory, combine(ioHandlerFactory, args));
    }

    // The return type should be IoHandleEventLoop but we choose EventLoop to allow us to introduce the IoHandle
    // concept without breaking API.
    @Override
    protected EventLoop newChild(Executor executor, Object... args) throws Exception {
        IoHandlerFactory handlerFactory = (IoHandlerFactory) args[0];
        Object[] argsCopy;
        if (args.length > 1) {
            argsCopy = new Object[args.length - 1];
            System.arraycopy(args, 1, argsCopy, 0, argsCopy.length);
        } else {
            argsCopy = EmptyArrays.EMPTY_OBJECTS;
        }
        return newChild(executor, handlerFactory, argsCopy);
    }

    /**
     * Creates a new {@link IoEventLoop} to use with the given {@link Executor} and {@link IoHandler}.
     *
     * @param executor              the {@link Executor} that should be used to handle execution of tasks and IO.
     * @param ioHandlerFactory      the {@link IoHandlerFactory} that should be used to obtain {@link IoHandler} to
     *                              handle IO.
     * @param args                  extra arguments that are based by the constructor.
     * @return                      the created {@link IoEventLoop}.
     */
    protected IoEventLoop newChild(Executor executor, IoHandlerFactory ioHandlerFactory,
                                   @SuppressWarnings("unused") Object... args) {
        return new SingleThreadIoEventLoop(this, executor, ioHandlerFactory);
    }

    @Override
    public IoEventLoop next() {
        return (IoEventLoop) super.next();
    }

    private static Object[] combine(IoHandlerFactory handlerFactory, Object... args) {
        List<Object> combinedList = new ArrayList<Object>();
        combinedList.add(handlerFactory);
        if (args != null) {
            Collections.addAll(combinedList, args);
        }
        return combinedList.toArray(new Object[0]);
    }
}
