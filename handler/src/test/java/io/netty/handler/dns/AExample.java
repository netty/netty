package io.netty.handler.dns;

import io.netty.util.concurrent.Future;
import bakkar.mohamed.dnscodec.Question;
import bakkar.mohamed.dnscodec.Resource;

public class AExample {

	public static void main(String[] args) throws Exception {
		byte[] dns = { (byte) 192, (byte) 168, 1, 37 }; // Google public dns
		String[] domain =  { "www.google.com", "google.com", "gmail.com", "yahoo.com", "reddit.com"};
		for (int i = 0; i < domain.length * 10; i++) {
			long start = System.nanoTime();
			Future<byte[]> future = new DnsTransmission(dns, new Question(domain[i % domain.length], Resource.TYPE_A)).submitQuery();
			while (!future.isDone()) {
				Thread.sleep(1);
			}
			byte[] b = future.get();
			for (int n = 0; n < 4; n++) {
				System.out.print((b[n] & 0xff) + ".");
			}
			long end = System.nanoTime();
			System.out.println();
			System.out.println((end - start) / 1000000);
		}
	}

}
