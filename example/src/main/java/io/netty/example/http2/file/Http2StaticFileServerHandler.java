/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.http2.file;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataChunkedInput;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.SystemPropertyUtil;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;

/**
 * A simple handler that serves incoming HTTP requests to send their respective
 * HTTP responses.  It also implements {@code 'If-Modified-Since'} header to
 * take advantage of browser cache, as described in
 * <a href="https://tools.ietf.org/html/rfc2616#section-14.25">RFC 2616</a>.
 *
 * <h3>How Browser Caching Works</h3>
 * <p>
 * Web browser caching works with HTTP headers as illustrated by the following
 * sample:
 * <ol>
 * <li>Request #1 returns the content of {@code /file1.txt}.</li>
 * <li>Contents of {@code /file1.txt} is cached by the browser.</li>
 * <li>Request #2 for {@code /file1.txt} does not return the contents of the
 *     file again. Rather, a 304 Not Modified is returned. This tells the
 *     browser to use the contents stored in its cache.</li>
 * <li>The server knows the file has not been modified because the
 *     {@code If-Modified-Since} date is the same as the file's last
 *     modified date.</li>
 * </ol>
 *
 * <pre>
 * Request #1 Headers
 * ===================
 * GET /file1.txt HTTP/1.1
 *
 * Response #1 Headers
 * ===================
 * HTTP/1.1 200 OK
 * Date:               Tue, 01 Mar 2011 22:44:26 GMT
 * Last-Modified:      Wed, 30 Jun 2010 21:36:48 GMT
 * Expires:            Tue, 01 Mar 2012 22:44:26 GMT
 * Cache-Control:      private, max-age=31536000
 *
 * Request #2 Headers
 * ===================
 * GET /file1.txt HTTP/1.1
 * If-Modified-Since:  Wed, 30 Jun 2010 21:36:48 GMT
 *
 * Response #2 Headers
 * ===================
 * HTTP/1.1 304 Not Modified
 * Date:               Tue, 01 Mar 2011 22:44:28 GMT
 *
 * </pre>
 */
public class Http2StaticFileServerHandler extends ChannelDuplexHandler {

    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    public static final int HTTP_CACHE_SECONDS = 60;

    private Http2FrameStream stream;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Http2HeadersFrame) {
            Http2HeadersFrame headersFrame = (Http2HeadersFrame) msg;
            this.stream = headersFrame.stream();

            if (!GET.toString().equals(headersFrame.headers().method().toString())) {
                sendError(ctx, METHOD_NOT_ALLOWED);
                return;
            }

            final String uri = headersFrame.headers().path().toString();
            final String path = sanitizeUri(uri);
            if (path == null) {
                sendError(ctx, FORBIDDEN);
                return;
            }

            File file = new File(path);
            if (file.isHidden() || !file.exists()) {
                sendError(ctx, NOT_FOUND);
                return;
            }

            if (file.isDirectory()) {
                if (uri.endsWith("/")) {
                    sendListing(ctx, file, uri);
                } else {
                    sendRedirect(ctx, uri + '/');
                }
                return;
            }

            if (!file.isFile()) {
                sendError(ctx, FORBIDDEN);
                return;
            }

            // Cache Validation
            CharSequence ifModifiedSince = headersFrame.headers().get(HttpHeaderNames.IF_MODIFIED_SINCE);
            if (ifModifiedSince != null && !ifModifiedSince.toString().isEmpty()) {
                SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
                Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince.toString());

                // Only compare up to the second because the datetime format we send to the client
                // does not have milliseconds
                long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
                long fileLastModifiedSeconds = file.lastModified() / 1000;
                if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                    sendNotModified(ctx);
                    return;
                }
            }

            RandomAccessFile raf;
            try {
                raf = new RandomAccessFile(file, "r");
            } catch (FileNotFoundException ignore) {
                sendError(ctx, NOT_FOUND);
                return;
            }
            long fileLength = raf.length();

            Http2Headers headers = new DefaultHttp2Headers();
            headers.status("200");
            headers.setLong(HttpHeaderNames.CONTENT_LENGTH, fileLength);

            setContentTypeHeader(headers, file);
            setDateAndCacheHeaders(headers, file);

            // Write the initial line and the header.
            ctx.writeAndFlush(new DefaultHttp2HeadersFrame(headers).stream(stream));

            // Write the content.
            ChannelFuture sendFileFuture;
            sendFileFuture = ctx.writeAndFlush(new Http2DataChunkedInput(
                    new ChunkedFile(raf, 0, fileLength, 8192), stream), ctx.newProgressivePromise());

            sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
                @Override
                public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
                    if (total < 0) { // total unknown
                        System.err.println(future.channel() + " Transfer progress: " + progress);
                    } else {
                        System.err.println(future.channel() + " Transfer progress: " + progress + " / " + total);
                    }
                }

                @Override
                public void operationComplete(ChannelProgressiveFuture future) {
                    System.err.println(future.channel() + " Transfer complete.");
                }
            });
        } else {
            // Unsupported message type
            System.out.println("Unsupported message type: " + msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (ctx.channel().isActive()) {
            sendError(ctx, INTERNAL_SERVER_ERROR);
        }
    }

    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");

    private static String sanitizeUri(String uri) throws UnsupportedEncodingException {
        // Decode the path.
        uri = URLDecoder.decode(uri, "UTF-8");

        if (uri.isEmpty() || uri.charAt(0) != '/') {
            return null;
        }

        // Convert file separators.
        uri = uri.replace('/', File.separatorChar);

        // Simplistic dumb security check.
        // You will have to do something serious in the production environment.
        if (uri.contains(File.separator + '.') ||
                uri.contains('.' + File.separator) ||
                uri.charAt(0) == '.' || uri.charAt(uri.length() - 1) == '.' ||
                INSECURE_URI.matcher(uri).matches()) {
            return null;
        }

        // Convert to absolute path.
        return SystemPropertyUtil.get("user.dir") + File.separator + uri;
    }

    private static final Pattern ALLOWED_FILE_NAME = Pattern.compile("[^-\\._]?[^<>&\\\"]*");

    private void sendListing(ChannelHandlerContext ctx, File dir, String dirPath) {
        StringBuilder buf = new StringBuilder()
                .append("<!DOCTYPE html>\r\n")
                .append("<html><head><meta charset='utf-8' /><title>")
                .append("Listing of: ")
                .append(dirPath)
                .append("</title></head><body>\r\n")

                .append("<h3>Listing of: ")
                .append(dirPath)
                .append("</h3>\r\n")

                .append("<ul>")
                .append("<li><a href=\"../\">..</a></li>\r\n");

        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isHidden() || !f.canRead()) {
                    continue;
                }

                String name = f.getName();
                if (!ALLOWED_FILE_NAME.matcher(name).matches()) {
                    continue;
                }

                buf.append("<li><a href=\"")
                        .append(name)
                        .append("\">")
                        .append(name)
                        .append("</a></li>\r\n");
            }
        }

        buf.append("</ul></body></html>\r\n");

        ByteBuf buffer = ctx.alloc().buffer(buf.length());
        buffer.writeCharSequence(buf.toString(), CharsetUtil.UTF_8);

        Http2Headers headers = new DefaultHttp2Headers();
        headers.status(OK.toString());
        headers.add(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");

        ctx.write(new DefaultHttp2HeadersFrame(headers).stream(stream));
        ctx.writeAndFlush(new DefaultHttp2DataFrame(buffer, true).stream(stream));
    }

    private void sendRedirect(ChannelHandlerContext ctx, String newUri) {
        Http2Headers headers = new DefaultHttp2Headers();
        headers.status(FOUND.toString());
        headers.add(HttpHeaderNames.LOCATION, newUri);

        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(headers, true).stream(stream));
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        Http2Headers headers = new DefaultHttp2Headers();
        headers.status(status.toString());
        headers.add(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(headers);
        headersFrame.stream(stream);

        Http2DataFrame dataFrame = new DefaultHttp2DataFrame(
                Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8), true);
        dataFrame.stream(stream);

        ctx.write(headersFrame);
        ctx.writeAndFlush(dataFrame);
    }

    /**
     * When file timestamp is the same as what the browser is sending up, send a "304 Not Modified"
     *
     * @param ctx Context
     */
    private void sendNotModified(ChannelHandlerContext ctx) {
        Http2Headers headers = new DefaultHttp2Headers();
        headers.status(NOT_MODIFIED.toString());
        setDateHeader(headers);

        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(headers, true).stream(stream));
    }

    /**
     * Sets the Date header for the HTTP response
     *
     * @param headers Http2 Headers
     */
    private static void setDateHeader(Http2Headers headers) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        Calendar time = new GregorianCalendar();
        headers.set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));
    }

    /**
     * Sets the Date and Cache headers for the HTTP Response
     *
     * @param headers     Http2 Headers
     * @param fileToCache file to extract content type
     */
    private static void setDateAndCacheHeaders(Http2Headers headers, File fileToCache) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        headers.set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        headers.set(HttpHeaderNames.EXPIRES, dateFormatter.format(time.getTime()));
        headers.set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        headers.set(HttpHeaderNames.LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
    }

    /**
     * Sets the content type header for the HTTP Response
     *
     * @param headers Http2 Headers
     * @param file    file to extract content type
     */
    private static void setContentTypeHeader(Http2Headers headers, File file) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        headers.set(HttpHeaderNames.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
    }
}
