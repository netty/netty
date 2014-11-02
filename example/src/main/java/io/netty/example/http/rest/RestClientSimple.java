/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.http.rest;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.netty.handler.codec.rest.RestArgument;
import io.netty.handler.codec.rest.RestInvalidAuthenticationException;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Promise;

public class RestClientSimple {
    private final EventLoopGroup workerGroup;
    private final Bootstrap bootstrap;
    private final HttpHeaders headers;
    private String baseUri = "/";

    public RestClientSimple(String baseUri, int nbclient, long timeout, ChannelInitializer<SocketChannel> initializer) {
        if (baseUri != null) {
            this.baseUri = baseUri;
        }
        // Configure the client.
        bootstrap = new Bootstrap();
        workerGroup = new NioEventLoopGroup(nbclient);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.group(workerGroup);
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        bootstrap.option(ChannelOption.SO_REUSEADDR, true);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000);
        bootstrap.option(ChannelOption.SO_RCVBUF, 1048576);
        bootstrap.option(ChannelOption.SO_SNDBUF, 1048576);
        // Configure the pipeline factory.
        bootstrap.handler(initializer);
        // will ignore real request
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, baseUri);
        headers = request.headers();
        headers.set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP + "," + HttpHeaderValues.DEFLATE);
        headers.set(HttpHeaderNames.ACCEPT_CHARSET, "utf-8;q=0.7,*;q=0.7");
        headers.set(HttpHeaderNames.ACCEPT_LANGUAGE, "fr,en");
        headers.set(HttpHeaderNames.USER_AGENT, "Netty Simple Http Rest Client side");
        headers.set(HttpHeaderNames.ACCEPT,
                "text/html,text/plain,application/xhtml+xml,application/xml,application/json;q=0.9,*/*;q=0.8");
    }

    /**
     * Create one new connection to the remote host using port
     * @return the channel if connected or Null if not
     */
    public Channel getChannel(String host, int port) {
        // Start the connection attempt.
        ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
        // Wait until the connection attempt succeeds or fails.
        Channel channel = future.awaitUninterruptibly().channel();
        if (channel != null) {
            resetSession(channel);
        }
        return channel;
    }

    /**
     * Reset the session in order to make a new request
     */
    public void resetSession(Channel channel) {
        Promise<Object> promise = bootstrap.group().next().newPromise();
        RestArgument argument = new RestArgument(null);
        argument.setPromise(promise);
        channel.attr(RestClientSimpleResponseHandler.RESTARGUMENT).set(argument);
    }

    /**
     * Send an HTTP query using the channel for target, using signature
     *
     * @param hmac
     *            SHA-1 or SHA-256 or equivalent key to create the signature
     * @param algo algorithm for the key ("HmacSHA256" for instance)
     * @param channel
     *            target of the query
     * @param method
     *            HttpMethod to use
     * @param host
     *            target of the query (shall be the same as for the channel)
     * @param addedUri
     *            additional uri, added to baseUri (shall include also extra arguments) (might be
     *            null)
     * @param user
     *            user to use in authenticated Rest procedure (might be null)
     * @param uriArgs
     *            arguments for Uri if any (might be null)
     * @param body
     *            to send as body in the request (might be null); Useful in PUT, POST but
     *            should not in GET, DELETE, OPTIONS
     * @return the RestArgument associated with this request
     */
    public RestArgument sendQuery(Key hmac, String algo, Channel channel, HttpMethod method,
            String host, String addedUri, String user, Map<String, List<String>> uriArgs,
            String body) {
        // Prepare the HTTP request.
        RestArgument argument = channel.attr(RestClientSimpleResponseHandler.RESTARGUMENT).get();
        QueryStringEncoder encoder = null;
        if (addedUri != null) {
            encoder = new QueryStringEncoder(baseUri + addedUri);
        } else {
            encoder = new QueryStringEncoder(baseUri);
        }
        // add Form attribute
        if (uriArgs != null) {
            for (Entry<String, List<String>> elt : uriArgs.entrySet()) {
                for (String item : elt.getValue()) {
                    encoder.addParam(elt.getKey(), item);
                }
            }
        }
        String[] result = null;
        try {
            result = RestArgument.getBaseAuthent(hmac, algo, encoder, user, RestExampleCommon.EXAMPLESECRET);
        } catch (RestInvalidAuthenticationException e) {
            argument.getPromise().setFailure(e);
            return argument;
        }
        URI uri;
        try {
            uri = encoder.toUri();
        } catch (URISyntaxException e) {
            argument.getPromise().setFailure(e);
            return argument;
        }
        FullHttpRequest request;
        if (body != null) {
            ByteBuf buffer = Unpooled.wrappedBuffer(body.getBytes(CharsetUtil.UTF_8));
            request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri.toASCIIString(),
                    buffer);
            request.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, buffer.readableBytes());
        } else {
            request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri.toASCIIString());
        }
        // it is legal to add directly header or cookie into the request until finalize
        request.headers().add(this.headers);
        request.headers().set(HttpHeaderNames.HOST, host);
        if (user != null) {
            request.headers().set(RestArgument.REST_ROOT_FIELD.ARG_X_AUTH_USER.field, user);
        }
        request.headers().set(RestArgument.REST_ROOT_FIELD.ARG_X_AUTH_TIMESTAMP.field, result[0]);
        request.headers().set(RestArgument.REST_ROOT_FIELD.ARG_X_AUTH_KEY.field, result[1]);
        // send request
        channel.writeAndFlush(request);
        return argument;
    }

    /**
     * Send an HTTP query using the channel for target, without any Signature but the timestamp
     *
     * @param channel
     *            target of the query
     * @param method
     *            HttpMethod to use
     * @param host
     *            target of the query (shall be the same as for the channel)
     * @param addedUri
     *            additional uri, added to baseUri (shall include also extra arguments) (might be
     *            null)
     * @param user
     *            user to use in authenticated Rest procedure (might be null)
     * @param uriArgs
     *            arguments for Uri if any (might be null)
     * @param body
     *            to send as body in the request (might be null); Useful in PUT, POST but
     *            should not in GET, DELETE, OPTIONS
     * @return the RestFuture associated with this request
     */
    public RestArgument sendQuery(Channel channel, HttpMethod method, String host, String addedUri,
            String user, Map<String, List<String>> uriArgs, String body) {
        // Prepare the HTTP request.
        RestArgument argument = channel.attr(RestClientSimpleResponseHandler.RESTARGUMENT).get();
        QueryStringEncoder encoder = null;
        if (addedUri != null) {
            encoder = new QueryStringEncoder(baseUri + addedUri);
        } else {
            encoder = new QueryStringEncoder(baseUri);
        }
        // add Form attribute
        if (uriArgs != null) {
            for (Entry<String, List<String>> elt : uriArgs.entrySet()) {
                for (String item : elt.getValue()) {
                    encoder.addParam(elt.getKey(), item);
                }
            }
        }
        URI uri;
        try {
            uri = encoder.toUri();
        } catch (URISyntaxException e) {
            argument.getPromise().setFailure(e);
            return argument;
        }
        FullHttpRequest request;
        if (body != null) {
            ByteBuf buffer = Unpooled.wrappedBuffer(body.getBytes(CharsetUtil.UTF_8));
            request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri.toASCIIString(),
                    buffer);
            request.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, buffer.readableBytes());
        } else {
            request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri.toASCIIString());
        }
        // it is legal to add directly header or cookie into the request until finalize
        request.headers().add(this.headers);
        request.headers().set(HttpHeaderNames.HOST, host);
        if (user != null) {
            request.headers().set(RestArgument.REST_ROOT_FIELD.ARG_X_AUTH_USER.field, user);
        }
        request.headers().set(RestArgument.REST_ROOT_FIELD.ARG_X_AUTH_TIMESTAMP.field,
                RestArgument.dateToIso8601(new Date()));
        // send request
        channel.writeAndFlush(request);
        return argument;
    }

    /**
     * Finalize the HttpRestClientHelper
     */
    public void closeAll() {
        bootstrap.group().shutdownGracefully();
    }

    /**
     *
     * @param args
     *            as uri (http://host:port/uri method user pwd sign=path|nosign [body])
     */
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Need more arguments: http://host:port/uri method user sign|nosign [body]\n" +
                    "http://127.0.0.1:8080/test/subtest?try=two POST usertest nosign\b" +
                    "http://127.0.0.1:8081/test/subtest?try=two POST usertest sign");
            return;
        }
        String uri = args[0];
        String meth = args[1];
        String user = args[2];
        boolean sign = args[3].toLowerCase().startsWith("sign");
        Key hmac = null;

        if (sign) {
            hmac = RestExampleCommon.loadSecretKey(RestExampleCommon.EXAMPLESHAKEY);
        }
        String body = null;
        if (args.length > 4) {
            body = args[4];
        }
        HttpMethod method = HttpMethod.valueOf(meth);
        int port = -1;
        String host = null;
        String path = null;
        Map<String, List<String>> map = null;
        try {
            URI realUri = new URI(uri);
            port = realUri.getPort();
            host = realUri.getHost();
            path = realUri.getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            int pos = uri.indexOf('?');
            if (pos > 0) {
                QueryStringDecoder decoderQuery = new QueryStringDecoder(uri);
                map = new HashMap<String, List<String>>();
                map.putAll(decoderQuery.parameters());
            }
        } catch (URISyntaxException e) {
            System.err.println("Error" + e);
            return;
        }
        RestClientSimple client = new RestClientSimple("/", 1, 30000,
                new RestClientSimpleInitializer());
        Channel channel = client.getChannel(host, port);
        if (channel == null) {
            client.closeAll();
            System.err.println("Cannot connect to " + host + " on port " + port);
            return;
        }
        // Send request
        RestArgument argument = null;
        if (sign) {
            argument = client.sendQuery(hmac, RestExampleCommon.ALGO, channel, method, host,
                    path, user, map, body);
        } else {
            argument = client.sendQuery(channel, method, host, path, user, map, body);
        }
        try {
            argument.getPromise().await();
        } catch (InterruptedException e) {
            client.closeAll();
            System.err.println("Interruption " + e);
            return;
        }
        if (argument.getPromise().isSuccess()) {
            System.out.println("Response: " + argument.responseBody());
        } else {
            System.err.println(argument.status().reasonPhrase() + " = " + argument.responseBody());
            client.closeAll();
            return;
        }
        client.resetSession(channel);
        // Send global OPTIONS request
        RestArgument argument2 = null;
        if (sign) {
            argument2 = client.sendQuery(hmac, RestExampleCommon.ALGO, channel, HttpMethod.OPTIONS, host, null,
                    user, null, null);
        } else {
            argument2 = client.sendQuery(channel, HttpMethod.OPTIONS, host, null, user, null, null);
        }
        try {
            argument2.getPromise().await();
        } catch (InterruptedException e) {
            client.closeAll();
            System.err.println("Interruption " + e);
            return;
        }
        if (argument2.getPromise().isSuccess()) {
            System.out.println("Response: " + argument2.responseBody());
        } else {
            System.err.println(argument2.status().reasonPhrase() + " = " + argument2.responseBody());
        }
        channel.close();
        client.closeAll();
    }
}
