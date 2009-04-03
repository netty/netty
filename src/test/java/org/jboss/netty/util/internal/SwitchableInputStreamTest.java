/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
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
package org.jboss.netty.util.internal;

import static org.easymock.EasyMock.*;
import static org.easymock.classextension.EasyMock.*;
import static org.junit.Assert.*;

import java.io.InputStream;

import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
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
