/*
 * Copyright 2013 The Netty Project
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
import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

public class ResponseDecoder extends MessageToMessageDecoder<DatagramPacket> {

	/**
	 * Decodes a response from a {@link DatagramPacket} containing a {@link ByteBuf} with a
	 * DNS packet. Responses are sent from a DNS server to a client in response to a query.
	 * This method writes the decoded response to the specified {@link MessageBuf} to be
	 * handled by a specialized message handler.
	 * 
	 * @param ctx The {@link ChannelHandlerContext} this {@link ResponseDecoder} belongs to.
	 * @param buf The message being decoded, a {@link DatagramPacket} containing a DNS packet.
	 * @param out The {@link MessageBuf} to which decoded messages should be added.
	 * @throws Exception
	 */
	@Override
	protected void decode(ChannelHandlerContext ctx, DatagramPacket packet,
			MessageBuf<Object> out) throws Exception {
		ByteBuf buf = packet.content();
		Response response = new Response();
		ResponseHeader header = new ResponseHeader(response, buf);
		response.setHeader(header);
		for (int i = 0; i < header.readQuestions(); i++) {
			response.addQuestion(Question.decode(buf));
		}
		for (int i = 0; i < header.readAnswers(); i++) {
			response.addAnswer(Resource.decode(buf));
		}
		for (int i = 0; i < header.readAuthorityResources(); i++) {
			response.addAuthorityResource(Resource.decode(buf));
		}
		for (int i = 0; i < header.readAdditionalResources(); i++) {
			response.addAdditionalResource(Resource.decode(buf));
		}
		out.add(response);
	}

}
