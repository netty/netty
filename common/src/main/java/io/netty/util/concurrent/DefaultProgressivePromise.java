/*
 * Copyright 2013 The Netty Project
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

package io.netty.util.concurrent;

public class DefaultProgressivePromise<V> extends DefaultPromise<V> implements ProgressivePromise<V> {

    private final long total;
    private volatile long progress;

    /**
     * Creates a new instance.
     *
     * It is preferable to use {@link EventExecutor#newProgressivePromise(long)} to create a new progressive promise
     *
     * @param executor
     *        the {@link EventExecutor} which is used to notify the promise when it progresses or it is complete
     */
    public DefaultProgressivePromise(EventExecutor executor, long total) {
        super(executor);
        validateTotal(total);
        this.total = total;
    }

    protected DefaultProgressivePromise(long total) {
        /* only for subclasses */
        validateTotal(total);
        this.total = total;
    }

    private static void validateTotal(long total) {
        if (total < 0) {
            throw new IllegalArgumentException("total: " + total + " (expected: >= 0)");
        }
    }

    @Override
    public long progress() {
        return progress;
    }

    @Override
    public long total() {
        return total;
    }

    @Override
    public ProgressivePromise<V> setProgress(long progress) {
        if (progress < 0 || progress > total) {
            throw new IllegalArgumentException(
                    "progress: " + progress + " (expected: 0 <= progress <= " + total + ')');
        }

        if (isDone()) {
            throw new IllegalStateException("complete already");
        }

        long oldProgress = this.progress;
        this.progress = progress;
        notifyProgressiveListeners(progress - oldProgress);
        return this;
    }

    @Override
    public boolean tryProgress(long progress) {
        if (progress < 0 || progress > total || isDone()) {
            return false;
        }

        this.progress = progress;
        notifyProgressiveListeners(progress);
        return true;
    }

    @Override
    public ProgressivePromise<V> addListener(GenericFutureListener<? extends Future<V>> listener) {
        super.addListener(listener);
        return this;
    }

    @Override
    public ProgressivePromise<V> addListeners(GenericFutureListener<? extends Future<V>>... listeners) {
        super.addListeners(listeners);
        return this;
    }

    @Override
    public ProgressivePromise<V> removeListener(GenericFutureListener<? extends Future<V>> listener) {
        super.removeListener(listener);
        return this;
    }

    @Override
    public ProgressivePromise<V> removeListeners(GenericFutureListener<? extends Future<V>>... listeners) {
        super.removeListeners(listeners);
        return this;
    }

    @Override
    public ProgressivePromise<V> sync() throws InterruptedException {
        super.sync();
        return this;
    }

    @Override
    public ProgressivePromise<V> syncUninterruptibly() {
        super.syncUninterruptibly();
        return this;
    }

    @Override
    public ProgressivePromise<V> await() throws InterruptedException {
        super.await();
        return this;
    }

    @Override
    public ProgressivePromise<V> awaitUninterruptibly() {
        super.awaitUninterruptibly();
        return this;
    }

    @Override
    public ProgressivePromise<V> setSuccess(V result) {
        super.setSuccess(result);
        return this;
    }

    @Override
    public ProgressivePromise<V> setFailure(Throwable cause) {
        super.setFailure(cause);
        return this;
    }
}
