package io.netty.handler.dns.decoder.record;

public class MailExchangerRecord {

	private final int priority;
	private final String name;

	public MailExchangerRecord(int priority, String name) {
		this.priority = priority;
		this.name = name;
	}

	public int priority() {
		return priority;
	}

	public String name() {
		return name;
	}

}
