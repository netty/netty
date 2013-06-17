package io.netty.handler.dns.decoder.record;

public class StartOfAuthorityRecord {

	private final String primaryNameServer;
	private final String responsiblePerson;
	private final long serial;
	private final int refreshTime;
	private final int retryTime;
	private final int expireTime;
	private final long minimumTtl;
	
	public StartOfAuthorityRecord(String primaryNameServer, String responsiblePerson, long serial, int refreshTime,
			int retryTime, int expireTime, long minimumTtl) {
		this.primaryNameServer = primaryNameServer;
		this.responsiblePerson = responsiblePerson;
		this.serial = serial;
		this.refreshTime = refreshTime;
		this.retryTime = retryTime;
		this.expireTime = expireTime;
		this.minimumTtl = minimumTtl;
	}

	public String primaryNameServer() {
		return primaryNameServer;
	}

	public String responsiblePerson() {
		return responsiblePerson;
	}

	public long serial() {
		return serial;
	}

	public int refreshTime() {
		return refreshTime;
	}

	public int retryTime() {
		return retryTime;
	}

	public int expireTime() {
		return expireTime;
	}

	public long minimumTtl() {
		return minimumTtl;
	}

}
