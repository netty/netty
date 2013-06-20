
/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.dns;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.MessageList;
import io.netty.handler.codec.dns.DnsMessage;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.timeout.ReadTimeoutException;

/**
 * Handles listening for messages for a single DNS server and passes messages to
 * the {@link DnsCallback} class to be linked to their callback.
 */
public class InboundDnsMessageHandler extends ChannelInboundHandlerAdapter {

	/**
	 * When a {@link ReadTimeoutException} is caught, this means a DNS client
	 * has been active for a period of 30 seconds, and will be removed from the
	 * list of active channels.
	 */
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		if (cause instanceof ReadTimeoutException) {
			DnsExchangeFactory.removeChannel(ctx.channel());
			return;
		}
		cause.printStackTrace();
	}

	/**
	 * Called when a new {@link DnsMessage} is received. The callback
	 * corresponding to this message is found and finished.
	 */
	@Override
	public void messageReceived(ChannelHandlerContext ctx,
			MessageList<Object> messages) {
		int size = messages.size();
		try {
			for (int i = 0; i < size; i++) {
				Object mesg = messages.get(i);
				if (mesg instanceof DnsResponse) {
					DnsCallback.finish((DnsResponse) mesg);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			messages.releaseAllAndRecycle();
		}
	}

}
