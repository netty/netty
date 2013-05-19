package io.netty.handler.dns;

import io.netty.channel.Channel;

public class DnsServerInfo {

	private final byte[] address;

	private int activeCount;
	private Channel channel;

	public DnsServerInfo(byte[] address, Channel channel) {
		this.address = address;
		this.channel = channel;
		activeCount = 1;
	}

	public byte[] address() {
		return address;
	}

	public Channel channel() {
		return channel;
	}

	public int getActiveCount() {
		return activeCount;
	}

	public void setActiveCount(int activeCount) {
		this.activeCount = activeCount;
	}

}
