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
package org.jboss.netty.handler.codec.compression;

import com.jcraft.jzlib.ZStream;
import com.jcraft.jzlib.ZStreamException;

/**
 * Utility methods used by {@link ZlibEncoder} and {@link ZlibDecoder}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
class ZlibUtil {

    static void fail(ZStream z, String message, int resultCode) throws ZStreamException {
        throw exception(z, message, resultCode);
    }

    static ZStreamException exception(ZStream z, String message, int resultCode) {
        return new ZStreamException(message + " (" + resultCode + ")" +
                (z.msg != null? ": " + z.msg : ""));
    }

    private ZlibUtil() {
        super();
    }
}
