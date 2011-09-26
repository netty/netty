/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http.websocketx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * Encodes binary data to plain text as Base64.
 * 
 * Despite there being a gazillion other Base64 implementations out there, this
 * has been written as part of XStream as it forms a core part but is too
 * trivial to warrant an extra dependency.
 * 
 * This meets the standard as described in RFC 1521, section 5.2
 * <http://www.freesoft.org/CIE/RFC/1521/7.htm>, allowing other Base64 tools to
 * manipulate the data.
 * 
 * This code originally came from the XStream http://xstream.codehaus.org
 * project by Joe Walnes. Relicensed to Webbit.
 */
public class Base64 {

	// Here's how encoding works:
	//
	// 1) Incoming bytes are broken up into groups of 3 (each byte having 8
	// bits).
	//
	// 2) The combined 24 bits (3 * 8) are split into 4 groups of 6 bits.
	//
	// input |------||------||------| (3 values each with 8 bits)
	// 101010101010101010101010
	// output |----||----||----||----| (4 values each with 6 bits)
	//
	// 3) Each of these 4 groups of 6 bits are converted back to a number, which
	// will fall in the range of 0 - 63.
	//
	// 4) Each of these 4 numbers are converted to an alphanumeric char in a
	// specified mapping table, to create
	// a 4 character string.
	//
	// 5) This is repeated for all groups of three bytes.
	//
	// 6) Special padding is done at the end of the stream using the '=' char.

	private static final char[] SIXTY_FOUR_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
			.toCharArray();
	private static final int[] REVERSE_MAPPING = new int[123];

	static {
		for (int i = 0; i < SIXTY_FOUR_CHARS.length; i++)
			REVERSE_MAPPING[SIXTY_FOUR_CHARS[i]] = i + 1;
	}

	public static String encode(byte[] input) {
		StringBuilder result = new StringBuilder();
		int outputCharCount = 0;
		for (int i = 0; i < input.length; i += 3) {
			int remaining = Math.min(3, input.length - i);
			int oneBigNumber = (input[i] & 0xff) << 16 | (remaining <= 1 ? 0 : input[i + 1] & 0xff) << 8
					| (remaining <= 2 ? 0 : input[i + 2] & 0xff);
			for (int j = 0; j < 4; j++)
				result.append(remaining + 1 > j ? SIXTY_FOUR_CHARS[0x3f & oneBigNumber >> 6 * (3 - j)] : '=');
			if ((outputCharCount += 4) % 76 == 0)
				result.append('\n');
		}
		return result.toString();
	}

	public static byte[] decode(String input) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			StringReader in = new StringReader(input);
			for (int i = 0; i < input.length(); i += 4) {
				int a[] = { mapCharToInt(in), mapCharToInt(in), mapCharToInt(in), mapCharToInt(in) };
				int oneBigNumber = (a[0] & 0x3f) << 18 | (a[1] & 0x3f) << 12 | (a[2] & 0x3f) << 6 | (a[3] & 0x3f);
				for (int j = 0; j < 3; j++) {
					if (a[j + 1] >= 0) {
						out.write(0xff & oneBigNumber >> 8 * (2 - j));
					}
				}
			}
			return out.toByteArray();
		} catch (IOException e) {
			throw new Error(e + ": " + e.getMessage());
		}
	}

	private static int mapCharToInt(Reader input) throws IOException {
		int c;
		while ((c = input.read()) != -1) {
			int result = REVERSE_MAPPING[c];
			if (result != 0)
				return result - 1;
			if (c == '=')
				return -1;
		}
		return -1;
	}
}
