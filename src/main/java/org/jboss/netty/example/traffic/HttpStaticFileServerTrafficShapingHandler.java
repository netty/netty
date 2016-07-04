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
package org.jboss.netty.example.traffic;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.SucceededChannelFuture;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.stream.ChunkedFile;
import org.jboss.netty.util.CharsetUtil;

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

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.*;
import static org.jboss.netty.handler.codec.http.HttpMethod.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.*;

/**
 * Based on HttpStaticFileServer example, which serves incoming HTTP requests
 * to send their respective HTTP responses, it shows several ways to implement
 * Traffic shaping using the TrafficShapingHandler.
 *
 */
public class HttpStaticFileServerTrafficShapingHandler extends SimpleChannelUpstreamHandler {

    static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    static final int HTTP_CACHE_SECONDS = 60;

    protected volatile RandomAccessFile raf;
    protected volatile HttpRequest request;
    protected volatile long fileLength;

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        request = (HttpRequest) e.getMessage();
        if (request.getMethod() != GET) {
            sendError(ctx, METHOD_NOT_ALLOWED);
            return;
        }

        final String path = sanitizeUri(request.getUri());
        if (path == null) {
            sendError(ctx, FORBIDDEN);
            return;
        }

        File file = new File(path);
        if (file.isHidden() || !file.exists()) {
            sendError(ctx, NOT_FOUND);
            return;
        }
        if (!file.isFile()) {
            sendError(ctx, FORBIDDEN);
            return;
        }

        // Cache Validation
        String ifModifiedSince = request.headers().get(IF_MODIFIED_SINCE);
        if (ifModifiedSince != null && ifModifiedSince.length() != 0) {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
            Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);

            // Only compare up to the second because the datetime format we send to the client does
            // not have milliseconds
            long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
            long fileLastModifiedSeconds = file.lastModified() / 1000;
            if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                sendNotModified(ctx);
                return;
            }
        }

        try {
            raf = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException fnfe) {
            sendError(ctx, NOT_FOUND);
            return;
        }
        fileLength = raf.length();

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        setContentLength(response, fileLength);
        setContentTypeHeader(response, file);
        setDateAndCacheHeaders(response, file);
        if (HttpHeaders.isKeepAlive(request)) {
            response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }

        Channel ch = e.getChannel();

        // Write the initial line and the header.
        ch.write(response);

        // Write the content.
        doSend(ctx);
    }

    private synchronized void doSend(final ChannelHandlerContext ctx) throws Exception {
        switch (HttpStaticFileServerTrafficShaping.modeTransfer) {
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

    volatile ChannelFuture nextFuture;
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
            final ChannelBuffer buf = ChannelBuffers.copiedBuffer(bytes, 0, read);
            if (future != null) {
                future.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        nextFuture = future.getChannel().write(buf);
                        if (HttpStaticFileServerTrafficShaping.simulLongTask) {
                            Thread.sleep(5);
                        }
                        doSendFutureListener(ctx);
                    }
                });
                return;
            }
            nextFuture = ctx.getChannel().write(buf);
            doSendFutureListener(ctx);
        } else {
            raf.close();
            raf = null;
            if (future == null) {
                future = new SucceededChannelFuture(ctx.getChannel());
            }
            finalizeSend(ctx, future);
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
            final ChannelBuffer buf = ChannelBuffers.copiedBuffer(bytes, 0, read);
            future = ctx.getChannel().write(buf);
            read = raf.read(bytes);
        }
        if (read > 0) {
            final ChannelBuffer buf = ChannelBuffers.copiedBuffer(bytes, 0, read);
            future = ctx.getChannel().write(buf);
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
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
            if (future == null) {
                future = new SucceededChannelFuture(ctx.getChannel());
            }
            finalizeSend(ctx, future);
        }
    }
    protected volatile boolean writable = true;
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
        ChannelFuture future = null;
        while (read > 0) {
            final ChannelBuffer buf = ChannelBuffers.copiedBuffer(bytes, 0, read);
            future = ctx.getChannel().write(buf);
            if (! ctx.getChannel().isWritable() || ! writable) {
                writable = false;
                return;
            }
            if (HttpStaticFileServerTrafficShaping.simulLongTask) {
                Thread.sleep(5);
            }
            read = raf.read(bytes);
        }
        raf.close();
        raf = null;
        if (future == null) {
            future = new SucceededChannelFuture(ctx.getChannel());
        }
        finalizeSend(ctx, future);
    }

    @Override
    public synchronized void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        if (HttpStaticFileServerTrafficShaping.modeTransfer ==
                HttpStaticFileServerTrafficShaping.ModeTransfer.useCheckWritability
                && raf != null) {
            if (e.getState() == ChannelState.INTEREST_OPS &&
                    (((Integer) e.getValue()).intValue() & Channel.OP_WRITE) != 0) {
                writable = false;
            } else if (! writable) {
                writable = true;
                doSendCheckWritabilityChanged(ctx);
            }
        }
        super.channelInterestChanged(ctx, e);
    }

    /**
     * Simple send, using ChunkedFile
     *
     * @param ctx
     * @throws Exception
     */
    private synchronized void doSendChunkedFile(final ChannelHandlerContext ctx) throws Exception {
        // note: this is not compatible with traffic shaping, as for SSL
        // ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength));
        ChannelFuture writeFuture = ctx.getChannel().write(new ChunkedFile(raf, 0, fileLength, 8192));
        raf = null;
        finalizeSend(ctx, writeFuture);
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
        ChannelFuture future = null;
        while (read > 0) {
            final ChannelBuffer buf = ChannelBuffers.copiedBuffer(bytes, 0, read);
            future = ctx.getChannel().write(buf);
            if (HttpStaticFileServerTrafficShaping.simulLongTask) {
                Thread.sleep(5);
            }
            read = raf.read(bytes);
        }
        raf.close();
        raf = null;
        if (future == null) {
            future = new SucceededChannelFuture(ctx.getChannel());
        }
        finalizeSend(ctx, future);
    }

    private void finalizeSend(ChannelHandlerContext ctx, ChannelFuture lastContentFuture) throws IOException {
        // Decide whether to close the connection or not.
        if (!HttpHeaders.isKeepAlive(request)) {
            // Close the connection when the whole content is written out.
            lastContentFuture.addListener(ChannelFutureListener.CLOSE);
        }
        lastContentFuture.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) throws Exception {
                System.err.println("Write finished");
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        Channel ch = e.getChannel();
        Throwable cause = e.getCause();
        if (cause instanceof TooLongFrameException) {
            sendError(ctx, BAD_REQUEST);
            return;
        }

        cause.printStackTrace();
        if (ch.isConnected()) {
            sendError(ctx, INTERNAL_SERVER_ERROR);
        }
    }

    private static String sanitizeUri(String uri) {
        // Decode the path.
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            try {
                uri = URLDecoder.decode(uri, "ISO-8859-1");
            } catch (UnsupportedEncodingException e1) {
                throw new Error();
            }
        }

        // Convert file separators.
        uri = uri.replace('/', File.separatorChar);

        // Simplistic dumb security check.
        // You will have to do something serious in the production environment.
        if (uri.contains(File.separator + '.') ||
            uri.contains('.' + File.separator) ||
            uri.startsWith(".") || uri.endsWith(".")) {
            return null;
        }

        // Convert to absolute path.
        return System.getProperty("user.dir") + File.separator + uri;
    }

    private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.setContent(ChannelBuffers.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));

        // Close the connection as soon as the error message is sent.
        ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * When file timestamp is the same as what the browser is sending up, send a "304 Not Modified"
     */
    private static void sendNotModified(ChannelHandlerContext ctx) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, NOT_MODIFIED);
        setDateHeader(response);

        // Close the connection as soon as the error message is sent.
        ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Sets the Date header for the HTTP response
     */
    private static void setDateHeader(HttpResponse response) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        Calendar time = new GregorianCalendar();
        response.headers().set(DATE, dateFormatter.format(time.getTime()));
    }

    /**
     * Sets the Date and Cache headers for the HTTP Response
     *
     * @param fileToCache the file to extract content type
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
        response.headers().set(LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
    }

    /**
     * Sets the content type header for the HTTP Response
     *
     * @param file the file to extract content type
     */
    private static void setContentTypeHeader(HttpResponse response, File file) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        response.headers().set(CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
    }
}
