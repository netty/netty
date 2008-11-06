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
package org.jboss.netty.util;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class UriQueryDecoder {

    final String uri;

    private String path;

    private Map<String, List<String>> params = new HashMap<String, List<String>>();

    public UriQueryDecoder(String uri) {
        this.uri = uri;
    }

    public String getPath() {
        //decode lazily
        if(path == null) {
            if(uri.contains("?")) {
                decode();
            }
            else {
                path = uri;
            }
        }
        return path;
    }

    public Map<String, List<String>> getParameters() {
        return params;
    }

    private void decode() {
        String[] split = uri.split("\\?", 2);
        path = split[0];
        decodeParams(split[1]);
    }
    //todo - just replacing spaces at the minute, should we check for all characters or leave this to the user?
    private void decodeParams(String s) {
        String[] params = s.split("&");
        for (String param : params) {
            String[] split = param.split("=");
            String key = removeSpaceDelimeters(split[0]);
            List<String> values = this.params.get(key);
            if(values == null) {
                values = new ArrayList<String>();
                this.params.put(key,values);
            }
            values.add(removeSpaceDelimeters(split[1]));
        }
    }

    private String removeSpaceDelimeters(String s) {
        return s.replaceAll("%20", " ");
    }
}
