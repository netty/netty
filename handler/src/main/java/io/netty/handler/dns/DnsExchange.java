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
package bakkar.mohamed.dnsresolver;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.Future;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;

import bakkar.mohamed.dnscodec.Question;

public class DnsExchange implements Callable<ByteBuf> {

	private static final Random random = new Random();
	private static final Map<Integer, DnsExchange> resolvers = new HashMap<Integer, DnsExchange>();

	public static DnsExchange forId(int id) {
		synchronized (resolvers) {
			return resolvers.get(id);
		}
	}

	private static int generateShort() {
		return random.nextInt(65536);
	}

	private final byte[] dnsAddress;
	private final String name;
	private final Question question;

	private int id = -1;
	private ByteBuf result = null;

	public DnsExchange(byte[] dnsAddress, Question question) {
		this.dnsAddress = dnsAddress;
		this.name = question.name();
		this.question = question;
	}

	@Override
	public ByteBuf call() {
		synchronized (resolvers) {
			for (id = generateShort(); resolvers.containsKey(id); id = generateShort());
			resolvers.put(id, this);
		}
		try {
			DnsClient.resolveQuery(this);
		} catch (UnknownHostException | InterruptedException e) {
			e.printStackTrace();
		}
		synchronized (resolvers) {
			resolvers.remove(id);
		}
		return result;
	}

	public Future<ByteBuf> submitQuery() throws Exception {
		ByteBuf cachedData = DnsCache.obtainAnswerData(name, question.type());
		if (cachedData != null) {
			return new CachedFuture(cachedData);
		}
		return DnsClient.serverInfo(dnsAddress).channel().eventLoop().parent().submit(this);
	}

	public byte[] dnsAddress() {
		return dnsAddress;
	}

	public int id() {
		return id;
	}

	public String name() {
		return name;
	}

	public Question question() {
		return question;
	}

	public void setResult(ByteBuf result) {
		this.result = result;
	}

}
