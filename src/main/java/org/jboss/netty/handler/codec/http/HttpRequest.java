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


/**
 * An HTTP request.
 *
 * <h3>Accessing Query Parameters and Cookie</h3>
 * <p>
 * Unlike the Servlet API, a query string is constructed and decomposed by
 * {@link QueryStringEncoder} and {@link QueryStringDecoder}.  {@link Cookie}
 * support is also provided separately via {@link CookieEncoder} and
 * {@link CookieDecoder}.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 * @see HttpResponse
 * @see CookieEncoder
 * @see CookieDecoder
 */
public interface HttpRequest extends HttpMessage {

    /**
     * Returns the method of this request.
     */
    HttpMethod getMethod();

    /**
     * Sets the method of this request.
     */
    void setMethod(HttpMethod method);

    /**
     * Returns the URI (or path) of this request.
     */
    String getUri();

    /**
     * Sets the URI (or path) of this request.
     */
    void setUri(String uri);
}
