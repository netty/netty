package io.netty.handler.dns;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.Future;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class DnsTest {

	private static final boolean isIPAddress(String name) {
		String[] parts = name.split("\\.");
		if (parts.length != 4 && parts.length != 16)
			return false;
		for (String s : parts) {
			for (char c : s.toCharArray()) {
				if (!Character.isDigit(c))
					return false;
			}
		}
		return true;
	}

	public static final int TOTAL = 500;

	public static void main(String[] args) throws InterruptedException, IOException, ExecutionException {
		String[] domains = new String[TOTAL * 10];
		BufferedReader in = new BufferedReader(new FileReader("C:\\Users\\Mohamed\\Desktop\\top.txt"));
		String str = null;
		int i = 0;
		while ((str = in.readLine()) != null) {
			domains[i++] = str;
			if (i == domains.length)
				break;
		}
		in.close();
		int off = 0;
		for (i = 0; i < TOTAL + off; i++) {
			String name = domains[i % domains.length];
			if (name.indexOf('/') > -1 || isIPAddress(name)) {
				off++;
			}
			Future<ByteBuf> future = DnsExchangeFactory.lookup(name);
			future.get();
			// Bottom code works, not top
			/*DnsQuery query = new DnsQuery(5);
			query.addQuestion(new Question(name, Resource.TYPE_A));
			ByteBuf buf = Unpooled.buffer(512);
			DnsQueryEncoder.encodeHeader(query.getHeader(), buf);
			List<Question> questions = query.getQuestions();
			for (Question question : questions) {
				DnsQueryEncoder.encodeQuestion(question, buf);
			}
			DatagramSocket socket = new DatagramSocket(53);
			DatagramPacket packet = new DatagramPacket(buf.array(), buf.writerIndex(), InetAddress.getByAddress(new byte[] {(byte)192, (byte)168, 1, 1}), 53);
			socket.send(packet);
			DatagramPacket resp = new DatagramPacket(new byte[512], 512);
			socket.receive(resp);
			socket.close();*/
			System.out.println(i - off);
		}
	}

}
