/*
 * Copyright 2012 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.net.InetAddress;

import org.junit.Assert;
import org.junit.Test;

public class DNSTest {

	@Test
	public void sendQuery() throws Exception {
		byte[] dns = { 8, 8, 4, 4 }; // Google public dns
		String domain = "www.google.com";
		Query query = new Query(15305);
		query.addQuestion(new Question(domain, Resource.TYPE_A));
		Assert.assertEquals("Invalid question count, expected 1.", 1, query.getHeader().questionCount());
		Assert.assertEquals("Invalid answer count, expected 0.", 0, query.getHeader().answerCount());
		Assert.assertEquals("Invalid authority resource record count, expected 0.", 0, query.getHeader().authorityResourceCount());
		Assert.assertEquals("Invalid additional resource record count, expected 0.", 0, query.getHeader().additionalResourceCount());
		Assert.assertEquals("Invalid type, should be TYPE_QUERY (0)", Header.TYPE_QUERY, query.getHeader().getType());
		ByteBuf buf = Unpooled.buffer(512);
		query.encode(buf);
		DNSClient client = new DNSClient(InetAddress.getByAddress(dns), 53);
		client.write(buf);
	}

}
