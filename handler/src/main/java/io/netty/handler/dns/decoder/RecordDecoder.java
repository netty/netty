package io.netty.handler.dns.decoder;

import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.Resource;

public interface RecordDecoder<T> {

	public T decode(DnsResponse response, Resource resource);

}
