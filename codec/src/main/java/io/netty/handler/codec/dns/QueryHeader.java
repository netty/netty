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
 * The DNS query header class. Used when sending data to a DNS server.
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public class QueryHeader extends Header {

	/**
	 * Constructor for a DNS packet query header. The id is user generated and
	 * will be replicated in the response packet by the server.
	 * 
	 * @param parent The {@link Message} this header belongs to.
	 * @param id A 2 bit unsigned identification number for this query.
	 */
	public QueryHeader(Message parent, int id) {
		super(parent);
		setId(id);
		setRecursionDesired(true);
	}

	/**
	 * Encodes all the information in the header and writes to a byte buffer.
	 * The header is always 12 bytes long.
	 */
	public void encode(ByteBuf buf) {
		buf.writeShort(getId());
		int flags = 0;
		flags |= getType() << 15;
		flags |= getOpcode() << 14;
		flags |= getRecursionDesired() ? (1 << 8) : 0;
		buf.writeShort(flags);
		buf.writeShort(questionCount());
		buf.writeShort(answerCount()); // Must be 0
		buf.writeShort(authorityResourceCount()); // Must be 0
		buf.writeShort(additionalResourceCount()); // Must be 0
	}

}
