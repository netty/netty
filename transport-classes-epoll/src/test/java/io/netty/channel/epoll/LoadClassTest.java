/*
 * Copyright 2025 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.epoll;

import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class LoadClassTest {

    static Class<?>[] classes() {
        List<Class<?>> classes = new ArrayList<Class<?>>();
        classes.add(EpollSocketChannel.class);
        classes.add(EpollServerSocketChannel.class);
        classes.add(EpollDatagramChannel.class);

        classes.add(EpollDomainSocketChannel.class);
        classes.add(EpollServerDomainSocketChannel.class);
        classes.add(EpollDomainDatagramChannel.class);

        classes.add(EpollEventLoopGroup.class);

        return classes.toArray(new Class<?>[0]);
    }

    @ParameterizedTest
    @MethodSource("classes")
    public void testLoadClassesWorkWithoutNativeLib(final Class<?> clazz) {
        // Force loading of class.
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                Class.forName(clazz.getName());
            }
        });
    }
}
