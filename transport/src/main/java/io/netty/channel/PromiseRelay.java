package io.netty.channel;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;

public class PromiseRelay implements ChannelFutureListener {
	private ChannelPromise promise;

	public PromiseRelay(ChannelPromise promise) {
		this.promise = promise;
	}

	@Override
	public void operationComplete(final ChannelFuture future) throws Exception {
		promise.channel().eventLoop().execute(new Runnable() {
			@Override
			public void run() {
				if (future.isSuccess()) {
					promise.setSuccess();
				} else if (future.isCancelled()) {
					promise.cancel(true);
				} else {
					promise.setFailure(future.cause());
				}
			}
		});
	}
}