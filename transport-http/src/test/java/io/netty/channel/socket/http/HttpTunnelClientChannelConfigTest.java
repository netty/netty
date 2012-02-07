/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.channel.socket.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.netty.channel.socket.SocketChannelConfig;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests HTTP tunnel client channel config
 */
@RunWith(JMock.class)
public class HttpTunnelClientChannelConfigTest {

    JUnit4Mockery mockContext = new JUnit4Mockery();

    SocketChannelConfig sendChannelConfig;

    SocketChannelConfig pollChannelConfig;

    HttpTunnelClientChannelConfig config;

    @Before
    public void setUp() {
        sendChannelConfig =
                mockContext
                        .mock(SocketChannelConfig.class, "sendChannelConfig");
        pollChannelConfig =
                mockContext
                        .mock(SocketChannelConfig.class, "pollChannelConfig");

        config =
                new HttpTunnelClientChannelConfig(sendChannelConfig,
                        pollChannelConfig);
    }

    @Test
    public void testGetReceiveBufferSize() {
        mockContext.checking(new Expectations() {
            {
                one(pollChannelConfig).getReceiveBufferSize();
                will(returnValue(100));
            }
        });

        assertEquals(100, config.getReceiveBufferSize());
    }

    @Test
    public void testGetSendBufferSize() {
        mockContext.checking(new Expectations() {
            {
                one(pollChannelConfig).getSendBufferSize();
                will(returnValue(100));
            }
        });

        assertEquals(100, config.getSendBufferSize());
    }

    @Test
    public void testGetSoLinger() {
        mockContext.checking(new Expectations() {
            {
                one(pollChannelConfig).getSoLinger();
                will(returnValue(100));
            }
        });

        assertEquals(100, config.getSoLinger());
    }

    @Test
    public void testTrafficClass() {
        mockContext.checking(new Expectations() {
            {
                one(pollChannelConfig).getTrafficClass();
                will(returnValue(1));
            }
        });

        assertEquals(1, config.getTrafficClass());
    }

    @Test
    public void testIsKeepAlive() {
        mockContext.checking(new Expectations() {
            {
                one(pollChannelConfig).isKeepAlive();
                will(returnValue(true));
            }
        });

        assertTrue(config.isKeepAlive());
    }

    @Test
    public void testIsReuseAddress() {
        mockContext.checking(new Expectations() {
            {
                one(pollChannelConfig).isReuseAddress();
                will(returnValue(true));
            }
        });

        assertTrue(config.isReuseAddress());
    }

    @Test
    public void testIsTcpNoDelay() {
        mockContext.checking(new Expectations() {
            {
                one(pollChannelConfig).isTcpNoDelay();
                will(returnValue(true));
            }
        });

        assertTrue(config.isTcpNoDelay());
    }

    @Test
    public void testSetKeepAlive() {
        mockContext.checking(new Expectations() {
            {
                one(pollChannelConfig).setKeepAlive(true);
                one(sendChannelConfig).setKeepAlive(true);
            }
        });

        config.setKeepAlive(true);
    }

    @Test
    public void testSetPerformancePreferences() {
        mockContext.checking(new Expectations() {
            {
                one(pollChannelConfig).setPerformancePreferences(100, 200, 300);
                one(sendChannelConfig).setPerformancePreferences(100, 200, 300);
            }
        });

        config.setPerformancePreferences(100, 200, 300);
    }

    @Test
    public void testSetReceiveBufferSize() {
        mockContext.checking(new Expectations() {
            {
                one(pollChannelConfig).setReceiveBufferSize(100);
                one(sendChannelConfig).setReceiveBufferSize(100);
            }
        });

        config.setReceiveBufferSize(100);
    }

    @Test
    public void testSetReuseAddress() {
        mockContext.checking(new Expectations() {
            {
                one(pollChannelConfig).setReuseAddress(true);
                one(sendChannelConfig).setReuseAddress(true);
            }
        });

        config.setReuseAddress(true);
    }

    @Test
    public void testSetSendBufferSize() {
        mockContext.checking(new Expectations() {
            {
                one(pollChannelConfig).setSendBufferSize(100);
                one(sendChannelConfig).setSendBufferSize(100);
            }
        });

        config.setSendBufferSize(100);
    }

    @Test
    public void testSetSoLinger() {
        mockContext.checking(new Expectations() {
            {
                one(pollChannelConfig).setSoLinger(100);
                one(sendChannelConfig).setSoLinger(100);
            }
        });

        config.setSoLinger(100);
    }

    @Test
    public void testTcpNoDelay() {
        mockContext.checking(new Expectations() {
            {
                one(pollChannelConfig).setTcpNoDelay(true);
                one(sendChannelConfig).setTcpNoDelay(true);
            }
        });

        config.setTcpNoDelay(true);
    }

    @Test
    public void testSetTrafficClass() {
        mockContext.checking(new Expectations() {
            {
                one(pollChannelConfig).setTrafficClass(1);
                one(sendChannelConfig).setTrafficClass(1);
            }
        });

        config.setTrafficClass(1);
    }

    @Test
    public void testSetHighWaterMark() {
        config.setWriteBufferHighWaterMark(128 * 1024);
        assertEquals(128 * 1024, config.getWriteBufferHighWaterMark());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetHighWaterMark_negative() {
        config.setWriteBufferHighWaterMark(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetHighWaterMark_zero() {
        config.setWriteBufferHighWaterMark(0);
    }

    @Test
    public void testSetLowWaterMark() {
        config.setWriteBufferLowWaterMark(100);
        assertEquals(100, config.getWriteBufferLowWaterMark());
    }

    @Test
    public void testSetLowWaterMark_zero() {
        // zero is permitted for the low water mark, unlike high water mark
        config.setWriteBufferLowWaterMark(0);
        assertEquals(0, config.getWriteBufferLowWaterMark());
    }

    @Test
    public void testSetHighWaterMark_lowerThanLow() {
        config.setWriteBufferLowWaterMark(100);
        try {
            config.setWriteBufferHighWaterMark(80);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals(
                    "Write buffer high water mark must be strictly greater than the low water mark",
                    e.getMessage());
        }
    }

    @Test
    public void testSetLowWaterMark_higherThanHigh() {
        config.setWriteBufferHighWaterMark(128 * 1024);
        try {
            config.setWriteBufferLowWaterMark(256 * 1024);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals(
                    "Write buffer low water mark must be strictly less than the high water mark",
                    e.getMessage());
        }
    }
}
