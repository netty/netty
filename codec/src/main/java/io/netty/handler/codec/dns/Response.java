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

/**
 * A DNS response packet. Sent to a client after server received a query.
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public class Response extends Message {

	/**
	 * Decodes a DNS response packet from a byte buffer.
	 * 
	 * @param buf The byte buffer containing the DNS packet.
	 * @return A Response object containing the packet's information.
	 * @throws ResponseException
	 */
	public static Response decode(ByteBuf buf) throws ResponseException {
		Response response = new Response(buf);
		ResponseHeader header = (ResponseHeader) response.getHeader();
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
		return response;
	}

	private Response(ByteBuf buf) throws ResponseException {
		setHeader(new ResponseHeader(this, buf));
	}

}
