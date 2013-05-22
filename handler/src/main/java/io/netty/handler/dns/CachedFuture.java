package bakkar.mohamed.dnsresolver;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.TimeUnit;

public class CachedFuture implements Future<ByteBuf> {

	private final ByteBuf content;

	public CachedFuture(ByteBuf content) {
		this.content = content;
	}

	@Override
	public ByteBuf get() {
		return content;
	}

	@Override
	public ByteBuf get(long timeout, TimeUnit timeUnit) {
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
	public Future<ByteBuf> addListener(GenericFutureListener<? extends Future<ByteBuf>> listener) {
		throw new RuntimeException("Cannot add a listener to a CachedFuture.");
	}

	@SuppressWarnings("unchecked")
	@Override
	public Future<ByteBuf> addListeners(GenericFutureListener<? extends Future<ByteBuf>>... listeners) {
		throw new RuntimeException("Cannot add listeners to a CachedFuture.");
	}

	@Override
	public Future<ByteBuf> await() {
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
	public Future<ByteBuf> awaitUninterruptibly() {
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
	public ByteBuf getNow() {
		return content;
	}

	@Override
	public boolean isSuccess() {
		return true;
	}

	@Override
	public Future<ByteBuf> removeListener(GenericFutureListener<? extends Future<ByteBuf>> listener) {
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Future<ByteBuf> removeListeners(GenericFutureListener<? extends Future<ByteBuf>>... listeners) {
		return this;
	}

	@Override
	public Future<ByteBuf> sync() {
		return this;
	}

	@Override
	public Future<ByteBuf> syncUninterruptibly() {
		return this;
	}

}
