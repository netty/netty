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
package io.netty.example.http2.client.httpobject;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.Http2HttpHeaders;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class Http2ResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

  @Override
  protected void messageReceived(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
    System.out.println("Http2ResponseHandler.messageReceived message type: " + msg.getClass() + " Body size: "
        + msg.content().readableBytes());
    String streamId = msg.headers().get(Http2HttpHeaders.Names.STREAM_ID);
    System.out.println("Http2ResponseHandler.messageReceived streamId: " + streamId);
  }
}
