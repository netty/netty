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

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.HttpPostBodyUtil.SeekAheadNoBackArrayException;
import io.netty.handler.codec.http.multipart.HttpPostBodyUtil.SeekAheadOptimize;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.MultiPartStatus;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.netty.buffer.Unpooled.*;

/**
 * This decoder will decode Body and can handle POST BODY (or for PUT, PATCH or OPTIONS).
 *
 * You <strong>MUST</strong> call {@link #destroy()} after completion to release all resources.
 *
 */
public class HttpPostStandardRequestDecoder implements HttpPostRequestDecoderInterface {
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
     * Does request have a body to decode
     */
    private boolean bodyToDecode;

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
    private ByteBuf undecodedChunk;

    /**
     * Body HttpDatas current position
     */
    private int bodyListHttpDataRank;

    /**
     * Current getStatus
     */
    private MultiPartStatus currentStatus = MultiPartStatus.NOTSTARTED;

    /**
     * The current Attribute that is currently in decode process
     */
    private Attribute currentAttribute;

    private boolean destroyed;

    private int discardThreshold = HttpPostRequestDecoder.DEFAULT_DISCARD_THRESHOLD;

    /**
     *
     * @param request
     *            the request to decode
     * @throws NullPointerException
     *             for request
     * @throws HttpPostRequestDecoder.IncompatibleDataDecoderException
     *             if the request has no body to decode
     * @throws HttpPostRequestDecoder.ErrorDataDecoderException
     *             if the default charset was wrong when decoding or other
     *             errors
     */
    public HttpPostStandardRequestDecoder(HttpRequest request) throws HttpPostRequestDecoder.ErrorDataDecoderException,
            HttpPostRequestDecoder.IncompatibleDataDecoderException {
        this(new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE), request, HttpConstants.DEFAULT_CHARSET);
    }

    /**
     *
     * @param factory
     *            the factory used to create InterfaceHttpData
     * @param request
     *            the request to decode
     * @throws NullPointerException
     *             for request or factory
     * @throws HttpPostRequestDecoder.IncompatibleDataDecoderException
     *             if the request has no body to decode
     * @throws HttpPostRequestDecoder.ErrorDataDecoderException
     *             if the default charset was wrong when decoding or other
     *             errors
     */
    public HttpPostStandardRequestDecoder(HttpDataFactory factory, HttpRequest request) throws HttpPostRequestDecoder.ErrorDataDecoderException,
            HttpPostRequestDecoder.IncompatibleDataDecoderException {
        this(factory, request, HttpConstants.DEFAULT_CHARSET);
    }

    /**
     *
     * @param request
     *            the request to decode
     * @param unlimitedMethod
     *            True to unlimit method, False will limit to POST, PUT, PATCH and OPTIONS
     * @throws NullPointerException
     *             for request
     * @throws HttpPostRequestDecoder.IncompatibleDataDecoderException
     *             if the request has no body to decode
     * @throws HttpPostRequestDecoder.ErrorDataDecoderException
     *             if the default charset was wrong when decoding or other
     *             errors
     */
    public HttpPostStandardRequestDecoder(HttpRequest request, boolean unlimitedMethod)
           throws HttpPostRequestDecoder.ErrorDataDecoderException,
           HttpPostRequestDecoder.IncompatibleDataDecoderException {
        this(new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE), request,
               HttpConstants.DEFAULT_CHARSET, unlimitedMethod);
    }

    /**
     *
     * @param factory
     *            the factory used to create InterfaceHttpData
     * @param request
     *            the request to decode
     * @param unlimitedMethod
     *            True to unlimit method, False will limit to POST, PUT, PATCH and OPTIONS
     * @throws NullPointerException
     *             for request or factory
     * @throws HttpPostRequestDecoder.IncompatibleDataDecoderException
     *             if the request has no body to decode
     * @throws HttpPostRequestDecoder.ErrorDataDecoderException
     *             if the default charset was wrong when decoding or other
     *             errors
     */
    public HttpPostStandardRequestDecoder(HttpDataFactory factory, HttpRequest request, boolean unlimitedMethod)
           throws HttpPostRequestDecoder.ErrorDataDecoderException,
           HttpPostRequestDecoder.IncompatibleDataDecoderException {
        this(factory, request, HttpConstants.DEFAULT_CHARSET, unlimitedMethod);
    }

    /**
     *
     * @param factory
     *            the factory used to create InterfaceHttpData
     * @param request
     *            the request to decode
     * @param charset
     *            the charset to use as default
     * @throws NullPointerException
     *             for request or charset or factory
     * @throws HttpPostRequestDecoder.IncompatibleDataDecoderException
     *             if the request has no body to decode
     * @throws HttpPostRequestDecoder.ErrorDataDecoderException
     *             if the default charset was wrong when decoding or other
     *             errors
     */
    public HttpPostStandardRequestDecoder(HttpDataFactory factory, HttpRequest request, Charset charset)
            throws HttpPostRequestDecoder.ErrorDataDecoderException, HttpPostRequestDecoder.IncompatibleDataDecoderException {
        this(factory, request, charset, false);
    }

    /**
     *
     * @param factory
     *            the factory used to create InterfaceHttpData
     * @param request
     *            the request to decode
     * @param charset
     *            the charset to use as default
     * @param unlimitedMethod
     *            True to unlimit method, False will limit to POST, PUT, PATCH and OPTIONS
     * @throws NullPointerException
     *             for request or charset or factory
     * @throws HttpPostRequestDecoder.IncompatibleDataDecoderException
     *             if the request has no body to decode
     * @throws HttpPostRequestDecoder.ErrorDataDecoderException
     *             if the default charset was wrong when decoding or other
     *             errors
     */
    public HttpPostStandardRequestDecoder(HttpDataFactory factory, HttpRequest request, Charset charset,
            boolean unlimitedMethod)
            throws HttpPostRequestDecoder.ErrorDataDecoderException, HttpPostRequestDecoder.IncompatibleDataDecoderException {
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
        if (unlimitedMethod) {
            bodyToDecode = true;
        } else {
            HttpMethod method = request.getMethod();
            if (method.equals(HttpMethod.POST) || method.equals(HttpMethod.PUT)
                    || method.equals(HttpMethod.PATCH) || method.equals(HttpMethod.OPTIONS)) {
                bodyToDecode = true;
            }
        }
        this.charset = charset;
        this.factory = factory;
        if (!bodyToDecode) {
            throw new HttpPostRequestDecoder.IncompatibleDataDecoderException("No Body to decode");
        }
        if (request instanceof HttpContent) {
            // Offer automatically if the given request is als type of HttpContent
            // See #1089
            offer((HttpContent) request);
        } else {
            undecodedChunk = buffer();
            parseBody();
        }
    }

    private void checkDestroyed() {
        if (destroyed) {
            throw new IllegalStateException(HttpPostStandardRequestDecoder.class.getSimpleName() + " was destroyed already");
        }
    }

    /**
     * True if this request is a Multipart request
     *
     * @return True if this request is a Multipart request
     */
    public boolean isMultipart() {
        checkDestroyed();
        return false;
    }

    /**
     * Set the amount of bytes after which read bytes in the buffer should be discarded.
     * Setting this lower gives lower memory usage but with the overhead of more memory copies.
     * Use {@code 0} to disable it.
     */
    public void setDiscardThreshold(int discardThreshold) {
        if (discardThreshold < 0) {
          throw new IllegalArgumentException("discardThreshold must be >= 0");
        }
        this.discardThreshold = discardThreshold;
    }

    /**
     * Return the threshold in bytes after which read data in the buffer should be discarded.
     */
    public int getDiscardThreshold() {
        return discardThreshold;
    }

    /**
     * This getMethod returns a List of all HttpDatas from body.<br>
     *
     * If chunked, all chunks must have been offered using offer() getMethod. If
     * not, NotEnoughDataDecoderException will be raised.
     *
     * @return the list of HttpDatas from Body part for POST getMethod
     * @throws HttpPostRequestDecoder.NotEnoughDataDecoderException
     *             Need more chunks
     */
    public List<InterfaceHttpData> getBodyHttpDatas() throws HttpPostRequestDecoder.NotEnoughDataDecoderException {
        checkDestroyed();

        if (!isLastChunk) {
            throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
        }
        return bodyListHttpData;
    }

    /**
     * This getMethod returns a List of all HttpDatas with the given name from
     * body.<br>
     *
     * If chunked, all chunks must have been offered using offer() getMethod. If
     * not, NotEnoughDataDecoderException will be raised.
     *
     * @return All Body HttpDatas with the given name (ignore case)
     * @throws HttpPostRequestDecoder.NotEnoughDataDecoderException
     *             need more chunks
     */
    public List<InterfaceHttpData> getBodyHttpDatas(String name) throws HttpPostRequestDecoder.NotEnoughDataDecoderException {
        checkDestroyed();

        if (!isLastChunk) {
            throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
        }
        return bodyMapHttpData.get(name);
    }

    /**
     * This getMethod returns the first InterfaceHttpData with the given name from
     * body.<br>
     *
     * If chunked, all chunks must have been offered using offer() getMethod. If
     * not, NotEnoughDataDecoderException will be raised.
     *
     * @return The first Body InterfaceHttpData with the given name (ignore
     *         case)
     * @throws HttpPostRequestDecoder.NotEnoughDataDecoderException
     *             need more chunks
     */
    public InterfaceHttpData getBodyHttpData(String name) throws HttpPostRequestDecoder.NotEnoughDataDecoderException {
        checkDestroyed();

        if (!isLastChunk) {
            throw new HttpPostRequestDecoder.NotEnoughDataDecoderException();
        }
        List<InterfaceHttpData> list = bodyMapHttpData.get(name);
        if (list != null) {
            return list.get(0);
        }
        return null;
    }

    /**
     * Initialized the internals from a new chunk
     *
     * @param content
     *            the new received chunk
     * @throws HttpPostRequestDecoder.ErrorDataDecoderException
     *             if there is a problem with the charset decoding or other
     *             errors
     */
    public HttpPostStandardRequestDecoder offer(HttpContent content) throws HttpPostRequestDecoder.ErrorDataDecoderException {
        checkDestroyed();

        // Maybe we should better not copy here for performance reasons but this will need
        // more care by the caller to release the content in a correct manner later
        // So maybe something to optimize on a later stage
        ByteBuf buf = content.content();
        if (undecodedChunk == null) {
            undecodedChunk = buf.copy();
        } else {
            undecodedChunk.writeBytes(buf);
        }
        if (content instanceof LastHttpContent) {
            isLastChunk = true;
        }
        parseBody();
        if (undecodedChunk != null && undecodedChunk.writerIndex() > discardThreshold) {
            undecodedChunk.discardReadBytes();
        }
        return this;
    }

    /**
     * True if at current getStatus, there is an available decoded
     * InterfaceHttpData from the Body.
     *
     * This getMethod works for chunked and not chunked request.
     *
     * @return True if at current getStatus, there is a decoded InterfaceHttpData
     * @throws HttpPostRequestDecoder.EndOfDataDecoderException
     *             No more data will be available
     */
    public boolean hasNext() throws HttpPostRequestDecoder.EndOfDataDecoderException {
        checkDestroyed();

        if (currentStatus == MultiPartStatus.EPILOGUE) {
            // OK except if end of list
            if (bodyListHttpDataRank >= bodyListHttpData.size()) {
                throw new HttpPostRequestDecoder.EndOfDataDecoderException();
            }
        }
        return !bodyListHttpData.isEmpty() && bodyListHttpDataRank < bodyListHttpData.size();
    }

    /**
     * Returns the next available InterfaceHttpData or null if, at the time it
     * is called, there is no more available InterfaceHttpData. A subsequent
     * call to offer(httpChunk) could enable more data.
     *
     * Be sure to call {@link InterfaceHttpData#release()} after you are done
     * with processing to make sure to not leak any resources
     *
     * @return the next available InterfaceHttpData or null if none
     * @throws HttpPostRequestDecoder.EndOfDataDecoderException
     *             No more data will be available
     */
    public InterfaceHttpData next() throws HttpPostRequestDecoder.EndOfDataDecoderException {
        checkDestroyed();

        if (hasNext()) {
            return bodyListHttpData.get(bodyListHttpDataRank++);
        }
        return null;
    }

    /**
     * This getMethod will parse as much as possible data and fill the list and map
     *
     * @throws HttpPostRequestDecoder.ErrorDataDecoderException
     *             if there is a problem with the charset decoding or other
     *             errors
     */
    private void parseBody() throws HttpPostRequestDecoder.ErrorDataDecoderException {
        if (currentStatus == MultiPartStatus.PREEPILOGUE || currentStatus == MultiPartStatus.EPILOGUE) {
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
    protected void addHttpData(InterfaceHttpData data) {
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
     * This getMethod fill the map and list with as much Attribute as possible from
     * Body in not Multipart mode.
     *
     * @throws HttpPostRequestDecoder.ErrorDataDecoderException
     *             if there is a problem with the charset decoding or other
     *             errors
     */
    private void parseBodyAttributesStandard() throws HttpPostRequestDecoder.ErrorDataDecoderException {
        int firstpos = undecodedChunk.readerIndex();
        int currentpos = firstpos;
        int equalpos;
        int ampersandpos;
        if (currentStatus == MultiPartStatus.NOTSTARTED) {
            currentStatus = MultiPartStatus.DISPOSITION;
        }
        boolean contRead = true;
        try {
            while (undecodedChunk.isReadable() && contRead) {
                char read = (char) undecodedChunk.readUnsignedByte();
                currentpos++;
                switch (currentStatus) {
                case DISPOSITION:// search '='
                    if (read == '=') {
                        currentStatus = MultiPartStatus.FIELD;
                        equalpos = currentpos - 1;
                        String key = decodeAttribute(undecodedChunk.toString(firstpos, equalpos - firstpos, charset),
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
                        setFinalBuffer(undecodedChunk.copy(firstpos, ampersandpos - firstpos));
                        firstpos = currentpos;
                        contRead = true;
                    } else if (read == HttpConstants.CR) {
                        if (undecodedChunk.isReadable()) {
                            read = (char) undecodedChunk.readUnsignedByte();
                            currentpos++;
                            if (read == HttpConstants.LF) {
                                currentStatus = MultiPartStatus.PREEPILOGUE;
                                ampersandpos = currentpos - 2;
                                setFinalBuffer(undecodedChunk.copy(firstpos, ampersandpos - firstpos));
                                firstpos = currentpos;
                                contRead = false;
                            } else {
                                // Error
                                throw new HttpPostRequestDecoder.ErrorDataDecoderException("Bad end of line");
                            }
                        } else {
                            currentpos--;
                        }
                    } else if (read == HttpConstants.LF) {
                        currentStatus = MultiPartStatus.PREEPILOGUE;
                        ampersandpos = currentpos - 1;
                        setFinalBuffer(undecodedChunk.copy(firstpos, ampersandpos - firstpos));
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
                    setFinalBuffer(undecodedChunk.copy(firstpos, ampersandpos - firstpos));
                } else if (!currentAttribute.isCompleted()) {
                    setFinalBuffer(EMPTY_BUFFER);
                }
                firstpos = currentpos;
                currentStatus = MultiPartStatus.EPILOGUE;
                return;
            }
            if (contRead && currentAttribute != null) {
                // reset index except if to continue in case of FIELD getStatus
                if (currentStatus == MultiPartStatus.FIELD) {
                    currentAttribute.addContent(undecodedChunk.copy(firstpos, currentpos - firstpos),
                                                false);
                    firstpos = currentpos;
                }
                undecodedChunk.readerIndex(firstpos);
            } else {
                // end of line so keep index
            }
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
            // error while decoding
            undecodedChunk.readerIndex(firstpos);
            throw e;
        } catch (IOException e) {
            // error while decoding
            undecodedChunk.readerIndex(firstpos);
            throw new HttpPostRequestDecoder.ErrorDataDecoderException(e);
        }
    }

    /**
     * This getMethod fill the map and list with as much Attribute as possible from
     * Body in not Multipart mode.
     *
     * @throws HttpPostRequestDecoder.ErrorDataDecoderException
     *             if there is a problem with the charset decoding or other
     *             errors
     */
    private void parseBodyAttributes() throws HttpPostRequestDecoder.ErrorDataDecoderException {
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
            loop: while (sao.pos < sao.limit) {
                char read = (char) (sao.bytes[sao.pos++] & 0xFF);
                currentpos++;
                switch (currentStatus) {
                case DISPOSITION:// search '='
                    if (read == '=') {
                        currentStatus = MultiPartStatus.FIELD;
                        equalpos = currentpos - 1;
                        String key = decodeAttribute(undecodedChunk.toString(firstpos, equalpos - firstpos, charset),
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
                        setFinalBuffer(undecodedChunk.copy(firstpos, ampersandpos - firstpos));
                        firstpos = currentpos;
                        contRead = true;
                    } else if (read == HttpConstants.CR) {
                        if (sao.pos < sao.limit) {
                            read = (char) (sao.bytes[sao.pos++] & 0xFF);
                            currentpos++;
                            if (read == HttpConstants.LF) {
                                currentStatus = MultiPartStatus.PREEPILOGUE;
                                ampersandpos = currentpos - 2;
                                sao.setReadPosition(0);
                                setFinalBuffer(undecodedChunk.copy(firstpos, ampersandpos - firstpos));
                                firstpos = currentpos;
                                contRead = false;
                                break loop;
                            } else {
                                // Error
                                sao.setReadPosition(0);
                                throw new HttpPostRequestDecoder.ErrorDataDecoderException("Bad end of line");
                            }
                        } else {
                            if (sao.limit > 0) {
                                currentpos--;
                            }
                        }
                    } else if (read == HttpConstants.LF) {
                        currentStatus = MultiPartStatus.PREEPILOGUE;
                        ampersandpos = currentpos - 1;
                        sao.setReadPosition(0);
                        setFinalBuffer(undecodedChunk.copy(firstpos, ampersandpos - firstpos));
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
                    setFinalBuffer(undecodedChunk.copy(firstpos, ampersandpos - firstpos));
                } else if (!currentAttribute.isCompleted()) {
                    setFinalBuffer(EMPTY_BUFFER);
                }
                firstpos = currentpos;
                currentStatus = MultiPartStatus.EPILOGUE;
                return;
            }
            if (contRead && currentAttribute != null) {
                // reset index except if to continue in case of FIELD getStatus
                if (currentStatus == MultiPartStatus.FIELD) {
                    currentAttribute.addContent(undecodedChunk.copy(firstpos, currentpos - firstpos),
                                                false);
                    firstpos = currentpos;
                }
                undecodedChunk.readerIndex(firstpos);
            } else {
                // end of line so keep index
            }
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
            // error while decoding
            undecodedChunk.readerIndex(firstpos);
            throw e;
        } catch (IOException e) {
            // error while decoding
            undecodedChunk.readerIndex(firstpos);
            throw new HttpPostRequestDecoder.ErrorDataDecoderException(e);
        }
    }

    private void setFinalBuffer(ByteBuf buffer) throws HttpPostRequestDecoder.ErrorDataDecoderException, IOException {
        currentAttribute.addContent(buffer, true);
        String value = decodeAttribute(currentAttribute.getByteBuf().toString(charset), charset);
        currentAttribute.setValue(value);
        addHttpData(currentAttribute);
        currentAttribute = null;
    }

    /**
     * Decode component
     *
     * @return the decoded component
     */
    private static String decodeAttribute(String s, Charset charset) throws HttpPostRequestDecoder.ErrorDataDecoderException {
        if (s == null) {
            return "";
        }
        try {
            return URLDecoder.decode(s, charset.name());
        } catch (UnsupportedEncodingException e) {
            throw new HttpPostRequestDecoder.ErrorDataDecoderException(charset.toString(), e);
        } catch (IllegalArgumentException e) {
            throw new HttpPostRequestDecoder.ErrorDataDecoderException("Bad string: '" + s + '\'', e);
        }
    }

    /**
     * Skip control Characters
     *
     * @throws HttpPostRequestDecoder.NotEnoughDataDecoderException
     */
    void skipControlCharacters() throws HttpPostRequestDecoder.NotEnoughDataDecoderException {
        SeekAheadOptimize sao;
        try {
            sao = new SeekAheadOptimize(undecodedChunk);
        } catch (SeekAheadNoBackArrayException e) {
            try {
                skipControlCharactersStandard();
            } catch (IndexOutOfBoundsException e1) {
                throw new HttpPostRequestDecoder.NotEnoughDataDecoderException(e1);
            }
            return;
        }

        while (sao.pos < sao.limit) {
            char c = (char) (sao.bytes[sao.pos++] & 0xFF);
            if (!Character.isISOControl(c) && !Character.isWhitespace(c)) {
                sao.setReadPosition(1);
                return;
            }
        }
        throw new HttpPostRequestDecoder.NotEnoughDataDecoderException("Access out of bounds");
    }

    void skipControlCharactersStandard() {
        for (;;) {
            char c = (char) undecodedChunk.readUnsignedByte();
            if (!Character.isISOControl(c) && !Character.isWhitespace(c)) {
                undecodedChunk.readerIndex(undecodedChunk.readerIndex() - 1);
                break;
            }
        }
    }

    /**
     * Destroy the {@link HttpPostStandardRequestDecoder} and release all it resources. After this method
     * was called it is not possible to operate on it anymore.
     */
    public void destroy() {
        checkDestroyed();
        cleanFiles();
        destroyed = true;

        if (undecodedChunk != null && undecodedChunk.refCnt() > 0) {
            undecodedChunk.release();
            undecodedChunk = null;
        }

        // release all data which was not yet pulled
        for (int i = bodyListHttpDataRank; i < bodyListHttpData.size(); i++) {
            bodyListHttpData.get(i).release();
        }
    }

    /**
     * Clean all HttpDatas (on Disk) for the current request.
     */
    public void cleanFiles() {
        checkDestroyed();

        factory.cleanRequestHttpDatas(request);
    }

    /**
     * Remove the given FileUpload from the list of FileUploads to clean
     */
    public void removeHttpDataFromClean(InterfaceHttpData data) {
        checkDestroyed();

        factory.removeHttpDataFromClean(request, data);
    }
}
