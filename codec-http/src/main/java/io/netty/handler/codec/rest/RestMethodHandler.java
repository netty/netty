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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.util.CharsetUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * Rest Method handler (used by Rest Handler)
 *
 */
public abstract class RestMethodHandler {
    final String path;
    final Set<HttpMethod> methods;
    final boolean isBodyDecodable;
    final RestConfiguration restConfiguration;

    /**
     * @param path associated base Path,
     *          name associated with this Method Handler (to enable some HashMap or Enum classification)
     * @param isBodyDecodable Is this method Handler using a standard decodable Body using HttpPostRequestDecoder
     * @param config the associated configuration
     * @param method the associated methods
     */
    public RestMethodHandler(String path, boolean isBodyDecodable, RestConfiguration config, HttpMethod ...method) {
        this.path = path;
        this.methods = new HashSet<HttpMethod>();
        setMethods(method);
        setMethods(HttpMethod.OPTIONS);
        this.isBodyDecodable = isBodyDecodable;
        this.restConfiguration = config;
    }

    /**
     * To set (and replace) all methods that are valid for this handler
     */
    public void setMethods(HttpMethod ...method) {
        for (HttpMethod method2 : method) {
            methods.add(method2);
        }
    }

    /**
     * Will assign the intersection of both set of Methods
     * @param selectedMethods the selected Methods among available
     * @param validMethod the validMethod for this handler
     */
    public void setIntersectionMethods(HttpMethod []selectedMethods, HttpMethod ...validMethod) {
        Set<HttpMethod> set = new HashSet<HttpMethod>();
        for (HttpMethod method : validMethod) {
            set.add(method);
        }
        Set<HttpMethod> set2 = new HashSet<HttpMethod>();
        for (HttpMethod method : selectedMethods) {
            set2.add(method);
        }
        set.retainAll(set2);
        HttpMethod [] methodsToSet = set.toArray(new HttpMethod[0]);
        setMethods(methodsToSet);
    }

    public String path() {
        return path;
    }

    /**
     * @return True if the Method is valid for this Handler
     */
    public boolean isMethodIncluded(HttpMethod method) {
        return methods.contains(method);
    }

    /**
     * Check the session (arguments) vs handler correctness, called before any addition of BODY elements.
     *
     * @throws RestForbiddenRequestException
     */
    public abstract void checkHandlerSessionCorrectness(RestHandler handler, RestArgument arguments)
            throws RestForbiddenRequestException;

    /**
     * Handle a new Http Uploaded Attribute from BODY (only call if isBodyDecodable is True)
     * @throws RestIncorrectRequestException
     */
    public abstract void setAttribute(RestHandler handler, Attribute data, RestArgument arguments)
            throws RestIncorrectRequestException;

    /**
     * Handle a new Http Uploaded File from BODY (only call if isBodyDecodable is True)
     * @throws RestIncorrectRequestException
     */
    public abstract void setFileUpload(RestHandler handler, FileUpload data, RestArgument arguments)
            throws RestIncorrectRequestException;

    /**
     * Handle data from BODY (supposedly a Json or whatever) (only call if isBodyDecodable is False)
     * @throws RestIncorrectRequestException
     */
    public abstract void setBody(RestHandler handler, ByteBuf body, RestArgument arguments)
            throws RestIncorrectRequestException;

    /**
     * Called when all Data were passed to the handler (after all setAttribute, setFileUpload or setBody)
     * @param handler
     * @param arguments
     * @throws RestIncorrectRequestException
     * @throws RestInvalidAuthenticationException
     * @throws RestNotFoundArgumentException
     */
    public abstract void endParsingRequest(RestHandler handler, RestArgument arguments)
            throws RestIncorrectRequestException, RestInvalidAuthenticationException, RestNotFoundArgumentException;

    /**
     * Called when an exception occurs
     * @param handler
     * @param arguments
     * @param exception
     * @return the status to used in sendReponse
     */
    public HttpResponseStatus handleException(RestHandler handler, RestArgument arguments, Exception exception) {
        if (arguments.status() != HttpResponseStatus.OK) {
            return arguments.status();
        }
        if (exception instanceof RestInvalidAuthenticationException) {
            arguments.setStatus(HttpResponseStatus.UNAUTHORIZED);
            return HttpResponseStatus.UNAUTHORIZED;
        } else if (exception instanceof RestForbiddenRequestException) {
            arguments.setStatus(HttpResponseStatus.FORBIDDEN);
            return HttpResponseStatus.FORBIDDEN;
        } else if (exception instanceof RestIncorrectRequestException) {
            arguments.setStatus(HttpResponseStatus.BAD_REQUEST);
            return HttpResponseStatus.BAD_REQUEST;
        } else if (exception instanceof RestMethodNotAllowedException) {
            arguments.setStatus(HttpResponseStatus.METHOD_NOT_ALLOWED);
            return HttpResponseStatus.METHOD_NOT_ALLOWED;
        } else if (exception instanceof RestNotFoundArgumentException) {
            arguments.setStatus(HttpResponseStatus.NOT_FOUND);
            return HttpResponseStatus.NOT_FOUND;
        } else {
            arguments.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
            return HttpResponseStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * Send a response (correct or not). The current status is in arguments.status()
     * and the possible body in arguments.responseBody().
     * If a specific way of sending the data could be uses
     * (as for instance using chunked based method and isWritable()), one should override this method to fit
     * the needs.
     * @return The ChannelFuture if this response will need the channel to be closed, else null
     */
    public ChannelFuture sendResponse(RestHandler handler, ChannelHandlerContext channelHandlerContext,
            RestArgument arguments) {
        if (arguments.status() == HttpResponseStatus.UNAUTHORIZED) {
            FullHttpResponse response = handler.getResponse(null);
            ChannelFuture future = channelHandlerContext.writeAndFlush(response);
            return future;
        }
        String answer = arguments.responseBody().toString();
        ByteBuf buffer = Unpooled.wrappedBuffer(answer.getBytes(CharsetUtil.UTF_8));
        FullHttpResponse response = handler.getResponse(buffer);
        response.headers().add(HttpHeaders.Names.CONTENT_TYPE, arguments.contentType());
        response.headers().add(HttpHeaders.Names.REFERER, arguments.request().uri());
        response.headers().add(HttpHeaders.Names.ALLOW, validOptions());
        ChannelFuture future = channelHandlerContext.writeAndFlush(response);
        if (handler.closeOnceAnswered()) {
            return future;
        }
        return null;
    }

    /**
     * Build default body for Options method (could be overridden if needed)
     * @param handler
     * @param channelHandlerContext
     * @param arguments
     * @return the associated ChannelFuture if this response will need the channel to be closed, else null
     */
    public ChannelFuture sendOptionsResponse(RestHandler handler, ChannelHandlerContext channelHandlerContext,
            RestArgument arguments) {
        String answer = optionsCommand(handler);
        ByteBuf buffer = Unpooled.wrappedBuffer(answer.getBytes(CharsetUtil.UTF_8));
        FullHttpResponse response = handler.getResponse(buffer);
        if (arguments.status() == HttpResponseStatus.UNAUTHORIZED) {
            ChannelFuture future = channelHandlerContext.writeAndFlush(response);
            return future;
        }
        response.headers().add(HttpHeaders.Names.CONTENT_TYPE, "application/json");
        response.headers().add(HttpHeaders.Names.REFERER, arguments.request().uri());
        response.headers().add(HttpHeaders.Names.ALLOW, validOptions());
        ChannelFuture future = channelHandlerContext.writeAndFlush(response);
        if (handler.closeOnceAnswered()) {
            return future;
        }
        return null;
    }

    /**
     * @return the string representation of allowed HttpMethods for this handler (used in Header)
     */
    public String validOptions() {
        StringBuilder builder = new StringBuilder(HttpMethod.OPTIONS.name());
        for (HttpMethod methoditem : methods) {
            builder.append(", ");
            builder.append(methoditem.name());
        }
        return builder.toString();
    }

    /**
     * Options command that all handler should (re)implement, returning the future response Body
     * as a string containing the detail
     * @return the String representation of the future ResponseBody in JSON format
     */
    public String optionsCommand(RestHandler handler) {
        StringBuilder builder = new StringBuilder("{ 'Path': '").append(path);
        builder.append("',\n'Methods': ['").append(HttpMethod.OPTIONS.name());
        for (HttpMethod methoditem : methods) {
            if (! methoditem.equals(HttpMethod.OPTIONS)) {
                builder.append("', '");
                builder.append(methoditem.name());
            }
        }
        builder.append("']");
        builder.append(",\n'Allow': ").append(getDetailedAllow(handler));
        builder.append("}");
        return builder.toString();
    }

    /**
     * @return the detail of the method handler in JSON format as a replacement of xxx in {'Allow': xxx}
     */
    protected abstract String getDetailedAllow(RestHandler handler);

    /**
     * @return True if the Body is Decodable through standard HttpPostRequestDecoder
     */
    public boolean isBodyDecodable() {
        return isBodyDecodable;
    }

    /**
     * @return the allowed methods associated with this handler
     */
    public Set<HttpMethod> methods() {
        return methods;
    }

    /**
     * @return the restConfiguration
     */
    public RestConfiguration restConfiguration() {
        return restConfiguration;
    }
}
