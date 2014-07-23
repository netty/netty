/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Decodes HTTP/2 data and associated streams into {@link HttpMessage}s and {@link HttpContent}s.
 */
public class Http2HttpDecoder implements Http2FrameObserver {

  private final long                                      maxContentLength;
  private final Map<Integer, Http2HttpMessageAccumulator> messageMap;
  private boolean                                         validateHttpHeaders;

  private static final Set<String>                        headersToExclude;
  private static final Map<String, String>                headerNameTranlation;

  static {
    headersToExclude = new HashSet<String>();
    headerNameTranlation = new HashMap<String, String>();
    for (Http2Headers.HttpName http2HeaderName : Http2Headers.HttpName.values()) {
      headersToExclude.add(http2HeaderName.value());
    }

    headerNameTranlation.put(Http2Headers.HttpName.AUTHORITY.value(), Http2HttpHeaders.Names.AUTHORITY.toString());
    headerNameTranlation.put(Http2Headers.HttpName.SCHEME.value(), Http2HttpHeaders.Names.SCHEME.toString());
  }

  /**
   * Creates a new instance
   *
   * @param maxContentLength
   *          the maximum length of the message content. If the length of the message content exceeds this value, a
   *          {@link TooLongFrameException} will be raised.
   */
  public Http2HttpDecoder(long maxContentLength) {
    this(maxContentLength, true);
  }

  /**
   * Creates a new instance
   *
   * @param maxContentLength
   *          the maximum length of the message content. If the length of the message content exceeds this value, a
   *          {@link TooLongFrameException} will be raised.
   * @param validateHeaders
   *          {@code true} if http headers should be validated
   */
  public Http2HttpDecoder(long maxContentLength, boolean validateHttpHeaders) {
    if (maxContentLength <= 0) {
      throw new IllegalArgumentException("maxContentLength must be a positive integer: " + maxContentLength);
    }
    this.maxContentLength = maxContentLength;
    this.validateHttpHeaders = validateHttpHeaders;
    messageMap = new HashMap<Integer, Http2HttpMessageAccumulator>();
  }

  @Override
  public void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream,
      boolean endOfSegment) throws Http2Exception {
    // Padding is already stripped out of data by super class
    Http2HttpMessageAccumulator msgAccumulator = getMessage(streamId);
    if (msgAccumulator == null) {
      throw Http2Exception.protocolError("Data Frame recieved for unknown stream id %d", streamId);
    }

    boolean isAddSuccessful = false;
    try {
      isAddSuccessful = msgAccumulator.add(endOfStream ? new DefaultLastHttpContent(data, validateHttpHeaders)
          : new DefaultHttpContent(data));
    } catch (TooLongFrameException e) {
      throw Http2Exception.format(Http2Error.INTERNAL_ERROR, "Content length exceeded max of %d for stream id %d",
          streamId);
    }

    if (isAddSuccessful) {
      if (endOfStream) {
        removeMessage(streamId);
        msgAccumulator.fireChannelRead(ctx);
      }
    } else {
      removeMessage(streamId);
      throw Http2Exception.format(Http2Error.INTERNAL_ERROR, "Failed to process data frame for stream id %d", streamId);
    }
  }

  /**
   * Extracts the common initial header processing and internal tracking
   *
   * @param streamId
   *          The stream id the {@code headers} apply to
   * @param headers
   *          The headers to process
   * @param allowAppend
   *          {@code true} if headers will be appended if the stream already exists. if {@code false} and the stream
   *          already exists this method returns {@code null}.
   * @return The object used to track the stream corresponding to {@code streamId}. {@code null} if {@code allowAppend}
   *         is {@code false} and the stream already exists.
   * @throws Http2Exception
   */
  protected Http2HttpMessageAccumulator processHeadersBegin(int streamId, Http2Headers headers, boolean allowAppend)
      throws Http2Exception {
    Http2HttpMessageAccumulator msgAccumulator = getMessage(streamId);
    if (msgAccumulator == null) {
      msgAccumulator = createHttpResponseAccumulator(headers);
    } else if (allowAppend) {
      msgAccumulator.add(headers);
    } else {
      return null;
    }

    msgAccumulator.setHeader(Http2HttpHeaders.Names.STREAM_ID, streamId);
    return msgAccumulator;
  }

  /**
   * Extracts the common final header processing and internal tracking
   *
   * @param ctx
   *          The context for which this message has been received
   * @param streamId
   *          The stream id the {@code msgAccumulator} corresponds to
   * @param msgAccumulator
   *          The object which represents all data for corresponding to {@code streamId}
   * @param endStream
   *          {@code true} if this is the last event for the stream
   */
  protected void processHeadersEnd(ChannelHandlerContext ctx, int streamId, Http2HttpMessageAccumulator msgAccumulator,
      boolean endStream) {
    if (endStream) {
      msgAccumulator.fireChannelRead(ctx);
    } else {
      putMessage(streamId, msgAccumulator);
    }
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
      boolean endStream, boolean endSegment) throws Http2Exception {
    Http2HttpMessageAccumulator msgAccumulator = processHeadersBegin(streamId, headers, true);
    processHeadersEnd(ctx, streamId, msgAccumulator, endStream);
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
      short weight, boolean exclusive, int padding, boolean endStream, boolean endSegment) throws Http2Exception {
    Http2HttpMessageAccumulator msgAccumulator = processHeadersBegin(streamId, headers, true);

    if (streamDependency != 0) {
      msgAccumulator.setHeader(Http2HttpHeaders.Names.STREAM_DEPENDENCY_ID, streamDependency);
      msgAccumulator.setHeader(Http2HttpHeaders.Names.STREAM_EXCLUSIVE, exclusive);
      msgAccumulator.setHeader(Http2HttpHeaders.Names.STREAM_WEIGHT, weight);
    }

    processHeadersEnd(ctx, streamId, msgAccumulator, endStream);
  }

  @Override
  public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
      boolean exclusive) throws Http2Exception {
    Http2HttpMessageAccumulator msgAccumulator = getMessage(streamId);
    if (msgAccumulator == null) {
      throw Http2Exception.protocolError("Priority Frame recieved for unknown stream id %d", streamId);
    }

    if (streamDependency != 0) {
      msgAccumulator.setHeader(Http2HttpHeaders.Names.STREAM_DEPENDENCY_ID, streamDependency);
      msgAccumulator.setHeader(Http2HttpHeaders.Names.STREAM_EXCLUSIVE, exclusive);
      msgAccumulator.setHeader(Http2HttpHeaders.Names.STREAM_WEIGHT, weight);
    } else {
      msgAccumulator.removeHeader(Http2HttpHeaders.Names.STREAM_DEPENDENCY_ID);
      msgAccumulator.removeHeader(Http2HttpHeaders.Names.STREAM_EXCLUSIVE);
      msgAccumulator.removeHeader(Http2HttpHeaders.Names.STREAM_WEIGHT);
    }
  }

  @Override
  public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
    Http2HttpMessageAccumulator msgAccumulator = getMessage(streamId);
    if (msgAccumulator == null) {
      throw Http2Exception.protocolError("Rst Frame recieved for unknown stream id %d", streamId);
    }

    removeMessage(streamId);
  }

  @Override
  public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
    // NOOP
  }

  @Override
  public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
    // NOOP
  }

  @Override
  public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
    // NOOP
  }

  @Override
  public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
    // NOOP
  }

  @Override
  public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers,
      int padding) throws Http2Exception {
    // Do not allow adding of headers to existing Http2HttpMessageAccumulator
    // according to spec (http://tools.ietf.org/html/draft-ietf-httpbis-http2-13#section-6.6) there must
    // be a CONTINUATION frame for more headers
    Http2HttpMessageAccumulator msgAccumulator = processHeadersBegin(promisedStreamId, headers, false);
    if (msgAccumulator == null) {
      throw Http2Exception.protocolError("Push Promise Frame recieved for pre-existing stream id %d", promisedStreamId);
    }

    msgAccumulator.setHeader(Http2HttpHeaders.Names.STREAM_PROMISE_ID, streamId);

    processHeadersEnd(ctx, streamId, msgAccumulator, false);
  }

  @Override
  public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
      throws Http2Exception {
    // NOOP
  }

  @Override
  public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
      throws Http2Exception {
    // NOOP
  }

  @Override
  public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
                             Http2Flags flags, ByteBuf payload) {
    // NOOP
  }

  protected Http2HttpMessageAccumulator putMessage(int streamId, Http2HttpMessageAccumulator message) {
    return messageMap.put(streamId, message);
  }

  protected Http2HttpMessageAccumulator getMessage(int streamId) {
    return messageMap.get(streamId);
  }

  protected Http2HttpMessageAccumulator removeMessage(int streamId) {
    return messageMap.remove(streamId);
  }

  /**
   * Translate HTTP/2 headers into an object which can build the corresponding HTTP/1.x objects
   *
   * @param http2Headers
   *          The HTTP/2 headers corresponding to a new stream
   * @return Collector for HTTP/1.x objects
   * @throws Http2Exception
   *           If any of the HTTP/2 headers to not translate to valid HTTP/1.x headers
   */
  private Http2HttpMessageAccumulator createHttpResponseAccumulator(Http2Headers http2Headers) throws Http2Exception {
    HttpResponseStatus status = null;
    try {
      status = HttpResponseStatus.parseLine(http2Headers.status());
    } catch (Exception e) {
      throw Http2Exception.protocolError("Unrecognized HTTP status code '%s' encountered in translation to HTTP/1.x",
          http2Headers);
    }
    // TODO: http://tools.ietf.org/html/draft-ietf-httpbis-http2-13#section-8.1.2.1 states
    // there is explicitly no ":version" header. How to derive the HTTP version?
    // - Track the version from the request portion of this stream?
    // -- What if this is a server push frame and there is no corresponding request?
    // - What negative implications come from always using HTTP_1_1 regardless?
    // -- Is it possible to use HTTP_1_0 through HTTP/2? (I am assuming it is)
    HttpVersion version = HttpVersion.HTTP_1_1;

    Http2HttpMessageAccumulator messageAccumulator = new Http2HttpMessageAccumulator(new DefaultHttpResponse(version,
        status, validateHttpHeaders));
    messageAccumulator.add(http2Headers);
    return messageAccumulator;
  }

  /**
   * Provides a container to collect HTTP/1.x objects until the end of the stream has been reached
   */
  protected class Http2HttpMessageAccumulator {
    private HttpMessage       message;
    private List<HttpContent> contents;
    private long              contentLength;

    /**
     * Creates a new instance
     *
     * @param message
     *          The HTTP/1.x object which represents the headers
     */
    public Http2HttpMessageAccumulator(HttpMessage message) {
      this.message = message;
      contents = new ArrayList<HttpContent>();
      contentLength = 0;
    }

    /**
     * Set a HTTP/1.x header
     *
     * @param name
     *          The name of the header
     * @param value
     *          The value of the header
     * @return The headers object after the set operation
     */
    public HttpHeaders setHeader(CharSequence name, Object value) {
      return message.headers().set(name, value);
    }

    /**
     * Removes the header with the specified name.
     *
     * @param name
     *          The name of the header to remove
     * @return {@code true} if and only if at least one entry has been removed
     */
    public boolean removeHeader(CharSequence name) {
      return message.headers().remove(name);
    }

    /**
     * Add a HTTP/1.x object which represents part of the message body
     *
     * @param httpContent
     *          The content to add
     * @return <tt>true</tt> (as specified by {@link Collection#add})
     * @throws TooLongFrameException
     *           If the {@code contentLength} is exceeded with the addition of the {@code httpContent}
     */
    public boolean add(HttpContent httpContent) throws TooLongFrameException {
      ByteBuf content = httpContent.content();
      if (contentLength > maxContentLength - content.readableBytes()) {
        throw new TooLongFrameException("HTTP/2 content length exceeded " + maxContentLength + " bytes.");
      }

      if (contents.add(httpContent)) {
        // TODO: Please Review the `retain` call logic below
        // I am assuming this is desired (no copy but still want to use the object)
        // but its worth a second look
        httpContent.retain();
        contentLength += content.readableBytes();
        return true;
      }
      return false;
    }

    /**
     * Extend the current set of HTTP/1.x headers
     *
     * @param http2Headers
     *          The HTTP/2 headers to be added
     * @throws Http2Exception
     *           If any HTTP/2 headers do not map to HTTP/1.x headers
     */
    public void add(Http2Headers http2Headers) throws Http2Exception {
      // http://tools.ietf.org/html/draft-ietf-httpbis-http2-13#section-8.1.2.1
      // All headers that start with ':' are only valid in HTTP/2 context
      HttpHeaders headers = message.headers();
      Iterator<Entry<String, String>> itr = http2Headers.iterator();
      while (itr.hasNext()) {
        Entry<String, String> entry = itr.next();
        if (!headersToExclude.contains(entry.getKey())) {
          String translatedName = headerNameTranlation.get(entry.getKey());
          if (translatedName == null) {
            translatedName = entry.getKey();
          }

          if (translatedName.isEmpty() || translatedName.charAt(0) == ':') {
            throw Http2Exception.protocolError("Unknown HTTP/2 header '%s' encountered in translation to HTTP/1.x",
                translatedName);
          } else {
            headers.add(translatedName, entry.getValue());
          }
        }
      }
    }

    /**
     * Pass this object's contents up the pipeline
     *
     * @param ctx
     *          The content to fire a channel read event on
     */
    public void fireChannelRead(ChannelHandlerContext ctx) {
      HttpHeaders headers = message.headers();

      // The Connection and Keep-Alive headers are no longer valid
      HttpHeaderUtil.setKeepAlive(message, true);
      HttpHeaderUtil.setContentLength(message, contentLength);

      // Transfer-Encoding header is not valid
      headers.remove(HttpHeaders.Names.TRANSFER_ENCODING);
      headers.remove(HttpHeaders.Names.TRAILER);

      ctx.fireChannelRead(message);

      if (contents.isEmpty()) {
        ctx.fireChannelRead(LastHttpContent.EMPTY_LAST_CONTENT);
      } else {
        for (HttpContent content : contents) {
          ctx.fireChannelRead(content);
        }
      }
    }
  }
}
