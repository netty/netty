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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 */
public class QueryStringEncoder {

    final String url;

    final List<Param> params = new ArrayList<Param>();

    public QueryStringEncoder(String url) {
        this.url = url;
    }

    public void addParam(String name, String value) {
        if(name == null) {
            throw new NullPointerException("name is null");
        }
        if(value == null) {
            throw new NullPointerException("value is null");
        }
        params.add(new Param(name, value));
    }

    public URI toUri() throws URISyntaxException {
        if (params.size() == 0) {
            return new URI(url);
        }
        else {
            StringBuffer sb = new StringBuffer(url).append("?");
            for (int i = 0; i < params.size(); i++) {
                Param param = params.get(i);
                sb.append(replaceSpaces(param.name)).append("=").append(replaceSpaces(param.value));
                if(i != params.size() - 1) {
                    sb.append("&");
                }
            }
            return new URI(sb.toString());
        }
    }

    private String replaceSpaces(String s) {
        return s.replaceAll(" ", "%20");
    }

    class Param {
        final String name;

        final String value;

        public Param(String name, String value) {
            this.value = value;
            this.name = name;
        }
    }
}
