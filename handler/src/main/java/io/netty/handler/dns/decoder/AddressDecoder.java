package io.netty.handler.dns.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.Resource;

public class AddressDecoder implements RecordDecoder<ByteBuf> {

	private final int octets;

	public AddressDecoder(int octets) {
		this.octets = octets;
	}

	@Override
	public ByteBuf decode(DnsResponse response, Resource resource) {
		ByteBuf data = resource.content().copy();
		int size = data.writerIndex() - data.readerIndex();
		if (data.readerIndex() != 0 || size != octets) {
			throw new RuntimeException("Invalid content length, or reader index when decoding address "
					+ "[index: " + data.readerIndex() + ", expected length: " + octets + ", actual: " + size + "].");
		}
		return data;
	}

}
