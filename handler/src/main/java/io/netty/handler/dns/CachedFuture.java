package io.netty.handler.dns;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.TimeUnit;

public class CachedFuture<E> implements Future<E> {

	private final E content;

	public CachedFuture(E content) {
		this.content = content;
	}

	@Override
	public E get() {
		return content;
	}

	@Override
	public E get(long timeout, TimeUnit timeUnit) {
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
	public Future<E> addListener(GenericFutureListener<? extends Future<E>> listener) {
		throw new RuntimeException("Cannot add a listener to a CachedFuture.");
	}

	@SuppressWarnings("unchecked")
	@Override
	public Future<E> addListeners(GenericFutureListener<? extends Future<E>>... listeners) {
		throw new RuntimeException("Cannot add listeners to a CachedFuture.");
	}

	@Override
	public Future<E> await() {
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
	public Future<E> awaitUninterruptibly() {
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
	public E getNow() {
		return content;
	}

	@Override
	public boolean isSuccess() {
		return true;
	}

	@Override
	public Future<E> removeListener(GenericFutureListener<? extends Future<E>> listener) {
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Future<E> removeListeners(GenericFutureListener<? extends Future<E>>... listeners) {
		return this;
	}

	@Override
	public Future<E> sync() {
		return this;
	}

	@Override
	public Future<E> syncUninterruptibly() {
		return this;
	}

}
