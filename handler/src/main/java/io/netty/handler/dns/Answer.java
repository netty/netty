package io.netty.handler.dns;

public class Answer {

	private final long expiration;
	private final byte[] data;

	public Answer(byte[] data, long ttl) {
		this.data = data;
		expiration = System.currentTimeMillis() + ttl * 1000l;
	}

	public long expiration() {
		return expiration;
	}

	public byte[] data() {
		return data;
	}

}
