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

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.internal.StringUtil;

import java.nio.charset.Charset;
import java.util.List;

/**
 * This decoder will decode Body and can handle POST BODY.
 *
 * You <strong>MUST</strong> call {@link #destroy()} after completion to release all resources.
 *
 */
public class HttpPostRequestDecoder implements HttpPostRequestDecoderInterface {
    protected static final int DEFAULT_DISCARD_THRESHOLD = 10 * 1024 * 1024;
    protected HttpPostRequestDecoderInterface decoder;

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
    public HttpPostRequestDecoder(HttpRequest request) throws HttpPostRequestDecoder.ErrorDataDecoderException,
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
    public HttpPostRequestDecoder(HttpDataFactory factory, HttpRequest request) throws HttpPostRequestDecoder.ErrorDataDecoderException,
            HttpPostRequestDecoder.IncompatibleDataDecoderException {
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
     * @throws HttpPostRequestDecoder.IncompatibleDataDecoderException
     *             if the request has no body to decode
     * @throws HttpPostRequestDecoder.ErrorDataDecoderException
     *             if the default charset was wrong when decoding or other
     *             errors
     */
    public HttpPostRequestDecoder(HttpDataFactory factory, HttpRequest request, Charset charset)
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
        // Fill default values
        if (isMultipart(request)) {
            decoder = new HttpPostMultipartRequestDecoder(factory, request, charset);
        } else {
            decoder = new HttpPostStandardRequestDecoder(factory, request, charset);
        }
    }

    /**
     * states follow NOTSTARTED PREAMBLE ( (HEADERDELIMITER DISPOSITION (FIELD |
     * FILEUPLOAD))* (HEADERDELIMITER DISPOSITION MIXEDPREAMBLE (MIXEDDELIMITER
     * MIXEDDISPOSITION MIXEDFILEUPLOAD)+ MIXEDCLOSEDELIMITER)* CLOSEDELIMITER)+
     * EPILOGUE
     *
     * First getStatus is: NOSTARTED
     *
     * Content-type: multipart/form-data, boundary=AaB03x => PREAMBLE in Header
     *
     * --AaB03x => HEADERDELIMITER content-disposition: form-data; name="field1"
     * => DISPOSITION
     *
     * Joe Blow => FIELD --AaB03x => HEADERDELIMITER content-disposition:
     * form-data; name="pics" => DISPOSITION Content-type: multipart/mixed,
     * boundary=BbC04y
     *
     * --BbC04y => MIXEDDELIMITER Content-disposition: attachment;
     * filename="file1.txt" => MIXEDDISPOSITION Content-Type: text/plain
     *
     * ... contents of file1.txt ... => MIXEDFILEUPLOAD --BbC04y =>
     * MIXEDDELIMITER Content-disposition: file; filename="file2.gif" =>
     * MIXEDDISPOSITION Content-type: image/gif Content-Transfer-Encoding:
     * binary
     *
     * ...contents of file2.gif... => MIXEDFILEUPLOAD --BbC04y-- =>
     * MIXEDCLOSEDELIMITER --AaB03x-- => CLOSEDELIMITER
     *
     * Once CLOSEDELIMITER is found, last getStatus is EPILOGUE
     */
    protected enum MultiPartStatus {
        NOTSTARTED, PREAMBLE, HEADERDELIMITER, DISPOSITION, FIELD, FILEUPLOAD, MIXEDPREAMBLE, MIXEDDELIMITER,
        MIXEDDISPOSITION, MIXEDFILEUPLOAD, MIXEDCLOSEDELIMITER, CLOSEDELIMITER, PREEPILOGUE, EPILOGUE
    }

    /**
	 * Exception when try reading data from request in chunked format, and not
	 * enough data are available (need more chunks)
	 */
	public static class NotEnoughDataDecoderException extends DecoderException {
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
	public static class EndOfDataDecoderException extends DecoderException {
	    private static final long serialVersionUID = 1336267941020800769L;
	}

	/**
	 * Exception when an error occurs while decoding
	 */
	public static class ErrorDataDecoderException extends DecoderException {
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
	 * Exception when an unappropriated getMethod was called on a request
	 */
	public static class IncompatibleDataDecoderException extends DecoderException {
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

	/**
     * Check if the given request is a multipart request
     * @param request
     * @return True if the request is a Multipart request
     * @throws HttpPostRequestDecoder.ErrorDataDecoderException
     */
    public static boolean isMultipart(HttpRequest request) throws HttpPostRequestDecoder.ErrorDataDecoderException {
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
            throws HttpPostRequestDecoder.ErrorDataDecoderException {
        // Check if Post using "multipart/form-data; boundary=--89421926422648"
        String[] headerContentType = splitHeaderContentType(contentType);
        if (headerContentType[0].toLowerCase().startsWith(
                HttpHeaders.Values.MULTIPART_FORM_DATA) &&
                headerContentType[1].toLowerCase().startsWith(
                        HttpHeaders.Values.BOUNDARY)) {
            String[] boundary = StringUtil.split(headerContentType[1], '=');
            if (boundary.length != 2) {
                throw new HttpPostRequestDecoder.ErrorDataDecoderException("Needs a boundary value");
            }
            return "--" + boundary[1];
        } else {
            return null;
        }
    }

    public boolean isMultipart() {
        return decoder.isMultipart();
    }

    public void setDiscardThreshold(int discardThreshold) {
        decoder.setDiscardThreshold(discardThreshold);
    }

    public int getDiscardThreshold() {
        return decoder.getDiscardThreshold();
    }

    public List<InterfaceHttpData> getBodyHttpDatas() throws HttpPostRequestDecoder.NotEnoughDataDecoderException {
        return decoder.getBodyHttpDatas();
    }

    public List<InterfaceHttpData> getBodyHttpDatas(String name) throws HttpPostRequestDecoder.NotEnoughDataDecoderException {
        return decoder.getBodyHttpDatas(name);
    }

    public InterfaceHttpData getBodyHttpData(String name) throws HttpPostRequestDecoder.NotEnoughDataDecoderException {
        return decoder.getBodyHttpData(name);
    }

    public HttpPostRequestDecoderInterface offer(HttpContent content) throws HttpPostRequestDecoder.ErrorDataDecoderException {
        return decoder.offer(content);
    }

    public boolean hasNext() throws HttpPostRequestDecoder.EndOfDataDecoderException {
        return decoder.hasNext();
    }

    public InterfaceHttpData next() throws HttpPostRequestDecoder.EndOfDataDecoderException {
        return decoder.next();
    }

    public void destroy() {
        decoder.destroy();
    }

    public void cleanFiles() {
        decoder.cleanFiles();
    }

    public void removeHttpDataFromClean(InterfaceHttpData data) {
        decoder.removeHttpDataFromClean(data);
    }

    /**
     * Split the very first line (Content-Type value) in 2 Strings
     *
     * @return the array of 2 Strings
     */
    private static String[] splitHeaderContentType(String sb) {
        int aStart;
        int aEnd;
        int bStart;
        int bEnd;
        aStart = HttpPostBodyUtil.findNonWhitespace(sb, 0);
        aEnd =  sb.indexOf(';');
        if (aEnd == -1) {
            return new String[] { sb, "" };
        }
        if (sb.charAt(aEnd - 1) == ' ') {
            aEnd--;
        }
        bStart = HttpPostBodyUtil.findNonWhitespace(sb, aEnd + 1);
        bEnd = HttpPostBodyUtil.findEndOfString(sb);
        return new String[] { sb.substring(aStart, aEnd), sb.substring(bStart, bEnd) };
    }
}
