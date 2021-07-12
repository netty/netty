package io.netty.channel;

import java.net.SocketAddress;

import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramSubChannel;
import io.netty.channel.socket.DuplexChannel;
import io.netty.channel.socket.DuplexSubChannel;

public class SubChannelHandler extends ChannelDuplexHandler {

	private ChannelHandler[] handlers;
	private Channel sc;

	public SubChannelHandler(ChannelHandler... handlers) {
		this.handlers = handlers;
	}

	public Channel subChannel() {
		return sc;
	}

	@Override
	public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
		sc.bind(localAddress).addListener(new PromiseRelay(promise));
	}

	@Override
	public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
			ChannelPromise promise) throws Exception {
		sc.connect(remoteAddress, localAddress).addListener(new PromiseRelay(promise));
	}

	@Override
	public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
		sc.disconnect().addListener(new PromiseRelay(promise));
	}

	@Override
	public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
		sc.close().addListener(new PromiseRelay(promise));
	}

	@Override
	public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
		sc.deregister().addListener(new PromiseRelay(promise));
	}

	@Override
	public void read(ChannelHandlerContext ctx) throws Exception {
		sc.pipeline().read();
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		sc.write(msg).addListener(new PromiseRelay(promise));
	}

	@Override
	public void flush(ChannelHandlerContext ctx) throws Exception {
		sc.flush();
	}

	@Override
	public void channelRegistered(final ChannelHandlerContext ctx) throws Exception {
		ChannelPromise promise = ctx.channel().newPromise();
		sc.unsafe().register(ctx.channel().eventLoop(), promise);
	}

	@Override
	public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
		sc.deregister();
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		sc.pipeline().fireChannelActive();
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		sc.pipeline().fireChannelInactive();
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		sc.pipeline().fireChannelRead(msg);
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		sc.pipeline().fireChannelReadComplete();
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		sc.pipeline().fireUserEventTriggered(evt);
	}

	@Override
	public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
		sc.pipeline().fireChannelWritabilityChanged();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		sc.pipeline().fireExceptionCaught(cause);
	}

	@Override
	public boolean isSharable() {
		return false;
	}

	protected Channel createSubChannel(ChannelHandlerContext ctx) {
		if (ctx.channel() instanceof DatagramChannel)
			return new DatagramSubChannel(ctx);
		else if (ctx.channel() instanceof DuplexChannel)
			return new DuplexSubChannel(ctx);
		else return new SubChannel(ctx);
	}
	
	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		sc = createSubChannel(ctx);
		if (handlers != null)
			sc.pipeline().addFirst(handlers);
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		ChannelHandler first;
		while ((first = sc.pipeline().first()) != null) {
			sc.pipeline().remove(first);
		}
	}

}
