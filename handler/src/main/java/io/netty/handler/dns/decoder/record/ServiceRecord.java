package io.netty.handler.dns.decoder.record;

public class ServiceRecord {

	private final int priority;
	private final int weight;
	private final int port;
	private final String name;
	private final String protocol;
	private final String service;
	private final String target;

	public ServiceRecord(String fullPath, int priority, int weight, int port, String target) {
		String[] parts = fullPath.split("\\.", 3);
		service = parts[0];
		protocol = parts[1];
		name = parts[2];
		this.priority = priority;
		this.weight = weight;
		this.port = port;
		this.target = target;
	}

	public int priority() {
		return priority;
	}

	public int weight() {
		return weight;
	}

	public int port() {
		return port;
	}

	public String name() {
		return name;
	}

	public String protocol() {
		return protocol;
	}

	public String service() {
		return service;
	}

	public String target() {
		return target;
	}

}
