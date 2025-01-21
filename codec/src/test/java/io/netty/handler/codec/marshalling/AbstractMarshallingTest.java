/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.marshalling;

import io.netty.util.internal.PlatformDependent;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public abstract class AbstractMarshallingTest {

    static final String SERIAL_FACTORY = "serial";
    static final String RIVER_FACTORY = "river";

    @BeforeAll
    public static void checkSupported() throws Throwable {
        Throwable error = null;
        try {
            checkFactorySupported(Marshalling.getProvidedMarshallerFactory(SERIAL_FACTORY));
            checkFactorySupported(Marshalling.getProvidedMarshallerFactory(RIVER_FACTORY));
        } catch (Throwable cause) {
            // This may fail on Java 9+ depending on which command-line arguments are used when building.
            if (PlatformDependent.javaVersion() < 9) {
                throw cause;
            }
            error = cause;
        }
        assumeTrue(error == null, error + " was not null");
    }

    private static void checkFactorySupported(MarshallerFactory factory) throws IOException {
        factory.createMarshaller(new MarshallingConfiguration()).close();
        factory.createUnmarshaller(new MarshallingConfiguration()).close();
    }
}
