/*
 * Copyright 2021 The Netty Project
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
package io.netty.incubator.codec.quic;

final class BoringSSLNativeStaticallyReferencedJniMethods {
    static native int ssl_verify_none();
    static native int ssl_verify_peer();
    static native int ssl_verify_fail_if_no_peer_cert();

    static native int x509_v_ok();
    static native int x509_v_err_cert_has_expired();
    static native int x509_v_err_cert_not_yet_valid();
    static native int x509_v_err_cert_revoked();
    static native int x509_v_err_unspecified();

    private BoringSSLNativeStaticallyReferencedJniMethods() { }
}
