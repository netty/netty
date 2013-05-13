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

/**
 * The DNS question class. Represents a question being sent to a server via a
 * query, or the question being duplicated and sent back in a response. Usually
 * a message contains a single question, and DNS servers often don't support
 * multiple questions in a single query.
 */
public class Question extends DNSEntry {

	/**
	 * Decodes a question, given a DNS packet in a byte buffer.
	 * 
	 * @param buf The byte buffer containing the DNS packet.
	 * @return A decoded Question object.
	 */
	public static Question decode(ByteBuf buf) {
		String name = readName(buf);
		int type = buf.readUnsignedShort();
		int qClass = buf.readUnsignedShort();
		return new Question(name, type, qClass);
	}

	/**
	 * Constructs a question with the default class IN (Internet).
	 * 
	 * @param name The domain name being queried i.e. "www.example.com"
	 * @param type The question type, which represents the type of {@link Resource} record that should be returned.
	 */
	public Question(String name, int type) {
		this(name, type, CLASS_IN);
	}

	/**
	 * Constructs a question with the given class.
	 * 
	 * @param name The domain name being queried i.e. "www.example.com"
	 * @param type The question type, which represents the type of {@link Resource} record that should be returned.
	 * @param qClass The class of a DNS record.
	 */
	public Question(String name, int type, int qClass) {
		super(name, type, qClass);
	}

	/**
	 * Encodes the question and writes it to a byte buffer.
	 */
	public void encode(ByteBuf buf) {
		String[] parts = name.split("\\.");
		for (int i = 0; i < parts.length; i++) {
			buf.writeByte(parts[i].length());
			buf.writeBytes(parts[i].getBytes());
		}
		buf.writeByte(0);
		buf.writeShort(type);
		buf.writeShort(dnsClass);
	}

}
