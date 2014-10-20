/*
 * Copyright 2014 The Netty Project
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
/**
 * <p>Rest server handler</p>
 * <p>Entry class is {@link io.netty.handler.codec.rest.RestHandler}
 * which is supposed to be the last handler in your pipeline once extended. This handler takes into
 * consideration various HTTP requests, while using specific business sub handlers extending
 * {@link io.netty.handler.codec.rest.RestMethodHandler}.</p>
 * <p>The {@link io.netty.handler.codec.rest.RootOptionsRestMethodHandler} class is the default "/" OPTIONS
 * method class.</p>
 * <p>The {@link io.netty.handler.codec.rest.RestConfiguration} contains the overall configuration
 * for the Rest handler, including which business sub handlers shall be called for one base uri and method.</p>
 * <p>The {@link io.netty.handler.codec.rest.RestArgument} is the object containing current information and future
 * results for the REST process.</p>
 * <p>This REST implementation proposes several options:</p>
 * <p><ul><li><b>A time limit</b>: a datetime is sent with the arguments (in the header as
 * X-Auth-Timestamp in ISO 8601 format); the difference between the current datetime and the sent datetime shall
 * not exceed the time limit. This is to prevent some deny of services, using repetitions of the same request.
 * It can be disabled by setting this limit to a value less or equals to 0.</li>
 * <li><b>A signature check</b>: the signature is computed using all arguments from URI plus
 * the X-Auth-Timestamp and a shared HmacSHA key (could be SHA1, SHA256, ...). Moreover a shared non sent extra
 * private key could be used to prevent easy reconstruction of the signature. Again this is to prevent some
 * deny of services and moreover some man in the middle attack (by validating the request against the argument).
 * Note that any item could be added manually (whatever from cookies or headers) into
 * the current args set list of the RestArgument to be taken into consideration for the check.</li>
 * <li><b>A set of business sub handlers</b>: selected on the base uri (http://host:port/baseuri/suburi gives baseuri)
 * and the associated valid methods (POST, GET, PUT, ...) while OPTIONS is mandatory for all. OPTIONS
 * gives the ability to retrieve automatically the interface for REST remote clients.</li>
 * <li><b>The RestHandler request body management</b>: 2 options are proposed;
 * <ul><li>1) <code>isBodyDecodable=true</code> meaning decoding is using the usual request
 * body using the standard {@link io.netty.handler.codec.http.multipart.HttpPostRequestDecoder} and calling
 * setAttribute or setFileUpload accordingly to the type of the decoding element into the associated
 * business sub handler. Note that one should take care of calling the static <code>initialize()</code>
 * method to initialize correctly the default behavior of the
 * {@link io.netty.handler.codec.http.multipart.HttpPostRequestDecoder}.</li>
 * <li>2) <code>isBodyDecodable=false</code> meaning all chunks are merged into one ByteBuf and
 * calling finally the setBody into the associated business sub handler, giving a chance for
 * instance to decode JSON content, XML content or whatever (note the decoding shall be done at once since
 * the sub handler is shared among various requests).</li>
 * <li>Another option is to override the following methods to fit the needs if necessary:
 * <code>createDecoder(), bodyChunk(), readHttpData()</code></li></ul></li>
 * <li>One could override methods in order to fit his/her own needs.</li></ul></p>
 */
package io.netty.handler.codec.rest;