/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

/**
 * Handler for outbound traffic on behalf of {@link Http2ConectionHandler}.
 */
public interface Http2ConnectionEncoder extends Http2FrameWriter, Http2OutboundFlowController {

    /**
     * Gets the local settings on the top of the queue that has been sent but not ACKed. This may
     * return {@code null}.
     */
    Http2Settings pollSentSettings();

    /**
     * Sets the settings for the remote endpoint of the HTTP/2 connection.
     */
    void remoteSettings(Http2Settings settings) throws Http2Exception;
}
