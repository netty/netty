package bakkar.mohamed.dnsresolver;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.Future;
import bakkar.mohamed.dnscodec.Question;
import bakkar.mohamed.dnscodec.Resource;

public class DnsExchangeTest {

	public static void main(String[] args) throws Exception {
		byte[] dns = { (byte) 192, (byte) 168, 1, 37 }; // Google public dns
		String[] domain =  { "www.google.com", "google.com", "gmail.com", "yahoo.com", "youtube.com"};
		for (int i = 0; i < 5; i++) {
			new Resolver(dns, domain[i % domain.length]).start();
		}
		while (true) {
			int count;
			synchronized (DnsExchangeTest.class) {
				count = DnsExchangeTest.count;
			}
			if (count == 50000)
				break;
			Thread.sleep(1);
		}
		System.out.println(count);
		System.exit(0);
	}

	private static int count = 0;

	static class Resolver extends Thread {
		private final byte[] dns;
		private final String domain;

		public Resolver(byte[] dns, String domain) {
			this.dns = dns;
			this.domain = domain;
		}

		@Override
		public void run() {
			for (int i = 0; i < 10000; i++) {
				try {
					Future<ByteBuf> future = new DnsExchange(dns, new Question(domain, Resource.TYPE_A)).submitQuery();
					future.get();
					synchronized (DnsExchangeTest.class) {
						count++;
					}
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}
	}
}
