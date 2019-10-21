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
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedInput;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Pattern;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.util.AbstractMap.SimpleImmutableEntry;

/**
 * This encoder will help to encode Request for a FORM as POST.
 *
 * <P>According to RFC 7231, POST, PUT and OPTIONS allow to have a body.
 * This encoder will support widely all methods except TRACE since the RFC notes
 * for GET, DELETE, HEAD and CONNECT: (replaces XXX by one of these methods)</P>
 * <P>"A payload within a XXX request message has no defined semantics;
 * sending a payload body on a XXX request might cause some existing
 * implementations to reject the request."</P>
 * <P>On the contrary, for TRACE method, RFC says:</P>
 * <P>"A client MUST NOT send a message body in a TRACE request."</P>
 */
public class HttpPostRequestEncoder implements ChunkedInput<HttpContent> {

    /**
     * Different modes to use to encode form data.
     */
    public enum EncoderMode {
        /**
         * Legacy mode which should work for most. It is known to not work with OAUTH. For OAUTH use
         * {@link EncoderMode#RFC3986}. The W3C form recommendations this for submitting post form data.
         */
        RFC1738,

        /**
         * Mode which is more new and is used for OAUTH
         */
        RFC3986,

        /**
         * The HTML5 spec disallows mixed mode in multipart/form-data
         * requests. More concretely this means that more files submitted
         * under the same name will not be encoded using mixed mode, but
         * will be treated as distinct fields.
         *
         * Reference:
         *   http://www.w3.org/TR/html5/forms.html#multipart-form-data
         */
        HTML5
    }

    @SuppressWarnings("rawtypes")
    private static final Map.Entry[] percentEncodings;

    static {
        percentEncodings = new Map.Entry[] {
                new SimpleImmutableEntry<Pattern, String>(Pattern.compile("\\*"), "%2A"),
                new SimpleImmutableEntry<Pattern, String>(Pattern.compile("\\+"), "%20"),
                new SimpleImmutableEntry<Pattern, String>(Pattern.compile("~"), "%7E")
        };
    }

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
    private final Charset charset;

    /**
     * Chunked false by default
     */
    private boolean isChunked;

    /**
     * InterfaceHttpData for Body (without encoding)
     */
    private final List<InterfaceHttpData> bodyListDatas;
    /**
     * The final Multipart List of InterfaceHttpData including encoding
     */
    final List<InterfaceHttpData> multipartHttpDatas;

    /**
     * Does this request is a Multipart request
     */
    private final boolean isMultipart;

    /**
     * If multipart, this is the boundary for the flobal multipart
     */
    String multipartDataBoundary;

    /**
     * If multipart, there could be internal multiparts (mixed) to the global multipart. Only one level is allowed.
     */
    String multipartMixedBoundary;
    /**
     * To check if the header has been finalized
     */
    private boolean headerFinalized;

    private final EncoderMode encoderMode;

    /**
     *
     * @param request
     *            the request to encode
     * @param multipart
     *            True if the FORM is a ENCTYPE="multipart/form-data"
     * @throws NullPointerException
     *             for request
     * @throws ErrorDataEncoderException
     *             if the request is a TRACE
     */
    public HttpPostRequestEncoder(HttpRequest request, boolean multipart) throws ErrorDataEncoderException {
        this(new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE), request, multipart,
                HttpConstants.DEFAULT_CHARSET, EncoderMode.RFC1738);
    }

    /**
     *
     * @param factory
     *            the factory used to create InterfaceHttpData
     * @param request
     *            the request to encode
     * @param multipart
     *            True if the FORM is a ENCTYPE="multipart/form-data"
     * @throws NullPointerException
     *             for request and factory
     * @throws ErrorDataEncoderException
     *             if the request is a TRACE
     */
    public HttpPostRequestEncoder(HttpDataFactory factory, HttpRequest request, boolean multipart)
            throws ErrorDataEncoderException {
        this(factory, request, multipart, HttpConstants.DEFAULT_CHARSET, EncoderMode.RFC1738);
    }

    /**
     *
     * @param factory
     *            the factory used to create InterfaceHttpData
     * @param request
     *            the request to encode
     * @param multipart
     *            True if the FORM is a ENCTYPE="multipart/form-data"
     * @param charset
     *            the charset to use as default
     * @param encoderMode
     *            the mode for the encoder to use. See {@link EncoderMode} for the details.
     * @throws NullPointerException
     *             for request or charset or factory
     * @throws ErrorDataEncoderException
     *             if the request is a TRACE
     */
    public HttpPostRequestEncoder(
            HttpDataFactory factory, HttpRequest request, boolean multipart, Charset charset,
            EncoderMode encoderMode)
            throws ErrorDataEncoderException {
        this.request = checkNotNull(request, "request");
        this.charset = checkNotNull(charset, "charset");
        this.factory = checkNotNull(factory, "factory");
        if (HttpMethod.TRACE.equals(request.method())) {
            throw new ErrorDataEncoderException("Cannot create a Encoder if request is a TRACE");
        }
        // Fill default values
        bodyListDatas = new ArrayList<InterfaceHttpData>();
        // default mode
        isLastChunk = false;
        isLastChunkSent = false;
        isMultipart = multipart;
        multipartHttpDatas = new ArrayList<InterfaceHttpData>();
        this.encoderMode = encoderMode;
        if (isMultipart) {
            initDataMultipart();
        }
    }

    /**
     * Clean all HttpDatas (on Disk) for the current request.
     */
    public void cleanFiles() {
        factory.cleanRequestHttpData(request);
    }

    /**
     * Does the last non empty chunk already encoded so that next chunk will be empty (last chunk)
     */
    private boolean isLastChunk;
    /**
     * Last chunk already sent
     */
    private boolean isLastChunkSent;
    /**
     * The current FileUpload that is currently in encode process
     */
    private FileUpload currentFileUpload;
    /**
     * While adding a FileUpload, is the multipart currently in Mixed Mode
     */
    private boolean duringMixedMode;
    /**
     * Global Body size
     */
    private long globalBodySize;
    /**
     * Global Transfer progress
     */
    private long globalProgress;

    /**
     * True if this request is a Multipart request
     *
     * @return True if this request is a Multipart request
     */
    public boolean isMultipart() {
        return isMultipart;
    }

    /**
     * Init the delimiter for Global Part (Data).
     */
    private void initDataMultipart() {
        multipartDataBoundary = getNewMultipartDelimiter();
    }

    /**
     * Init the delimiter for Mixed Part (Mixed).
     */
    private void initMixedMultipart() {
        multipartMixedBoundary = getNewMultipartDelimiter();
    }

    /**
     *
     * @return a newly generated Delimiter (either for DATA or MIXED)
     */
    private static String getNewMultipartDelimiter() {
        // construct a generated delimiter
        return Long.toHexString(PlatformDependent.threadLocalRandom().nextLong());
    }

    /**
     * This getMethod returns a List of all InterfaceHttpData from body part.<br>

     * @return the list of InterfaceHttpData from Body part
     */
    public List<InterfaceHttpData> getBodyListAttributes() {
        return bodyListDatas;
    }

    /**
     * Set the Body HttpDatas list
     *
     * @throws NullPointerException
     *             for datas
     * @throws ErrorDataEncoderException
     *             if the encoding is in error or if the finalize were already done
     */
    public void setBodyHttpDatas(List<InterfaceHttpData> datas) throws ErrorDataEncoderException {
        if (datas == null) {
            throw new NullPointerException("datas");
        }
        globalBodySize = 0;
        bodyListDatas.clear();
        currentFileUpload = null;
        duringMixedMode = false;
        multipartHttpDatas.clear();
        for (InterfaceHttpData data : datas) {
            addBodyHttpData(data);
        }
    }

    /**
     * Add a simple attribute in the body as Name=Value
     *
     * @param name
     *            name of the parameter
     * @param value
     *            the value of the parameter
     * @throws NullPointerException
     *             for name
     * @throws ErrorDataEncoderException
     *             if the encoding is in error or if the finalize were already done
     */
    public void addBodyAttribute(String name, String value) throws ErrorDataEncoderException {
        String svalue = value != null? value : StringUtil.EMPTY_STRING;
        Attribute data = factory.createAttribute(request, checkNotNull(name, "name"), svalue);
        addBodyHttpData(data);
    }

    /**
     * Add a file as a FileUpload
     *
     * @param name
     *            the name of the parameter
     * @param file
     *            the file to be uploaded (if not Multipart mode, only the filename will be included)
     * @param contentType
     *            the associated contentType for the File
     * @param isText
     *            True if this file should be transmitted in Text format (else binary)
     * @throws NullPointerException
     *             for name and file
     * @throws ErrorDataEncoderException
     *             if the encoding is in error or if the finalize were already done
     */
    public void addBodyFileUpload(String name, File file, String contentType, boolean isText)
            throws ErrorDataEncoderException {
        addBodyFileUpload(name, file.getName(), file, contentType, isText);
    }

    /**
     * Add a file as a FileUpload
     *
     * @param name
     *            the name of the parameter
     * @param file
     *            the file to be uploaded (if not Multipart mode, only the filename will be included)
     * @param filename
     *            the filename to use for this File part, empty String will be ignored by
     *            the encoder
     * @param contentType
     *            the associated contentType for the File
     * @param isText
     *            True if this file should be transmitted in Text format (else binary)
     * @throws NullPointerException
     *             for name and file
     * @throws ErrorDataEncoderException
     *             if the encoding is in error or if the finalize were already done
     */
    public void addBodyFileUpload(String name, String filename, File file, String contentType, boolean isText)
            throws ErrorDataEncoderException {
        checkNotNull(name, "name");
        checkNotNull(file, "file");
        if (filename == null) {
            filename = StringUtil.EMPTY_STRING;
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
            contentTransferEncoding = HttpPostBodyUtil.TransferEncodingMechanism.BINARY.value();
        }
        FileUpload fileUpload = factory.createFileUpload(request, name, filename, scontentType,
                contentTransferEncoding, null, file.length());
        try {
            fileUpload.setContent(file);
        } catch (IOException e) {
            throw new ErrorDataEncoderException(e);
        }
        addBodyHttpData(fileUpload);
    }

    /**
     * Add a series of Files associated with one File parameter
     *
     * @param name
     *            the name of the parameter
     * @param file
     *            the array of files
     * @param contentType
     *            the array of content Types associated with each file
     * @param isText
     *            the array of isText attribute (False meaning binary mode) for each file
     * @throws IllegalArgumentException
     *             also throws if array have different sizes
     * @throws ErrorDataEncoderException
     *             if the encoding is in error or if the finalize were already done
     */
    public void addBodyFileUploads(String name, File[] file, String[] contentType, boolean[] isText)
            throws ErrorDataEncoderException {
        if (file.length != contentType.length && file.length != isText.length) {
            throw new IllegalArgumentException("Different array length");
        }
        for (int i = 0; i < file.length; i++) {
            addBodyFileUpload(name, file[i], contentType[i], isText[i]);
        }
    }

    /**
     * Add the InterfaceHttpData to the Body list
     *
     * @throws NullPointerException
     *             for data
     * @throws ErrorDataEncoderException
     *             if the encoding is in error or if the finalize were already done
     */
    public void addBodyHttpData(InterfaceHttpData data) throws ErrorDataEncoderException {
        if (headerFinalized) {
            throw new ErrorDataEncoderException("Cannot add value once finalized");
        }
        bodyListDatas.add(checkNotNull(data, "data"));
        if (!isMultipart) {
            if (data instanceof Attribute) {
                Attribute attribute = (Attribute) data;
                try {
                    // name=value& with encoded name and attribute
                    String key = encodeAttribute(attribute.getName(), charset);
                    String value = encodeAttribute(attribute.getValue(), charset);
                    Attribute newattribute = factory.createAttribute(request, key, value);
                    multipartHttpDatas.add(newattribute);
                    globalBodySize += newattribute.getName().length() + 1 + newattribute.length() + 1;
                } catch (IOException e) {
                    throw new ErrorDataEncoderException(e);
                }
            } else if (data instanceof FileUpload) {
                // since not Multipart, only name=filename => Attribute
                FileUpload fileUpload = (FileUpload) data;
                // name=filename& with encoded name and filename
                String key = encodeAttribute(fileUpload.getName(), charset);
                String value = encodeAttribute(fileUpload.getFilename(), charset);
                Attribute newattribute = factory.createAttribute(request, key, value);
                multipartHttpDatas.add(newattribute);
                globalBodySize += newattribute.getName().length() + 1 + newattribute.length() + 1;
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
         *      if (duringMixedMode)
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
                InternalAttribute internal = new InternalAttribute(charset);
                internal.addValue("\r\n--" + multipartMixedBoundary + "--");
                multipartHttpDatas.add(internal);
                multipartMixedBoundary = null;
                currentFileUpload = null;
                duringMixedMode = false;
            }
            InternalAttribute internal = new InternalAttribute(charset);
            if (!multipartHttpDatas.isEmpty()) {
                // previously a data field so CRLF
                internal.addValue("\r\n");
            }
            internal.addValue("--" + multipartDataBoundary + "\r\n");
            // content-disposition: form-data; name="field1"
            Attribute attribute = (Attribute) data;
            internal.addValue(HttpHeaderNames.CONTENT_DISPOSITION + ": " + HttpHeaderValues.FORM_DATA + "; "
                    + HttpHeaderValues.NAME + "=\"" + attribute.getName() + "\"\r\n");
            // Add Content-Length: xxx
            internal.addValue(HttpHeaderNames.CONTENT_LENGTH + ": " +
                    attribute.length() + "\r\n");
            Charset localcharset = attribute.getCharset();
            if (localcharset != null) {
                // Content-Type: text/plain; charset=charset
                internal.addValue(HttpHeaderNames.CONTENT_TYPE + ": " +
                        HttpPostBodyUtil.DEFAULT_TEXT_CONTENT_TYPE + "; " +
                        HttpHeaderValues.CHARSET + '='
                        + localcharset.name() + "\r\n");
            }
            // CRLF between body header and data
            internal.addValue("\r\n");
            multipartHttpDatas.add(internal);
            multipartHttpDatas.add(data);
            globalBodySize += attribute.length() + internal.size();
        } else if (data instanceof FileUpload) {
            FileUpload fileUpload = (FileUpload) data;
            InternalAttribute internal = new InternalAttribute(charset);
            if (!multipartHttpDatas.isEmpty()) {
                // previously a data field so CRLF
                internal.addValue("\r\n");
            }
            boolean localMixed;
            if (duringMixedMode) {
                if (currentFileUpload != null && currentFileUpload.getName().equals(fileUpload.getName())) {
                    // continue a mixed mode

                    localMixed = true;
                } else {
                    // end a mixed mode

                    // add endmixedmultipart delimiter, multipart body header
                    // and
                    // Data to multipart list
                    internal.addValue("--" + multipartMixedBoundary + "--");
                    multipartHttpDatas.add(internal);
                    multipartMixedBoundary = null;
                    // start a new one (could be replaced if mixed start again
                    // from here
                    internal = new InternalAttribute(charset);
                    internal.addValue("\r\n");
                    localMixed = false;
                    // new currentFileUpload and no more in Mixed mode
                    currentFileUpload = fileUpload;
                    duringMixedMode = false;
                }
            } else {
                if (encoderMode != EncoderMode.HTML5 && currentFileUpload != null
                        && currentFileUpload.getName().equals(fileUpload.getName())) {
                    // create a new mixed mode (from previous file)

                    // change multipart body header of previous file into
                    // multipart list to
                    // mixedmultipart start, mixedmultipart body header

                    // change Internal (size()-2 position in multipartHttpDatas)
                    // from (line starting with *)
                    // --AaB03x
                    // * Content-Disposition: form-data; name="files";
                    // filename="file1.txt"
                    // Content-Type: text/plain
                    // to (lines starting with *)
                    // --AaB03x
                    // * Content-Disposition: form-data; name="files"
                    // * Content-Type: multipart/mixed; boundary=BbC04y
                    // *
                    // * --BbC04y
                    // * Content-Disposition: attachment; filename="file1.txt"
                    // Content-Type: text/plain
                    initMixedMultipart();
                    InternalAttribute pastAttribute = (InternalAttribute) multipartHttpDatas.get(multipartHttpDatas
                            .size() - 2);
                    // remove past size
                    globalBodySize -= pastAttribute.size();
                    StringBuilder replacement = new StringBuilder(
                            139 + multipartDataBoundary.length() + multipartMixedBoundary.length() * 2 +
                                    fileUpload.getFilename().length() + fileUpload.getName().length())

                        .append("--")
                        .append(multipartDataBoundary)
                        .append("\r\n")

                        .append(HttpHeaderNames.CONTENT_DISPOSITION)
                        .append(": ")
                        .append(HttpHeaderValues.FORM_DATA)
                        .append("; ")
                        .append(HttpHeaderValues.NAME)
                        .append("=\"")
                        .append(fileUpload.getName())
                        .append("\"\r\n")

                        .append(HttpHeaderNames.CONTENT_TYPE)
                        .append(": ")
                        .append(HttpHeaderValues.MULTIPART_MIXED)
                        .append("; ")
                        .append(HttpHeaderValues.BOUNDARY)
                        .append('=')
                        .append(multipartMixedBoundary)
                        .append("\r\n\r\n")

                        .append("--")
                        .append(multipartMixedBoundary)
                        .append("\r\n")

                        .append(HttpHeaderNames.CONTENT_DISPOSITION)
                        .append(": ")
                        .append(HttpHeaderValues.ATTACHMENT);

                    if (!fileUpload.getFilename().isEmpty()) {
                        replacement.append("; ")
                                   .append(HttpHeaderValues.FILENAME)
                                   .append("=\"")
                                   .append(currentFileUpload.getFilename())
                                   .append('"');
                    }

                    replacement.append("\r\n");

                    pastAttribute.setValue(replacement.toString(), 1);
                    pastAttribute.setValue("", 2);

                    // update past size
                    globalBodySize += pastAttribute.size();

                    // now continue
                    // add mixedmultipart delimiter, mixedmultipart body header
                    // and
                    // Data to multipart list
                    localMixed = true;
                    duringMixedMode = true;
                } else {
                    // a simple new multipart
                    // add multipart delimiter, multipart body header and Data
                    // to multipart list
                    localMixed = false;
                    currentFileUpload = fileUpload;
                    duringMixedMode = false;
                }
            }

            if (localMixed) {
                // add mixedmultipart delimiter, mixedmultipart body header and
                // Data to multipart list
                internal.addValue("--" + multipartMixedBoundary + "\r\n");

                if (fileUpload.getFilename().isEmpty()) {
                    // Content-Disposition: attachment
                    internal.addValue(HttpHeaderNames.CONTENT_DISPOSITION + ": "
                            + HttpHeaderValues.ATTACHMENT + "\r\n");
                } else {
                    // Content-Disposition: attachment; filename="file1.txt"
                    internal.addValue(HttpHeaderNames.CONTENT_DISPOSITION + ": "
                            + HttpHeaderValues.ATTACHMENT + "; "
                            + HttpHeaderValues.FILENAME + "=\"" + fileUpload.getFilename() + "\"\r\n");
                }
            } else {
                internal.addValue("--" + multipartDataBoundary + "\r\n");

                if (fileUpload.getFilename().isEmpty()) {
                    // Content-Disposition: form-data; name="files";
                    internal.addValue(HttpHeaderNames.CONTENT_DISPOSITION + ": " + HttpHeaderValues.FORM_DATA + "; "
                            + HttpHeaderValues.NAME + "=\"" + fileUpload.getName() + "\"\r\n");
                } else {
                    // Content-Disposition: form-data; name="files";
                    // filename="file1.txt"
                    internal.addValue(HttpHeaderNames.CONTENT_DISPOSITION + ": " + HttpHeaderValues.FORM_DATA + "; "
                            + HttpHeaderValues.NAME + "=\"" + fileUpload.getName() + "\"; "
                            + HttpHeaderValues.FILENAME + "=\"" + fileUpload.getFilename() + "\"\r\n");
                }
            }
            // Add Content-Length: xxx
            internal.addValue(HttpHeaderNames.CONTENT_LENGTH + ": " +
                    fileUpload.length() + "\r\n");
            // Content-Type: image/gif
            // Content-Type: text/plain; charset=ISO-8859-1
            // Content-Transfer-Encoding: binary
            internal.addValue(HttpHeaderNames.CONTENT_TYPE + ": " + fileUpload.getContentType());
            String contentTransferEncoding = fileUpload.getContentTransferEncoding();
            if (contentTransferEncoding != null
                    && contentTransferEncoding.equals(HttpPostBodyUtil.TransferEncodingMechanism.BINARY.value())) {
                internal.addValue("\r\n" + HttpHeaderNames.CONTENT_TRANSFER_ENCODING + ": "
                        + HttpPostBodyUtil.TransferEncodingMechanism.BINARY.value() + "\r\n\r\n");
            } else if (fileUpload.getCharset() != null) {
                internal.addValue("; " + HttpHeaderValues.CHARSET + '=' + fileUpload.getCharset().name() + "\r\n\r\n");
            } else {
                internal.addValue("\r\n\r\n");
            }
            multipartHttpDatas.add(internal);
            multipartHttpDatas.add(data);
            globalBodySize += fileUpload.length() + internal.size();
        }
    }

    /**
     * Iterator to be used when encoding will be called chunk after chunk
     */
    private ListIterator<InterfaceHttpData> iterator;

    /**
     * Finalize the request by preparing the Header in the request and returns the request ready to be sent.<br>
     * Once finalized, no data must be added.<br>
     * If the request does not need chunk (isChunked() == false), this request is the only object to send to the remote
     * server.
     *
     * @return the request object (chunked or not according to size of body)
     * @throws ErrorDataEncoderException
     *             if the encoding is in error or if the finalize were already done
     */
    public HttpRequest finalizeRequest() throws ErrorDataEncoderException {
        // Finalize the multipartHttpDatas
        if (!headerFinalized) {
            if (isMultipart) {
                InternalAttribute internal = new InternalAttribute(charset);
                if (duringMixedMode) {
                    internal.addValue("\r\n--" + multipartMixedBoundary + "--");
                }
                internal.addValue("\r\n--" + multipartDataBoundary + "--\r\n");
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

        HttpHeaders headers = request.headers();
        List<String> contentTypes = headers.getAll(HttpHeaderNames.CONTENT_TYPE);
        List<String> transferEncoding = headers.getAll(HttpHeaderNames.TRANSFER_ENCODING);
        if (contentTypes != null) {
            headers.remove(HttpHeaderNames.CONTENT_TYPE);
            for (String contentType : contentTypes) {
                // "multipart/form-data; boundary=--89421926422648"
                String lowercased = contentType.toLowerCase();
                if (lowercased.startsWith(HttpHeaderValues.MULTIPART_FORM_DATA.toString()) ||
                        lowercased.startsWith(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString())) {
                    // ignore
                } else {
                    headers.add(HttpHeaderNames.CONTENT_TYPE, contentType);
                }
            }
        }
        if (isMultipart) {
            String value = HttpHeaderValues.MULTIPART_FORM_DATA + "; " + HttpHeaderValues.BOUNDARY + '='
                    + multipartDataBoundary;
            headers.add(HttpHeaderNames.CONTENT_TYPE, value);
        } else {
            // Not multipart
            headers.add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED);
        }
        // Now consider size for chunk or not
        long realSize = globalBodySize;
        if (!isMultipart) {
            realSize -= 1; // last '&' removed
        }
        iterator = multipartHttpDatas.listIterator();

        headers.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(realSize));
        if (realSize > HttpPostBodyUtil.chunkSize || isMultipart) {
            isChunked = true;
            if (transferEncoding != null) {
                headers.remove(HttpHeaderNames.TRANSFER_ENCODING);
                for (CharSequence v : transferEncoding) {
                    if (HttpHeaderValues.CHUNKED.contentEqualsIgnoreCase(v)) {
                        // ignore
                    } else {
                        headers.add(HttpHeaderNames.TRANSFER_ENCODING, v);
                    }
                }
            }
            HttpUtil.setTransferEncodingChunked(request, true);

            // wrap to hide the possible content
            return new WrappedHttpRequest(request);
        } else {
            // get the only one body and set it to the request
            HttpContent chunk = nextChunk();
            if (request instanceof FullHttpRequest) {
                FullHttpRequest fullRequest = (FullHttpRequest) request;
                ByteBuf chunkContent = chunk.content();
                if (fullRequest.content() != chunkContent) {
                    fullRequest.content().clear().writeBytes(chunkContent);
                    chunkContent.release();
                }
                return fullRequest;
            } else {
                return new WrappedFullHttpRequest(request, chunk);
            }
        }
    }

    /**
     * @return True if the request is by Chunk
     */
    public boolean isChunked() {
        return isChunked;
    }

    /**
     * Encode one attribute
     *
     * @return the encoded attribute
     * @throws ErrorDataEncoderException
     *             if the encoding is in error
     */
    @SuppressWarnings("unchecked")
    private String encodeAttribute(String s, Charset charset) throws ErrorDataEncoderException {
        if (s == null) {
            return "";
        }
        try {
            String encoded = URLEncoder.encode(s, charset.name());
            if (encoderMode == EncoderMode.RFC3986) {
                for (Map.Entry<Pattern, String> entry : percentEncodings) {
                    String replacement = entry.getValue();
                    encoded = entry.getKey().matcher(encoded).replaceAll(replacement);
                }
            }
            return encoded;
        } catch (UnsupportedEncodingException e) {
            throw new ErrorDataEncoderException(charset.name(), e);
        }
    }

    /**
     * The ByteBuf currently used by the encoder
     */
    private ByteBuf currentBuffer;
    /**
     * The current InterfaceHttpData to encode (used if more chunks are available)
     */
    private InterfaceHttpData currentData;
    /**
     * If not multipart, does the currentBuffer stands for the Key or for the Value
     */
    private boolean isKey = true;

    /**
     *
     * @return the next ByteBuf to send as an HttpChunk and modifying currentBuffer accordingly
     */
    private ByteBuf fillByteBuf() {
        int length = currentBuffer.readableBytes();
        if (length > HttpPostBodyUtil.chunkSize) {
            return currentBuffer.readRetainedSlice(HttpPostBodyUtil.chunkSize);
        } else {
            // to continue
            ByteBuf slice = currentBuffer;
            currentBuffer = null;
            return slice;
        }
    }

    /**
     * From the current context (currentBuffer and currentData), returns the next HttpChunk (if possible) trying to get
     * sizeleft bytes more into the currentBuffer. This is the Multipart version.
     *
     * @param sizeleft
     *            the number of bytes to try to get from currentData
     * @return the next HttpChunk or null if not enough bytes were found
     * @throws ErrorDataEncoderException
     *             if the encoding is in error
     */
    private HttpContent encodeNextChunkMultipart(int sizeleft) throws ErrorDataEncoderException {
        if (currentData == null) {
            return null;
        }
        ByteBuf buffer;
        if (currentData instanceof InternalAttribute) {
            buffer = ((InternalAttribute) currentData).toByteBuf();
            currentData = null;
        } else {
            try {
                buffer = ((HttpData) currentData).getChunk(sizeleft);
            } catch (IOException e) {
                throw new ErrorDataEncoderException(e);
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
            currentBuffer = wrappedBuffer(currentBuffer, buffer);
        }
        if (currentBuffer.readableBytes() < HttpPostBodyUtil.chunkSize) {
            currentData = null;
            return null;
        }
        buffer = fillByteBuf();
        return new DefaultHttpContent(buffer);
    }

    /**
     * From the current context (currentBuffer and currentData), returns the next HttpChunk (if possible) trying to get
     * sizeleft bytes more into the currentBuffer. This is the UrlEncoded version.
     *
     * @param sizeleft
     *            the number of bytes to try to get from currentData
     * @return the next HttpChunk or null if not enough bytes were found
     * @throws ErrorDataEncoderException
     *             if the encoding is in error
     */
    private HttpContent encodeNextChunkUrlEncoded(int sizeleft) throws ErrorDataEncoderException {
        if (currentData == null) {
            return null;
        }
        int size = sizeleft;
        ByteBuf buffer;

        // Set name=
        if (isKey) {
            String key = currentData.getName();
            buffer = wrappedBuffer(key.getBytes());
            isKey = false;
            if (currentBuffer == null) {
                currentBuffer = wrappedBuffer(buffer, wrappedBuffer("=".getBytes()));
            } else {
                currentBuffer = wrappedBuffer(currentBuffer, buffer, wrappedBuffer("=".getBytes()));
            }
            // continue
            size -= buffer.readableBytes() + 1;
            if (currentBuffer.readableBytes() >= HttpPostBodyUtil.chunkSize) {
                buffer = fillByteBuf();
                return new DefaultHttpContent(buffer);
            }
        }

        // Put value into buffer
        try {
            buffer = ((HttpData) currentData).getChunk(size);
        } catch (IOException e) {
            throw new ErrorDataEncoderException(e);
        }

        // Figure out delimiter
        ByteBuf delimiter = null;
        if (buffer.readableBytes() < size) {
            isKey = true;
            delimiter = iterator.hasNext() ? wrappedBuffer("&".getBytes()) : null;
        }

        // End for current InterfaceHttpData, need potentially more data
        if (buffer.capacity() == 0) {
            currentData = null;
            if (currentBuffer == null) {
                if (delimiter == null) {
                    return null;
                } else {
                    currentBuffer = delimiter;
                }
            } else {
                if (delimiter != null) {
                    currentBuffer = wrappedBuffer(currentBuffer, delimiter);
                }
            }
            if (currentBuffer.readableBytes() >= HttpPostBodyUtil.chunkSize) {
                buffer = fillByteBuf();
                return new DefaultHttpContent(buffer);
            }
            return null;
        }

        // Put it all together: name=value&
        if (currentBuffer == null) {
            if (delimiter != null) {
                currentBuffer = wrappedBuffer(buffer, delimiter);
            } else {
                currentBuffer = buffer;
            }
        } else {
            if (delimiter != null) {
                currentBuffer = wrappedBuffer(currentBuffer, buffer, delimiter);
            } else {
                currentBuffer = wrappedBuffer(currentBuffer, buffer);
            }
        }

        // end for current InterfaceHttpData, need more data
        if (currentBuffer.readableBytes() < HttpPostBodyUtil.chunkSize) {
            currentData = null;
            isKey = true;
            return null;
        }

        buffer = fillByteBuf();
        return new DefaultHttpContent(buffer);
    }

    @Override
    public void close() throws Exception {
        // NO since the user can want to reuse (broadcast for instance)
        // cleanFiles();
    }

    @Deprecated
    @Override
    public HttpContent readChunk(ChannelHandlerContext ctx) throws Exception {
        return readChunk(ctx.alloc());
    }

    /**
     * Returns the next available HttpChunk. The caller is responsible to test if this chunk is the last one (isLast()),
     * in order to stop calling this getMethod.
     *
     * @return the next available HttpChunk
     * @throws ErrorDataEncoderException
     *             if the encoding is in error
     */
    @Override
    public HttpContent readChunk(ByteBufAllocator allocator) throws Exception {
        if (isLastChunkSent) {
            return null;
        } else {
            HttpContent nextChunk = nextChunk();
            globalProgress += nextChunk.content().readableBytes();
            return nextChunk;
        }
    }

    /**
     * Returns the next available HttpChunk. The caller is responsible to test if this chunk is the last one (isLast()),
     * in order to stop calling this getMethod.
     *
     * @return the next available HttpChunk
     * @throws ErrorDataEncoderException
     *             if the encoding is in error
     */
    private HttpContent nextChunk() throws ErrorDataEncoderException {
        if (isLastChunk) {
            isLastChunkSent = true;
            return LastHttpContent.EMPTY_LAST_CONTENT;
        }
        // first test if previous buffer is not empty
        int size = calculateRemainingSize();
        if (size <= 0) {
            // NextChunk from buffer
            ByteBuf buffer = fillByteBuf();
            return new DefaultHttpContent(buffer);
        }
        // size > 0
        if (currentData != null) {
            // continue to read data
            HttpContent chunk;
            if (isMultipart) {
                chunk = encodeNextChunkMultipart(size);
            } else {
                chunk = encodeNextChunkUrlEncoded(size);
            }
            if (chunk != null) {
                // NextChunk from data
                return chunk;
            }
            size = calculateRemainingSize();
        }
        if (!iterator.hasNext()) {
            return lastChunk();
        }
        while (size > 0 && iterator.hasNext()) {
            currentData = iterator.next();
            HttpContent chunk;
            if (isMultipart) {
                chunk = encodeNextChunkMultipart(size);
            } else {
                chunk = encodeNextChunkUrlEncoded(size);
            }
            if (chunk == null) {
                // not enough
                size = calculateRemainingSize();
                continue;
            }
            // NextChunk from data
            return chunk;
        }
        // end since no more data
        return lastChunk();
    }

    private int calculateRemainingSize() {
        int size = HttpPostBodyUtil.chunkSize;
        if (currentBuffer != null) {
            size -= currentBuffer.readableBytes();
        }
        return size;
    }

    private HttpContent lastChunk() {
        isLastChunk = true;
        if (currentBuffer == null) {
            isLastChunkSent = true;
            // LastChunk with no more data
            return LastHttpContent.EMPTY_LAST_CONTENT;
        }
        // NextChunk as last non empty from buffer
        ByteBuf buffer = currentBuffer;
        currentBuffer = null;
        return new DefaultHttpContent(buffer);
    }

    @Override
    public boolean isEndOfInput() throws Exception {
        return isLastChunkSent;
    }

    @Override
    public long length() {
        return isMultipart? globalBodySize : globalBodySize - 1;
    }

    @Override
    public long progress() {
        return globalProgress;
    }

    /**
     * Exception when an error occurs while encoding
     */
    public static class ErrorDataEncoderException extends Exception {
        private static final long serialVersionUID = 5020247425493164465L;

        public ErrorDataEncoderException() {
        }

        public ErrorDataEncoderException(String msg) {
            super(msg);
        }

        public ErrorDataEncoderException(Throwable cause) {
            super(cause);
        }

        public ErrorDataEncoderException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    private static class WrappedHttpRequest implements HttpRequest {
        private final HttpRequest request;
        WrappedHttpRequest(HttpRequest request) {
            this.request = request;
        }

        @Override
        public HttpRequest setProtocolVersion(HttpVersion version) {
            request.setProtocolVersion(version);
            return this;
        }

        @Override
        public HttpRequest setMethod(HttpMethod method) {
            request.setMethod(method);
            return this;
        }

        @Override
        public HttpRequest setUri(String uri) {
            request.setUri(uri);
            return this;
        }

        @Override
        public HttpMethod getMethod() {
            return request.method();
        }

        @Override
        public HttpMethod method() {
            return request.method();
        }

        @Override
        public String getUri() {
            return request.uri();
        }

        @Override
        public String uri() {
            return request.uri();
        }

        @Override
        public HttpVersion getProtocolVersion() {
            return request.protocolVersion();
        }

        @Override
        public HttpVersion protocolVersion() {
            return request.protocolVersion();
        }

        @Override
        public HttpHeaders headers() {
            return request.headers();
        }

        @Override
        public DecoderResult decoderResult() {
            return request.decoderResult();
        }

        @Override
        @Deprecated
        public DecoderResult getDecoderResult() {
            return request.getDecoderResult();
        }

        @Override
        public void setDecoderResult(DecoderResult result) {
            request.setDecoderResult(result);
        }
    }

    private static final class WrappedFullHttpRequest extends WrappedHttpRequest implements FullHttpRequest {
        private final HttpContent content;

        private WrappedFullHttpRequest(HttpRequest request, HttpContent content) {
            super(request);
            this.content = content;
        }

        @Override
        public FullHttpRequest setProtocolVersion(HttpVersion version) {
            super.setProtocolVersion(version);
            return this;
        }

        @Override
        public FullHttpRequest setMethod(HttpMethod method) {
            super.setMethod(method);
            return this;
        }

        @Override
        public FullHttpRequest setUri(String uri) {
            super.setUri(uri);
            return this;
        }

        @Override
        public FullHttpRequest copy() {
            return replace(content().copy());
        }

        @Override
        public FullHttpRequest duplicate() {
            return replace(content().duplicate());
        }

        @Override
        public FullHttpRequest retainedDuplicate() {
            return replace(content().retainedDuplicate());
        }

        @Override
        public FullHttpRequest replace(ByteBuf content) {
            DefaultFullHttpRequest duplicate = new DefaultFullHttpRequest(protocolVersion(), method(), uri(), content);
            duplicate.headers().set(headers());
            duplicate.trailingHeaders().set(trailingHeaders());
            return duplicate;
        }

        @Override
        public FullHttpRequest retain(int increment) {
            content.retain(increment);
            return this;
        }

        @Override
        public FullHttpRequest retain() {
            content.retain();
            return this;
        }

        @Override
        public FullHttpRequest touch() {
            content.touch();
            return this;
        }

        @Override
        public FullHttpRequest touch(Object hint) {
            content.touch(hint);
            return this;
        }

        @Override
        public ByteBuf content() {
            return content.content();
        }

        @Override
        public HttpHeaders trailingHeaders() {
            if (content instanceof LastHttpContent) {
                return ((LastHttpContent) content).trailingHeaders();
            } else {
                return EmptyHttpHeaders.INSTANCE;
            }
        }

        @Override
        public int refCnt() {
            return content.refCnt();
        }

        @Override
        public boolean release() {
            return content.release();
        }

        @Override
        public boolean release(int decrement) {
            return content.release(decrement);
        }
    }
}
