package io.netty.channel.socket;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;

public class PromiseRelay implements ChannelFutureListener {
	private ChannelPromise promise;
	private Exception caller = new Exception();

	public PromiseRelay(ChannelPromise promise) {
		this.promise = promise;
//		System.err.println("Called by " + caller() + " on a " + promise.channel().getClass());
//		caller.printStackTrace();
	}

	private String caller() {
		return caller.getStackTrace()[2].toString();
	}

	@Override
	public void operationComplete(final ChannelFuture future) throws Exception {
		promise.channel().eventLoop().execute(new Runnable() {
			@Override
			public void run() {
				if (future.isSuccess()) {
//					System.out.println("Success! " + caller());
					promise.setSuccess();
				} else if (future.isCancelled()) {
//					System.out.println("Cancelled! " + caller());
					promise.cancel(true);
				} else {
//					System.out.println("Failure! " + caller());
//					promise.setFailure(future.cause());
//					caller.printStackTrace();
//					future.cause().printStackTrace();
				}
			}
		});
	}
}