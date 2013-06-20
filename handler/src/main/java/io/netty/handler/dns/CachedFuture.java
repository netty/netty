
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
package io.netty.handler.dns;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * A {@link CachedFuture} is used in place of a regular {@link Future} when
 * cached information answering the {@link DnsQuery} in question exists and has
 * not expired. All the results of a query are stored in the
 * {@link ResourceCache} class. A {@link CachedFuture} is created by passing
 * cached information to the constructor. The information can be obtained by
 * calling any of the {@code get()} methods. Attempting to add a listener to
 * this class will throw a {@link RuntimeException}.
 *
 * @param <T>
 *            the type for the cached information (i.e. for A and AAAA records,
 *            this is {@link ByteBuf})
 */
public class CachedFuture<T> implements Future<T> {

	private final T content;

	/**
	 * Constructs a {@link CachedFuture}, used for instantly returning data
	 * while implementing {@link Future}.
	 *
	 * @param content
	 *            the data to be returned on when calling {@code get()} methods
	 */
	public CachedFuture(T content) {
		this.content = content;
	}

	/**
	 * Returns cached content instantly, without blocking.
	 */
	@Override
	public T get() {
		return content;
	}

	/**
	 * Returns cached content instantly, without blocking, ignoring the
	 * arguments.
	 */
	@Override
	public T get(long timeout, TimeUnit timeUnit) {
		return content;
	}

	/**
	 * Returns {@code false} because this {@link Future} completes
	 * instantaneously and thus cannot be cancelled.
	 */
	@Override
	public boolean isCancelled() {
		return false;
	}

	/**
	 * Returns {@code true} because this {@link Future} completes
	 * instantaneously and thus is always done.
	 */
	@Override
	public boolean isDone() {
		return true;
	}

	/**
	 * This method does not block and simply returns this {@link CachedFuture}.
	 */
	@Override
	public Future<T> await() {
		return this;
	}

	/**
	 * This method does not block and simply returns this {@link CachedFuture}.
	 */
	@Override
	public boolean await(long timeout) {
		return true;
	}

	/**
	 * This method does not block and simply returns this {@link CachedFuture}.
	 */
	@Override
	public boolean await(long timeoutMillis, TimeUnit unit) {
		return true;
	}

	/**
	 * This method does not block and simply returns this {@link CachedFuture}.
	 */
	@Override
	public Future<T> awaitUninterruptibly() {
		return this;
	}

	/**
	 * This method does not block and simply returns this {@link CachedFuture}.
	 */
	@Override
	public boolean awaitUninterruptibly(long timeoutMillis) {
		return true;
	}

	/**
	 * This method does not block and simply returns this {@link CachedFuture}.
	 */
	@Override
	public boolean awaitUninterruptibly(long timeoutMillis, TimeUnit unit) {
		return true;
	}

	/**
	 * Returns {@code false} since this {@link Future} completes instantaneously
	 * and thus cannot be cancelled.
	 */
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return false;
	}

	/**
	 * Returns {@code null} because this {@link Future} cannot fail since it
	 * does not actually get created when passing a {@link Callable} to a
	 * {@code Executor}, but rather is created via its public constructor which
	 * throws no exceptions.
	 */
	@Override
	public Throwable cause() {
		return null;
	}

	/**
	 * Returns cached content instantly.
	 */
	@Override
	public T getNow() {
		return content;
	}

	/**
	 * Returns {@code true} because this {@link Future} cannot fail and
	 * completes instantly.
	 */
	@Override
	public boolean isSuccess() {
		return true;
	}

	/**
	 * This method does not block and simply returns this {@link CachedFuture}.
	 */
	@Override
	public Future<T> sync() {
		return this;
	}

	/**
	 * This method does not block and simply returns this {@link CachedFuture}.
	 */
	@Override
	public Future<T> syncUninterruptibly() {
		return this;
	}

	/**
	 * Since this {@link Future} completes instantly, a listener cannot be
	 * added. This method throws a {@link RuntimeException}.
	 */
	@Override
	public Future<T> addListener(
			GenericFutureListener<? extends Future<? super T>> listener) {
		throw new RuntimeException("Cannot add a listener to a CachedFuture.");
	}

	/**
	 * Since this {@link Future} completes instantly, listeners cannot be added.
	 * This method throws a {@link RuntimeException}.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Future<T> addListeners(
			GenericFutureListener<? extends Future<? super T>>... listeners) {
		throw new RuntimeException("Cannot add listeners to a CachedFuture.");
	}

	/**
	 * Returns {@code false} because this {@link Future} completes
	 * instantaneously and thus cannot be cancelled.
	 */
	@Override
	public boolean isCancellable() {
		return false;
	}

	/**
	 * Returns this {@link Future} and does nothing else.
	 */
	@Override
	public Future<T> removeListener(
			GenericFutureListener<? extends Future<? super T>> listener) {
		return this;
	}

	/**
	 * Returns this {@link Future} and does nothing else.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Future<T> removeListeners(
			GenericFutureListener<? extends Future<? super T>>... listeners) {
		return this;
	}

}
