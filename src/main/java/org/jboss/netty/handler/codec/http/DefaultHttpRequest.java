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
 * The default {@link HttpRequest} implementation.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
public class DefaultHttpRequest extends DefaultHttpMessage implements HttpRequest {

    private HttpMethod method;
    private String uri;

    /**
     * Creates a new instance.
     *
     * @param httpVersion the HTTP version of the request
     * @param method      the HTTP method of the request
     * @param uri         the URI or path of the request
     */
    public DefaultHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri) {
        super(httpVersion);
        setMethod(method);
        setUri(uri);
    }

    public HttpMethod getMethod() {
        return method;
    }

    public void setMethod(HttpMethod method) {
        if (method == null) {
            throw new NullPointerException("method");
        }
        this.method = method;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        if (uri == null) {
            throw new NullPointerException("uri");
        }
        this.uri = uri;
    }

    @Override
    public String toString() {
        return getMethod().toString() + ' ' + getUri() + ' ' + getProtocolVersion().getText();
    }
}
