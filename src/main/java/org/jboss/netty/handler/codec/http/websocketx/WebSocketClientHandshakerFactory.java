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

import java.net.URI;

/**
 * Instances the appropriate handshake class to use for clients
 * 
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 */
public class WebSocketClientHandshakerFactory {

	/**
	 * Instances a new handshaker
	 * 
	 * @param webSocketURL
	 *            URL for web socket communications. e.g
	 *            "ws://myhost.com/mypath". Subsequent web socket frames will be
	 *            sent to this URL.
	 * @param version
	 *            Version of web socket specification to use to connect to the
	 *            server
	 * @param subProtocol
	 *            Sub protocol request sent to the server. Null if no
	 *            sub-protocol support is required.
	 * @throws WebSocketHandshakeException
	 */
	public WebSocketClientHandshaker newHandshaker(URI webSocketURL, WebSocketSpecificationVersion version,
			String subProtocol) throws WebSocketHandshakeException {
		if (version == WebSocketSpecificationVersion.V10) {
			return new WebSocketClientHandshaker10(webSocketURL, version, subProtocol);
		}
		if (version == WebSocketSpecificationVersion.V00) {
			return new WebSocketClientHandshaker00(webSocketURL, version, subProtocol);
		}

		throw new WebSocketHandshakeException("Protocol version " + version.toString() + " not supported.");

	}
}
