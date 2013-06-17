package io.netty.handler.dns.decoder;

import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.Resource;

import java.util.HashMap;
import java.util.Map;

public class RecordDecoderFactory {

	private static final Map<Integer, RecordDecoder<?>> decoders = new HashMap<Integer, RecordDecoder<?>>();
	static {
		decoders.put(Resource.TYPE_A, new AddressDecoder(4));
		decoders.put(Resource.TYPE_AAAA, new AddressDecoder(16));
		decoders.put(Resource.TYPE_MX, new MailExchangerDecoder());
		decoders.put(Resource.TYPE_TXT, new TextDecoder());
		decoders.put(Resource.TYPE_SRV, new ServiceDecoder());
		RecordDecoder<?> decoder = new DomainDecoder();
		decoders.put(Resource.TYPE_NS, decoder);
		decoders.put(Resource.TYPE_CNAME, decoder);
		decoders.put(Resource.TYPE_PTR, decoder);
		decoders.put(Resource.TYPE_SOA, new StartOfAuthorityDecoder());
	}

	@SuppressWarnings("unchecked")
	public static <T> T decode(int type, DnsResponse response, Resource resource) {
		if (type == -1)
			return null;
		RecordDecoder<?> decoder = decoders.get(type);
		if (decoder == null)
			throw new RuntimeException("Unsupported resource record type [id: " + type + "].");
		T result = null;
		try {
			result = (T) decoder.decode(response, resource);
		} catch (Exception e) {
			System.out.println("Failed: " + resource.name());
			e.printStackTrace();
		}
		return result;
	}

}
