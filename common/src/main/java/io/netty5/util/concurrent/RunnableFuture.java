/*
 * Copyright 2018 The Netty Project
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
package io.netty5.util.concurrent;

/**
 * A combination of {@link java.util.concurrent.RunnableFuture} and {@link Future}.
 */
public interface RunnableFuture<V> extends Runnable, Future<V> {

    @Override
    RunnableFuture<V> addListener(FutureListener<? super V> listener);

    @Override
    <C> RunnableFuture<V> addListener(C context, FutureContextListener<? super C, ? super V> listener);

    @Override
    RunnableFuture<V> sync() throws InterruptedException;

    @Override
    RunnableFuture<V> syncUninterruptibly();

    @Override
    RunnableFuture<V> await() throws InterruptedException;

    @Override
    RunnableFuture<V> awaitUninterruptibly();
}
