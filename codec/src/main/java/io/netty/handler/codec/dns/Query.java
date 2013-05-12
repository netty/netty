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
 * A DNS query packet. Sent to a server to receive a DNS response packet with information
 * answering a query's questions.
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public class Query extends Message {

	/**
	 * Constructs a DNS query. By default recursion will be toggled on.
	 */
	public Query(int id) {
		setHeader(new QueryHeader(this, id));
	}

	/**
	 * Encodes a query and writes it to a byte buffer. This can be sent to
	 * a DNS server, and a response will be sent from the server.
	 */
	public void encode(ByteBuf buf) {
		((QueryHeader) getHeader()).encode(buf);
		Question[] questions = getQuestions();
		for (int i = 0; i < questions.length; i++) {
			questions[i].encode(buf);
		}
	}

}
