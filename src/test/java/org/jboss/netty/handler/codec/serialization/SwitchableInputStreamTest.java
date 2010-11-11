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
package org.jboss.netty.handler.codec.serialization;

import static org.easymock.EasyMock.*;
import static org.easymock.classextension.EasyMock.*;
import static org.junit.Assert.*;

import java.io.InputStream;

import org.junit.Test;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2116 $, $Date: 2010-02-01 15:25:23 +0900 (Mon, 01 Feb 2010) $
 *
 */
public class SwitchableInputStreamTest {

    @Test
    public void testSwitchStream() throws Exception {
        SwitchableInputStream sin = new SwitchableInputStream();

        InputStream in1 = createStrictMock(InputStream.class);
        InputStream in2 = createStrictMock(InputStream.class);
        expect(in1.read()).andReturn(1);
        replay(in1, in2);

        sin.switchStream(in1);
        assertEquals(1, sin.read());
        verify(in1, in2);
        reset(in1, in2);

        expect(in2.read()).andReturn(2);
        replay(in1, in2);

        sin.switchStream(in2);
        assertEquals(2, sin.read());
        verify(in1, in2);
    }
}
