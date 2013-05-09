package bakkar.mohamed.dnscodec;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class DNSTest {

	public static void main(String[] args) throws Exception {
		byte[] dns = new byte[4];
		try {
			String[] dnsAddress = args[0].split("\\.");
			for (int i = 0; i < 4; i++) {
				dns[i] = (byte) Integer.parseInt(dnsAddress[i]);
			}
		} catch (Exception e) {
			System.out.println("Usage:");
			System.out.println("\tDNSTest <DNS IP>");
			System.out.println("Example:");
			System.out.println("\tDNSTest 8.8.8.8");
			System.exit(1);
		}
		Query query = new Query(15305, new Question(Resource.A, "google.com"));
		byte[] data = query.encode();
		System.out.println("Sending...");
		DatagramSocket socket = new DatagramSocket(53);
		DatagramPacket send = new DatagramPacket(data, data.length, InetAddress.getByAddress(dns), 53);
		socket.send(send);
		byte[] buf = new byte[512];
		DatagramPacket receive = new DatagramPacket(buf, buf.length);
		System.out.println("Receiving...");
		socket.receive(receive);
		socket.close();
		Response response = Response.decode(receive.getData());
		System.out.println("Questions: " + response.getQuestionCount());
		System.out.println("Answers: " + response.getAnswerCount());
		System.out.println("Name servers: " + response.getNameServerResourceCount());
		System.out.println("Additional: " + response.getAdditionalResourceCount());
		Resource[] answers = response.getAnswers();
		for (int i = 0; i < answers.length; i++) {
			if (answers[i].getType() != Resource.A) {
				//System.out.println(new String(answers[i].getData()));
				for (byte b : answers[i].getData())
					System.out.print((b) + " ");
				System.out.println();
			} else {
				byte[] info = answers[i].getData();
				StringBuilder builder = new StringBuilder();
				for (byte b : info)
					builder.append(b & 0xff).append(".");
				System.out.println(builder.substring(0, builder.length() - 1));
			}
		}
		answers = response.getNameServers();
		for (int i = 0; i < answers.length; i++) {
			if (answers[i].getType() != Resource.A) {
				//System.out.println(new String(answers[i].getData()));
				for (byte b : answers[i].getData())
					System.out.print((b) + " ");
				System.out.println();
				System.out.println(new String(answers[i].getData()));
			} else {
				byte[] info = answers[i].getData();
				StringBuilder builder = new StringBuilder();
				for (byte b : info)
					builder.append(b & 0xff).append(".");
				System.out.println(builder.substring(0, builder.length() - 1));
			}
		}
	}

}
