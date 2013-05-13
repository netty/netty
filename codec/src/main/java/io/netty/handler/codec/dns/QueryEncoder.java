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
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

public class QueryEncoder extends MessageToMessageEncoder<Query> {

	/**
	 * Encodes a query and writes it to a {@link ByteBuf}. Queries are sent to
	 * a DNS server and a response will be returned from the server. The encoded
	 * ByteBuf is written to the specified {@link MessageBuf}.
	 * 
	 * @param ctx The {@link ChannelHandlerContext} this {@link QueryEncoder} belongs to.
	 * @param query The query being encoded.
	 * @param out The {@link MessageBuf} to which encoded messages should be added.
	 * @throws Exception
	 */
	@Override
	protected void encode(ChannelHandlerContext ctx, Query query,
			MessageBuf<Object> out) throws Exception {
		ByteBuf buf = Unpooled.buffer(512);
		query.getHeader().encode(buf);
		List<Question> questions = query.getQuestions();
		for (Question question : questions) {
			question.encode(buf);
		}
		out.add(buf);
	}

}