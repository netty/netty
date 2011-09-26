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

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Base class for web socket frames
 * 
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 */
public abstract class WebSocketFrame {

	/**
	 * Contents of this frame
	 */
	private ChannelBuffer binaryData;

	/**
	 * Returns the type of this frame.
	 */
	public abstract WebSocketFrameType getType();

	/**
	 * Returns binary data
	 */
	public ChannelBuffer getBinaryData() {
		return binaryData;
	}

	/**
	 * Sets the binary data for this frame
	 */
	public void setBinaryData(ChannelBuffer binaryData) {
		this.binaryData = binaryData;
	}

}
