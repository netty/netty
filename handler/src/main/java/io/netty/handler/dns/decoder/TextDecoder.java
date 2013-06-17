package io.netty.handler.dns.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.Resource;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class TextDecoder implements RecordDecoder<List<String>> {

	@Override
	public List<String> decode(DnsResponse response, Resource resource) {
		List<String> list = new ArrayList<String>();
		ByteBuf data = resource.content();
		int index = data.readerIndex();
		while (index < data.writerIndex()) {
			int len = data.getUnsignedByte(index++);
			list.add(data.toString(index, len, Charset.forName("UTF-8")));
			index += len;
		}
		return list;
	}

}
