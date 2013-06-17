package io.netty.handler.dns;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.TimeUnit;

public class CachedFuture<T> implements Future<T> {

	private final T content;

	public CachedFuture(T content) {
		this.content = content;
	}

	@Override
	public T get() {
		return content;
	}

	@Override
	public T get(long timeout, TimeUnit timeUnit) {
		return content;
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public boolean isDone() {
		return true;
	}

	@Override
	public Future<T> await() {
		return this;
	}

	@Override
	public boolean await(long timeout) {
		return true;
	}

	@Override
	public boolean await(long timeoutMillis, TimeUnit unit) {
		return true;
	}

	@Override
	public Future<T> awaitUninterruptibly() {
		return this;
	}

	@Override
	public boolean awaitUninterruptibly(long timeoutMillis) {
		return true;
	}

	@Override
	public boolean awaitUninterruptibly(long timeoutMillis, TimeUnit unit) {
		return true;
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return false;
	}

	@Override
	public Throwable cause() {
		return null;
	}

	@Override
	public T getNow() {
		return content;
	}

	@Override
	public boolean isSuccess() {
		return true;
	}

	@Override
	public Future<T> sync() {
		return this;
	}

	@Override
	public Future<T> syncUninterruptibly() {
		return this;
	}

	@Override
	public Future<T> addListener(GenericFutureListener<? extends Future<? super T>> listener) {
		throw new RuntimeException("Cannot add a listener to a CachedFuture.");
	}

	@SuppressWarnings("unchecked")
	@Override
	public Future<T> addListeners(GenericFutureListener<? extends Future<? super T>>... listeners) {
		throw new RuntimeException("Cannot add listeners to a CachedFuture.");
	}

	@Override
	public boolean isCancellable() {
		return false;
	}

	@Override
	public Future<T> removeListener(GenericFutureListener<? extends Future<? super T>> listener) {
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Future<T> removeListeners(GenericFutureListener<? extends Future<? super T>>... listeners) {
		return this;
	}

}
