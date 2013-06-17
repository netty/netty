package io.netty.handler.dns.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseDecoder;
import io.netty.handler.codec.dns.Resource;

public class DomainDecoder implements RecordDecoder<String> {

	@Override
	public String decode(DnsResponse response, Resource resource) {
		ByteBuf packet = Unpooled.copiedBuffer(response.getRawPacket());
		String name = DnsResponseDecoder.getName(packet, resource.contentIndex());
		packet.release();
		return name;
	}

}
