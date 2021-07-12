package io.netty.channel.socket;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SubChannel;

public class DuplexSubChannel extends SubChannel implements DuplexChannel {

	public DuplexSubChannel(ChannelHandlerContext ctx) {
		super(ctx);
	}

	@Override
	public DuplexChannel parent() {
		return (DuplexChannel) super.parent();
	}

	@Override
	public boolean isInputShutdown() {
		return parent().isInputShutdown();
	}

	@Override
	public ChannelFuture shutdownInput() {
		return parent().shutdownInput();
	}

	@Override
	public ChannelFuture shutdownInput(ChannelPromise promise) {
		ChannelFuture cf = parent().shutdownInput();
		cf.addListener(new PromiseRelay(promise));
		return cf;
	}

	@Override
	public boolean isOutputShutdown() {
		return parent().isOutputShutdown();
	}

	@Override
	public ChannelFuture shutdownOutput() {
		return parent().shutdownOutput();
	}

	@Override
	public ChannelFuture shutdownOutput(ChannelPromise promise) {
		ChannelFuture cf = parent().shutdownOutput();
		cf.addListener(new PromiseRelay(promise));
		return cf;
	}

	@Override
	public boolean isShutdown() {
		return parent().isShutdown();
	}

	@Override
	public ChannelFuture shutdown() {
		return parent().shutdown();
	}

	@Override
	public ChannelFuture shutdown(ChannelPromise promise) {
		ChannelFuture cf = parent().shutdown();
		cf.addListener(new PromiseRelay(promise));
		return cf;
	}
}