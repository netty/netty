/*
 * Copyright 2022 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PemReaderTest {
    @Test
    public void mustBeAbleToReadMultipleCertificates() throws Exception {
        byte[] certs = ("-----BEGIN CERTIFICATE-----\n" +
                "MIICqjCCAZKgAwIBAgIIEaz8uuDHTcIwDQYJKoZIhvcNAQELBQAwFDESMBAGA1UEAwwJbG9jYWxo\n" +
                "b3N0MCAXDTIxMDYxNjE3MjYyOFoYDzk5OTkxMjMxMjM1OTU5WjAUMRIwEAYDVQQDDAlsb2NhbGhv\n" +
                "c3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCVjENomtpMqHkg1yJ/uYZgSWmf/0Gb\n" +
                "U4yMDf30muPvMYb3gO6peEnoXa2b0WDOjLbLrcltp1YdjTlLhRRTYgDo9TAvHoUdoMGlTnfQtQne\n" +
                "2o+/92bnlZTroRIjUT0lqSxQ6UNXcOi9tNqVD4tML3vk20fudwBur8Plx+3hOhM/v64GbV46k06+\n" +
                "AblrFwBt9u6V0uIVtvgraOd+NgL4yNf594uND30mbB7Q7xe/Y6DiPhI6cVI/CbLlXVwKLvC5OziS\n" +
                "JKZ7svP0K3DBRxk+dOD9pg4SdaAEQVtR734ZlDh1XJ+mZssuDDda3NGZAjpCU4rkeV/J3Tr5KKMD\n" +
                "g3NEOmifAgMBAAEwDQYJKoZIhvcNAQELBQADggEBABejZGeRNCyPdIqac6cyAf99JPp5OySEMWHU\n" +
                "QXVCHEbQ8Eh6hsmrXSEaZS2zy/5dixPb5Rin0xaX5fqZdfLIUc0Mw/zXV/RiOIjrtUKez15Ko/4K\n" +
                "ONyxELjUq+SaJx2C1UcKEMfQBeq7O6XO60CLQ2AtVozmvJU9pt4KYQv0Kr+br3iNFpRuR43IYHyx\n" +
                "HP7QsD3L3LEqIqW/QtYEnAAngZofUiq0XELh4GB0L8DbcSJIxfZmYagFl7c2go9OZPD14mlaTnMV\n" +
                "Pjd+OkwMif5T7v+r+KVSmDSMQwa+NfW+V6Xngg5/bN3kWHdw9qFQGANojl9wsRVN/B3pu3Cc2XFD\n" +
                "MmQ=\n" +
                "-----END CERTIFICATE-----\n" +
                "-----BEGIN CERTIFICATE-----\n" +
                "MIICqjCCAZKgAwIBAgIIIsUS6UkDau4wDQYJKoZIhvcNAQELBQAwFDESMBAGA1UEAwwJbG9jYWxo\n" +
                "b3N0MCAXDTIxMDYxNjE3MjYyOFoYDzk5OTkxMjMxMjM1OTU5WjAUMRIwEAYDVQQDDAlsb2NhbGhv\n" +
                "c3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCTZmahFZXB0Dv3N8t6gfXTeqhTxRng\n" +
                "mIBBPmrbZBODrZm06vrR5KNhxB2FhWIq1Yu8xXXv8sO+PaO2Sw/h6TeslRJ4EkrNd9zmYhT2cJvP\n" +
                "d1CtkX5EHyMZRUKj7Eg4eUO1k/+JnhMmaY+nUAG7fCtvs8pS9SEXbEqYW7S4AQ1oopbCAMqQekly\n" +
                "KCdnjGlVhXwL2Lj2rr/uw1Fc2+WvY/leQGo0rbIqoc7OSAktsP+MXI6iQ1RWJOec15V6iFRzcdE3\n" +
                "Q4ODSMZ/R8wm9DH+4hkeQNPMbcc1wlvVZpDZ/FZegr1XimcYcJr2AoAQf3Xe1yFKAtBMXCjCIGm8\n" +
                "veCQ+xeHAgMBAAEwDQYJKoZIhvcNAQELBQADggEBAGyV+dib2MdpenbntKk7PdZEx+/vNl9cEpwL\n" +
                "BfWmQN/j2RmHUxrUM+PVkLTgyCq8okdCKqCvraKwBkF6vlzp4u5CL4L323z+/uxAi6pzbcWnG1EH\n" +
                "JpSkf1OhTUFu6UhLfpg3XqeiIujYdVZTpHr7KHVLRYUSQPprt4HjLZeCIg4P2pZ0yQ3SEBhVed89\n" +
                "GMj/+O4jjvuZv5NQc57NpMIrE9fNINczLG1CPTgnhvqMP42W6ahBuexQUe4gP+jmB/BZmBYKoauU\n" +
                "mPBKruq3mNuoXtbHufv5I7CFVXNgJ0/aT+lvEkQ4IlCIcJyvTgyUTOQVbqDp+SswymAIRowaRdxa\n" +
                "7Ss=\n" +
                "-----END CERTIFICATE-----\n").getBytes(CharsetUtil.US_ASCII);
        ByteArrayInputStream in = new ByteArrayInputStream(certs);
        ByteBuf[] bufs = PemReader.readCertificates(in);
        in.close();
        assertThat(bufs.length).isEqualTo(2);
        for (ByteBuf buf : bufs) {
            buf.release();
        }
    }

    @Test
    public void mustBeAbleToReadPrivateKey() throws Exception {
        byte[] key = ("-----BEGIN PRIVATE KEY-----\n" +
                "MIICqjCCAZKgAwIBAgIIEaz8uuDHTcIwDQYJKoZIhvcNAQELBQAwFDESMBAGA1UEAwwJbG9jYWxo\n" +
                "b3N0MCAXDTIxMDYxNjE3MjYyOFoYDzk5OTkxMjMxMjM1OTU5WjAUMRIwEAYDVQQDDAlsb2NhbGhv\n" +
                "c3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCVjENomtpMqHkg1yJ/uYZgSWmf/0Gb\n" +
                "U4yMDf30muPvMYb3gO6peEnoXa2b0WDOjLbLrcltp1YdjTlLhRRTYgDo9TAvHoUdoMGlTnfQtQne\n" +
                "2o+/92bnlZTroRIjUT0lqSxQ6UNXcOi9tNqVD4tML3vk20fudwBur8Plx+3hOhM/v64GbV46k06+\n" +
                "AblrFwBt9u6V0uIVtvgraOd+NgL4yNf594uND30mbB7Q7xe/Y6DiPhI6cVI/CbLlXVwKLvC5OziS\n" +
                "JKZ7svP0K3DBRxk+dOD9pg4SdaAEQVtR734ZlDh1XJ+mZssuDDda3NGZAjpCU4rkeV/J3Tr5KKMD\n" +
                "g3NEOmifAgMBAAEwDQYJKoZIhvcNAQELBQADggEBABejZGeRNCyPdIqac6cyAf99JPp5OySEMWHU\n" +
                "QXVCHEbQ8Eh6hsmrXSEaZS2zy/5dixPb5Rin0xaX5fqZdfLIUc0Mw/zXV/RiOIjrtUKez15Ko/4K\n" +
                "ONyxELjUq+SaJx2C1UcKEMfQBeq7O6XO60CLQ2AtVozmvJU9pt4KYQv0Kr+br3iNFpRuR43IYHyx\n" +
                "HP7QsD3L3LEqIqW/QtYEnAAngZofUiq0XELh4GB0L8DbcSJIxfZmYagFl7c2go9OZPD14mlaTnMV\n" +
                "Pjd+OkwMif5T7v+r+KVSmDSMQwa+NfW+V6Xngg5/bN3kWHdw9qFQGANojl9wsRVN/B3pu3Cc2XFD\n" +
                "MmQ=\n" +
                "-----END PRIVATE KEY-----\n").getBytes(CharsetUtil.US_ASCII);
        ByteArrayInputStream in = new ByteArrayInputStream(key);
        ByteBuf buf = PemReader.readPrivateKey(in);
        in.close();
        assertThat(buf.readableBytes()).isEqualTo(686);
        buf.release();
    }
}
