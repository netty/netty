/*
 * Copyright 2020 The Netty Project
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

final class NativeStaticallyReferencedJniMethods {

    static native int quiche_protocol_version();
    static native int quiche_max_conn_id_len();
    static native int quiche_shutdown_read();
    static native int quiche_shutdown_write();

    static native int quiche_err_done();
    static native int quiche_err_buffer_too_short();
    static native int quiche_err_unknown_version();
    static native int quiche_err_invalid_frame();
    static native int quiche_err_invalid_packet();
    static native int quiche_err_invalid_state();
    static native int quiche_err_invalid_stream_state();
    static native int quiche_err_invalid_transport_param();
    static native int quiche_err_crypto_fail();
    static native int quiche_err_tls_fail();
    static native int quiche_err_flow_control();
    static native int quiche_err_stream_limit();
    static native int quiche_err_final_size();
    static native int quiche_err_congestion_control();

    static native int quiche_cc_reno();
    static native int quiche_cc_cubic();

    private NativeStaticallyReferencedJniMethods() { }
}
