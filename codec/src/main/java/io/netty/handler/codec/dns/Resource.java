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
import io.netty.buffer.Unpooled;


/**
 * Represents any resource record (answer, authority, or additional resource records).
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public class Resource extends DNSEntry {

	/**
	 * Decodes a resource record, given a DNS packet buffer.
	 * 
	 * @param buf The byte buffer containing the DNS packet.
	 * @return A resource record containing response data.
	 */
	public static Resource decode(ByteBuf buf) {
		String name = DNSEntry.readName(buf);
		int type = buf.readUnsignedShort();
		int aClass = buf.readUnsignedShort();
		long ttl = buf.readUnsignedInt();
		int len = buf.readUnsignedShort();
		ByteBuf resourceData = Unpooled.buffer(len);
		resourceData.writeBytes(buf, len);
		return new Resource(name, type, aClass, ttl, resourceData);
	}

	private final long ttl; // The time to live is actually a 4 byte integer, but since it's unsigned
					  // we should store it as long to be properly expressed in Java.
	private final ByteBuf resourceData;

	/**
	 * Constructs a resource record.
	 * 
	 * @param name The domain name.
	 * @param type The type of record being returned.
	 * @param aClass The class for this resource record.
	 * @param ttl The time to live after reading.
	 * @param resourceData The data contained in this record.
	 */
	public Resource(String name, int type, int aClass, long ttl, ByteBuf resourceData) {
		super(name, type, aClass);
		this.ttl = ttl;
		this.resourceData = resourceData;
	}

	/**
	 * @return The time to live after reading for this resource record.
	 */
	public long timeToLive() {
		return ttl;
	}

	/**
	 * @return The length of the data in this resource record.
	 */
	public int dataLength() {
		return resourceData.writerIndex();
	}

	/**
	 * @return The data contained in this resource record.
	 */
	public ByteBuf data() {
		return resourceData.copy();
	}

}
