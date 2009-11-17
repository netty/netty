/*
 * Copyright 2009 Red Hat, Inc.
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
package org.jboss.netty.example.http.snoop;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public class HttpRequestHandler extends SimpleChannelUpstreamHandler {

    private volatile HttpRequest request;
    private volatile boolean readingChunks;
    private final StringBuilder responseContent = new StringBuilder();

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (!readingChunks) {
            HttpRequest request = this.request = (HttpRequest) e.getMessage();
            responseContent.append("WELCOME TO THE WILD WILD WEB SERVER\r\n");
            responseContent.append("===================================\r\n");

            responseContent.append("VERSION: " + request.getProtocolVersion().getText() + "\r\n");

            if (request.containsHeader(HttpHeaders.Names.HOST)) {
                responseContent.append("HOSTNAME: " + request.getHeader(HttpHeaders.Names.HOST) + "\r\n");
            }

            responseContent.append("REQUEST_URI: " + request.getUri() + "\r\n\r\n");

            if (!request.getHeaderNames().isEmpty()) {
                for (String name: request.getHeaderNames()) {
                    for (String value: request.getHeaders(name)) {
                        responseContent.append("HEADER: " + name + " = " + value + "\r\n");
                    }
                }
                responseContent.append("\r\n");
            }

            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());
            Map<String, List<String>> params = queryStringDecoder.getParameters();
            if (!params.isEmpty()) {
                for (Entry<String, List<String>> p: params.entrySet()) {
                    String key = p.getKey();
                    List<String> vals = p.getValue();
                    for (String val : vals) {
                        responseContent.append("PARAM: " + key + " = " + val + "\r\n");
                    }
                }
                responseContent.append("\r\n");
            }

            if (request.isChunked()) {
                readingChunks = true;
            } else {
                ChannelBuffer content = request.getContent();
                if (content.readable()) {
                    responseContent.append("CONTENT: " + content.toString("UTF-8") + "\r\n");
                }
                writeResponse(e);
            }
        } else {
            HttpChunk chunk = (HttpChunk) e.getMessage();
            if (chunk.isLast()) {
                readingChunks = false;
                responseContent.append("END OF CONTENT\r\n");

                HttpChunkTrailer trailer = (HttpChunkTrailer) chunk;
                if (!trailer.getHeaderNames().isEmpty()) {
                    responseContent.append("\r\n");
                    for (String name: trailer.getHeaderNames()) {
                        for (String value: trailer.getHeaders(name)) {
                            responseContent.append("TRAILING HEADER: " + name + " = " + value + "\r\n");
                        }
                    }
                    responseContent.append("\r\n");
                }

                writeResponse(e);
            } else {
                responseContent.append("CHUNK: " + chunk.getContent().toString("UTF-8") + "\r\n");
            }
        }
    }

    private void writeResponse(MessageEvent e) {
        // Convert the response content to a ChannelBuffer.
        ChannelBuffer buf = ChannelBuffers.copiedBuffer(responseContent.toString(), "UTF-8");
        responseContent.setLength(0);

        // Decide whether to close the connection or not.
        boolean close =
            HttpHeaders.Values.CLOSE.equalsIgnoreCase(request.getHeader(HttpHeaders.Names.CONNECTION)) ||
            request.getProtocolVersion().equals(HttpVersion.HTTP_1_0) &&
            !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(request.getHeader(HttpHeaders.Names.CONNECTION));

        // Build the response object.
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.setContent(buf);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");

        if (!close) {
            // There's no need to add 'Content-Length' header
            // if this is the last response.
            response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(buf.readableBytes()));
        }

        String cookieString = request.getHeader(HttpHeaders.Names.COOKIE);
        if (cookieString != null) {
            CookieDecoder cookieDecoder = new CookieDecoder();
            Set<Cookie> cookies = cookieDecoder.decode(cookieString);
            if(!cookies.isEmpty()) {
                // Reset the cookies if necessary.
                CookieEncoder cookieEncoder = new CookieEncoder(true);
                for (Cookie cookie : cookies) {
                    cookieEncoder.addCookie(cookie);
                }
                response.addHeader(HttpHeaders.Names.SET_COOKIE, cookieEncoder.encode());
            }
        }

        // Write the response.
        ChannelFuture future = e.getChannel().write(response);

        // Close the connection after the write operation is done if necessary.
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        e.getCause().printStackTrace();
        e.getChannel().close();
    }
}
