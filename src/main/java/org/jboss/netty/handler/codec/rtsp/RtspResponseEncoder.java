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
package org.jboss.netty.handler.codec.rtsp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * Encodes an RTSP response represented in {@link HttpResponse} into
 * a {@link ChannelBuffer}.

 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://amitbhayani.blogspot.com/">Amit Bhayani</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2117 $, $Date: 2010-02-01 17:28:17 +0900 (Mon, 01 Feb 2010) $
 */
public class RtspResponseEncoder extends RtspMessageEncoder {

    @Override
    protected void encodeInitialLine(ChannelBuffer buf, HttpMessage message)
            throws Exception {
        HttpResponse response = (HttpResponse) message;
        buf.writeBytes(response.getProtocolVersion().toString().getBytes("ASCII"));
        buf.writeByte((byte) ' ');
        buf.writeBytes(String.valueOf(response.getStatus().getCode()).getBytes("ASCII"));
        buf.writeByte((byte) ' ');
        buf.writeBytes(String.valueOf(response.getStatus().getReasonPhrase()).getBytes("ASCII"));
        buf.writeByte((byte) '\r');
        buf.writeByte((byte) '\n');
    }
}
