/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.http;

import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * Utility class for creating http requests for the operation of the full duplex
 * http tunnel, and verifying that received requests are of the correct types.
 */
final class HttpTunnelMessageUtils {

    private static final String HTTP_URL_PREFIX = "http://";

    /**
     * An upper bound is enforced on the size of message bodies, so as
     * to ensure we do not dump large chunks of data on either peer.
     */
    public static final int MAX_BODY_SIZE = 16 * 1024;

    /**
     * The tunnel will only accept connections from this specific user agent. This
     * allows us to distinguish a legitimate tunnel connection from someone pointing
     * a web browser or robot at the tunnel URL.
     */
    static final String USER_AGENT = "HttpTunnelClient";

    static final String OPEN_TUNNEL_REQUEST_URI = "/http-tunnel/open";

    static final String CLOSE_TUNNEL_REQUEST_URI = "/http-tunnel/close";

    static final String CLIENT_SEND_REQUEST_URI = "/http-tunnel/send";

    static final String CLIENT_RECV_REQUEST_URI = "/http-tunnel/poll";

    static final String CONTENT_TYPE = "application/octet-stream";

    public static HttpRequest createOpenTunnelRequest(SocketAddress host) {
        return createOpenTunnelRequest(convertToHostString(host));
    }

    public static HttpRequest createOpenTunnelRequest(String host) {
        HttpRequest request =
                createRequestTemplate(host, null, OPEN_TUNNEL_REQUEST_URI);
        setNoData(request);
        return request;
    }

    public static boolean isOpenTunnelRequest(HttpRequest request) {
        return isRequestTo(request, OPEN_TUNNEL_REQUEST_URI);
    }

    public static boolean checkHost(HttpRequest request,
            SocketAddress expectedHost) {
        String host = request.getHeader(HttpHeaders.Names.HOST);
        return expectedHost == null? host == null : HttpTunnelMessageUtils
                .convertToHostString(expectedHost).equals(host);
    }

    public static HttpRequest createSendDataRequest(SocketAddress host,
            String cookie, ChannelBuffer data) {
        return createSendDataRequest(convertToHostString(host), cookie, data);
    }

    public static HttpRequest createSendDataRequest(String host, String cookie,
            ChannelBuffer data) {
        HttpRequest request =
                createRequestTemplate(host, cookie, CLIENT_SEND_REQUEST_URI);
        request.setHeader(HttpHeaders.Names.CONTENT_LENGTH,
                Long.toString(data.readableBytes()));
        request.setContent(data);

        return request;
    }

    public static boolean isSendDataRequest(HttpRequest request) {
        return isRequestTo(request, CLIENT_SEND_REQUEST_URI);
    }

    public static HttpRequest createReceiveDataRequest(SocketAddress host,
            String tunnelId) {
        return createReceiveDataRequest(convertToHostString(host), tunnelId);
    }

    public static HttpRequest createReceiveDataRequest(String host,
            String tunnelId) {
        HttpRequest request =
                createRequestTemplate(host, tunnelId, CLIENT_RECV_REQUEST_URI);
        setNoData(request);
        return request;
    }

    public static boolean isReceiveDataRequest(HttpRequest request) {
        return isRequestTo(request, CLIENT_RECV_REQUEST_URI);
    }

    public static HttpRequest createCloseTunnelRequest(String host,
            String tunnelId) {
        HttpRequest request =
                createRequestTemplate(host, tunnelId, CLOSE_TUNNEL_REQUEST_URI);
        setNoData(request);
        return request;
    }

    public static boolean isCloseTunnelRequest(HttpRequest request) {
        return isRequestTo(request, CLOSE_TUNNEL_REQUEST_URI);
    }

    public static boolean isServerToClientRequest(HttpRequest request) {
        return isRequestTo(request, CLIENT_RECV_REQUEST_URI);
    }

    public static String convertToHostString(SocketAddress hostAddress) {
        StringWriter host = new StringWriter();
        InetSocketAddress inetSocketAddr = (InetSocketAddress) hostAddress;
        InetAddress addr = inetSocketAddr.getAddress();
        if (addr instanceof Inet6Address) {
            host.append('[');
            host.append(addr.getHostAddress());
            host.append(']');
        } else if (addr != null) {
            host.append(addr.getHostAddress());
        } else {
            host.append(inetSocketAddr.getHostName());
        }

        host.append(':');
        host.append(Integer.toString(inetSocketAddr.getPort()));
        return host.toString();
    }

    private static HttpRequest createRequestTemplate(String host,
            String tunnelId, String uri) {
        HttpRequest request =
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                        createCompleteUri(host, uri));
        request.setHeader(HttpHeaders.Names.HOST, host);
        request.setHeader(HttpHeaders.Names.USER_AGENT, USER_AGENT);
        if (tunnelId != null) {
            request.setHeader(HttpHeaders.Names.COOKIE, tunnelId);
        }

        return request;
    }

    private static String createCompleteUri(String host, String uri) {
        StringBuilder builder =
                new StringBuilder(HTTP_URL_PREFIX.length() + host.length() +
                        uri.length());
        builder.append(HTTP_URL_PREFIX);
        builder.append(host);
        builder.append(uri);

        return builder.toString();
    }

    private static boolean isRequestTo(HttpRequest request, String uri) {
        URI decodedUri;
        try {
            decodedUri = new URI(request.getUri());
        } catch (URISyntaxException e) {
            return false;
        }

        return HttpVersion.HTTP_1_1.equals(request.getProtocolVersion()) &&
                USER_AGENT.equals(request
                        .getHeader(HttpHeaders.Names.USER_AGENT)) &&
                HttpMethod.POST.equals(request.getMethod()) &&
                uri.equals(decodedUri.getPath());
    }

    private static void setNoData(HttpRequest request) {
        request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, "0");
        request.setContent(null);
    }

    public static String extractTunnelId(HttpRequest request) {
        return request.getHeader(HttpHeaders.Names.COOKIE);
    }

    private static byte[] toBytes(String string) {
        try {
            return string.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is meant to be supported on all platforms
            throw new RuntimeException("UTF-8 encoding not supported!");
        }
    }

    public static HttpResponse createTunnelOpenResponse(String tunnelId) {
        HttpResponse response =
                createResponseTemplate(HttpResponseStatus.CREATED, null);
        response.setHeader(HttpHeaders.Names.SET_COOKIE, tunnelId);
        return response;
    }

    public static boolean isTunnelOpenResponse(HttpResponse response) {
        return isResponseWithCode(response, HttpResponseStatus.CREATED);
    }

    public static boolean isOKResponse(HttpResponse response) {
        return isResponseWithCode(response, HttpResponseStatus.OK);
    }

    public static boolean hasContents(HttpResponse response,
            byte[] expectedContents) {
        if (response.getContent() != null &&
                HttpHeaders.getContentLength(response, 0) == expectedContents.length &&
                response.getContent().readableBytes() == expectedContents.length) {
            byte[] compareBytes = new byte[expectedContents.length];
            response.getContent().readBytes(compareBytes);
            return Arrays.equals(expectedContents, compareBytes);
        }

        return false;
    }

    public static HttpResponse createTunnelCloseResponse() {
        return createResponseTemplate(HttpResponseStatus.RESET_CONTENT, null);
    }

    public static boolean isTunnelCloseResponse(HttpResponse response) {
        return isResponseWithCode(response, HttpResponseStatus.RESET_CONTENT);
    }

    public static String extractCookie(HttpResponse response) {
        if (response.containsHeader(HttpHeaders.Names.SET_COOKIE)) {
            return response.getHeader(HttpHeaders.Names.SET_COOKIE);
        }

        return null;
    }

    public static HttpResponse createSendDataResponse() {
        return createOKResponseTemplate(null);
    }

    public static HttpResponse createRecvDataResponse(ChannelBuffer data) {
        return createOKResponseTemplate(data);
    }

    public static HttpResponse createRejection(HttpRequest request,
            String reason) {
        HttpVersion version =
                request != null? request.getProtocolVersion()
                        : HttpVersion.HTTP_1_1;
        HttpResponse response =
                new DefaultHttpResponse(version, HttpResponseStatus.BAD_REQUEST);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE,
                "text/plain; charset=\"utf-8\"");
        ChannelBuffer reasonBuffer =
                ChannelBuffers.wrappedBuffer(toBytes(reason));
        response.setHeader(HttpHeaders.Names.CONTENT_LENGTH,
                Integer.toString(reasonBuffer.readableBytes()));
        response.setContent(reasonBuffer);
        return response;
    }

    public static boolean isRejection(HttpResponse response) {
        return !HttpResponseStatus.OK.equals(response.getStatus());
    }

    public static Object extractErrorMessage(HttpResponse response) {
        if (response.getContent() == null ||
                HttpHeaders.getContentLength(response, 0) == 0) {
            return "";
        }

        byte[] bytes = new byte[response.getContent().readableBytes()];
        response.getContent().readBytes(bytes);
        try {
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private static boolean isResponseWithCode(HttpResponse response,
            HttpResponseStatus status) {
        return HttpVersion.HTTP_1_1.equals(response.getProtocolVersion()) &&
                status.equals(response.getStatus());
    }

    private static HttpResponse createOKResponseTemplate(ChannelBuffer data) {
        return createResponseTemplate(HttpResponseStatus.OK, data);
    }

    private static HttpResponse createResponseTemplate(
            HttpResponseStatus status, ChannelBuffer data) {
        HttpResponse response =
                new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
        if (data != null) {
            response.setHeader(HttpHeaders.Names.CONTENT_LENGTH,
                    Integer.toString(data.readableBytes()));
            response.setHeader(HttpHeaders.Names.CONTENT_TYPE,
                    "application/octet-stream");
            response.setContent(data);
        } else {
            response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, "0");
            response.setContent(null);
        }
        return response;
    }

    private HttpTunnelMessageUtils() {
        // Unused
    }
}
