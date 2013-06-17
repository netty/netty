package io.netty.handler.dns.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseDecoder;
import io.netty.handler.codec.dns.Resource;
import io.netty.handler.dns.decoder.record.StartOfAuthorityRecord;

public class StartOfAuthorityDecoder implements RecordDecoder<StartOfAuthorityRecord> {

	@Override
	public StartOfAuthorityRecord decode(DnsResponse response, Resource resource) {
		ByteBuf packet = Unpooled.copiedBuffer(response.getRawPacket()).readerIndex(resource.contentIndex());
		String mName = DnsResponseDecoder.readName(packet);
		String rName = DnsResponseDecoder.readName(packet);
		long serial = packet.readUnsignedInt();
		int refresh = packet.readInt();
		int retry = packet.readInt();
		int expire = packet.readInt();
		long minimum = packet.readUnsignedInt();
		packet.release();
		return new StartOfAuthorityRecord(mName, rName, serial, refresh, retry, expire, minimum);
	}

}
