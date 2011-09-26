// (BSD License: http://www.opensource.org/licenses/bsd-license)
//
// Copyright (c) 2011, Joe Walnes and contributors
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or
// without modification, are permitted provided that the
// following conditions are met:
//
// * Redistributions of source code must retain the above
// copyright notice, this list of conditions and the
// following disclaimer.
//
// * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the
// following disclaimer in the documentation and/or other
// materials provided with the distribution.
//
// * Neither the name of the Webbit nor the names of
// its contributors may be used to endorse or promote products
// derived from this software without specific prior written
// permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
// CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
// GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
// LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
// OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package org.jboss.netty.handler.codec.http.websocketx;

import java.nio.ByteBuffer;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

/**
 * <p>
 * Encodes a web socket frame into wire protocol version 8 format. This code was
 * originally taken from webbit and modified.
 * </p>
 * <p>
 * Currently fragmentation is not supported. Data is always sent in 1 frame.
 * </p>
 * 
 * @author https://github.com/joewalnes/webbit
 * @author <a href="http://www.veebsbraindump.com/">Vibul Imtarnasan</a>
 */
public class WebSocket08FrameEncoder extends OneToOneEncoder {

	private static final byte OPCODE_TEXT = 0x1;
	private static final byte OPCODE_BINARY = 0x2;
	private static final byte OPCODE_CLOSE = 0x8;
	private static final byte OPCODE_PING = 0x9;
	private static final byte OPCODE_PONG = 0xA;

	private boolean maskPayload = false;

	/**
	 * Constructor
	 * 
	 * @param maskPayload
	 *            Web socket clients must set this to true to mask payload.
	 *            Server implementations must set this to false.
	 */
	public WebSocket08FrameEncoder(boolean maskPayload) {
		this.maskPayload = maskPayload;
	}

	@Override
	protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {

		byte[] mask = null;

		if (msg instanceof WebSocketFrame) {
			WebSocketFrame frame = (WebSocketFrame) msg;
			ChannelBuffer data = frame.getBinaryData();

			// Create buffer with 10 extra bytes for:
			// 1 byte opCode
			// 5 bytes length in worst case scenario
			// 4 bites mask
			ChannelBuffer encoded = channel.getConfig().getBufferFactory()
					.getBuffer(data.order(), data.readableBytes() + 10);

			// Write opcode and length
			byte opcode;
			if (frame instanceof TextWebSocketFrame) {
				opcode = OPCODE_TEXT;
			} else if (frame instanceof PingWebSocketFrame) {
				opcode = OPCODE_PING;
			} else if (frame instanceof PongWebSocketFrame) {
				opcode = OPCODE_PONG;
			} else if (frame instanceof CloseWebSocketFrame) {
				opcode = OPCODE_CLOSE;
			} else if (frame instanceof BinaryWebSocketFrame) {
				opcode = OPCODE_BINARY;
			} else {
				throw new UnsupportedOperationException("Cannot encode frame of type: " + frame.getClass().getName());
			}
			encoded.writeByte(0x80 | opcode); // Fragmentation currently not
												// supported

			int length = data.readableBytes();
			if (length < 126) {
				byte b = (byte) (this.maskPayload ? (0x80 | (byte) length) : (byte) length);
				encoded.writeByte(b);
			} else if (length < 65535) {
				byte b = (byte) (this.maskPayload ? (0xFE) : 126);
				encoded.writeByte(b);
				encoded.writeShort(length);
			} else {
				byte b = (byte) (this.maskPayload ? (0xFF) : 127);
				encoded.writeByte(b);
				encoded.writeInt(length);
			}

			// Write payload
			if (this.maskPayload) {
				Integer random = (int) (Math.random() * Integer.MAX_VALUE);
				mask = ByteBuffer.allocate(4).putInt(random).array();

				encoded.writeBytes(mask);

				int counter = 0;
				while (data.readableBytes() > 0) {
					byte byteData = data.readByte();
					encoded.writeByte(byteData ^ mask[+counter++ % 4]);
				}

				counter++;
			} else {
				encoded.writeBytes(data, data.readerIndex(), data.readableBytes());
				encoded = encoded.slice(0, encoded.writerIndex());
			}

			return encoded;
		}
		return msg;
	}
}