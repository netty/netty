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

import java.util.List;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.util.UriQueryDecoder;

/**
 * decodes an http request.
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 * @author Trustin Lee (tlee@redhat.com)
 */
public class HttpRequestDecoder extends HttpMessageDecoder {

    @Override
    protected void readInitial(ChannelBuffer buffer) {
        String line = readIntoCurrentLine(buffer);
        checkpoint(ResponseState.READ_HEADER);
        String[] split = splitInitial(line);
        UriQueryDecoder uriQueryDecoder = new UriQueryDecoder(split[1]);
        DefaultHttpRequest httpRequest = new DefaultHttpRequest(HttpVersion.getProtocol(split[2]), HttpMethod.valueOf(split[0]), uriQueryDecoder.getPath());
        message = httpRequest;
        Map<String, List<String>> params = uriQueryDecoder.getParameters();
        for (String key: params.keySet()) {
            httpRequest.setParameters(key, params.get(key));
        }
    }
}
