/*
 * Copyright 2012 The Netty Project
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
package io.netty.example.traffic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.traffic.AbstractTrafficShapingHandler.TrafficShapingEvent;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.internal.SystemPropertyUtil;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
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

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

/**
 * Based on HttpStaticFileServer example, which serves incoming HTTP requests 
 * to send their respective HTTP responses, it shows several ways to implement
 * Traffic shaping using the TrafficShapingHandler.
 *
 */
public class HttpStaticFileServerTrafficShapingHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    public static final int HTTP_CACHE_SECONDS = 60;

    protected volatile RandomAccessFile raf;
    protected volatile FullHttpRequest request;
    protected volatile long fileLength;

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        this.request = request;
        if (!request.getDecoderResult().isSuccess()) {
            sendError(ctx, BAD_REQUEST);
            return;
        }

        if (request.getMethod() != GET) {
            sendError(ctx, METHOD_NOT_ALLOWED);
            return;
        }

        final String uri = request.getUri();
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
                sendListing(ctx, file);
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
        String ifModifiedSince = request.headers().get(IF_MODIFIED_SINCE);
        if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
            Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);

            // Only compare up to the second because the datetime format we send to the client
            // does not have milliseconds
            long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
            long fileLastModifiedSeconds = file.lastModified() / 1000;
            if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                sendNotModified(ctx);
                return;
            }
        }

        try {
            raf = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException ignore) {
            sendError(ctx, NOT_FOUND);
            return;
        }
        fileLength = raf.length();

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        HttpHeaders.setContentLength(response, fileLength);
        setContentTypeHeader(response, file);
        setDateAndCacheHeaders(response, file);
        if (HttpHeaders.isKeepAlive(request)) {
            response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }

        // Write the initial line and the header.
        ctx.write(response);

        // Write the content.
        doSend(ctx);
    }

    private synchronized void doSend(final ChannelHandlerContext ctx) throws Exception {
        switch (HttpStaticFileServerTrafficShaping.modeTransfer) {
            case useCheckWriteSuspended:
                doSendCheckWriteSuspended(ctx);
                break;
            case useCheckWritability:
            	doSendCheckWritabilityChanged(ctx);
            	break;
            case useChunkedFile:
                doSendChunkedFile(ctx);
                break;
            case useDefault:
                doSendSimple(ctx);
                break;
            case useFutureListener:
                doSendFutureListener(ctx);
                break;
            case useSetFutureListener:
                doSendFutureListenerSet(ctx);
                break;
            default:
                doSendSimple(ctx);
                break;
        }
    }

    volatile ChannelFuture nextFuture = null;
    /**
     * Using explicit future listener on send to send one element while the next one is prepared.
     * 
     * The advantage of this method is to only have one element in the buffer of the TrafficShapingHandler.
     * 
     * @param ctx
     * @throws Exception
     */
    private synchronized void doSendFutureListener(final ChannelHandlerContext ctx) throws Exception {
        byte[] bytes = new byte[8192];
        int read = raf.read(bytes);
        ChannelFuture future = nextFuture;
        if (read > 0) {
            final ByteBuf buf = ctx.alloc().buffer(read, read);
            buf.writeBytes(bytes, 0, read);
            if (future != null) {
                future.addListener(new GenericFutureListener<Future<? super Void>>() {
                    public void operationComplete(Future<? super Void> future) throws Exception {
                        nextFuture = ctx.writeAndFlush(buf);
                        if (HttpStaticFileServerTrafficShaping.simulLongTask) {
                            Thread.sleep(5);
                        }
                        doSendFutureListener(ctx);
                    }
                });
                return;
            }
            nextFuture = ctx.writeAndFlush(buf);
            doSendFutureListener(ctx);
        } else {
            raf.close();
            raf = null;
            finalizeSend(ctx);
        }
    }
    /**
     * Using explicit future listener on send to send one element while the next one is prepared.
     * 
     * The advantage of this method is to only have one fix set of blocks in the buffer of the TrafficShapingHandler.
     * 
     * @param ctx
     * @throws Exception
     */
    private synchronized void doSendFutureListenerSet(final ChannelHandlerContext ctx) throws Exception {
        byte[] bytes = new byte[8192];
        int read = raf.read(bytes);
        ChannelFuture future = null;
        for (int i = 0; i < 9 && read > 0; i++) {
            final ByteBuf buf = ctx.alloc().buffer(read, read);
            buf.writeBytes(bytes, 0, read);
            ctx.write(buf);
        }
        if (read > 0) {
            final ByteBuf buf = ctx.alloc().buffer(read, read);
            buf.writeBytes(bytes, 0, read);
            future = ctx.writeAndFlush(buf);
            future.addListener(new GenericFutureListener<Future<? super Void>>() {
                public void operationComplete(Future<? super Void> future) throws Exception {
                    if (HttpStaticFileServerTrafficShaping.simulLongTask) {
                        Thread.sleep(10);
                    }
                    doSendFutureListenerSet(ctx);
                }
            });
            return;
        }
        if (read < 0) {
            raf.close();
            raf = null;
            finalizeSend(ctx);
        }
    }
    /**
     * Using Channel.isWritable to check if the buffer is full or not.
     * Use then the channelWritabilityChanged to restart the sending operation.
     * 
     * @param ctx
     * @throws Exception
     */
    private synchronized void doSendCheckWritabilityChanged(final ChannelHandlerContext ctx) throws Exception {
        byte[] bytes = new byte[8192];
        int read = raf.read(bytes);
        while (read > 0) {
            final ByteBuf buf = ctx.alloc().buffer(read, read);
            buf.writeBytes(bytes, 0, read);
            ctx.write(buf);
            if (! ctx.channel().isWritable()) {
                ctx.flush();
                return;
            }
            if (HttpStaticFileServerTrafficShaping.simulLongTask) {
                Thread.sleep(5);
            }
            read = raf.read(bytes);
        }
        raf.close();
        raf = null;
        finalizeSend(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (HttpStaticFileServerTrafficShaping.modeTransfer != HttpStaticFileServerTrafficShaping.ModeTransfer.useCheckWritability
                || raf == null || ! ctx.channel().isWritable()) {
            ctx.fireChannelWritabilityChanged();
            return;
        }
        doSendCheckWritabilityChanged(ctx);
        ctx.fireChannelWritabilityChanged();
	}

	/**
     * Using ChannelTrafficShapingHandler.checkWriteSuspended
     * or GlobalTrafficShapingHandler.checkWriteSuspended (no impact on the choice) to
     * check if the buffer is full or not. Use then the userEventTrigger to restart
     * the sending operation.
     * 
     * @param ctx
     * @throws Exception
     */
    private synchronized void doSendCheckWriteSuspended(final ChannelHandlerContext ctx) throws Exception {
        byte[] bytes = new byte[8192];
        int read = raf.read(bytes);
        while (read > 0) {
            final ByteBuf buf = ctx.alloc().buffer(read, read);
            buf.writeBytes(bytes, 0, read);
            ctx.write(buf);
            if (ChannelTrafficShapingHandler.checkWriteSuspended(ctx)) {
                ctx.flush();
                return;
            }
            if (HttpStaticFileServerTrafficShaping.simulLongTask) {
                Thread.sleep(5);
            }
            read = raf.read(bytes);
        }
        raf.close();
        raf = null;
        finalizeSend(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == TrafficShapingEvent.WRITE_ENABLED) {
            if (HttpStaticFileServerTrafficShaping.modeTransfer != HttpStaticFileServerTrafficShaping.ModeTransfer.useCheckWriteSuspended
                    || raf == null || ChannelTrafficShapingHandler.checkWriteSuspended(ctx)) {
                return;
            }
            doSendCheckWriteSuspended(ctx);
        }
        super.userEventTriggered(ctx, evt);
    }

    /**
     * Simple send, using ChunkedFile
     * 
     * @param ctx
     * @throws Exception
     */
    private synchronized void doSendChunkedFile(final ChannelHandlerContext ctx) throws Exception {
        // note: this is not compatible with traffic shaping, as for SSL
        // ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength), ctx.newProgressivePromise());
        ChannelFuture sendFileFuture;
            sendFileFuture =
                    ctx.write(new HttpChunkedInput(new ChunkedFile(raf, 0, fileLength, 8192)),
                            ctx.newProgressivePromise());

        sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
            @Override
            public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
            }

            @Override
            public void operationComplete(ChannelProgressiveFuture future) {
                System.err.println(future.channel() + " Transfer complete.");
            }
        });
        raf = null;
        finalizeSend(ctx);
    }

    /**
     * Simple send, chunk by chunk by hand
     * 
     * @param ctx
     * @throws Exception
     */
    private synchronized void doSendSimple(final ChannelHandlerContext ctx) throws Exception {
        byte[] bytes = new byte[8192];
        int read = raf.read(bytes);
        while (read > 0) {
            final ByteBuf buf = ctx.alloc().buffer(read, read);
            buf.writeBytes(bytes, 0, read);
            ctx.write(buf);
            if (HttpStaticFileServerTrafficShaping.simulLongTask) {
                Thread.sleep(5);
            }
            read = raf.read(bytes);
        }
        raf.close();
        raf = null;
        finalizeSend(ctx);
    }

    private void finalizeSend(ChannelHandlerContext ctx) throws IOException {
        // Write the end marker
        ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        // Decide whether to close the connection or not.
        if (!HttpHeaders.isKeepAlive(request)) {
            // Close the connection when the whole content is written out.
            lastContentFuture.addListener(ChannelFutureListener.CLOSE);
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

    private static String sanitizeUri(String uri) {
        // Decode the path.
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }

        if (!uri.startsWith("/")) {
            return null;
        }

        // Convert file separators.
        uri = uri.replace('/', File.separatorChar);

        // Simplistic dumb security check.
        // You will have to do something serious in the production environment.
        if (uri.contains(File.separator + '.') ||
            uri.contains('.' + File.separator) ||
            uri.startsWith(".") || uri.endsWith(".") ||
            INSECURE_URI.matcher(uri).matches()) {
            return null;
        }

        // Convert to absolute path.
        return SystemPropertyUtil.get("user.dir") + File.separator + uri;
    }

    private static final Pattern ALLOWED_FILE_NAME = Pattern.compile("[A-Za-z0-9][-_A-Za-z0-9\\.]*");

    private static void sendListing(ChannelHandlerContext ctx, File dir) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK);
        response.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");

        StringBuilder buf = new StringBuilder();
        String dirPath = dir.getPath();

        buf.append("<!DOCTYPE html>\r\n");
        buf.append("<html><head><title>");
        buf.append("Listing of: ");
        buf.append(dirPath);
        buf.append("</title></head><body>\r\n");

        buf.append("<h3>Listing of: ");
        buf.append(dirPath);
        buf.append("</h3>\r\n");

        buf.append("<ul>");
        buf.append("<li><a href=\"../\">..</a></li>\r\n");

        for (File f: dir.listFiles()) {
            if (f.isHidden() || !f.canRead()) {
                continue;
            }

            String name = f.getName();
            if (!ALLOWED_FILE_NAME.matcher(name).matches()) {
                continue;
            }

            buf.append("<li><a href=\"");
            buf.append(name);
            buf.append("\">");
            buf.append(name);
            buf.append("</a></li>\r\n");
        }

        buf.append("</ul></body></html>\r\n");
        ByteBuf buffer = Unpooled.copiedBuffer(buf, CharsetUtil.UTF_8);
        response.content().writeBytes(buffer);
        buffer.release();

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendRedirect(ChannelHandlerContext ctx, String newUri) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, FOUND);
        response.headers().set(LOCATION, newUri);

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * When file timestamp is the same as what the browser is sending up, send a "304 Not Modified"
     *
     * @param ctx
     *            Context
     */
    private static void sendNotModified(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED);
        setDateHeader(response);

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Sets the Date header for the HTTP response
     *
     * @param response
     *            HTTP response
     */
    private static void setDateHeader(FullHttpResponse response) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        Calendar time = new GregorianCalendar();
        response.headers().set(DATE, dateFormatter.format(time.getTime()));
    }

    /**
     * Sets the Date and Cache headers for the HTTP Response
     *
     * @param response
     *            HTTP response
     * @param fileToCache
     *            file to extract content type
     */
    private static void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.headers().set(DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        response.headers().set(EXPIRES, dateFormatter.format(time.getTime()));
        response.headers().set(CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        response.headers().set(
                LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
    }

    /**
     * Sets the content type header for the HTTP Response
     *
     * @param response
     *            HTTP response
     * @param file
     *            file to extract content type
     */
    private static void setContentTypeHeader(HttpResponse response, File file) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        response.headers().set(CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
    }
}
