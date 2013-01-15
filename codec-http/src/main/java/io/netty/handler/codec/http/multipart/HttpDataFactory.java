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
package io.netty.handler.codec.http.multipart;

import io.netty.handler.codec.http.HttpRequestHeader;

import java.nio.charset.Charset;

/**
 * Interface to enable creation of InterfaceHttpData objects
 */
public interface HttpDataFactory {
    /**
    *
    * @param request associated request
    * @return a new Attribute with no value
    */
    Attribute createAttribute(HttpRequestHeader request, String name);

    /**
     * @param request associated request
     * @return a new Attribute
     */
    Attribute createAttribute(HttpRequestHeader request, String name, String value);

    /**
     * @param request associated request
     * @param size the size of the Uploaded file
     * @return a new FileUpload
     */
    FileUpload createFileUpload(HttpRequestHeader request, String name, String filename,
                                String contentType, String contentTransferEncoding, Charset charset,
                                long size);

    /**
     * Remove the given InterfaceHttpData from clean list (will not delete the file, except if the file
     * is still a temporary one as setup at construction)
     * @param request associated request
     */
    void removeHttpDataFromClean(HttpRequestHeader request, InterfaceHttpData data);

    /**
     * Remove all InterfaceHttpData from virtual File storage from clean list for the request
     *
     * @param request associated request
     */
    void cleanRequestHttpDatas(HttpRequestHeader request);

    /**
     * Remove all InterfaceHttpData from virtual File storage from clean list for all requests
     */
    void cleanAllHttpDatas();
}
