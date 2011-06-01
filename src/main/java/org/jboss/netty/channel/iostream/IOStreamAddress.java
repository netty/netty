package org.jboss.netty.channel.iostream;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;

public class IOStreamAddress extends SocketAddress {

	private final InputStream inputStream;

	private final OutputStream outputStream;

	public IOStreamAddress(final InputStream inputStream, final OutputStream outputStream) {

		this.inputStream = inputStream;
		this.outputStream = outputStream;
	}

	public InputStream getInputStream() {
		return inputStream;
	}

	public OutputStream getOutputStream() {
		return outputStream;
	}
}
