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
package io.netty.handler.ssl;

import io.netty.internal.tcnative.CertificateVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OpenSslCertificateExceptionTest {

    @BeforeAll
    public static void ensureOpenSsl() {
        OpenSsl.ensureAvailability();
    }

    @Test
    public void testValidErrorCode() throws Exception {
        Field[] fields = CertificateVerifier.class.getFields();
        for (Field field : fields) {
            if (field.isAccessible()) {
                int errorCode = field.getInt(null);
                OpenSslCertificateException exception = new OpenSslCertificateException(errorCode);
                assertEquals(errorCode, exception.errorCode());
            }
        }
    }

    @Test
    public void testNonValidErrorCode() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                new OpenSslCertificateException(Integer.MIN_VALUE);
            }
        });
    }

    @Test
    public void testCanBeInstancedWhenOpenSslIsNotAvailable() {
        new OpenSslCertificateException(0);
    }
}
