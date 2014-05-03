/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.ssl;

import java.util.Map;

/**
 * Encapsulates the configuration parameters necessary for the construction of a SSLContextHolder.
 */
public class OpenSslConfig {
    /**
     * Thrown when required fields have not been assigned in a SSLConfiguration.
     */
    public class IncompleteConfiguration extends Exception {
        public IncompleteConfiguration(String message) {
            super(message);
        }
    }

    public static final String CERT_PATH = "ssl.cert_path";
    public static final String KEY_PATH = "ssl.key_path";
    public static final String CIPHER_SPEC = "ssl.cipher_spec";
    public static final String KEY_PASSWORD = "ssl.key_password";
    public static final String CA_PATH = "ssl.ca_path";
    public static final String NEXT_PROTOS = "ssl.next_protos";

    private String certPath;
    private String keyPath;
    private String cipherSpec;
    private String keyPassword;
    private String caPath;
    private String nextProtos;

    public OpenSslConfig(
            String certPath, String keyPath, String cipherSpec,
            String keyPassword, String caPath, String nextProtos) throws IncompleteConfiguration {
        this.certPath = certPath;
        this.keyPath = keyPath;
        this.cipherSpec = cipherSpec;
        this.keyPassword = keyPassword;
        this.caPath = caPath;
        this.nextProtos = nextProtos;
        verifyCorrectConstruction();
    }

    /**
     * Construct a SSLConfiguration with data from the given Map.
     *
     * @param map The Map containing keys corresponding to the values of  CERT_PATH, KEY_PATH,
     * CIPHER_SPEC, KEY_PASSWORD, and CA_PATH.
     *
     * @throws IncompleteConfiguration if required fields are not assigned.
     */
    public OpenSslConfig(Map<String, String> map) throws IncompleteConfiguration {
        assignFromMap(map);
        verifyCorrectConstruction();
    }

    /**
     * Assert all required fields are present, and set defaults for unassigned optional fields.
     * @throws IncompleteConfiguration if required fields are not assigned
     */
    private void verifyCorrectConstruction() throws IncompleteConfiguration {
        assertRequiredFieldsAssigned();
        assignDefaultsToUnassignedOptionalFields();
    }

    /**
     * Assign fields from the given Map.
     *
     * @param map the Map to assign from.
     * @throws IncompleteConfiguration if required fields are not assigned
     */
    private void assignFromMap(Map<String, String> map) throws IncompleteConfiguration {
        // Assign required fields
        this.certPath = map.get(CERT_PATH);
        this.keyPath = map.get(KEY_PATH);
        this.cipherSpec = map.get(CIPHER_SPEC);

        // Assign optional fields
        this.keyPassword = map.get(KEY_PASSWORD);
        this.caPath = map.get(CA_PATH);
        this.nextProtos = map.get(NEXT_PROTOS);
    }

    /**
     * Assert that all required fields have been assigned.
     *
     * @throws IncompleteConfiguration if a required value is not set.
     */
    private void assertRequiredFieldsAssigned()
            throws IncompleteConfiguration {
        if (certPath == null || certPath.isEmpty()) {
            throw new IncompleteConfiguration("certPath");
        }

        if (keyPath == null || keyPath.isEmpty()) {
            throw new IncompleteConfiguration("keyPath");
        }

        if (cipherSpec == null || cipherSpec.isEmpty()) {
            throw new IncompleteConfiguration("cipherSpec");
        }
    }

    private void assignDefaultsToUnassignedOptionalFields() {
        if (keyPassword == null) {
            keyPassword = "";
        }
        if (caPath == null) {
            caPath = "";
        }
        if (nextProtos == null) {
            nextProtos = "";
        }
    }

    public String getCertPath() {
        return certPath;
    }

    public void setCertPath(String certPath) {
        this.certPath = certPath;
    }

    public String getKeyPath() {
        return keyPath;
    }

    public void setKeyPath(String keyPath) {
        this.keyPath = keyPath;
    }

    public String getCipherSpec() {
        return cipherSpec;
    }

    public void setCipherSpec(String cipherSpec) {
        this.cipherSpec = cipherSpec;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public void setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
    }

    public String getCaPath() {
        return caPath;
    }

    public void setCaPath(String caPath) {
        this.caPath = caPath;
    }

    public void setNextProtos(String nextProtos) {
        this.nextProtos = nextProtos;
    }

    public String getNextProtos() {
        return nextProtos;
    }
}
