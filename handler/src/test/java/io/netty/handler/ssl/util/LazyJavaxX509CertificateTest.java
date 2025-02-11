/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.ssl.util;

import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LazyJavaxX509CertificateTest {

    private static final String CERTIFICATE =
            "-----BEGIN CERTIFICATE-----\n" +
                    "MIIEITCCAwmgAwIBAgIUaLL8vLOhWLCLXVHEJqXJhfmsTB8wDQYJKoZIhvcNAQEL\n" +
                    "BQAwgawxCzAJBgNVBAYTAlVTMRYwFAYDVQQIDA1NYXNzYWNodXNldHRzMRIwEAYD\n" +
                    "VQQHDAlDYW1icmlkZ2UxGDAWBgNVBAoMD25ldHR5IHRlc3QgY2FzZTEYMBYGA1UE\n" +
                    "CwwPbmV0dHkgdGVzdCBjYXNlMRgwFgYDVQQDDA9uZXR0eSB0ZXN0IGNhc2UxIzAh\n" +
                    "BgkqhkiG9w0BCQEWFGNjb25uZWxsQGh1YnNwb3QuY29tMB4XDTI0MDEyMTE5MzMy\n" +
                    "MFoXDTI1MDEyMDE5MzMyMFowgawxCzAJBgNVBAYTAlVTMRYwFAYDVQQIDA1NYXNz\n" +
                    "YWNodXNldHRzMRIwEAYDVQQHDAlDYW1icmlkZ2UxGDAWBgNVBAoMD25ldHR5IHRl\n" +
                    "c3QgY2FzZTEYMBYGA1UECwwPbmV0dHkgdGVzdCBjYXNlMRgwFgYDVQQDDA9uZXR0\n" +
                    "eSB0ZXN0IGNhc2UxIzAhBgkqhkiG9w0BCQEWFGNjb25uZWxsQGh1YnNwb3QuY29t\n" +
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAy+qzEZpQMjVdLj0siUcG\n" +
                    "y8LIHOW4S+tgHIKFkF865qWq6FVGbROe2Z0f5W6yIamZkdxzptT0iv+8S5okNNeW\n" +
                    "2NbsN/HNJIRtWfxku1Jh1gBqSkAYIjXyq7+20hIaJTzzxqike9M/Lc14EGb33Ja/\n" +
                    "kDPRV3UtiM3Ntf3eALXKbrWptkbgQngCaTgtfg8IkMAEpP270wZ9fW0lDHv3NPPt\n" +
                    "Zt0QSJzWSqWfu+l4ayvcUQYyNJesx9YmTHSJu69lvT4QApoX8FEiHfNCJ28R50CS\n" +
                    "aIgOpCWUvkH7rqx0p9q393uJRS/S6RlLbU30xUN1fNrVmP/XAapfy+R0PSgiUi8o\n" +
                    "EQIDAQABozkwNzAWBgNVHRIEDzANggt3d3cuZm9vLmNvbTAdBgNVHQ4EFgQUl4FD\n" +
                    "Y8jJ/JHJR68YqPsGUjUJuwgwDQYJKoZIhvcNAQELBQADggEBADVzivYz2M0qsWUc\n" +
                    "jXjCHymwTIr+7ud10um53FbYEAfKWsIY8Pp35fKpFzUwc5wVdCnLU86K/YMKRzNB\n" +
                    "zL2Auow3PJFRvXecOv7dWxNlNneLDcwbVrdNRu6nQXmZUgyz0oUKuJbF+JGtI+7W\n" +
                    "kRw7yhBfki+UCSQWeDqvaWzgmA4Us0N8NFq3euAs4xFbMMPMQWrT9Z7DGchCeRiB\n" +
                    "dkQBvh88vbR3v2Saq14W4Wt5rj2++vXWGQSeAQL6nGbOwc3ohW6isNNV0eGQQTmS\n" +
                    "khS2d/JDZq2XL5RGexf3CA6YYzWiTr9YZHNjuobvLH7mVnA2c8n6Zty/UhfnuK1x\n" +
                    "JbkleFk=\n" +
                    "-----END CERTIFICATE-----";

    @Test
    public void testLazyX509Certificate() throws Exception {
        X509Certificate x509Certificate;
        try {
            x509Certificate = X509Certificate.getInstance(CERTIFICATE.getBytes(CharsetUtil.UTF_8));
        } catch (CertificateException e) {
            Assumptions.abort("JDK does not support creating " + X509Certificate.class);
            // JDK does not support this anymore
            return;
        }
        LazyJavaxX509Certificate lazyX509Certificate =
                new LazyJavaxX509Certificate(CERTIFICATE.getBytes(CharsetUtil.UTF_8));
        assertEquals(x509Certificate.getVersion(), lazyX509Certificate.getVersion());
        assertEquals(x509Certificate.getSerialNumber(), lazyX509Certificate.getSerialNumber());
        assertEquals(x509Certificate.getIssuerDN(), lazyX509Certificate.getIssuerDN());
        assertEquals(x509Certificate.getSubjectDN(), lazyX509Certificate.getSubjectDN());
        assertEquals(x509Certificate.getNotBefore(), lazyX509Certificate.getNotBefore());
        assertEquals(x509Certificate.getNotAfter(), lazyX509Certificate.getNotAfter());
        assertEquals(x509Certificate.getSigAlgName(), lazyX509Certificate.getSigAlgName());
        assertEquals(x509Certificate.getSigAlgOID(), lazyX509Certificate.getSigAlgOID());
        assertArrayEquals(x509Certificate.getSigAlgParams(), lazyX509Certificate.getSigAlgParams());
    }
}
