
/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.dns.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.Resource;

/**
 * Decodes A and AAAA resource records into IPv4 and IPv6 addresses,
 * respectively.
 */
public class AddressDecoder implements RecordDecoder<ByteBuf> {

	private final int octets;

	/**
	 * Constructs an {@code AddressDecoder}, which decodes A and AAAA resource
	 * records.
	 *
	 * @param octets
	 *            the number of octets an address has. 4 for type A records and
	 *            16 for type AAAA records
	 */
	public AddressDecoder(int octets) {
		this.octets = octets;
	}

	/**
	 * Returns a {@link ByteBuf} containing a decoded address from either an A
	 * or AAAA resource record.
	 *
	 * @param response
	 *            the {@link DnsResponse} received that contained the resource
	 *            record being decoded
	 * @param resource
	 *            the {@link Resource} being decoded
	 */
	@Override
	public ByteBuf decode(DnsResponse response, Resource resource) {
		ByteBuf data = resource.content().copy();
		int size = data.writerIndex() - data.readerIndex();
		if (data.readerIndex() != 0 || size != octets) {
			throw new RuntimeException(
					"Invalid content length, or reader index when decoding address [index: "
							+ data.readerIndex() + ", expected length: "
							+ octets + ", actual: " + size + "].");
		}
		return data;
	}

}
