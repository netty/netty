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
package io.netty.handler.codec.rest;

import java.io.File;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.Set;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.ServerCookieEncoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.DiskAttribute;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.CharsetUtil;

/**
 * Handler for HTTP Rest support
 */
public class RestHandler extends SimpleChannelInboundHandler<HttpObject> {
    protected static HttpDataFactory factory = new DefaultHttpDataFactory(
            DefaultHttpDataFactory.MINSIZE);
    /**
     * Initialize the HttpDataFactory support if needed by any RestMethodHandler (isBodyDecodable == True)
     * @param minSize minimum size for the HttpDataFactory (if used); -1 means no disk usage
     * @param tempPath system temp directory, could be null
     * @throws IOException 
     * @throws CryptoException 
     */
    public static void initialize(long minSize, String tempPath) {
        if (factory != null) {
            factory.cleanAllHttpData();
        }
        if (minSize < 0) {
            factory = new DefaultHttpDataFactory(false);
        } else {
            factory = new DefaultHttpDataFactory(minSize);
            // should delete file on exit (in normal exit)
            DiskFileUpload.deleteOnExitTemporaryFile = true;
            // should delete file on exit (in normal exit)
            DiskAttribute.deleteOnExitTemporaryFile = true;
            if (tempPath != null) {
                File file = new File(tempPath);
                file.mkdirs();
                DiskFileUpload.baseDirectory = tempPath;
                DiskAttribute.baseDirectory = tempPath;
            }
        }
    }

    final RestConfiguration restConfiguration;
    final RootOptionsRestMethodHandler rootHandler;

    /**
     * Might be unused
     */
    HttpPostRequestDecoder decoder;

    /**
     * Current request
     */
    HttpRequest request;

    /**
     * Current handler
     */
    RestMethodHandler handler;

    /**
     * Control to close or not the connection once answered
     */
    volatile boolean closeOnceAnswered;

    /**
     * Arguments received and to be sent (responseBody())
     */
    RestArgument arguments = null;

    /**
     * Cumulative chunks
     */
    ByteBuf cumulativeBody = null;

    public RestHandler(RestConfiguration config) {
        super(HttpObject.class);
        this.restConfiguration = config;
        rootHandler = new RootOptionsRestMethodHandler(config);
    }

    /**
     * Clean method.
     *
     * Override if needed
     */
    protected void clean() {
        if (arguments != null) {
            arguments.clean();
            arguments = null;
        }
        if (decoder != null) {
            decoder.cleanFiles();
            decoder = null;
        }
        handler = null;
        cumulativeBody = null;
    }

    /**
     * Called at the beginning of every new request.
     *
     * Override if needed
     */
    protected void initialize(HttpRequest request) {
        // clean previous FileUpload if Any
        clean();
        this.request = request;
        setCloseOnceAnswered(false);
        arguments = new RestArgument(request);
    }

    /**
     * To be used for instance to check correctness of connection (user/password for instance),
     * before the BODY is checked. Default doing nothing.
     * @param channelHandlerContext
     * @throws RestInvalidAuthenticationException
     */
    protected void checkConnection(ChannelHandlerContext channelHandlerContext) throws RestInvalidAuthenticationException {
        // NOOP
    }

    /**
     * Method to set Cookies in httpResponse as in request.
     *
     * Override if needed
     * @param httpResponse
     */
    protected void setCookies(HttpResponse httpResponse) {
        if (request == null) {
            return;
        }
        String cookieString = request.headers().get(HttpHeaders.Names.SET_COOKIE);
        if (cookieString != null) {
            Set<Cookie> cookies = CookieDecoder.decode(cookieString);
            httpResponse.headers().add(HttpHeaders.Names.SET_COOKIE, ServerCookieEncoder.encode(cookies));
        }
    }

    /**
     * Override if needed
     * @return RestMethodHandler associated with the current context
     * @throws RestMethodNotAllowedException
     * @throws RestForbiddenRequestException
     */
    protected RestMethodHandler getAssociatedHandler() throws RestMethodNotAllowedException, RestForbiddenRequestException {
        HttpMethod method = arguments.method();
        String uri = arguments.basePath();
        boolean restFound = false;
        RestMethodHandler handler = restConfiguration.getRestMethodHandler(uri);
        if (handler != null) {
            handler.checkHandlerSessionCorrectness(this, arguments);
            if (handler.isMethodIncluded(method)) {
                restFound = true;
            }
        }
        if (handler == null && method == HttpMethod.OPTIONS) {
            handler = rootHandler;
            // use Options default handler
            restFound = true;
        }
        if (! restFound){
            throw new RestMethodNotAllowedException("No valid method found for that URI: " + uri + " and " + method);
        }
        return handler;
    }

    /**
     * Check the signature or the time limit: using the configuration.
     * Override if needed
     * @throws RestInvalidAuthenticationException
     */
    protected void checkSignature() throws RestInvalidAuthenticationException {
        if (restConfiguration.restSignature()) {
            arguments.checkBaseAuthent(restConfiguration);
        } else if (restConfiguration.restTimeLimit() > 0) {
            arguments.checkTime(restConfiguration);
        }
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        try {
            if (msg instanceof HttpRequest) {
                request = (HttpRequest) msg;
                initialize(request);
                checkConnection(ctx);
                handler = getAssociatedHandler();
                checkSignature();
                if (arguments.method() == HttpMethod.OPTIONS) {
                    handler.optionsCommand(this);
                    finalizeSend(ctx);
                    return;
                }
                if (request instanceof FullHttpRequest) {
                    if (! handler.isBodyDecodable()) {
                        ByteBuf buffer = ((FullHttpRequest) request).content();
                        arguments.setBody(buffer);
                    } else {
                        // decoder for 1 chunk
                        createDecoder();
                        // Not chunk version
                        bodyChunk(ctx, null);
                    }
                    handler.endParsingRequest(this, arguments);
                    finalizeSend(ctx);
                    return;
                }
                // no body yet
                if (handler.isBodyDecodable()) {
                    createDecoder();
                }
                return;
            } else {
                // New chunk is received
                if (handler != null) {
                    bodyChunk(ctx, (HttpContent) msg);
                }
            }
        } catch (RestIncorrectRequestException e1) {
            exceptionFinalize(ctx, e1);
        } catch (RestMethodNotAllowedException e1) {
            exceptionFinalize(ctx, e1);
        } catch (RestForbiddenRequestException e1) {
            exceptionFinalize(ctx, e1);
        } catch (RestInvalidAuthenticationException e1) {
            exceptionFinalize(ctx, e1);
        } catch (RestNotFoundArgumentException e1) {
            exceptionFinalize(ctx, e1);
        }
    }

    private void exceptionFinalize(ChannelHandlerContext ctx, Exception e1) {
        HttpResponseStatus status = HttpResponseStatus.BAD_REQUEST;
        if (handler != null) {
            status = handler.handleException(this, arguments, e1);
        }
        if (status == HttpResponseStatus.OK) {
            if (e1 instanceof RestMethodNotAllowedException) {
                status = HttpResponseStatus.METHOD_NOT_ALLOWED;
            } else if (e1 instanceof RestForbiddenRequestException) {
                status = HttpResponseStatus.FORBIDDEN;
            } else if (e1 instanceof RestInvalidAuthenticationException) {
                status = HttpResponseStatus.UNAUTHORIZED;
            } else if (e1 instanceof RestNotFoundArgumentException) {
                status = HttpResponseStatus.NOT_FOUND;
            } else {
                status = HttpResponseStatus.BAD_REQUEST;
            }
        }
        arguments.setStatus(status);
        if (arguments.responseBody == null) {
            arguments.setResponseBody(e1.getMessage());
        }
        if (handler != null) {
            finalizeSend(ctx);
        } else {
            forceClosing(ctx);
        }
    }

    /**
     * Create the decoder (default being {@link HttpPostRequestDecoder}).
     * Override if needed
     * @throws RestIncorrectRequestException
     */
    protected void createDecoder() throws RestIncorrectRequestException {
        HttpMethod method = request.method();
        if (!method.equals(HttpMethod.HEAD)) {
            // in order decoder allows to parse as needed
            request.setMethod(HttpMethod.POST);
        }
        try {
            decoder = new HttpPostRequestDecoder(factory, request);
        } catch (ErrorDataDecoderException e1) {
            arguments.setStatus(HttpResponseStatus.NOT_ACCEPTABLE);
            throw new RestIncorrectRequestException(e1);
        } catch (Exception e1) {
            arguments.setStatus(HttpResponseStatus.NOT_ACCEPTABLE);
            throw new RestIncorrectRequestException(e1);
        }
    }

    /**
     * Read one Data at a time using the decoder.
     * Override if needed
     * @param data the data to set through the handler
     * @throws RestIncorrectRequestException
     */
    protected void setHttpData(InterfaceHttpData data)
            throws RestIncorrectRequestException {
        if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
            handler.setAttribute(this, (Attribute) data, arguments);
        } else if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
            FileUpload fileUpload = (FileUpload) data;
            if (fileUpload.isCompleted()) {
                handler.setFileUpload(this, fileUpload, arguments);
            } else {
                fileUpload.delete();
                arguments.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
                throw new RestIncorrectRequestException("File still pending but should not");
            }
        }
    }

    /**
     * Method that get a chunk of data. If chunk is null, will not add the chunk but goes directly
     * to getting all data from the decoder, passing them to readHttpData.
     * Override if needed
     * @param chunk might be null (equivalent to {@link LastHttpContent} but to handle {@link FullHttpRequest})
     * @throws RestIncorrectRequestException
     * @throws RestInvalidAuthenticationException
     * @throws RestNotFoundArgumentException
     */
    protected void bodyChunk(ChannelHandlerContext ctx, HttpContent chunk)
            throws RestIncorrectRequestException, RestInvalidAuthenticationException,
            RestNotFoundArgumentException {
        // New chunk is received: only for Post!
        if (! handler.isBodyDecodable()) {
            ByteBuf buffer = chunk.content();
            if (cumulativeBody != null) {
                if (buffer.isReadable()) {
                    cumulativeBody = Unpooled.wrappedBuffer(cumulativeBody, buffer.retain());
                }
            } else {
                cumulativeBody = buffer.retain();
            }
        } else {
            if (chunk != null) {
                try {
                    decoder.offer(chunk);
                } catch (ErrorDataDecoderException e1) {
                    arguments.setStatus(HttpResponseStatus.NOT_ACCEPTABLE);
                    throw new RestIncorrectRequestException(e1);
                }
            }
            try {
                while (decoder.hasNext()) {
                    InterfaceHttpData data = decoder.next();
                    if (data != null) {
                        // new value
                        setHttpData(data);
                    }
                }
            } catch (EndOfDataDecoderException e1) {
                // end
            }
        }
        if (chunk == null || chunk instanceof LastHttpContent) {
            if (! handler.isBodyDecodable()) {
                arguments.setBody(cumulativeBody);
                handler.setBody(this, cumulativeBody, arguments);
                cumulativeBody = null;
            }
            handler.endParsingRequest(this, arguments);
            finalizeSend(ctx);
        }
    }

    /**
     * To allow quick answer even if in very bad shape
     */
    protected void forceClosing(ChannelHandlerContext ctx) {
        if (arguments.status() == HttpResponseStatus.OK) {
            arguments.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
        if (ctx.channel().isActive()) {
            setCloseOnceAnswered(true);
            String answer = "<html><body>Error " + arguments.status().reasonPhrase() + "</body></html>";
            FullHttpResponse response = getResponse(Unpooled.wrappedBuffer(answer
                    .getBytes(CharsetUtil.UTF_8)));
            response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html");
            response.headers().set(HttpHeaders.Names.REFERER, request.uri());
            ChannelFuture future = ctx.writeAndFlush(response);
            future.addListener(ChannelFutureListener.CLOSE);
        }
        clean();
    }

    /**
     * @param content the future Body content of the response
     * @return the Http Response according to the status and the content if not null (setting the
     *         CONTENT_LENGTH)
     */
    protected FullHttpResponse getResponse(ByteBuf content) {
        // Decide whether to close the connection or not.
        if (request == null) {
            FullHttpResponse response;
            if (content == null) {
                response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_0, arguments.status());
            } else {
                response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_0, arguments.status(), content);
                response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, content.readableBytes());
            }
            setCookies(response);
            setCloseOnceAnswered(true);
            return response;
        }
        boolean keepAlive = HttpHeaderUtil.isKeepAlive(request);
        setCloseOnceAnswered(closeOnceAnswered() ||
                arguments.status() != HttpResponseStatus.OK ||
                HttpHeaders.Values.CLOSE.equalsIgnoreCase(request
                        .headers().get(HttpHeaders.Names.CONNECTION)) ||
                request.protocolVersion().equals(HttpVersion.HTTP_1_0) &&
                !keepAlive);
        if (closeOnceAnswered()) {
            keepAlive = false;
        }
        // Build the response object.
        FullHttpResponse response;
        if (content != null) {
            response = new DefaultFullHttpResponse(request.protocolVersion(), arguments.status(), content);
            response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, content.readableBytes());
        } else {
            response = new DefaultFullHttpResponse(request.protocolVersion(), arguments.status());
        }
        if (keepAlive) {
            response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }
        setCookies(response);
        return response;
    }

    /**
     * Standard send operation using arguments information.
     */
    protected void finalizeSend(ChannelHandlerContext ctx) {
        ChannelFuture future = null;
        if (arguments.method() == HttpMethod.OPTIONS) {
            future = handler.sendOptionsResponse(this, ctx, arguments);
        } else {
            future = handler.sendResponse(this, ctx, arguments);
        }
        if (future != null) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
        clean();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        cause.printStackTrace();
        if (ctx.channel().isActive()) {
            Throwable thro = cause;
            if (thro instanceof ClosedChannelException || thro instanceof IOException) {
                return;
            }
            if (handler != null) {
                arguments.setStatus(handler.handleException(this, arguments,
                        (Exception) cause));
            }
            if (arguments.status() == HttpResponseStatus.OK) {
                arguments.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
            if (handler != null) {
                finalizeSend(ctx);
            } else {
                forceClosing(ctx);
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        clean();
    }

    /**
     * @return the status of the current connection: shall it be closed once answered?
     */
    public boolean closeOnceAnswered() {
        return closeOnceAnswered;
    }

    /**
     * @param closeOnceAnswered
     *            the future status of the connection once answered
     */
    public void setCloseOnceAnswered(boolean closeOnceAnswered) {
        this.closeOnceAnswered = closeOnceAnswered;
    }

    /**
     * @return the current handler associated with the request
     */
    protected RestMethodHandler getHandler() {
        return handler;
    }

    /**
     * @param handler the handler to set and use with the current request,
     * to manually override the handler to use.
     */
    protected void setHandler(RestMethodHandler handler) {
        this.handler = handler;
    }

    /**
     * @return the restConfiguration
     */
    public RestConfiguration getRestConfiguration() {
        return restConfiguration;
    }

    /**
     * @return the rootHandler
     */
    public RootOptionsRestMethodHandler getRootHandler() {
        return rootHandler;
    }

    /**
     * @return the current request
     */
    protected HttpRequest getRequest() {
        return request;
    }

    /**
     * @return the current arguments
     */
    protected RestArgument getArguments() {
        return arguments;
    }

    /**
     * @return the cumulativeBody if any
     */
    protected ByteBuf getCumulativeBody() {
        return cumulativeBody;
    }
}
