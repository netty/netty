package io.netty.handler.codec.dns;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Connects to a DNS server and retrieves the IP address of a website
 * using its domain name. A DNS server and a domain must be given as
 * arguments to this program. Example:
 * 
 * DNSTest 8.8.4.4 www.google.com
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public class DNSTest {

	public static void main(String[] args) throws Exception {
		byte[] dns = new byte[4];
		String domain = null;
		try {
			String[] dnsAddress = args[0].split("\\.");
			for (int i = 0; i < 4; i++) {
				dns[i] = (byte) Integer.parseInt(dnsAddress[i]);
			}
			domain = args[1];
		} catch (Exception e) {
			System.out.println("Usage:");
			System.out.println("\tDNSTest <DNS IP> <DOMAIN QUERYING>");
			System.out.println("Example:");
			System.out.println("\tDNSTest 8.8.4.4 www.google.com");
			System.exit(1);
		}
		Query query = new Query(15305/* This is our generated ID for the DNS packet  */, new Question(domain, Resource.TYPE_A));
		byte[] data = query.encode();
		System.out.println("Sending packet...");
		DatagramSocket socket = new DatagramSocket();
		DatagramPacket send = new DatagramPacket(data, data.length, InetAddress.getByAddress(dns), 53);
		socket.send(send);
		byte[] buf = new byte[512];
		DatagramPacket receive = new DatagramPacket(buf, buf.length, InetAddress.getByAddress(dns), 53);
		System.out.println("Listening for response...");
		socket.receive(receive);
		socket.close();
		Response response = Response.decode(receive.getData());
		if (response.getHeader().getId() != 15305) {
			throw new Exception("Who's DNS packet is this anyways?! Invalid ID: " + response.getHeader().getId());
		}
		System.out.println("Questions: " + response.getQuestionCount());
		System.out.println("Answer records: " + response.getAnswerCount());
		System.out.println("Authority records: " + response.getAuthorityResourceCount());
		System.out.println("Additional records: " + response.getAdditionalResourceCount());
		Resource[] answers = response.getAnswers();
		for (int i = 0; i < answers.length; i++) {
			if (answers[i].getType() == DNSEntry.TYPE_A) {
				byte[] info = answers[i].getData();
				StringBuilder builder = new StringBuilder();
				for (byte b : info)
					builder.append(b & 0xff).append(".");
				System.out.println(builder.substring(0, builder.length() - 1));
			}
		}
	}

}
