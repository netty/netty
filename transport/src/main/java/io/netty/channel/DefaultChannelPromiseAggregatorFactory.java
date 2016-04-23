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
package io.netty.channel;

import io.netty.util.concurrent.EventExecutor;

/**
 * Provides the ability to associate the outcome of multiple {@link ChannelPromise}
 * objects into a single {@link ChannelPromise} object.
 * <p>
 * Not thread safe. Must be used within the {@link EventLoop}.
 */
public final class DefaultChannelPromiseAggregatorFactory extends DefaultChannelPromise
                                                          implements ChannelPromiseAggregatorFactory {
    private final ChannelPromise promise;
    private int expectedCount;
    private int doneCount;
    private Throwable lastFailure;
    private boolean doneAllocating;

    /**
     * Create a new instance which returns an appropriate type if {@code aggregationResult} is {@link #isVoid()}.
     * @param aggregationResult The promise whose status will reflect the result of the aggregation.
     * @param executor the {@link EventExecutor} which is used to notify the promise once it is complete.
     */
    public static ChannelPromiseAggregatorFactory newInstance(ChannelPromise aggregationResult,
                                                              EventExecutor executor) {
        return aggregationResult.isVoid() ? new VoidChannelPromiseAggregatorFactory(aggregationResult.channel(), true)
                                          : new DefaultChannelPromiseAggregatorFactory(aggregationResult, executor);
    }

    /**
     * Create a new instance.
     * @param aggregationResult The promise whose status will reflect the result of the aggregation.
     * @param executor the {@link EventExecutor} which is used to notify the promise once it is complete.
     */
    private DefaultChannelPromiseAggregatorFactory(ChannelPromise aggregationResult, EventExecutor executor) {
        super(aggregationResult.channel(), executor);
        assert !aggregationResult.isDone();
        this.promise = aggregationResult;
    }

    /**
     * Allocate a new promise which will be used to aggregate the overall success of this promise aggregator.
     * @return A new promise which will be aggregated.
     * {@code null} if {@link #doneAllocatingPromises()} was previously called.
     */
    @Override
    public ChannelPromise newPromise() {
        assert !doneAllocating : "Done allocating. No more promises can be allocated.";
        ++expectedCount;
        return this;
    }

    /**
     * Signify that no more {@link #newPromise()} allocations will be made.
     * The aggregation can not be successful until this method is called.
     * @return The promise that is the aggregation of all promises allocated with {@link #newPromise()}.
     */
    @Override
    public ChannelPromise doneAllocatingPromises() {
        if (!doneAllocating) {
            doneAllocating = true;
            if (doneCount == expectedCount || expectedCount == 0) {
                return setPromise();
            }
        }
        return this;
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        if (allowFailure()) {
            ++doneCount;
            lastFailure = cause;
            if (allPromisesDone()) {
                return tryPromise();
            }
            // TODO: We break the interface a bit here, but we can't return false because according to the javadoc
            // returning false can mean "already marked as either a success or a failure".
            // Multiple failure events can be processed without issue because this is an aggregation.
            return true;
        }
        return false;
    }

    /**
     * Fail this object if it has not already been failed.
     * <p>
     * This method will NOT throw an {@link IllegalStateException} if called multiple times
     * because that may be expected.
     */
    @Override
    public ChannelPromise setFailure(Throwable cause) {
        if (allowFailure()) {
            ++doneCount;
            lastFailure = cause;
            if (allPromisesDone()) {
                return setPromise();
            }
        }
        return this;
    }

    @Override
    public ChannelPromise setSuccess(Void result) {
        if (awaitingPromises()) {
            ++doneCount;
            if (allPromisesDone()) {
                setPromise();
            }
        }
        return this;
    }

    @Override
    public boolean trySuccess(Void result) {
        if (awaitingPromises()) {
            ++doneCount;
            if (allPromisesDone()) {
                return tryPromise();
            }
            // TODO: We break the interface a bit here, but we can't return false because according to the javadoc
            // returning false can mean "already marked as either a success or a failure".
            // Multiple success events can be processed without issue because this is an aggregation.
            return true;
        }
        return false;
    }

    private boolean allowFailure() {
        return awaitingPromises() || expectedCount == 0;
    }

    private boolean awaitingPromises() {
        return doneCount < expectedCount;
    }

    private boolean allPromisesDone() {
        return doneCount == expectedCount && doneAllocating;
    }

    private ChannelPromise setPromise() {
        if (lastFailure == null) {
            promise.setSuccess();
            return super.setSuccess(null);
        } else {
            promise.setFailure(lastFailure);
            return super.setFailure(lastFailure);
        }
    }

    private boolean tryPromise() {
        if (lastFailure == null) {
            promise.trySuccess();
            return super.trySuccess(null);
        } else {
            promise.tryFailure(lastFailure);
            return super.tryFailure(lastFailure);
        }
    }
}
