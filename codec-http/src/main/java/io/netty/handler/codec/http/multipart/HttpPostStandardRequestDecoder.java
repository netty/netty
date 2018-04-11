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
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.HttpPostBodyUtil.SeekAheadOptimize;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.MultiPartStatus;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.NotEnoughDataDecoderException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.netty.buffer.Unpooled.*;
import static io.netty.util.internal.ObjectUtil.*;

/**
 * This decoder will decode Body and can handle POST BODY.
 *
 * You <strong>MUST</strong> call {@link #destroy()} after completion to release all resources.
 *
 */
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
     * @throws ErrorDataDecoderException
     *             if the default charset was wrong when decoding or other
     *             errors
     */
    public HttpPostStandardRequestDecoder(HttpRequest request) {
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
     * @throws ErrorDataDecoderException
     *             if the default charset was wrong when decoding or other
     *             errors
     */
    public HttpPostStandardRequestDecoder(HttpDataFactory factory, HttpRequest request) {
        this(factory, request, HttpConstants.DEFAULT_CHARSET);
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
     * @throws ErrorDataDecoderException
     *             if the default charset was wrong when decoding or other
     *             errors
     */
    public HttpPostStandardRequestDecoder(HttpDataFactory factory, HttpRequest request, Charset charset) {
        this.request = checkNotNull(request, "request");
        this.charset = checkNotNull(charset, "charset");
        this.factory = checkNotNull(factory, "factory");
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
            throw new IllegalStateException(HttpPostStandardRequestDecoder.class.getSimpleName()
                    + " was destroyed already");
        }
    }

    /**
     * True if this request is a Multipart request
     *
     * @return True if this request is a Multipart request
     */
    @Override
    public boolean isMultipart() {
        checkDestroyed();
        return false;
    }

    /**
     * Set the amount of bytes after which read bytes in the buffer should be discarded.
     * Setting this lower gives lower memory usage but with the overhead of more memory copies.
     * Use {@code 0} to disable it.
     */
    @Override
    public void setDiscardThreshold(int discardThreshold) {
        this.discardThreshold = checkPositiveOrZero(discardThreshold, "discardThreshold");
    }

    /**
     * Return the threshold in bytes after which read data in the buffer should be discarded.
     */
    @Override
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
     * @throws NotEnoughDataDecoderException
     *             Need more chunks
     */
    @Override
    public List<InterfaceHttpData> getBodyHttpDatas() {
        checkDestroyed();

        if (!isLastChunk) {
            throw new NotEnoughDataDecoderException();
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
     * @throws NotEnoughDataDecoderException
     *             need more chunks
     */
    @Override
    public List<InterfaceHttpData> getBodyHttpDatas(String name) {
        checkDestroyed();

        if (!isLastChunk) {
            throw new NotEnoughDataDecoderException();
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
     * @throws NotEnoughDataDecoderException
     *             need more chunks
     */
    @Override
    public InterfaceHttpData getBodyHttpData(String name) {
        checkDestroyed();

        if (!isLastChunk) {
            throw new NotEnoughDataDecoderException();
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
     * @throws ErrorDataDecoderException
     *             if there is a problem with the charset decoding or other
     *             errors
     */
    @Override
    public HttpPostStandardRequestDecoder offer(HttpContent content) {
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
     * @throws EndOfDataDecoderException
     *             No more data will be available
     */
    @Override
    public boolean hasNext() {
        checkDestroyed();

        if (currentStatus == MultiPartStatus.EPILOGUE) {
            // OK except if end of list
            if (bodyListHttpDataRank >= bodyListHttpData.size()) {
                throw new EndOfDataDecoderException();
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
     * @throws EndOfDataDecoderException
     *             No more data will be available
     */
    @Override
    public InterfaceHttpData next() {
        checkDestroyed();

        if (hasNext()) {
            return bodyListHttpData.get(bodyListHttpDataRank++);
        }
        return null;
    }

    @Override
    public InterfaceHttpData currentPartialHttpData() {
        return currentAttribute;
    }

    /**
     * This getMethod will parse as much as possible data and fill the list and map
     *
     * @throws ErrorDataDecoderException
     *             if there is a problem with the charset decoding or other
     *             errors
     */
    private void parseBody() {
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
     * @throws ErrorDataDecoderException
     *             if there is a problem with the charset decoding or other
     *             errors
     */
    private void parseBodyAttributesStandard() {
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
                                throw new ErrorDataDecoderException("Bad end of line");
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
            } else if (contRead && currentAttribute != null && currentStatus == MultiPartStatus.FIELD) {
                // reset index except if to continue in case of FIELD getStatus
                currentAttribute.addContent(undecodedChunk.copy(firstpos, currentpos - firstpos),
                                            false);
                firstpos = currentpos;
            }
            undecodedChunk.readerIndex(firstpos);
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
     * This getMethod fill the map and list with as much Attribute as possible from
     * Body in not Multipart mode.
     *
     * @throws ErrorDataDecoderException
     *             if there is a problem with the charset decoding or other
     *             errors
     */
    private void parseBodyAttributes() {
        if (!undecodedChunk.hasArray()) {
            parseBodyAttributesStandard();
            return;
        }
        SeekAheadOptimize sao = new SeekAheadOptimize(undecodedChunk);
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
                                throw new ErrorDataDecoderException("Bad end of line");
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
            } else if (contRead && currentAttribute != null && currentStatus == MultiPartStatus.FIELD) {
                // reset index except if to continue in case of FIELD getStatus
                currentAttribute.addContent(undecodedChunk.copy(firstpos, currentpos - firstpos),
                                            false);
                firstpos = currentpos;
            }
            undecodedChunk.readerIndex(firstpos);
        } catch (ErrorDataDecoderException e) {
            // error while decoding
            undecodedChunk.readerIndex(firstpos);
            throw e;
        } catch (IOException e) {
            // error while decoding
            undecodedChunk.readerIndex(firstpos);
            throw new ErrorDataDecoderException(e);
        } catch (IllegalArgumentException e) {
            // error while decoding
            undecodedChunk.readerIndex(firstpos);
            throw new ErrorDataDecoderException(e);
        }
    }

    private void setFinalBuffer(ByteBuf buffer) throws IOException {
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
    private static String decodeAttribute(String s, Charset charset) {
        try {
            return QueryStringDecoder.decodeComponent(s, charset);
        } catch (IllegalArgumentException e) {
            throw new ErrorDataDecoderException("Bad string: '" + s + '\'', e);
        }
    }

    /**
     * Destroy the {@link HttpPostStandardRequestDecoder} and release all it resources. After this method
     * was called it is not possible to operate on it anymore.
     */
    @Override
    public void destroy() {
        // Release all data items, including those not yet pulled
        cleanFiles();

        destroyed = true;

        if (undecodedChunk != null && undecodedChunk.refCnt() > 0) {
            undecodedChunk.release();
            undecodedChunk = null;
        }
    }

    /**
     * Clean all {@link HttpData}s for the current request.
     */
    @Override
    public void cleanFiles() {
        checkDestroyed();

        factory.cleanRequestHttpData(request);
    }

    /**
     * Remove the given FileUpload from the list of FileUploads to clean
     */
    @Override
    public void removeHttpDataFromClean(InterfaceHttpData data) {
        checkDestroyed();

        factory.removeHttpDataFromClean(request, data);
    }
}
