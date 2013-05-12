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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ClientHandler extends ChannelInboundMessageHandlerAdapter<DatagramPacket> {

	@Override
	@Test
	public void messageReceived(ChannelHandlerContext ctx, DatagramPacket msg)
			throws Exception {
		Response response = null;
		try {
			response = Response.decode(msg.content());
		} catch (ResponseException e) {
			Assert.fail(e.getMessage());
			e.printStackTrace();
			return;
		}
		ResponseHeader header = (ResponseHeader) response.getHeader();
		Assert.assertEquals("Invalid response code, expected TYPE_RESPONSE (1).", Header.TYPE_RESPONSE,
					header.getType());
		Assert.assertFalse("Server response was truncated.", header.isTruncated());
		Assert.assertTrue("Inconsistency between recursion desirability and availability.",
					header.getRecursionDesired() == header.isRecursionAvailable());
		Assert.assertEquals("Invalid ID returned from server.", 15305, response.getHeader().getId());
		Assert.assertEquals("Question count in response not 1.", 1, response.getHeader().questionCount());
		Assert.assertTrue("Server didn't send any resources.",
					  response.getHeader().answerCount()
					+ response.getHeader().authorityResourceCount()
					+ response.getHeader().additionalResourceCount() > 0);
		Resource[] answers = response.getAnswers();
		for (int i = 0; i < answers.length; i++) {
			if (answers[i].type() == DNSEntry.TYPE_A) {
				ByteBuf info = answers[i].data();
				Assert.assertEquals("A non-IPv4 resource record was returned.", info.writerIndex(), 4);
				StringBuilder builder = new StringBuilder();
				for (int n = 0; n < 4; n++)
					builder.append(info.readByte() & 0xff).append(".");
				System.out.println(builder.substring(0, builder.length() - 1));
			}
		}
		ctx.close();
	}

	@Override
	@Test
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {
		Assert.fail(cause.getMessage());
		cause.printStackTrace();
		ctx.close();
	}

}
