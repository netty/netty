
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

import io.netty.handler.codec.dns.DnsEntry;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.Resource;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles the decoding of resource records. Some default decoders are mapped to
 * their resource types in the map {@code decoders}.
 */
public class RecordDecoderFactory {

	/**
	 * Contains the default resource record decoders, which are for A, AAAA, MX,
	 * TXT, SRV, NS, CNAME, PTR, and SOA resource records. Decoders can be added
	 * or removed with the respective {@link #addDecoder(int, RecordDecoder)}
	 * and {@link #removeDecoder(int)} methods.
	 */
	private static final Map<Integer, RecordDecoder<?>> decoders = new HashMap<Integer, RecordDecoder<?>>();
	static {
		decoders.put(DnsEntry.TYPE_A, new AddressDecoder(4));
		decoders.put(DnsEntry.TYPE_AAAA, new AddressDecoder(16));
		decoders.put(DnsEntry.TYPE_MX, new MailExchangerDecoder());
		decoders.put(DnsEntry.TYPE_TXT, new TextDecoder());
		decoders.put(DnsEntry.TYPE_SRV, new ServiceDecoder());
		RecordDecoder<?> decoder = new DomainDecoder();
		decoders.put(DnsEntry.TYPE_NS, decoder);
		decoders.put(DnsEntry.TYPE_CNAME, decoder);
		decoders.put(DnsEntry.TYPE_PTR, decoder);
		decoders.put(DnsEntry.TYPE_SOA, new StartOfAuthorityDecoder());
	}

	/**
	 * Decodes a resource record and returns the result.
	 *
	 * @param type
	 *            the type of resource record (if -1 returns null)
	 * @param response
	 *            the DNS response that contains the resource record being
	 *            decoded
	 * @param resource
	 *            the resource record being decoded
	 * @return the decoded resource record
	 */
	@SuppressWarnings("unchecked")
	public static <T> T decode(int type, DnsResponse response, Resource resource) {
		if (type == -1) {
			return null;
		}
		RecordDecoder<?> decoder = decoders.get(type);
		if (decoder == null) {
			throw new RuntimeException("Unsupported resource record type [id: "
					+ type + "].");
		}
		T result = null;
		try {
			result = (T) decoder.decode(response, resource);
		} catch (Exception e) {
			System.out.println("Failed: " + resource.name());
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * Adds a new resource record decoder, or replaces an existing one for the
	 * given type.
	 *
	 * @param type
	 *            the type of the {@link RecordDecoder} that should be added
	 *            (i.e. A or AAAA)
	 * @param decoder
	 *            the {@link RecordDecoder} being added
	 */
	public static void addDecoder(int type, RecordDecoder<?> decoder) {
		decoders.put(type, decoder);
	}

	/**
	 * Removes the {@link RecordDecoder} with the given type.
	 *
	 * @param type
	 *            the type of the {@link RecordDecoder} that should be removed
	 *            (i.e. A or AAAA)
	 */
	public static void removeDecoder(int type) {
		decoders.remove(type);
	}

}
