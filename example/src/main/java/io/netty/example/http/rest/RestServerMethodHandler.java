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

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.rest.RestArgument;
import io.netty.handler.codec.rest.RestConfiguration;
import io.netty.handler.codec.rest.RestForbiddenRequestException;
import io.netty.handler.codec.rest.RestHandler;
import io.netty.handler.codec.rest.RestIncorrectRequestException;
import io.netty.handler.codec.rest.RestInvalidAuthenticationException;
import io.netty.handler.codec.rest.RestMethodHandler;
import io.netty.handler.codec.rest.RestNotFoundArgumentException;

/**
 * Example of Simple RestMethodHandler for Rest Server, where response is OK when correctly called
 */
public class RestServerMethodHandler extends RestMethodHandler {

    public RestServerMethodHandler(String path, boolean isBodyDecodable, RestConfiguration config,
            HttpMethod... method) {
        super(path, isBodyDecodable, config, method);
    }

    @Override
    public void checkHandlerSessionCorrectness(RestHandler handler, RestArgument arguments)
            throws RestForbiddenRequestException {
        // Could check arguments before BODY is taken into consideration
        // Could also be used to handle data from Cookies or Headers
    }

    @Override
    public void setAttribute(RestHandler handler, Attribute data, RestArgument arguments)
            throws RestIncorrectRequestException {
        // Add the attribute to the Argument
        try {
            arguments.addArg(data.getName(), data.getValue());
        } catch (IOException e) {
            throw new RestIncorrectRequestException(e);
        }
    }

    @Override
    public void setFileUpload(RestHandler handler, FileUpload data, RestArgument arguments)
            throws RestIncorrectRequestException {
        // Add the attribute to the Argument
        arguments.addArg(data.getName(), data.getFilename());
    }

    @Override
    public void setBody(RestHandler handler, ByteBuf body, RestArgument arguments)
            throws RestIncorrectRequestException {
        // Could do parsing of the body there (for instance JSON decoding)
    }

    @Override
    public void endParsingRequest(RestHandler handler, RestArgument arguments)
            throws RestIncorrectRequestException, RestInvalidAuthenticationException,
            RestNotFoundArgumentException {
        // Create the answer once all data were parsed
        StringBuilder answer = new StringBuilder();
        answer.append("Method=").append(arguments.method()).append("\n");
        answer.append("BasePath=").append(arguments.basePath()).append("\n");
        answer.append("SubPathes=").append(arguments.subPathes()).append("\n");
        answer.append("Attributes=[");
        for (String key : arguments.argKeys()) {
            answer.append(key).append("='").append(arguments.argList(key)).append("' ");
        }
        answer.append("]\n");
        arguments.setResponseBody(answer.toString());
    }

    @Override
    protected String getDetailedAllow(RestHandler handler) {
        // Detail of implementation for REST interface in order clients cloud automatically fetch the options
        StringBuilder builder = new StringBuilder(" { 'DecodableBody': ").append(isBodyDecodable()).append("}");
        return builder.toString();
    }

}
