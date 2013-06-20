/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.dns;

/**
 * A DNS response packet which is sent to a client after a server receives a
 * query.
 */
public class DnsResponse extends DnsMessage<DnsResponseHeader> {

	private byte[] rawPacket = null;

	/**
	 * Returns the original, non-decoded DNS response packet. Stored as a byte
	 * array since an instance of {@link DnsResponse} may be discarded at any
	 * time, necessitating a release of any {@link ReferenceCounted} objects.
	 * Byte arrays will automatically be cleaned up by the garbage collector.
	 */
	public byte[] getRawPacket() {
		return rawPacket;
	}

	/**
	 * Sets the non-decoded DNS response packet.
	 */
	public void setRawPacket(byte[] rawPacket) {
		this.rawPacket = rawPacket;
	}

}
