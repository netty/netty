/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.http2.internal;

import io.netty.channel.Channel;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;

/**
 * Provides the ability to associate the outcome of multiple {@link Promise}
 * objects into a single {@link Promise} object.
 */
public final class SimpleChannelPromiseAggregator extends DefaultPromise<Void> {
    private final Promise<Void> promise;
    private int expectedCount;
    private int doneCount;
    private Throwable aggregateFailure;
    private boolean doneAllocating;

    public SimpleChannelPromiseAggregator(Promise<Void> promise, Channel c, EventExecutor e) {
        super(e);
        assert promise != null && !promise.isDone();
        this.promise = promise;
    }

    /**
     * Allocate a new promise which will be used to aggregate the overall success of this promise aggregator.
     *
     * @return A new promise which will be aggregated.
     * {@code null} if {@link #doneAllocatingPromises()} was previously called.
     */
    public Promise<Void> newPromise() {
        assert !doneAllocating : "Done allocating. No more promises can be allocated.";
        ++expectedCount;
        return this;
    }

    /**
     * Signify that no more {@link #newPromise()} allocations will be made.
     * The aggregation can not be successful until this method is called.
     *
     * @return The promise that is the aggregation of all promises allocated with {@link #newPromise()}.
     */
    public Promise<Void> doneAllocatingPromises() {
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
            setAggregateFailure(cause);
            if (allPromisesDone()) {
                return tryPromise();
            }
            // TODO: We break the interface a bit here.
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
    public Promise<Void> setFailure(Throwable cause) {
        if (allowFailure()) {
            ++doneCount;
            setAggregateFailure(cause);
            if (allPromisesDone()) {
                return setPromise();
            }
        }
        return this;
    }

    @Override
    public Promise<Void> setSuccess(Void result) {
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
            // TODO: We break the interface a bit here.
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

    private Promise<Void> setPromise() {
        if (aggregateFailure == null) {
            promise.setSuccess(null);
            return super.setSuccess(null);
        } else {
            promise.setFailure(aggregateFailure);
            return super.setFailure(aggregateFailure);
        }
    }

    private boolean tryPromise() {
        if (aggregateFailure == null) {
            promise.trySuccess(null);
            return super.trySuccess(null);
        } else {
            promise.tryFailure(aggregateFailure);
            return super.tryFailure(aggregateFailure);
        }
    }

    private void setAggregateFailure(Throwable cause) {
        if (aggregateFailure == null) {
            aggregateFailure = cause;
        }
    }
}
