/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.stream.ChunkedInput;

/**
 * This encoder will help to encode Request for a FORM as POST.
 * @author frederic bregier
 *
 */
public class HttpPostRequestEncoder implements ChunkedInput {
    /**
     * Factory used to create InterfaceHttpData
     */
    private final HttpDataFactory factory;

    /**
     * Request to encode
     */
    private final HttpRequest request;

    /**
     * Default charset to use
     */
    private final String charset;

    /**
     * Chunked false by default
     */
    private boolean isChunked = false;

    /**
     * InterfaceHttpData for Body (without encoding)
     */
    private List<InterfaceHttpData> bodyListDatas = null;
    /**
     * The final Multipart List of InterfaceHttpData including encoding
     */
    private List<InterfaceHttpData> multipartHttpDatas = null;

    /**
     * Does this request is a Multipart request
     */
    private final boolean isMultipart;

    /**
     * If multipart, this is the boundary for the flobal multipart
     */
    private String multipartDataBoundary = null;

    /**
     * If multipart, there could be internal multiparts (mixed) to the global multipart.
     * Only one level is allowed.
     */
    private String multipartMixedBoundary = null;
    /**
     * To check if the header has been finalized
     */
    private boolean headerFinalized = false;

    /**
    *
    * @param request the request to encode
    * @param multipart True if the FORM is a ENCTYPE="multipart/form-data"
    * @throws NullPointerException for request
    * @throws ErrorDataEncoderException if the request is not a POST
    */
    public HttpPostRequestEncoder(HttpRequest request, boolean multipart)
            throws ErrorDataEncoderException, NullPointerException {
        this(new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE),
                request, multipart, HttpCodecUtil.DEFAULT_CHARSET);
    }

    /**
     *
     * @param factory the factory used to create InterfaceHttpData
     * @param request the request to encode
     * @param multipart True if the FORM is a ENCTYPE="multipart/form-data"
     * @throws NullPointerException for request and factory
     * @throws ErrorDataEncoderException if the request is not a POST
     */
    public HttpPostRequestEncoder(HttpDataFactory factory, HttpRequest request, boolean multipart)
            throws ErrorDataEncoderException, NullPointerException {
        this(factory, request, multipart, HttpCodecUtil.DEFAULT_CHARSET);
    }

    /**
     *
     * @param factory the factory used to create InterfaceHttpData
     * @param request the request to encode
     * @param multipart True if the FORM is a ENCTYPE="multipart/form-data"
     * @param charset the charset to use as default
     * @throws NullPointerException for request or charset or factory
     * @throws ErrorDataEncoderException if the request is not a POST
     */
    public HttpPostRequestEncoder(HttpDataFactory factory, HttpRequest request,
            boolean multipart, String charset) throws ErrorDataEncoderException,
            NullPointerException {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        if (request == null) {
            throw new NullPointerException("request");
        }
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        if (request.getMethod() != HttpMethod.POST) {
            throw new ErrorDataEncoderException("Cannot create a Encoder if not a POST");
        }
        this.request = request;
        this.charset = charset;
        this.factory = factory;
        // Fill default values
        bodyListDatas = new ArrayList<InterfaceHttpData>();
        // default mode
        isLastChunk = false;
        isLastChunkSent = false;
        isMultipart = multipart;
        multipartHttpDatas = new ArrayList<InterfaceHttpData>();
        if (isMultipart) {
            initDataMultipart();
        }
    }

    /**
     * Clean all HttpDatas (on Disk).
     *
     */
    public void cleanFiles() {
        factory.cleanAllHttpDatas();
    }

    /**
     * Does the last non empty chunk already encoded so that next chunk will be empty (last chunk)
     */
    private boolean isLastChunk = false;
    /**
     * Last chunk already sent
     */
    private boolean isLastChunkSent = false;
    /**
     * The current FileUpload that is currently in encode process
     */
    private FileUpload currentFileUpload = null;
    /**
     * While adding a FileUpload, is the multipart currently in Mixed Mode
     */
    private boolean duringMixedMode = false;

    /**
     * Global Body size
     */
    private long globalBodySize = 0;

    /**
     * True if this request is a Multipart request
     * @return True if this request is a Multipart request
     */
    public boolean isMultipart() {
        return isMultipart;
    }

    /**
     * Init the delimiter for Global Part (Data).
     * @param delimiter (may be null so computed)
     */
    private void initDataMultipart() {
        multipartDataBoundary = getNewMultipartDelimiter();
    }

    /**
     * Init the delimiter for Mixed Part (Mixed).
     * @param delimiter (may be null so computed)
     */
    private void initMixedMultipart() {
        multipartMixedBoundary = getNewMultipartDelimiter();
    }

    /**
     *
     * @return a newly generated Delimiter (either for DATA or MIXED)
     */
    private String getNewMultipartDelimiter() {
        // construct a generated delimiter
        Random random = new Random();
        return Long.toHexString(random.nextLong()).toLowerCase();
    }

    /**
     * This method returns a List of all InterfaceHttpData from body part.<br>

     * @return the list of InterfaceHttpData from Body part
     */
    public List<InterfaceHttpData> getBodyListAttributes() {
        return bodyListDatas;
    }

    /**
     * Set the Body HttpDatas list
     * @param datas
     * @throws NullPointerException for datas
     * @throws ErrorDataEncoderException if the encoding is in error or if the finalize were already done
     */
    public void setBodyHttpDatas(List<InterfaceHttpData> datas)
            throws NullPointerException, ErrorDataEncoderException {
        if (datas == null) {
            throw new NullPointerException("datas");
        }
        globalBodySize = 0;
        bodyListDatas.clear();
        currentFileUpload = null;
        duringMixedMode = false;
        multipartHttpDatas.clear();
        for (InterfaceHttpData data: datas) {
            addBodyHttpData(data);
        }
    }

    /**
     * Add a simple attribute in the body as Name=Value
     * @param name name of the parameter
     * @param value the value of the parameter
     * @throws NullPointerException for name
     * @throws ErrorDataEncoderException if the encoding is in error or if the finalize were already done
     */
    public void addBodyAttribute(String name, String value)
    throws NullPointerException, ErrorDataEncoderException {
        if (name == null) {
            throw new NullPointerException("name");
        }
        String svalue = value;
        if (value == null) {
            svalue = "";
        }
        Attribute data = factory.createAttribute(name, svalue);
        addBodyHttpData(data);
    }

    /**
     * Add a file as a FileUpload
     * @param name the name of the parameter
     * @param file the file to be uploaded (if not Multipart mode, only the filename will be included)
     * @param contentType the associated contentType for the File
     * @param isText True if this file should be transmitted in Text format (else binary)
     * @throws NullPointerException for name and file
     * @throws ErrorDataEncoderException if the encoding is in error or if the finalize were already done
     */
    public void addBodyFileUpload(String name, File file, String contentType, boolean isText)
    throws NullPointerException, ErrorDataEncoderException {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (file == null) {
            throw new NullPointerException("file");
        }
        String scontentType = contentType;
        String contentTransferEncoding = null;
        if (contentType == null) {
            if (isText) {
                scontentType = HttpPostBodyUtil.DEFAULT_TEXT_CONTENT_TYPE;
            } else {
                scontentType = HttpPostBodyUtil.DEFAULT_BINARY_CONTENT_TYPE;
            }
        }
        if (!isText) {
            contentTransferEncoding = HttpPostBodyUtil.TransferEncodingMechanism.BINARY.value;
        }
        FileUpload fileUpload = factory.createFileUpload(name, file.getName(),
                scontentType, contentTransferEncoding, name, file.length());
        try {
            fileUpload.setContent(file);
        } catch (IOException e) {
            throw new ErrorDataEncoderException(e);
        }
        addBodyHttpData(fileUpload);
    }

    /**
     * Add a series of Files associated with one File parameter (implied Mixed mode in Multipart)
     * @param name the name of the parameter
     * @param file the array of files
     * @param contentType the array of content Types associated with each file
     * @param isText the array of isText attribute (False meaning binary mode) for each file
     * @throws NullPointerException also throws if array have different sizes
     * @throws ErrorDataEncoderException if the encoding is in error or if the finalize were already done
     */
    public void addBodyFileUploads(String name, File[] file, String[] contentType, boolean[] isText)
    throws NullPointerException, ErrorDataEncoderException {
        if (file.length != contentType.length && file.length != isText.length) {
            throw new NullPointerException("Different array length");
        }
        for (int i = 0; i < file.length; i++) {
            addBodyFileUpload(name, file[i], contentType[i], isText[i]);
        }
    }

    /**
     * Add the InterfaceHttpData to the Body list
     * @param data
     * @throws NullPointerException for data
     * @throws ErrorDataEncoderException if the encoding is in error or if the finalize were already done
     */
    public void addBodyHttpData(InterfaceHttpData data)
    throws NullPointerException, ErrorDataEncoderException {
        if (headerFinalized) {
            throw new ErrorDataEncoderException("Cannot add value once finalized");
        }
        if (data == null) {
            throw new NullPointerException("data");
        }
        bodyListDatas.add(data);
        if (! isMultipart) {
            if (data instanceof Attribute) {
                Attribute attribute = (Attribute) data;
                try {
                    // name=value& with encoded name and attribute
                    String key = encodeAttribute(attribute.getName(), charset);
                    String value = encodeAttribute(attribute.getValue(), charset);
                    Attribute newattribute = factory.createAttribute(key, value);
                    multipartHttpDatas.add(newattribute);
                    globalBodySize += newattribute.getName().length()+1+
                        newattribute.length()+1;
                } catch (IOException e) {
                    throw new ErrorDataEncoderException(e);
                }
            } else if (data instanceof FileUpload){
                // since not Multipart, only name=filename => Attribute
                FileUpload fileUpload = (FileUpload) data;
                // name=filename& with encoded name and filename
                String key = encodeAttribute(fileUpload.getName(), charset);
                String value = encodeAttribute(fileUpload.getFilename(), charset);
                Attribute newattribute = factory.createAttribute(key, value);
                multipartHttpDatas.add(newattribute);
                globalBodySize += newattribute.getName().length()+1+
                    newattribute.length()+1;
            }
            return;
        }
        /*
         * Logic:
         * if not Attribute:
         *      add Data to body list
         *      if (duringMixedMode)
         *          add endmixedmultipart delimiter
         *          currentFileUpload = null
         *          duringMixedMode = false;
         *      add multipart delimiter, multipart body header and Data to multipart list
         *      reset currentFileUpload, duringMixedMode
         * if FileUpload: take care of multiple file for one field => mixed mode
         *      if (duringMixeMode)
         *          if (currentFileUpload.name == data.name)
         *              add mixedmultipart delimiter, mixedmultipart body header and Data to multipart list
         *          else
         *              add endmixedmultipart delimiter, multipart body header and Data to multipart list
         *              currentFileUpload = data
         *              duringMixedMode = false;
         *      else
         *          if (currentFileUpload.name == data.name)
         *              change multipart body header of previous file into multipart list to
         *                      mixedmultipart start, mixedmultipart body header
         *              add mixedmultipart delimiter, mixedmultipart body header and Data to multipart list
         *              duringMixedMode = true
         *          else
         *              add multipart delimiter, multipart body header and Data to multipart list
         *              currentFileUpload = data
         *              duringMixedMode = false;
         * Do not add last delimiter! Could be:
         * if duringmixedmode: endmixedmultipart + endmultipart
         * else only endmultipart
         */
        if (data instanceof Attribute) {
            if (duringMixedMode) {
                InternalAttribute internal = new InternalAttribute();
                internal.addValue("\r\n--"+multipartMixedBoundary+"--");
                multipartHttpDatas.add(internal);
                multipartMixedBoundary = null;
                currentFileUpload = null;
                duringMixedMode = false;
            }
            InternalAttribute internal = new InternalAttribute();
            if (multipartHttpDatas.size() > 0) {
                // previously a data field so CRLF
                internal.addValue("\r\n");
            }
            internal.addValue("--"+multipartDataBoundary+"\r\n");
            // content-disposition: form-data; name="field1"
            Attribute attribute = (Attribute) data;
            internal.addValue(HttpPostBodyUtil.CONTENT_DISPOSITION+": "+
                    HttpPostBodyUtil.FORM_DATA+"; "+
                    HttpPostBodyUtil.NAME+"=\""+
                    encodeAttribute(attribute.getName(), charset)+"\"\r\n");
            String localcharset = attribute.getCharset();
            if (localcharset != null) {
                // Content-Type: charset=charset
                internal.addValue(HttpHeaders.Names.CONTENT_TYPE+": "+
                        HttpHeaders.Values.CHARSET+"="+localcharset+"\r\n");
            }
            // CRLF between body header and data
            internal.addValue("\r\n");
            multipartHttpDatas.add(internal);
            multipartHttpDatas.add(data);
            globalBodySize += attribute.length()+internal.size();
        } else if (data instanceof FileUpload) {
            FileUpload fileUpload = (FileUpload) data;
            InternalAttribute internal = new InternalAttribute();
            if (multipartHttpDatas.size() > 0) {
                // previously a data field so CRLF
                internal.addValue("\r\n");
            }
            boolean localMixed = false;
            if (duringMixedMode) {
                if (currentFileUpload != null &&
                        currentFileUpload.getName().equals(fileUpload.getName())) {
                    // continue a mixed mode

                    localMixed = true;
                } else {
                    // end a mixed mode

                    // add endmixedmultipart delimiter, multipart body header and
                    // Data to multipart list
                    internal.addValue("--"+multipartMixedBoundary+"--");
                    multipartHttpDatas.add(internal);
                    multipartMixedBoundary = null;
                    // start a new one (could be replaced if mixed start again from here
                    internal = new InternalAttribute();
                    internal.addValue("\r\n");
                    localMixed = false;
                    // new currentFileUpload and no more in Mixed mode
                    currentFileUpload = fileUpload;
                    duringMixedMode = false;
                }
            } else {
                if (currentFileUpload != null &&
                        currentFileUpload.getName().equals(fileUpload.getName())) {
                    // create a new mixed mode (from previous file)

                    // change multipart body header of previous file into multipart list to
                    // mixedmultipart start, mixedmultipart body header

                    // change Internal (size()-2 position in multipartHttpDatas)
                    // from (line starting with *)
                    // --AaB03x
                    // * Content-Disposition: form-data; name="files"; filename="file1.txt"
                    // Content-Type: text/plain
                    // to (lines starting with *)
                    // --AaB03x
                    // * Content-Disposition: form-data; name="files"
                    // * Content-Type: multipart/mixed; boundary=BbC04y
                    // *
                    // * --BbC04y
                    // * Content-Disposition: file; filename="file1.txt"
                    // Content-Type: text/plain
                    initMixedMultipart();
                    InternalAttribute pastAttribute =
                        (InternalAttribute) multipartHttpDatas.get(multipartHttpDatas.size()-2);
                    // remove past size
                    globalBodySize -= pastAttribute.size();
                    String replacement = HttpPostBodyUtil.CONTENT_DISPOSITION+": "+
                        HttpPostBodyUtil.FORM_DATA+"; "+HttpPostBodyUtil.NAME+"=\""+
                        encodeAttribute(fileUpload.getName(), charset)+"\"\r\n";
                    replacement += HttpHeaders.Names.CONTENT_TYPE+": "+
                        HttpPostBodyUtil.MULTIPART_MIXED+"; "+HttpHeaders.Values.BOUNDARY+
                        "="+multipartMixedBoundary+"\r\n\r\n";
                    replacement += "--"+multipartMixedBoundary+"\r\n";
                    replacement += HttpPostBodyUtil.CONTENT_DISPOSITION+": "+
                        HttpPostBodyUtil.FILE+"; "+HttpPostBodyUtil.FILENAME+"=\""+
                        encodeAttribute(fileUpload.getFilename(), charset)+
                        "\"\r\n";
                    pastAttribute.setValue(replacement, 1);
                    // update past size
                    globalBodySize += pastAttribute.size();

                    // now continue
                    // add mixedmultipart delimiter, mixedmultipart body header and
                    // Data to multipart list
                    localMixed = true;
                    duringMixedMode = true;
                } else {
                    // a simple new multipart
                    //add multipart delimiter, multipart body header and Data to multipart list
                    localMixed = false;
                    currentFileUpload = fileUpload;
                    duringMixedMode = false;
                }
            }

            if (localMixed) {
                // add mixedmultipart delimiter, mixedmultipart body header and
                // Data to multipart list
                internal.addValue("--"+multipartMixedBoundary+"\r\n");
                // Content-Disposition: file; filename="file1.txt"
                internal.addValue(HttpPostBodyUtil.CONTENT_DISPOSITION+": "+
                        HttpPostBodyUtil.FILE+"; "+HttpPostBodyUtil.FILENAME+"=\""+
                        encodeAttribute(fileUpload.getFilename(), charset)+
                        "\"\r\n");

            } else {
                internal.addValue("--"+multipartDataBoundary+"\r\n");
                // Content-Disposition: form-data; name="files"; filename="file1.txt"
                internal.addValue(HttpPostBodyUtil.CONTENT_DISPOSITION+": "+
                        HttpPostBodyUtil.FORM_DATA+"; "+HttpPostBodyUtil.NAME+"=\""+
                        encodeAttribute(fileUpload.getName(), charset)+"\"; "+
                        HttpPostBodyUtil.FILENAME+"=\""+
                        encodeAttribute(fileUpload.getFilename(), charset)+
                        "\"\r\n");
            }
            // Content-Type: image/gif
            // Content-Type: text/plain; charset=ISO-8859-1
            // Content-Transfer-Encoding: binary
            internal.addValue(HttpHeaders.Names.CONTENT_TYPE+": "+
                    fileUpload.getContentType());
            String contentTransferEncoding = fileUpload.getContentTransferEncoding();
            if (contentTransferEncoding != null &&
                    contentTransferEncoding.equals(
                            HttpPostBodyUtil.TransferEncodingMechanism.BINARY.value)) {
                internal.addValue("\r\n"+HttpHeaders.Names.CONTENT_TRANSFER_ENCODING+
                        ": "+HttpPostBodyUtil.TransferEncodingMechanism.BINARY.value+
                        "\r\n\r\n");
            } else if (fileUpload.getCharset() != null) {
                internal.addValue("; "+HttpHeaders.Values.CHARSET+"="+
                        fileUpload.getCharset()+"\r\n\r\n");
            } else {
                internal.addValue("\r\n\r\n");
            }
            multipartHttpDatas.add(internal);
            multipartHttpDatas.add(data);
            globalBodySize += fileUpload.length()+internal.size();
        }
    }

    /**
     * Iterator to be used when encoding will be called chunk after chunk
     */
    private ListIterator<InterfaceHttpData> iterator = null;

    /**
     * Finalize the request by preparing the Header in the request and
     * returns the request ready to be sent.<br>
     * Once finalized, no data must be added.<br>
     * If the request does not need chunk (isChunked() == false),
     * this request is the only object to send to
     * the remote server.
     *
     * @return the request object (chunked or not according to size of body)
     * @throws ErrorDataEncoderException if the encoding is in error or if the finalize were already done
     */
    public HttpRequest finalizeRequest() throws ErrorDataEncoderException {
        // Finalize the multipartHttpDatas
        if (! headerFinalized) {
            if (isMultipart) {
                InternalAttribute internal = new InternalAttribute();
                if (duringMixedMode) {
                    internal.addValue("\r\n--"+multipartMixedBoundary+"--");
                }
                internal.addValue("\r\n--"+multipartDataBoundary+"--\r\n");
                multipartHttpDatas.add(internal);
                multipartMixedBoundary = null;
                currentFileUpload = null;
                duringMixedMode = false;
                globalBodySize += internal.size();
            }
            headerFinalized = true;
        } else {
            throw new ErrorDataEncoderException("Header already encoded");
        }
        List<String> contentTypes = request.getHeaders(HttpHeaders.Names.CONTENT_TYPE);
        List<String> transferEncoding =
            request.getHeaders(HttpHeaders.Names.TRANSFER_ENCODING);
        if (contentTypes != null) {
            request.removeHeader(HttpHeaders.Names.CONTENT_TYPE);
            for (String contentType: contentTypes) {
                // "multipart/form-data; boundary=--89421926422648"
                if (contentType.toLowerCase().startsWith(
                        HttpHeaders.Values.MULTIPART_FORM_DATA)) {
                    // ignore
                } else if (contentType.toLowerCase().startsWith(
                        HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED)){
                    // ignore
                } else {
                    request.addHeader(HttpHeaders.Names.CONTENT_TYPE, contentType);
                }
            }
        }
        if (isMultipart) {
            String value = HttpHeaders.Values.MULTIPART_FORM_DATA + "; " +
                HttpHeaders.Values.BOUNDARY + "=" + multipartDataBoundary;
            request.addHeader(HttpHeaders.Names.CONTENT_TYPE, value);
        } else {
            // Not multipart
            request.addHeader(HttpHeaders.Names.CONTENT_TYPE,
                    HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);
        }
        // Now consider size for chunk or not
        long realSize = globalBodySize;
        if (isMultipart) {
            iterator = multipartHttpDatas.listIterator();
        } else {
            realSize -= 1; // last '&' removed
            iterator = multipartHttpDatas.listIterator();
        }
        request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, String
                .valueOf(realSize));
        if (realSize > HttpPostBodyUtil.chunkSize) {
            isChunked = true;
            if (transferEncoding != null) {
                request.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
                for (String v: transferEncoding) {
                    if (v.equalsIgnoreCase(HttpHeaders.Values.CHUNKED)) {
                        // ignore
                    } else {
                        request.addHeader(HttpHeaders.Names.TRANSFER_ENCODING, v);
                    }
                }
            }
            request.addHeader(HttpHeaders.Names.TRANSFER_ENCODING,
                    HttpHeaders.Values.CHUNKED);
            request.setContent(ChannelBuffers.EMPTY_BUFFER);
        } else {
            // get the only one body and set it to the request
            HttpChunk chunk = nextChunk();
            request.setContent(chunk.getContent());
        }
        return request;
    }

    /**
     * @return True if the request is by Chunk
     */
    public boolean isChunked() {
        return isChunked;
    }

    /**
     * Encode one attribute
     * @param s
     * @param charset
     * @return the encoded attribute
     * @throws ErrorDataEncoderException if the encoding is in error
     */
    private static String encodeAttribute(String s, String charset)
            throws ErrorDataEncoderException {
        if (s == null) {
            return "";
        }
        try {
            return URLEncoder.encode(s, charset);
        } catch (UnsupportedEncodingException e) {
            throw new ErrorDataEncoderException(charset, e);
        }
    }

    /**
     * The ChannelBuffer currently used by the encoder
     */
    private ChannelBuffer currentBuffer = null;
    /**
     * The current InterfaceHttpData to encode (used if more chunks are available)
     */
    private InterfaceHttpData currentData = null;
    /**
     * If not multipart, does the currentBuffer stands for the Key or for the Value
     */
    private boolean isKey = true;

    /**
     *
     * @return the next ChannelBuffer to send as a HttpChunk and modifying currentBuffer
     * accordingly
     */
    private ChannelBuffer fillChannelBuffer() {
        int length = currentBuffer.readableBytes();
        if (length > HttpPostBodyUtil.chunkSize) {
            ChannelBuffer slice =
                currentBuffer.slice(currentBuffer.readerIndex(), HttpPostBodyUtil.chunkSize);
            currentBuffer.skipBytes(HttpPostBodyUtil.chunkSize);
            return slice;
        } else {
            // to continue
            ChannelBuffer slice = currentBuffer;
            currentBuffer = null;
            return slice;
        }
    }

    /**
     * From the current context (currentBuffer and currentData), returns the next HttpChunk
     * (if possible) trying to get sizeleft bytes more into the currentBuffer.
     * This is the Multipart version.
     *
     * @param sizeleft the number of bytes to try to get from currentData
     * @return the next HttpChunk or null if not enough bytes were found
     * @throws ErrorDataEncoderException if the encoding is in error
     */
    private HttpChunk encodeNextChunkMultipart(int sizeleft) throws ErrorDataEncoderException {
        if (currentData == null) {
            return null;
        }
        ChannelBuffer buffer;
        if (currentData instanceof InternalAttribute) {
            String internal = ((InternalAttribute) currentData).toString();
            byte[] bytes;
            try {
                bytes = internal.getBytes("ASCII");
            } catch (UnsupportedEncodingException e) {
                throw new ErrorDataEncoderException(e);
            }
            buffer = ChannelBuffers.wrappedBuffer(bytes);
            currentData = null;
        } else {
            if (currentData instanceof Attribute) {
                try {
                    buffer = ((Attribute) currentData).getChunk(sizeleft);
                } catch (IOException e) {
                    throw new ErrorDataEncoderException(e);
                }
            } else {
                try {
                    buffer = ((FileUpload) currentData).getChunk(sizeleft);
                } catch (IOException e) {
                    throw new ErrorDataEncoderException(e);
                }
            }
            if (buffer.capacity() == 0) {
                // end for current InterfaceHttpData, need more data
                currentData = null;
                return null;
            }
        }
        if (currentBuffer == null) {
            currentBuffer = buffer;
        } else {
            currentBuffer = ChannelBuffers.wrappedBuffer(currentBuffer,
                buffer);
        }
        if (currentBuffer.readableBytes() < HttpPostBodyUtil.chunkSize) {
            currentData = null;
            return null;
        }
        buffer = fillChannelBuffer();
        return new DefaultHttpChunk(buffer);
    }

    /**
     * From the current context (currentBuffer and currentData), returns the next HttpChunk
     * (if possible) trying to get sizeleft bytes more into the currentBuffer.
     * This is the UrlEncoded version.
     *
     * @param sizeleft the number of bytes to try to get from currentData
     * @return the next HttpChunk or null if not enough bytes were found
     * @throws ErrorDataEncoderException if the encoding is in error
     */
    private HttpChunk encodeNextChunkUrlEncoded(int sizeleft) throws ErrorDataEncoderException {
        if (currentData == null) {
            return null;
        }
        int size = sizeleft;
        ChannelBuffer buffer;
        if (isKey) {
            // get name
            String key = currentData.getName();
            buffer = ChannelBuffers.wrappedBuffer(key.getBytes());
            isKey = false;
            if (currentBuffer == null) {
                currentBuffer = ChannelBuffers.wrappedBuffer(
                        buffer, ChannelBuffers.wrappedBuffer("=".getBytes()));
                //continue
                size -= (buffer.readableBytes()+1);
            } else {
                currentBuffer = ChannelBuffers.wrappedBuffer(currentBuffer,
                    buffer, ChannelBuffers.wrappedBuffer("=".getBytes()));
                //continue
                size -= (buffer.readableBytes()+1);
            }
            if (currentBuffer.readableBytes() >= HttpPostBodyUtil.chunkSize) {
                buffer = fillChannelBuffer();
                return new DefaultHttpChunk(buffer);
            }
        }
        try {
            buffer = ((Attribute) currentData).getChunk(size);
        } catch (IOException e) {
            throw new ErrorDataEncoderException(e);
        }
        ChannelBuffer delimiter = null;
        if (buffer.readableBytes() < size) {
            // delimiter
            isKey = true;
            delimiter = (iterator.hasNext()) ?
                    ChannelBuffers.wrappedBuffer("&".getBytes()) :
                        null;
        }
        if (buffer.capacity() == 0) {
            // end for current InterfaceHttpData, need potentially more data
            currentData = null;
            if (currentBuffer == null) {
                currentBuffer = delimiter;
            } else {
                if (delimiter != null) {
                    currentBuffer = ChannelBuffers.wrappedBuffer(currentBuffer,
                        delimiter);
                }
            }
            if (currentBuffer.readableBytes() >= HttpPostBodyUtil.chunkSize) {
                buffer = fillChannelBuffer();
                return new DefaultHttpChunk(buffer);
            }
            return null;
        }
        if (currentBuffer == null) {
            if (delimiter != null) {
                currentBuffer = ChannelBuffers.wrappedBuffer(buffer,
                    delimiter);
            } else {
                currentBuffer = buffer;
            }
        } else {
            if (delimiter != null) {
                currentBuffer = ChannelBuffers.wrappedBuffer(currentBuffer,
                    buffer, delimiter);
            } else {
                currentBuffer = ChannelBuffers.wrappedBuffer(currentBuffer,
                        buffer);
            }
        }
        if (currentBuffer.readableBytes() < HttpPostBodyUtil.chunkSize) {
            // end for current InterfaceHttpData, need more data
            currentData = null;
            isKey = true;
            return null;
        }
        buffer = fillChannelBuffer();
        // size = 0
        return new DefaultHttpChunk(buffer);
    }

    public void close() throws Exception {
        //NO since the user can want to reuse (broadcast for instance) cleanFiles();
    }

    public boolean hasNextChunk() throws Exception {
        return (!isLastChunkSent);
    }

    /**
     * Returns the next available HttpChunk. The caller is responsible to test if this chunk is the
     * last one (isLast()), in order to stop calling this method.
     *
     * @return the next available HttpChunk
     * @throws ErrorDataEncoderException if the encoding is in error
     */
    public HttpChunk nextChunk() throws ErrorDataEncoderException {
        if (isLastChunk) {
            isLastChunkSent = true;
            return new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER);
        }
        ChannelBuffer buffer = null;
        int size = HttpPostBodyUtil.chunkSize;
        // first test if previous buffer is not empty
        if (currentBuffer != null) {
            size -= currentBuffer.readableBytes();
        }
        if (size <= 0) {
            //NextChunk from buffer
            buffer = fillChannelBuffer();
            return new DefaultHttpChunk(buffer);
        }
        // size > 0
        if (currentData != null) {
            // continue to read data
            if (isMultipart) {
                HttpChunk chunk = encodeNextChunkMultipart(size);
                if (chunk != null) {
                    return chunk;
                }
            } else {
                HttpChunk chunk = encodeNextChunkUrlEncoded(size);
                if (chunk != null) {
                    //NextChunk Url from currentData
                    return chunk;
                }
            }
            size = HttpPostBodyUtil.chunkSize - currentBuffer.readableBytes();
        }
        if (! iterator.hasNext()) {
            isLastChunk = true;
            //NextChunk as last non empty from buffer
            buffer = currentBuffer;
            currentBuffer = null;
            return new DefaultHttpChunk(buffer);
        }
        while (size > 0 && iterator.hasNext()) {
            currentData = iterator.next();
            HttpChunk chunk;
            if (isMultipart) {
                chunk = encodeNextChunkMultipart(size);
            } else {
                chunk = encodeNextChunkUrlEncoded(size);
            }
            if (chunk == null) {
                // not enough
                size = HttpPostBodyUtil.chunkSize - currentBuffer.readableBytes();
                continue;
            }
            //NextChunk from data
            return chunk;
        }
        // end since no more data
        isLastChunk = true;
        if (currentBuffer == null) {
            isLastChunkSent = true;
            //LastChunk with no more data
            return new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER);
        }
        //Previous LastChunk with no more data
        buffer = currentBuffer;
        currentBuffer = null;
        return new DefaultHttpChunk(buffer);
    }

    /**
     * Exception when an error occurs while encoding
     *
     * @author frederic bregier
     *
     */
    public static class ErrorDataEncoderException extends Exception {
        /**
         *
         */
        private static final long serialVersionUID = 5020247425493164465L;

        /**
         *
         */
        public ErrorDataEncoderException() {
            super();
        }

        /**
         * @param arg0
         */
        public ErrorDataEncoderException(String arg0) {
            super(arg0);
        }

        /**
         * @param arg0
         */
        public ErrorDataEncoderException(Throwable arg0) {
            super(arg0);
        }

        /**
         * @param arg0
         * @param arg1
         */
        public ErrorDataEncoderException(String arg0, Throwable arg1) {
            super(arg0, arg1);
        }
    }
}
