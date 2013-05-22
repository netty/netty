package bakkar.mohamed.dnsresolver;

import io.netty.buffer.ByteBuf;

public class Answer {

	private final long expiration;
	private final ByteBuf content;

	public Answer(ByteBuf content, long ttl) {
		this.content = content;
		expiration = System.currentTimeMillis() + ttl * 1000l;
	}

	public long expiration() {
		return expiration;
	}

	public ByteBuf content() {
		return content;
	}

}
