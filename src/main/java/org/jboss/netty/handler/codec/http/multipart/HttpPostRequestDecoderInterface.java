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
package org.jboss.netty.handler.codec.http.multipart;

import java.util.List;

import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.NotEnoughDataDecoderException;

public interface HttpPostRequestDecoderInterface {
    /**
     * True if this request is a Multipart request
     * @return True if this request is a Multipart request
     */
    boolean isMultipart();

    /**
     * This method returns a List of all HttpDatas from body.<br>
     *
     * If chunked, all chunks must have been offered using offer() method.
     * If not, NotEnoughDataDecoderException will be raised.
     *
     * @return the list of HttpDatas from Body part for POST method
     * @throws NotEnoughDataDecoderException Need more chunks
     */
    List<InterfaceHttpData> getBodyHttpDatas()
            throws NotEnoughDataDecoderException;

    /**
     * This method returns a List of all HttpDatas with the given name from body.<br>
     *
     * If chunked, all chunks must have been offered using offer() method.
     * If not, NotEnoughDataDecoderException will be raised.

     * @return All Body HttpDatas with the given name (ignore case)
     * @throws NotEnoughDataDecoderException need more chunks
     */
    List<InterfaceHttpData> getBodyHttpDatas(String name)
            throws NotEnoughDataDecoderException;

    /**
     * This method returns the first InterfaceHttpData with the given name from body.<br>
     *
     * If chunked, all chunks must have been offered using offer() method.
     * If not, NotEnoughDataDecoderException will be raised.
    *
    * @return The first Body InterfaceHttpData with the given name (ignore case)
    * @throws NotEnoughDataDecoderException need more chunks
    */
    InterfaceHttpData getBodyHttpData(String name)
            throws NotEnoughDataDecoderException;

    /**
     * Initialized the internals from a new chunk
     * @param chunk the new received chunk
     * @throws ErrorDataDecoderException if there is a problem with the charset decoding or
     *          other errors
     */
    void offer(HttpChunk chunk) throws ErrorDataDecoderException;

    /**
     * True if at current status, there is an available decoded InterfaceHttpData from the Body.
     *
     * This method works for chunked and not chunked request.
     *
     * @return True if at current status, there is a decoded InterfaceHttpData
     * @throws EndOfDataDecoderException No more data will be available
     */
    boolean hasNext() throws EndOfDataDecoderException;

    /**
     * Returns the next available InterfaceHttpData or null if, at the time it is called, there is no more
     * available InterfaceHttpData. A subsequent call to offer(httpChunk) could enable more data.
     *
     * @return the next available InterfaceHttpData or null if none
     * @throws EndOfDataDecoderException No more data will be available
     */
    InterfaceHttpData next() throws EndOfDataDecoderException;

    /**
     * Clean all HttpDatas (on Disk) for the current request.
     */
    void cleanFiles();

    /**
     * Remove the given FileUpload from the list of FileUploads to clean
     */
    void removeHttpDataFromClean(InterfaceHttpData data);

}
