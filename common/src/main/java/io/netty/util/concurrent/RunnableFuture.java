/*
 * Copyright 2018 The Netty Project
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

/**
 * A combination of {@link java.util.concurrent.RunnableFuture} and {@link Future}.
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface RunnableFuture<V> extends java.util.concurrent.RunnableFuture<V>, Future<V> {

    @Override
    RunnableFuture<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    RunnableFuture<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    RunnableFuture<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    RunnableFuture<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    RunnableFuture<V> sync() throws InterruptedException;

    @Override
    RunnableFuture<V> syncUninterruptibly();

    @Override
    RunnableFuture<V> await() throws InterruptedException;

    @Override
    RunnableFuture<V> awaitUninterruptibly();
}
