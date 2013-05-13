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
 * Represents any resource record (answer, authority, or additional resource records).
 */
public class Resource extends DnsEntry {

	private final long ttl; // The time to live is actually a 4 byte integer, but since it's unsigned
					  // we should store it as long to be properly expressed in Java.
	private final ByteBuf resourceData;

	/**
	 * Constructs a resource record.
	 * 
	 * @param name the domain name
	 * @param type the type of record being returned
	 * @param aClass the class for this resource record
	 * @param ttl the time to live after reading
	 * @param resourceData the data contained in this record
	 */
	public Resource(String name, int type, int aClass, long ttl, ByteBuf resourceData) {
		super(name, type, aClass);
		this.ttl = ttl;
		this.resourceData = resourceData;
	}

	/**
	 * Returns the time to live after reading for this resource record.
	 */
	public long timeToLive() {
		return ttl;
	}

	/**
	 *Returns the length of the data in this resource record.
	 */
	public int dataLength() {
		return resourceData.writerIndex();
	}

	/**
	 * Returns the data contained in this resource record.
	 */
	public ByteBuf data() {
		return resourceData.copy();
	}

}
