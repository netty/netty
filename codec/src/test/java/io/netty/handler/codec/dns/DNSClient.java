/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.junit.Assert;
import org.junit.Test;

public class DNSClient {

	private final InetAddress address;
	private final int port;

	public DNSClient(InetAddress address, int port) {
		this.address = address;
		this.port = port;
	}

	@Test
	public void write(ByteBuf data) {
		EventLoopGroup group = new NioEventLoopGroup();
		try {
			Bootstrap b = new Bootstrap();
			b.group(group)
			.channel(NioDatagramChannel.class)
			.option(ChannelOption.SO_BROADCAST, true)
			.handler(new ClientHandler());

			Channel ch = b.bind(53).sync().channel();

			System.out.println("Sending DNS packet to server...");
			ch.write(new DatagramPacket(data, new InetSocketAddress(address, port))).sync();
			System.out.println("Listening for response...");

			if (!ch.closeFuture().await(5000)) {
				System.err.println("DNS request timed out.");
			}
		} catch (Exception e) {
			Assert.fail(e.getMessage());
			e.printStackTrace();
		} finally {
			group.shutdownGracefully();
		}
	}

}
