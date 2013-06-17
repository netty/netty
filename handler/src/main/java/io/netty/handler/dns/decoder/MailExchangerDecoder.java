package io.netty.handler.dns.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseDecoder;
import io.netty.handler.codec.dns.Resource;
import io.netty.handler.dns.decoder.record.MailExchangerRecord;

public class MailExchangerDecoder implements RecordDecoder<MailExchangerRecord> {

	@Override
	public MailExchangerRecord decode(DnsResponse response, Resource resource) {
		ByteBuf packet = Unpooled.copiedBuffer(response.getRawPacket()).readerIndex(resource.contentIndex());
		int priority = packet.readShort();
		String name =  DnsResponseDecoder.readName(packet);
		packet.release();
		return new MailExchangerRecord(priority, name);
	}

}
