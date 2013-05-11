package io.netty.handler.codec.dns;

/**
 * Represents any resource record (answer, authority, or additional resource records).
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public class Resource extends DNSEntry {

	private long ttl; // The time to live is actually a 4 byte integer, but since it's unsigned
					  // we should store it as long to be properly expressed in Java.
	private byte[] resourceData;

	/**
	 * Constructs a resource record.
	 * 
	 * @param name The domain name.
	 * @param type The type of record being returned.
	 * @param aClass The class for this resource record.
	 * @param ttl The time to live after reading.
	 * @param resourceData The data contained in this record.
	 */
	public Resource(String name, int type, int aClass, long ttl, byte[] resourceData) {
		super(name, type, aClass);
		this.ttl = ttl;
		this.resourceData = resourceData;
	}

	/**
	 * Returns the time to live after reading for this resource record.
	 */
	public long getTimeToLive() {
		return ttl;
	}

	/**
	 * Returns the length of the data in this resource record.
	 */
	public int getDataLength() {
		return resourceData.length;
	}

	/**
	 * Returns the true size of this resource record (does not add redundant
	 * data to the size such as domain names read via pointers).
	 * 
	 * @param data The DNS packet being read from.
	 * @param pos The position at which this resource record's name begins.
	 * @return Returns the true size of this resource record in bytes.
	 */
	public int getSize(byte[] data, int pos) {
		return getTrueSize(data, pos) + resourceData.length + 10;
	}

	/**
	 * Returns the data contained in this resource record.
	 */
	public byte[] getData() {
		return resourceData.clone();
	}

}
