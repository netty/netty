/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.dns;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import io.netty.buffer.ByteBuf;
import io.netty.handler.dns.decoder.record.MailExchangerRecord;
import io.netty.util.concurrent.Future;

import java.util.List;

import org.junit.Test;

public class DnsImplementationTest {

	private static final String[] TOP_WEBSITES = { "facebook.com",
			"google.com", "youtube.com", "yahoo.com", "baidu.com",
			"wikipedia.org", "amazon.com", "qq.com", "live.com", "taobao.com",
			"blogspot.com", "google.co.in", "linkedin.com", "twitter.com",
			"yahoo.co.jp", "bing.com", "sina.com.cn", "msn.com", "ebay.com",
			"twitter.com" };

	private static final int MAX_ERRORS = 10; // Not all websites use every
	// available record type
	// (MX, AAAA, TXT, etc) so there has to be some room for error.

	private static final int TRIALS_PER_WEBSITE = 2; // Make sure the cache is

	// working.

	// Maybe this test should be expanded a bit, too bad websites have different
	// records available
	@Test
	public void test() {
		int errors = 0;
		System.out.println("Conducting DNS unit test.");
		for (int i = 0; i < TOP_WEBSITES.length; i++) {
			try {
				String domain = TOP_WEBSITES[i];
				for (int n = 0; n < TRIALS_PER_WEBSITE; n++) {
					assertTrue("Encountered too many errors.",
							errors < MAX_ERRORS);
					Future<ByteBuf> future = DnsExchangeFactory.lookup(domain);
					if (future.get() == null) {
						System.err
								.println("Failed to retrieve address for domain \""
										+ domain + "\".");
						errors++;
					}
				}
				for (int n = 0; n < TRIALS_PER_WEBSITE; n++) {
					assertTrue("Encountered too many errors.",
							errors < MAX_ERRORS);
					Future<List<MailExchangerRecord>> future = DnsExchangeFactory
							.resolveMx(domain);
					if (future.get() == null) {
						System.err
								.println("Failed to receive mail exchanger record for domain \""
										+ domain + "\".");
						errors++;
						break;
					}
				}
				for (int n = 0; n < TRIALS_PER_WEBSITE; n++) {
					assertTrue("Encountered too many errors.",
							errors < MAX_ERRORS);
					Future<List<List<String>>> future = DnsExchangeFactory
							.resolveTxt(domain);
					if (future.get() == null) {
						System.err
								.println("Failed to receive text record for domain \""
										+ domain + "\".");
						errors++;
						break;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}
		System.out.println("Finished DNS unit test.");
	}

}
