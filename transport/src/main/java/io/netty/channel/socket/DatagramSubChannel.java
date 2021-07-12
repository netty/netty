package io.netty.channel.socket;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SubChannel;

public class DatagramSubChannel extends SubChannel implements DatagramChannel {

	public DatagramSubChannel(ChannelHandlerContext ctx) {
		super(ctx);
	}

	@Override
	public DatagramChannel parent() {
		return (DatagramChannel) super.parent();
	}

	@Override
	public DatagramChannelConfig config() {
		return parent().config();
	}

	@Override
	public InetSocketAddress localAddress() {
		return parent().localAddress();
	}

	@Override
	public InetSocketAddress remoteAddress() {
		return parent().remoteAddress();
	}

	@Override
	public boolean isConnected() {
		return parent().isConnected();
	}

	@Override
	public ChannelFuture joinGroup(InetAddress multicastAddress) {
		return parent().joinGroup(multicastAddress);
	}

	@Override
	public ChannelFuture joinGroup(InetAddress multicastAddress, ChannelPromise future) {
		return parent().joinGroup(multicastAddress, future);
	}

	@Override
	public ChannelFuture joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
		return parent().joinGroup(multicastAddress, networkInterface);
	}

	@Override
	public ChannelFuture joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface,
			ChannelPromise future) {
		return parent().joinGroup(multicastAddress, networkInterface, future);
	}

	@Override
	public ChannelFuture joinGroup(InetAddress multicastAddress, NetworkInterface networkInterface,
			InetAddress source) {
		return parent().joinGroup(multicastAddress, networkInterface, source);
	}

	@Override
	public ChannelFuture joinGroup(InetAddress multicastAddress, NetworkInterface networkInterface,
			InetAddress source, ChannelPromise future) {
		return parent().joinGroup(multicastAddress, networkInterface, source, future);
	}

	@Override
	public ChannelFuture leaveGroup(InetAddress multicastAddress) {
		return parent().leaveGroup(multicastAddress);
	}

	@Override
	public ChannelFuture leaveGroup(InetAddress multicastAddress, ChannelPromise future) {
		return parent().leaveGroup(multicastAddress, future);
	}

	@Override
	public ChannelFuture leaveGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
		return parent().leaveGroup(multicastAddress, networkInterface);
	}

	@Override
	public ChannelFuture leaveGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface,
			ChannelPromise future) {
		return parent().leaveGroup(multicastAddress, networkInterface, future);
	}

	@Override
	public ChannelFuture leaveGroup(InetAddress multicastAddress, NetworkInterface networkInterface,
			InetAddress source) {
		return parent().leaveGroup(multicastAddress, networkInterface, source);
	}

	@Override
	public ChannelFuture leaveGroup(InetAddress multicastAddress, NetworkInterface networkInterface,
			InetAddress source, ChannelPromise future) {
		return parent().leaveGroup(multicastAddress, networkInterface, source, future);
	}

	@Override
	public ChannelFuture block(InetAddress multicastAddress, NetworkInterface networkInterface,
			InetAddress sourceToBlock) {
		return parent().block(multicastAddress, networkInterface, sourceToBlock);
	}

	@Override
	public ChannelFuture block(InetAddress multicastAddress, NetworkInterface networkInterface,
			InetAddress sourceToBlock, ChannelPromise future) {
		return parent().block(multicastAddress, networkInterface, sourceToBlock, future);
	}

	@Override
	public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock) {
		return parent().block(multicastAddress, sourceToBlock);
	}

	@Override
	public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock, ChannelPromise future) {
		return parent().block(multicastAddress, sourceToBlock, future);
	}

}