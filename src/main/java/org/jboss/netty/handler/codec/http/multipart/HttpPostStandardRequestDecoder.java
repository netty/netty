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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpConstants;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.multipart.HttpPostBodyUtil.SeekAheadNoBackArrayException;
import org.jboss.netty.handler.codec.http.multipart.HttpPostBodyUtil.SeekAheadOptimize;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.IncompatibleDataDecoderException;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.MultiPartStatus;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.NotEnoughDataDecoderException;
import org.jboss.netty.util.internal.CaseIgnoringComparator;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * This decoder will decode Body and can handle standard (non multipart) POST BODY.
 */
@SuppressWarnings({ "deprecation", "RedundantThrowsDeclaration" })
public class HttpPostStandardRequestDecoder implements InterfaceHttpPostRequestDecoder {
    /**
     * Factory used to create InterfaceHttpData
     */
    private final HttpDataFactory factory;

    /**
     * Request to decode
     */
    private final HttpRequest request;

    /**
     * Default charset to use
     */
    private final Charset charset;

    /**
     * Does the last chunk already received
     */
    private boolean isLastChunk;

    /**
     * HttpDatas from Body
     */
    private final List<InterfaceHttpData> bodyListHttpData = new ArrayList<InterfaceHttpData>();

    /**
     * HttpDatas as Map from Body
     */
    private final Map<String, List<InterfaceHttpData>> bodyMapHttpData = new TreeMap<String, List<InterfaceHttpData>>(
            CaseIgnoringComparator.INSTANCE);

    /**
     * The current channelBuffer
     */
    private ChannelBuffer undecodedChunk;

    /**
     * Body HttpDatas current position
     */
    private int bodyListHttpDataRank;

    /**
     * Current status
     */
    private MultiPartStatus currentStatus = MultiPartStatus.NOTSTARTED;

    /**
     * The current Attribute that is currently in decode process
     */
    private Attribute currentAttribute;

    /**
    *
    * @param request the request to decode
    * @throws NullPointerException for request
    * @throws IncompatibleDataDecoderException if the request has no body to decode
    * @throws ErrorDataDecoderException if the default charset was wrong when decoding or other errors
    */
    public HttpPostStandardRequestDecoder(HttpRequest request)
            throws ErrorDataDecoderException, IncompatibleDataDecoderException {
        this(new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE),
                request, HttpConstants.DEFAULT_CHARSET);
    }

    /**
     *
     * @param factory the factory used to create InterfaceHttpData
     * @param request the request to decode
     * @throws NullPointerException for request or factory
     * @throws IncompatibleDataDecoderException if the request has no body to decode
     * @throws ErrorDataDecoderException if the default charset was wrong when decoding or other errors
     */
    public HttpPostStandardRequestDecoder(HttpDataFactory factory, HttpRequest request)
            throws ErrorDataDecoderException, IncompatibleDataDecoderException {
        this(factory, request, HttpConstants.DEFAULT_CHARSET);
    }

    /**
     *
     * @param factory the factory used to create InterfaceHttpData
     * @param request the request to decode
     * @param charset the charset to use as default
     * @throws NullPointerException for request or charset or factory
     * @throws IncompatibleDataDecoderException if the request has no body to decode
     * @throws ErrorDataDecoderException if the default charset was wrong when decoding or other errors
     */
    public HttpPostStandardRequestDecoder(HttpDataFactory factory, HttpRequest request,
            Charset charset) throws ErrorDataDecoderException,
            IncompatibleDataDecoderException {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        if (request == null) {
            throw new NullPointerException("request");
        }
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        this.request = request;
        this.charset = charset;
        this.factory = factory;
        if (!this.request.isChunked()) {
            undecodedChunk = this.request.getContent();
            isLastChunk = true;
            parseBody();
        }
    }

    public boolean isMultipart() {
        return false;
    }

    public List<InterfaceHttpData> getBodyHttpDatas()
            throws NotEnoughDataDecoderException {
        if (!isLastChunk) {
            throw new NotEnoughDataDecoderException();
        }
        return bodyListHttpData;
    }

    public List<InterfaceHttpData> getBodyHttpDatas(String name)
            throws NotEnoughDataDecoderException {
        if (!isLastChunk) {
            throw new NotEnoughDataDecoderException();
        }
        return bodyMapHttpData.get(name);
    }

    public InterfaceHttpData getBodyHttpData(String name)
            throws NotEnoughDataDecoderException {
        if (!isLastChunk) {
            throw new NotEnoughDataDecoderException();
        }
        List<InterfaceHttpData> list = bodyMapHttpData.get(name);
        if (list != null) {
            return list.get(0);
        }
        return null;
    }

    public void offer(HttpChunk chunk) throws ErrorDataDecoderException {
        ChannelBuffer chunked = chunk.getContent();
        if (undecodedChunk == null) {
            undecodedChunk = chunked;
        } else {
            //undecodedChunk = ChannelBuffers.wrappedBuffer(undecodedChunk, chunk.getContent());
            // less memory usage
            undecodedChunk = ChannelBuffers.wrappedBuffer(
                    undecodedChunk, chunked);
        }
        if (chunk.isLast()) {
            isLastChunk = true;
        }
        parseBody();
    }

    public boolean hasNext() throws EndOfDataDecoderException {
        if (currentStatus == MultiPartStatus.EPILOGUE) {
            // OK except if end of list
            if (bodyListHttpDataRank >= bodyListHttpData.size()) {
                throw new EndOfDataDecoderException();
            }
        }
        return !bodyListHttpData.isEmpty() && bodyListHttpDataRank < bodyListHttpData.size();
    }

    public InterfaceHttpData next() throws EndOfDataDecoderException {
        if (hasNext()) {
            return bodyListHttpData.get(bodyListHttpDataRank++);
        }
        return null;
    }

    /**
     * This method will parse as much as possible data and fill the list and map
     * @throws ErrorDataDecoderException if there is a problem with the charset decoding or
     *          other errors
     */
    private void parseBody() throws ErrorDataDecoderException {
        if (currentStatus == MultiPartStatus.PREEPILOGUE ||
                currentStatus == MultiPartStatus.EPILOGUE) {
            if (isLastChunk) {
                currentStatus = MultiPartStatus.EPILOGUE;
            }
            return;
        }
        parseBodyAttributes();
    }

    /**
     * Utility function to add a new decoded data
     */
    private void addHttpData(InterfaceHttpData data) {
        if (data == null) {
            return;
        }
        List<InterfaceHttpData> datas = bodyMapHttpData.get(data.getName());
        if (datas == null) {
            datas = new ArrayList<InterfaceHttpData>(1);
            bodyMapHttpData.put(data.getName(), datas);
        }
        datas.add(data);
        bodyListHttpData.add(data);
    }

    /**
      * This method fill the map and list with as much Attribute as possible from Body in
      * not Multipart mode.
      *
      * @throws ErrorDataDecoderException if there is a problem with the charset decoding or
      *          other errors
      */
    private void parseBodyAttributesStandard() throws ErrorDataDecoderException {
        int firstpos = undecodedChunk.readerIndex();
        int currentpos = firstpos;
        int equalpos;
        int ampersandpos;
        if (currentStatus == MultiPartStatus.NOTSTARTED) {
            currentStatus = MultiPartStatus.DISPOSITION;
        }
        boolean contRead = true;
        try {
           while (undecodedChunk.readable() && contRead) {
                char read = (char) undecodedChunk.readUnsignedByte();
                currentpos++;
                switch (currentStatus) {
                case DISPOSITION:// search '='
                    if (read == '=') {
                        currentStatus = MultiPartStatus.FIELD;
                        equalpos = currentpos - 1;
                        String key = decodeAttribute(
                                undecodedChunk.toString(firstpos, equalpos - firstpos, charset),
                                charset);
                        currentAttribute = factory.createAttribute(request, key);
                        firstpos = currentpos;
                    } else if (read == '&') { // special empty FIELD
                        currentStatus = MultiPartStatus.DISPOSITION;
                        ampersandpos = currentpos - 1;
                        String key = decodeAttribute(
                                undecodedChunk.toString(firstpos, ampersandpos - firstpos, charset), charset);
                        currentAttribute = factory.createAttribute(request, key);
                        currentAttribute.setValue(""); // empty
                        addHttpData(currentAttribute);
                        currentAttribute = null;
                        firstpos = currentpos;
                        contRead = true;
                    }
                    break;
                case FIELD:// search '&' or end of line
                    if (read == '&') {
                        currentStatus = MultiPartStatus.DISPOSITION;
                        ampersandpos = currentpos - 1;
                        setFinalBuffer(undecodedChunk.slice(firstpos, ampersandpos - firstpos));
                        firstpos = currentpos;
                        contRead = true;
                    } else if (read == HttpConstants.CR) {
                        if (undecodedChunk.readable()) {
                            read = (char) undecodedChunk.readUnsignedByte();
                            currentpos++;
                            if (read == HttpConstants.LF) {
                                currentStatus = MultiPartStatus.PREEPILOGUE;
                                ampersandpos = currentpos - 2;
                                setFinalBuffer(
                                        undecodedChunk.slice(firstpos, ampersandpos - firstpos));
                                firstpos = currentpos;
                                contRead = false;
                            } else {
                                // Error
                                throw new ErrorDataDecoderException("Bad end of line");
                            }
                        } else {
                            currentpos--;
                        }
                    } else if (read == HttpConstants.LF) {
                        currentStatus = MultiPartStatus.PREEPILOGUE;
                        ampersandpos = currentpos - 1;
                        setFinalBuffer(
                                undecodedChunk.slice(firstpos, ampersandpos - firstpos));
                        firstpos = currentpos;
                        contRead = false;
                    }
                    break;
                default:
                    // just stop
                    contRead = false;
                }
            }
            if (isLastChunk && currentAttribute != null) {
                // special case
                ampersandpos = currentpos;
                if (ampersandpos > firstpos) {
                    setFinalBuffer(
                            undecodedChunk.slice(firstpos, ampersandpos - firstpos));
                } else if (! currentAttribute.isCompleted()) {
                    setFinalBuffer(ChannelBuffers.EMPTY_BUFFER);
                }
                firstpos = currentpos;
                currentStatus = MultiPartStatus.EPILOGUE;
                return;
            }
            if (contRead && currentAttribute != null) {
                // reset index except if to continue in case of FIELD status
                if (currentStatus == MultiPartStatus.FIELD) {
                    currentAttribute.addContent(
                            undecodedChunk.slice(firstpos, currentpos - firstpos),
                            false);
                    firstpos = currentpos;
                }
                undecodedChunk.readerIndex(firstpos);
            } else {
                // end of line or end of block so keep index to last valid position
                undecodedChunk.readerIndex(firstpos);
            }
        } catch (ErrorDataDecoderException e) {
            // error while decoding
            undecodedChunk.readerIndex(firstpos);
            throw e;
        } catch (IOException e) {
            // error while decoding
            undecodedChunk.readerIndex(firstpos);
            throw new ErrorDataDecoderException(e);
        }
    }

    /**
     * This method fill the map and list with as much Attribute as possible from Body in
     * not Multipart mode.
     *
     * @throws ErrorDataDecoderException if there is a problem with the charset decoding or
     * other errors
     */
    private void parseBodyAttributes() throws ErrorDataDecoderException {
        SeekAheadOptimize sao;
        try {
            sao = new SeekAheadOptimize(undecodedChunk);
        } catch (SeekAheadNoBackArrayException e1) {
            parseBodyAttributesStandard();
            return;
        }
        int firstpos = undecodedChunk.readerIndex();
        int currentpos = firstpos;
        int equalpos;
        int ampersandpos;
        if (currentStatus == MultiPartStatus.NOTSTARTED) {
            currentStatus = MultiPartStatus.DISPOSITION;
        }
        boolean contRead = true;
        try {
            loop:
            while (sao.pos < sao.limit) {
                char read = (char) (sao.bytes[sao.pos ++] & 0xFF);
                currentpos ++;
                switch (currentStatus) {
                case DISPOSITION:// search '='
                    if (read == '=') {
                        currentStatus = MultiPartStatus.FIELD;
                        equalpos = currentpos - 1;
                        String key = decodeAttribute(
                                undecodedChunk.toString(firstpos, equalpos - firstpos, charset),
                                charset);
                        currentAttribute = factory.createAttribute(request, key);
                        firstpos = currentpos;
                    } else if (read == '&') { // special empty FIELD
                        currentStatus = MultiPartStatus.DISPOSITION;
                        ampersandpos = currentpos - 1;
                        String key = decodeAttribute(
                                undecodedChunk.toString(firstpos, ampersandpos - firstpos, charset), charset);
                        currentAttribute = factory.createAttribute(request, key);
                        currentAttribute.setValue(""); // empty
                        addHttpData(currentAttribute);
                        currentAttribute = null;
                        firstpos = currentpos;
                        contRead = true;
                    }
                    break;
                case FIELD:// search '&' or end of line
                    if (read == '&') {
                        currentStatus = MultiPartStatus.DISPOSITION;
                        ampersandpos = currentpos - 1;
                        setFinalBuffer(undecodedChunk.slice(firstpos, ampersandpos - firstpos));
                        firstpos = currentpos;
                        contRead = true;
                    } else if (read == HttpConstants.CR) {
                        if (sao.pos < sao.limit) {
                            read = (char) (sao.bytes[sao.pos ++] & 0xFF);
                            currentpos++;
                            if (read == HttpConstants.LF) {
                                currentStatus = MultiPartStatus.PREEPILOGUE;
                                ampersandpos = currentpos - 2;
                                sao.setReadPosition(0);
                                setFinalBuffer(
                                        undecodedChunk.slice(firstpos, ampersandpos - firstpos));
                                firstpos = currentpos;
                                contRead = false;
                                break loop;
                            } else {
                                // Error
                                sao.setReadPosition(0);
                                throw new ErrorDataDecoderException("Bad end of line");
                            }
                        } else {
                            if (sao.limit > 0) {
                                currentpos --;
                            }
                        }
                    } else if (read == HttpConstants.LF) {
                        currentStatus = MultiPartStatus.PREEPILOGUE;
                        ampersandpos = currentpos - 1;
                        sao.setReadPosition(0);
                        setFinalBuffer(
                                undecodedChunk.slice(firstpos, ampersandpos - firstpos));
                        firstpos = currentpos;
                        contRead = false;
                        break loop;
                    }
                    break;
                default:
                    // just stop
                    sao.setReadPosition(0);
                    contRead = false;
                    break loop;
                }
            }
            if (isLastChunk && currentAttribute != null) {
                // special case
                ampersandpos = currentpos;
                if (ampersandpos > firstpos) {
                    setFinalBuffer(
                            undecodedChunk.slice(firstpos, ampersandpos - firstpos));
                } else if (! currentAttribute.isCompleted()) {
                    setFinalBuffer(ChannelBuffers.EMPTY_BUFFER);
                }
                firstpos = currentpos;
                currentStatus = MultiPartStatus.EPILOGUE;
                return;
            }
            if (contRead && currentAttribute != null) {
                // reset index except if to continue in case of FIELD status
                if (currentStatus == MultiPartStatus.FIELD) {
                    currentAttribute.addContent(
                            undecodedChunk.slice(firstpos, currentpos - firstpos),
                            false);
                    firstpos = currentpos;
                }
                undecodedChunk.readerIndex(firstpos);
            } else {
                // end of line or end of block so keep index to last valid position
                undecodedChunk.readerIndex(firstpos);
            }
        } catch (ErrorDataDecoderException e) {
            // error while decoding
            undecodedChunk.readerIndex(firstpos);
            throw e;
        } catch (IOException e) {
            // error while decoding
            undecodedChunk.readerIndex(firstpos);
            throw new ErrorDataDecoderException(e);
        }
    }

    private void setFinalBuffer(ChannelBuffer buffer) throws ErrorDataDecoderException, IOException {
        currentAttribute.addContent(buffer, true);
        String value = decodeAttribute(
                currentAttribute.getChannelBuffer().toString(charset),
                charset);
        currentAttribute.setValue(value);
        addHttpData(currentAttribute);
        currentAttribute = null;
    }

    /**
     * Decode component
     * @return the decoded component
     */
    private static String decodeAttribute(String s, Charset charset)
            throws ErrorDataDecoderException {
        if (s == null) {
            return "";
        }
        try {
            return URLDecoder.decode(s, charset.name());
        } catch (UnsupportedEncodingException e) {
            throw new ErrorDataDecoderException(charset.toString(), e);
        } catch (IllegalArgumentException e) {
            throw new ErrorDataDecoderException("Bad string: '" + s + '\'', e);
        }
    }

    public void cleanFiles() {
        factory.cleanRequestHttpDatas(request);
    }

    public void removeHttpDataFromClean(InterfaceHttpData data) {
        factory.removeHttpDataFromClean(request, data);
    }
}
