package io.netty.handler.dns.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseDecoder;
import io.netty.handler.codec.dns.Resource;
import io.netty.handler.dns.decoder.record.ServiceRecord;

public class ServiceDecoder implements RecordDecoder<ServiceRecord> {

	@Override
	public ServiceRecord decode(DnsResponse response, Resource resource) {
		ByteBuf packet = Unpooled.copiedBuffer(response.getRawPacket()).readerIndex(resource.contentIndex());
		int priority = packet.readShort();
		int weight = packet.readShort();
		int port = packet.readUnsignedShort();
		String target = DnsResponseDecoder.readName(packet);
		packet.release();
		return new ServiceRecord(resource.name(), priority, weight, port, target);
	}

}
