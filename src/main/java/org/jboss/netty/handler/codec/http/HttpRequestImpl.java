/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.codec.http;

import org.jboss.netty.buffer.ChannelBuffer;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.net.URI;

/**
 * An http request implementation
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class HttpRequestImpl extends HttpMessageImpl implements HttpRequest {
    private Map<String, String> params = new HashMap<String, String>();

    private final HttpMethod method;


    private final URI uri;

    public HttpRequestImpl(HttpProtocol httpProtocol, HttpMethod method, URI uri) {
        super(httpProtocol);
        this.method = method;
        this.uri = uri;
    }

    public void addParameter(final String name, final String val) {
        params.put(name, val);
    }

    public boolean removeParameter(final String name) {
        return params.remove(name) != null;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public URI getURI() {
        return uri;
    }

    public String getParameter(final String name) {
        return params.get(name);
    }

    public boolean containsParameter(final String name) {
        return params.containsKey(name);
    }

    public Set<String> getParameterNames() {
        return params.keySet();
    }

    public boolean isKeepAlive() {
        //todo
        return true;
    }
}
