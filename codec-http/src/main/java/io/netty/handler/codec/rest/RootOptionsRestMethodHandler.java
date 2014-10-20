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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;

/**
 * RestMethod handler to implement default Root Options handler
 */
public class RootOptionsRestMethodHandler extends RestMethodHandler {

    public RootOptionsRestMethodHandler(RestConfiguration config) {
        super("/", true, config, HttpMethod.OPTIONS);
    }

    @Override
    public void checkHandlerSessionCorrectness(RestHandler handler, RestArgument arguments)
            throws RestForbiddenRequestException {
    }

    @Override
    public void setFileUpload(RestHandler handler, FileUpload data, RestArgument arguments)
            throws RestIncorrectRequestException {
    }

    @Override
    public void setAttribute(RestHandler handler, Attribute data, RestArgument arguments)
            throws RestIncorrectRequestException {
    }

    @Override
    public void setBody(RestHandler handler, ByteBuf body, RestArgument arguments)
            throws RestIncorrectRequestException {
    }

    @Override
    public void endParsingRequest(RestHandler handler, RestArgument arguments)
            throws RestIncorrectRequestException, RestInvalidAuthenticationException,
            RestNotFoundArgumentException {
    }

    @Override
    public ChannelFuture sendResponse(RestHandler handler,
            ChannelHandlerContext channelHandlerContext, RestArgument arguments) {
        return sendOptionsResponse(handler, channelHandlerContext, arguments);
    }

    @Override
    protected String getDetailedAllow(RestHandler handler) {
        StringBuilder builder = new StringBuilder();
        StringBuilder uri = new StringBuilder();
        for (RestMethodHandler restMethodHandler : handler.restConfiguration.restMethodHandler().values()) {
            if (uri.length() > 0) {
                uri.append("', '");
            } else {
                uri.append("'");
            }
            uri.append(restMethodHandler.path());
            if (builder.length() > 0) {
                builder.append(",\n\n");
            }
            builder.append(restMethodHandler.optionsCommand(handler));
        }
        uri.insert(0, "{ 'Uri': [");
        uri.append("'],\n\n 'Options': [").append(builder).append("]}");
        return uri.toString();
    }

}
