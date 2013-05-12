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
 * The DNS response header class. Used when receiving data from a DNS server.
 * Contains information contained in a DNS response header, such as recursion
 * availability, and response codes.
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public class ResponseHeader extends Header {

	private int readQuestions;
	private int readAnswers;
	private int readAuthorityResources;
	private int readAdditionalResources;

	private boolean authoritativeAnswer;
	private boolean truncated;
	private boolean recursionAvailable;

	private int z;
	private int responseCode;

	protected ResponseHeader(Message parent, ByteBuf buf) throws ResponseException {
		super(parent);
		decode(buf);
	}

	/**
	 * @return True when responding server is authoritative for the domain name in the query message.
	 */
	public boolean isAuthoritativeAnswer() {
		return authoritativeAnswer;
	}

	/**
	 * @return True if response has been truncated, usually if it is over 512 bytes.
	 */
	public boolean isTruncated() {
		return truncated;
	}

	/**
	 * @return True if DNS can handle recursive queries.
	 */
	public boolean isRecursionAvailable() {
		return recursionAvailable;
	}

	/**
	 * @return The 3 bit reserved field 'Z'.
	 */
	public int z() {
		return z;
	}

	/**
	 * @return 4 bit return code for query message. Response codes outlined in {@link ReturnCode}.
	 */
	public int responseCode() {
		return responseCode;
	}

	/**
	 * @return The number of questions to read for this DNS response packet.
	 */
	public int readQuestions() {
		return readQuestions;
	}

	/**
	 * @return The number of answers to read for this DNS response packet.
	 */
	public int readAnswers() {
		return readAnswers;
	}

	/**
	 * @return The number of authority resource records to read for this DNS response packet.
	 */
	public int readAuthorityResources() {
		return readAuthorityResources;
	}

	/**
	 * @return The number of additional resource records to read for this DNS response packet.
	 */
	public int readAdditionalResources() {
		return readAdditionalResources;
	}

	/**
	 * Decodes data from a response packet received from a DNS server.
	 * 
	 * @param buf The byte buffer containing the DNS packet.
	 */
	private void decode(ByteBuf buf) throws ResponseException {
		setId(buf.readUnsignedShort());
		int flags = buf.readUnsignedShort();
		setType(flags >> 15);
		setOpcode((flags >> 11) & 0xf);
		setRecursionDesired(((flags >> 8) & 1) == 1);
		authoritativeAnswer = ((flags >> 10) & 1) == 1;
		truncated = ((flags >> 9) & 1) == 1;
		recursionAvailable = ((flags >> 7) & 1) == 1;
		z = (flags >> 4) & 0x7;
		responseCode = flags & 0xf;
		if (responseCode != 0)
			throw new ResponseException(responseCode);
		readQuestions = buf.readUnsignedShort();
		readAnswers = buf.readUnsignedShort();
		readAuthorityResources = buf.readUnsignedShort();
		readAdditionalResources = buf.readUnsignedShort();
	}

}
