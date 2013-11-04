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

import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpConstants;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.util.internal.StringUtil;

import java.nio.charset.Charset;
import java.util.List;

/**
 * This decoder will decode Body and can handle POST BODY (both multipart and standard).
 */
@SuppressWarnings("deprecation")
public class HttpPostRequestDecoder implements InterfaceHttpPostRequestDecoder {
    /**
     * Does this request is a Multipart request
     */
    private final InterfaceHttpPostRequestDecoder decoder;

    /**
    *
    * @param request the request to decode
    * @throws NullPointerException for request
    * @throws IncompatibleDataDecoderException if the request has no body to decode
    * @throws ErrorDataDecoderException if the default charset was wrong when decoding or other errors
    */
    public HttpPostRequestDecoder(HttpRequest request)
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
    public HttpPostRequestDecoder(HttpDataFactory factory, HttpRequest request)
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
    public HttpPostRequestDecoder(HttpDataFactory factory, HttpRequest request,
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
        // Fill default values
        if (isMultipart(request)) {
            decoder = new HttpPostMultipartRequestDecoder(factory, request, charset);
        } else {
            decoder = new HttpPostStandardRequestDecoder(factory, request, charset);
        }
    }

    /**
     * states follow
     * NOTSTARTED PREAMBLE (
     *  (HEADERDELIMITER DISPOSITION (FIELD | FILEUPLOAD))*
     *  (HEADERDELIMITER DISPOSITION MIXEDPREAMBLE
     *     (MIXEDDELIMITER MIXEDDISPOSITION MIXEDFILEUPLOAD)+
     *   MIXEDCLOSEDELIMITER)*
     * CLOSEDELIMITER)+ EPILOGUE
     *
     *  First status is: NOSTARTED

        Content-type: multipart/form-data, boundary=AaB03x     => PREAMBLE in Header

        --AaB03x                                               => HEADERDELIMITER
        content-disposition: form-data; name="field1"          => DISPOSITION

        Joe Blow                                               => FIELD
        --AaB03x                                               => HEADERDELIMITER
        content-disposition: form-data; name="pics"            => DISPOSITION
        Content-type: multipart/mixed, boundary=BbC04y

        --BbC04y                                               => MIXEDDELIMITER
        Content-disposition: attachment; filename="file1.txt"  => MIXEDDISPOSITION
        Content-Type: text/plain

        ... contents of file1.txt ...                          => MIXEDFILEUPLOAD
        --BbC04y                                               => MIXEDDELIMITER
        Content-disposition: file; filename="file2.gif"  => MIXEDDISPOSITION
        Content-type: image/gif
        Content-Transfer-Encoding: binary

          ...contents of file2.gif...                          => MIXEDFILEUPLOAD
        --BbC04y--                                             => MIXEDCLOSEDELIMITER
        --AaB03x--                                             => CLOSEDELIMITER

       Once CLOSEDELIMITER is found, last status is EPILOGUE
     */
    protected enum MultiPartStatus {
        NOTSTARTED,
        PREAMBLE,
        HEADERDELIMITER,
        DISPOSITION,
        FIELD,
        FILEUPLOAD,
        MIXEDPREAMBLE,
        MIXEDDELIMITER,
        MIXEDDISPOSITION,
        MIXEDFILEUPLOAD,
        MIXEDCLOSEDELIMITER,
        CLOSEDELIMITER,
        PREEPILOGUE,
        EPILOGUE
    }

    /**
     * Check if the given request is a multipart request
     *
     * @return True if the request is a Multipart request
     */
    public static boolean isMultipart(HttpRequest request) throws ErrorDataDecoderException {
        if (request.headers().contains(HttpHeaders.Names.CONTENT_TYPE)) {
            return getMultipartDataBoundary(request.headers().get(HttpHeaders.Names.CONTENT_TYPE)) != null;
        } else {
            return false;
        }
    }

    /**
     * Check from the request ContentType if this request is a Multipart request.
     * @return the multipartDataBoundary if it exists, else null
     */
    protected static String getMultipartDataBoundary(String contentType)
            throws ErrorDataDecoderException {
        // Check if Post using "multipart/form-data; boundary=--89421926422648"
        String[] headerContentType = splitHeaderContentType(contentType);
        if (headerContentType[0].toLowerCase().startsWith(
                HttpHeaders.Values.MULTIPART_FORM_DATA) &&
                headerContentType[1].toLowerCase().startsWith(
                        HttpHeaders.Values.BOUNDARY)) {
            String[] boundary = StringUtil.split(headerContentType[1], '=');
            if (boundary.length != 2) {
                throw new ErrorDataDecoderException("Needs a boundary value");
            }
            return "--" + boundary[1];
        } else {
            return null;
        }
    }

    /**
     * True if this request is a Multipart request
     * @return True if this request is a Multipart request
     */
    public boolean isMultipart() {
        return decoder.isMultipart();
    }

    /**
     * This method returns a List of all HttpDatas from body.<br>
     *
     * If chunked, all chunks must have been offered using offer() method.
     * If not, NotEnoughDataDecoderException will be raised.
     *
     * @return the list of HttpDatas from Body part for POST method
     * @throws NotEnoughDataDecoderException Need more chunks
     */
    public List<InterfaceHttpData> getBodyHttpDatas()
            throws NotEnoughDataDecoderException {
        return decoder.getBodyHttpDatas();
    }

    /**
     * This method returns a List of all HttpDatas with the given name from body.<br>
     *
     * If chunked, all chunks must have been offered using offer() method.
     * If not, NotEnoughDataDecoderException will be raised.

     * @return All Body HttpDatas with the given name (ignore case)
     * @throws NotEnoughDataDecoderException need more chunks
     */
    public List<InterfaceHttpData> getBodyHttpDatas(String name)
            throws NotEnoughDataDecoderException {
        return decoder.getBodyHttpDatas(name);
    }

    /**
     * This method returns the first InterfaceHttpData with the given name from body.<br>
     *
     * If chunked, all chunks must have been offered using offer() method.
     * If not, NotEnoughDataDecoderException will be raised.
    *
    * @return The first Body InterfaceHttpData with the given name (ignore case)
    * @throws NotEnoughDataDecoderException need more chunks
    */
    public InterfaceHttpData getBodyHttpData(String name)
            throws NotEnoughDataDecoderException {
        return decoder.getBodyHttpData(name);
    }

    /**
     * Initialized the internals from a new chunk
     * @param chunk the new received chunk
     * @throws ErrorDataDecoderException if there is a problem with the charset decoding or
     *          other errors
     */
    public void offer(HttpChunk chunk) throws ErrorDataDecoderException {
        decoder.offer(chunk);
    }

    /**
     * True if at current status, there is an available decoded InterfaceHttpData from the Body.
     *
     * This method works for chunked and not chunked request.
     *
     * @return True if at current status, there is a decoded InterfaceHttpData
     * @throws EndOfDataDecoderException No more data will be available
     */
    public boolean hasNext() throws EndOfDataDecoderException {
        return decoder.hasNext();
    }

    /**
     * Returns the next available InterfaceHttpData or null if, at the time it is called, there is no more
     * available InterfaceHttpData. A subsequent call to offer(httpChunk) could enable more data.
     *
     * @return the next available InterfaceHttpData or null if none
     * @throws EndOfDataDecoderException No more data will be available
     */
    public InterfaceHttpData next() throws EndOfDataDecoderException {
        return decoder.next();
    }

    /**
     * Clean all HttpDatas (on Disk) for the current request.
     */
    public void cleanFiles() {
        decoder.cleanFiles();
    }

    /**
     * Remove the given FileUpload from the list of FileUploads to clean
     */
    public void removeHttpDataFromClean(InterfaceHttpData data) {
        decoder.removeHttpDataFromClean(data);
    }

    /**
     * Split the very first line (Content-Type value) in 2 Strings
     * @return the array of 2 Strings
     */
    private static String[] splitHeaderContentType(String sb) {
        int size = sb.length();
        int aStart;
        int aEnd;
        int bStart;
        int bEnd;
        aStart = HttpPostBodyUtil.findNonWhitespace(sb, 0);
        aEnd = HttpPostBodyUtil.findWhitespace(sb, aStart);
        if (aEnd >= size) {
            return new String[] { sb, "" };
        }
        if (sb.charAt(aEnd) == ';') {
            aEnd --;
        }
        bStart = HttpPostBodyUtil.findNonWhitespace(sb, aEnd);
        bEnd = HttpPostBodyUtil.findEndOfString(sb);
        return new String[] { sb.substring(aStart, aEnd),
                sb.substring(bStart, bEnd) };
    }

    /**
     * Exception when try reading data from request in chunked format, and not enough
     * data are available (need more chunks)
     */
    public static class NotEnoughDataDecoderException extends Exception {
        private static final long serialVersionUID = -7846841864603865638L;

        public NotEnoughDataDecoderException() {
        }

        public NotEnoughDataDecoderException(String msg) {
            super(msg);
        }

        public NotEnoughDataDecoderException(Throwable cause) {
            super(cause);
        }

        public NotEnoughDataDecoderException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    /**
     * Exception when the body is fully decoded, even if there is still data
     */
    public static class EndOfDataDecoderException extends Exception {
        private static final long serialVersionUID = 1336267941020800769L;
    }

    /**
     * Exception when an error occurs while decoding
     */
    public static class ErrorDataDecoderException extends Exception {
        private static final long serialVersionUID = 5020247425493164465L;

        public ErrorDataDecoderException() {
        }

        public ErrorDataDecoderException(String msg) {
            super(msg);
        }

        public ErrorDataDecoderException(Throwable cause) {
            super(cause);
        }

        public ErrorDataDecoderException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    /**
     * Exception when an unappropriated method was called on a request
     */
    @Deprecated
    public static class IncompatibleDataDecoderException extends Exception {
        private static final long serialVersionUID = -953268047926250267L;

        public IncompatibleDataDecoderException() {
        }

        public IncompatibleDataDecoderException(String msg) {
            super(msg);
        }

        public IncompatibleDataDecoderException(Throwable cause) {
            super(cause);
        }

        public IncompatibleDataDecoderException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
