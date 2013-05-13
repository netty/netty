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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

/**
 * DnsResponseDecoder accepts {@link DatagramPacket} and encodes to {@link DnsResponse}. This class
 * also contains methods for decoding parts of DnsResponses such as questions and resource records.
 */
public class DnsResponseDecoder extends MessageToMessageDecoder<DatagramPacket> {

	/**
	 * Decodes a question, given a DNS packet in a byte buffer.
	 * 
	 * @param buf the byte buffer containing the DNS packet
	 * @return a decoded {@link Question}
	 */
	public static Question decodeQuestion(ByteBuf buf) {
		String name = DnsEntry.readName(buf);
		int type = buf.readUnsignedShort();
		int qClass = buf.readUnsignedShort();
		return new Question(name, type, qClass);
	}

	/**
	 * Decodes a resource record, given a DNS packet in a byte buffer.
	 * 
	 * @param buf the byte buffer containing the DNS packet
	 * @return a {@link Resource} record containing response data
	 */
	public static Resource decodeResource(ByteBuf buf) {
		String name = DnsEntry.readName(buf);
		int type = buf.readUnsignedShort();
		int aClass = buf.readUnsignedShort();
		long ttl = buf.readUnsignedInt();
		int len = buf.readUnsignedShort();
		ByteBuf resourceData = Unpooled.buffer(len);
		resourceData.writeBytes(buf, len);
		return new Resource(name, type, aClass, ttl, resourceData);
	}

	/**
	 * Decodes a DNS response header, given a DNS packet in a byte buffer.
	 * 
	 * @param parent the parent {@link Message} to this header
	 * @param buf the byte buffer containing the DNS packet
	 * @return a {@link DnsResponseHeader} containing the response's header information
	 */
	public DnsResponseHeader decodeHeader(DnsResponse parent, ByteBuf buf) throws ResponseException {
		DnsResponseHeader header = new DnsResponseHeader(parent);
		header.setId(buf.readUnsignedShort());
		int flags = buf.readUnsignedShort();
		header.setType(flags >> 15);
		header.setOpcode((flags >> 11) & 0xf);
		header.setRecursionDesired(((flags >> 8) & 1) == 1);
		header.setAuthoritativeAnswer(((flags >> 10) & 1) == 1);
		header.setTruncated(((flags >> 9) & 1) == 1);
		header.setRecursionAvailable(((flags >> 7) & 1) == 1);
		header.setZ((flags >> 4) & 0x7);
		header.setResponseCode(flags & 0xf);
		if (header.getResponseCode() != 0)
			throw new ResponseException(header.getResponseCode());
		header.setReadQuestions(buf.readUnsignedShort());
		header.setReadAnswers(buf.readUnsignedShort());
		header.setReadAuthorityResources(buf.readUnsignedShort());
		header.setReadAdditionalResources(buf.readUnsignedShort());
		return header;
	}

	/**
	 * Decodes a response from a {@link DatagramPacket} containing a {@link ByteBuf} with a
	 * DNS packet. Responses are sent from a DNS server to a client in response to a query.
	 * This method writes the decoded response to the specified {@link MessageBuf} to be
	 * handled by a specialized message handler.
	 * 
	 * @param ctx the {@link ChannelHandlerContext} this {@link DnsResponseDecoder} belongs to
	 * @param buf the message being decoded, a {@link DatagramPacket} containing a DNS packet
	 * @param out the {@link MessageBuf} to which decoded messages should be added
	 * @throws Exception
	 */
	@Override
	protected void decode(ChannelHandlerContext ctx, DatagramPacket packet,
			MessageBuf<Object> out) throws Exception {
		ByteBuf buf = packet.content();
		DnsResponse response = new DnsResponse();
		DnsResponseHeader header = decodeHeader(response, buf);
		response.setHeader(header);
		for (int i = 0; i < header.getReadQuestions(); i++) {
			response.addQuestion(decodeQuestion(buf));
		}
		for (int i = 0; i < header.getReadAnswers(); i++) {
			response.addAnswer(decodeResource(buf));
		}
		for (int i = 0; i < header.getReadAuthorityResources(); i++) {
			response.addAuthorityResource(decodeResource(buf));
		}
		for (int i = 0; i < header.getReadAdditionalResources(); i++) {
			response.addAdditionalResource(decodeResource(buf));
		}
		out.add(response);
	}

}
