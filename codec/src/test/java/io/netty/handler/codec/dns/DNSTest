package bakkar.mohamed.dnscodec;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Test {

	public static void main(String[] args) throws Exception {
		Query query = new Query(15305);
		query.addQuestion(new Question(Question.A, "microsoft", "com"));
		byte[] data = query.encode();
		System.out.println(data.length);
		for (byte b : data) {
			System.out.print((b & 0xff) + " ");
		}
		System.out.println();
		System.out.println("Sending...");
		DatagramSocket socket = new DatagramSocket(53);
		DatagramPacket send = new DatagramPacket(data, data.length, InetAddress.getByAddress(new byte[] {(byte) 192, (byte) 168, 1, 37}), 53);
		socket.send(send);
		System.out.println("Sent...");
		byte[] buf = new byte[512];
		DatagramPacket receive = new DatagramPacket(buf, buf.length);
		
		System.out.println("Receiving...");
		socket.receive(receive);
		System.out.println("Received...");
		socket.close();
		System.out.println("Success");
		Response.decode(receive.getData());
	}

}
