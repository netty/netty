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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelPromise;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;

final class Quiche {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Quiche.class);
    private static final IntObjectMap<String> ERROR_MAP = new IntObjectHashMap<>();
    private static final boolean DEBUG_LOGGING_ENABLED = logger.isDebugEnabled();

    static {
        try {
            // First, try calling a side-effect free JNI method to see if the library was already
            // loaded by the application.
            quiche_version();
        } catch (UnsatisfiedLinkError ignore) {
            // The library was not previously loaded, load it now.
            loadNativeLibrary();
        }

        // Let's enable debug logging for quiche if its enabled in our logger.
        if (DEBUG_LOGGING_ENABLED) {
            quiche_enable_debug_logging(new QuicheLogger(logger));
        }
    }

    private static void loadNativeLibrary() {
        String staticLibName = "netty_quic_quiche";
        String sharedLibName = staticLibName + '_' + PlatformDependent.normalizedArch();
        ClassLoader cl = PlatformDependent.getClassLoader(Quiche.class);
        try {
            NativeLibraryLoader.load(sharedLibName, cl);
        } catch (UnsatisfiedLinkError e1) {
            try {
                NativeLibraryLoader.load(staticLibName, cl);
                logger.debug("Failed to load {}", sharedLibName, e1);
            } catch (UnsatisfiedLinkError e2) {
                ThrowableUtil.addSuppressed(e1, e2);
                throw e1;
            }
        }
    }

    static final int QUICHE_PROTOCOL_VERSION = NativeStaticallyReferencedJniMethods.quiche_protocol_version();
    static final int QUICHE_MAX_CONN_ID_LEN = NativeStaticallyReferencedJniMethods.quiche_max_conn_id_len();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L266">QUICHE_SHUTDOWN_READ</a>.
     */
    static final int QUICHE_SHUTDOWN_READ = NativeStaticallyReferencedJniMethods.quiche_shutdown_read();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L267">QUICHE_SHUTDOWN_WRITE</a>.
     */
    static final int QUICHE_SHUTDOWN_WRITE = NativeStaticallyReferencedJniMethods.quiche_shutdown_write();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L59">QUICHE_ERR_DONE</a>.
     */
    static final int QUICHE_ERR_DONE = NativeStaticallyReferencedJniMethods.quiche_err_done();

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L62">QUICHE_ERR_BUFFER_TOO_SHORT</a>.
     */
    static final int QUICHE_ERR_BUFFER_TOO_SHORT = NativeStaticallyReferencedJniMethods.quiche_err_buffer_too_short();

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L65">QUICHE_ERR_UNKNOWN_VERSION</a>.
     */
    static final int QUICHE_ERR_UNKNOWN_VERSION = NativeStaticallyReferencedJniMethods.quiche_err_unknown_version();

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L69">QUICHE_ERR_INVALID_FRAME</a>.
     */
    static final int QUICHE_ERR_INVALID_FRAME = NativeStaticallyReferencedJniMethods.quiche_err_invalid_frame();

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L72">QUICHE_ERR_INVALID_PACKET</a>.
     */
    static final int QUICHE_ERR_INVALID_PACKET = NativeStaticallyReferencedJniMethods.quiche_err_invalid_packet();

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L76">QUICHE_ERR_INVALID_STATE</a>.
     */
    static final int QUICHE_ERR_INVALID_STATE = NativeStaticallyReferencedJniMethods.quiche_err_invalid_state();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L80">
     *     QUICHE_ERR_INVALID_STREAM_STATE</a>.
     */
    static final int QUICHE_ERR_INVALID_STREAM_STATE =
            NativeStaticallyReferencedJniMethods.quiche_err_invalid_stream_state();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L83">
     *     QUICHE_ERR_INVALID_TRANSPORT_PARAM</a>.
     */
    static final int QUICHE_ERR_INVALID_TRANSPORT_PARAM =
            NativeStaticallyReferencedJniMethods.quiche_err_invalid_transport_param();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L86">
     *     QUICHE_ERR_CRYPTO_FAIL</a>.
     */
    static final int QUICHE_ERR_CRYPTO_FAIL = NativeStaticallyReferencedJniMethods.quiche_err_crypto_fail();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L89">
     *     QUICHE_ERR_TLS_FAIL</a>.
     */
    static final int QUICHE_ERR_TLS_FAIL = NativeStaticallyReferencedJniMethods.quiche_err_tls_fail();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L92">
     *     QUICHE_ERR_FLOW_CONTROL</a>.
     */
    static final int QUICHE_ERR_FLOW_CONTROL = NativeStaticallyReferencedJniMethods.quiche_err_flow_control();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#95">
     *     QUICHE_ERR_STREAM_LIMIT</a>.
     */
    static final int QUICHE_ERR_STREAM_LIMIT = NativeStaticallyReferencedJniMethods.quiche_err_stream_limit();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#98">
     *     QUICHE_ERR_FINAL_SIZE</a>.
     */
    static final int QUICHE_ERR_FINAL_SIZE = NativeStaticallyReferencedJniMethods.quiche_err_final_size();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#101">
     *     QUICHE_ERR_CONGESTION_CONTROL</a>.
     */
    static final int QUICHE_ERR_CONGESTION_CONTROL =
            NativeStaticallyReferencedJniMethods.quiche_err_congestion_control();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L176">
     *     QUICHE_CC_RENO</a>.
     */
    static final int QUICHE_CC_RENO = NativeStaticallyReferencedJniMethods.quiche_cc_reno();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L177">
     *     QUICHE_CC_CUBIC</a>.
     */
    static final int QUICHE_CC_CUBIC = NativeStaticallyReferencedJniMethods.quiche_cc_cubic();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L105">quiche_version</a>.
     */
    static native String quiche_version();

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L227">quiche_version_is_supported</a>.
     */
    static native boolean quiche_version_is_supported(int version);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L196">quiche_header_info</a>.
     */
    static native int quiche_header_info(long bufAddr, int bufLength, int dcil, long versionAddr, long typeAddr,
                                         long scidAddr, long scidLenAddr, long dcidAddr, long dcidLenAddr,
                                         long tokenAddr, long tokenLenAddr);
    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L215">quiche_negotiate_version</a>.
     */
    static native int quiche_negotiate_version(
            long scidAddr, int scidLen, long dcidAddr, int dcidLen, long outAddr, int outLen);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L220">quiche_retry</a>.
     */
    static native int quiche_retry(long scidAddr, int scidLen, long dcidAddr, int dcidLen, long newScidAddr,
                                   int newScidLen, long tokenAddr, int tokenLen, int version, long outAddr, int outLen);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L206">quiche_accept</a>.
     */
    static native long quiche_accept(long scidAddr, int scidLen, long odcidAddr, int odcidLen, long configAddr);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L249">quiche_conn_recv</a>.
     */
    static native int quiche_conn_recv(long connAddr, long bufAddr, int bufLen);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L262">quiche_conn_send</a>.
     */
    static native int quiche_conn_send(long connAddr, long outAddr, int outLen);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L373">quiche_conn_free</a>.
     */
    static native void quiche_conn_free(long connAddr);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L211">quiche_connect</a>.
     */
    static native long quiche_connect(String server_name, long scidAddr, int scidLen, long configAddr);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L258">quiche_conn_stream_recv</a>.
     */
    static native int quiche_conn_stream_recv(long connAddr, long streamId, long outAddr, int bufLen, long finAddr);
    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L262">quiche_conn_stream_send</a>.
     */
    static native int quiche_conn_stream_send(long connAddr, long streamId, long bufAddr, int bufLen, boolean fin);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L271">quiche_conn_stream_shutdown</a>.
     */
    static native int quiche_conn_stream_shutdown(long connAddr, long streamId, int direction, long err);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L274">quiche_conn_stream_capacity</a>.
     */
    static native int quiche_conn_stream_capacity(long connAddr, long streamId);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L277">quiche_conn_stream_finished</a>.
     */
    static native boolean quiche_conn_stream_finished(long connAddr, long streamId);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L297">quiche_conn_close</a>.
     */
    static native int quiche_conn_close(long connAddr, boolean app, long err, long reasonAddr, int reasonLen);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L305">quiche_conn_is_established</a>.
     */
    static native boolean quiche_conn_is_established(long connAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L309">quiche_conn_is_in_early_data</a>.
     */
    static native boolean quiche_conn_is_in_early_data(long connAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L312">quiche_conn_is_closed</a>.
     */
    static native boolean quiche_conn_is_closed(long connAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L288">quiche_conn_timeout_as_nanos</a>.
     */
    static native long quiche_conn_timeout_as_nanos(long connAddr);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L291">
     *     quiche_conn_timeout_as_millis</a>.
     */
    static native long quiche_conn_timeout_as_millis(long connAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L294">quiche_conn_on_timeout</a>.
     */
    static native void quiche_conn_on_timeout(long connAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L282">quiche_conn_readable</a> and
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L329">quiche_stream_iter_next</a>.
     *
     * This method will fill the {@code readableStreams} array and return the number of streams that were readable. If
     * the number is the same as the length of the array you should call it again.
     */
    static native int quiche_conn_readable(long connAddr, long[] readableStreams);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L285">quiche_conn_writabe</a> and
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L329">quiche_stream_iter_next</a>.
     *
     * This method will fill the {@code writableStreams} array and return the number of streams that were writable. If
     * the number is the same as the length of the array you should call it again.
     */
    static native int quiche_conn_writable(long connAddr, long[] writableStreams);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L358">
     *     quiche_conn_dgram_max_writable_len</a>.
     */
    static native int quiche_conn_dgram_max_writable_len(long connAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L115">quiche_config_new</a>.
     */
    static native long quiche_config_new(int version);

    static native int quiche_config_load_cert_chain_from_pem_file(long configAddr, String path);
    static native int quiche_config_load_priv_key_from_pem_file(long configAddr, String path);
    static native void quiche_config_verify_peer(long configAddr, boolean value);
    static native void quiche_config_grease(long configAddr, boolean value);
    static native void quiche_config_enable_early_data(long configAddr);
    static native int quiche_config_set_application_protos(long configAddr, byte[] protos);
    static native void quiche_config_set_max_idle_timeout(long configAddr, long value);
    static native void quiche_config_set_max_udp_payload_size(long configAddr, long value);
    static native void quiche_config_set_initial_max_data(long configAddr, long value);
    static native void quiche_config_set_initial_max_stream_data_bidi_local(long configAddr, long value);
    static native void quiche_config_set_initial_max_stream_data_bidi_remote(long configAddr, long value);
    static native void quiche_config_set_initial_max_stream_data_uni(long configAddr, long value);
    static native void quiche_config_set_initial_max_streams_bidi(long configAddr, long value);
    static native void quiche_config_set_initial_max_streams_uni(long configAddr, long value);
    static native void quiche_config_set_ack_delay_exponent(long configAddr, long value);
    static native void quiche_config_set_max_ack_delay(long configAddr, long value);
    static native void quiche_config_set_disable_active_migration(long configAddr, boolean value);
    static native void quiche_config_set_cc_algorithm(long configAddr, int algo);
    static native void quiche_config_enable_hystart(long configAddr, boolean value);
    static native void quiche_config_free(long configAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L108">quiche_config_new</a>.
     */
    private static native void quiche_enable_debug_logging(QuicheLogger logger);

    static {
        ERROR_MAP.put(QUICHE_ERR_DONE, "QUICHE_ERR_DONE");
        ERROR_MAP.put(QUICHE_ERR_BUFFER_TOO_SHORT, "QUICHE_ERR_BUFFER_TOO_SHORT");
        ERROR_MAP.put(QUICHE_ERR_UNKNOWN_VERSION, "QUICHE_ERR_UNKNOWN_VERSION");
        ERROR_MAP.put(QUICHE_ERR_INVALID_FRAME, "QUICHE_ERR_INVALID_FRAME");
        ERROR_MAP.put(QUICHE_ERR_INVALID_PACKET, "QUICHE_ERR_INVALID_PACKET");
        ERROR_MAP.put(QUICHE_ERR_INVALID_STATE, "QUICHE_ERR_INVALID_STATE");
        ERROR_MAP.put(QUICHE_ERR_INVALID_STREAM_STATE, "QUICHE_ERR_INVALID_STREAM_STATE");
        ERROR_MAP.put(QUICHE_ERR_INVALID_TRANSPORT_PARAM, "QUICHE_ERR_INVALID_TRANSPORT_PARAM");
        ERROR_MAP.put(QUICHE_ERR_CRYPTO_FAIL, "QUICHE_ERR_CRYPTO_FAIL");
        ERROR_MAP.put(QUICHE_ERR_TLS_FAIL, "QUICHE_ERR_TLS_FAIL");
        ERROR_MAP.put(QUICHE_ERR_FLOW_CONTROL, "QUICHE_ERR_FLOW_CONTROL");
        ERROR_MAP.put(QUICHE_ERR_STREAM_LIMIT, "QUICHE_ERR_STREAM_LIMIT");
        ERROR_MAP.put(QUICHE_ERR_FINAL_SIZE, "QUICHE_ERR_FINAL_SIZE");
        ERROR_MAP.put(QUICHE_ERR_CONGESTION_CONTROL, "QUICHE_ERR_CONGESTION_CONTROL");
    }

    static String errorAsString(int err) {
        return ERROR_MAP.get(err);
    }

    static Exception newException(int err) {
        String errStr = errorAsString(err);
        if (err == QUICHE_ERR_TLS_FAIL) {
            return new SSLHandshakeException(errStr);
        }
        if (err == QUICHE_ERR_CRYPTO_FAIL) {
            return new SSLException(errStr);
        }
        return new IOException(errStr);
    }

    static boolean throwIfError(int res) throws Exception {
        if (res < 0) {
             if (res == Quiche.QUICHE_ERR_DONE) {
                 return true;
             }
            throw Quiche.newException(res);
        }
        return false;
    }

    static void notifyPromise(int res, ChannelPromise promise) {
        if (res < 0 && res != Quiche.QUICHE_ERR_DONE) {
            promise.setFailure(Quiche.newException(res));
        } else {
            promise.setSuccess();
        }
    }

    static String traceId(long connAddr, ByteBuf buffer) {
        // We just do the same as quiche does for the traceid but we should use Connection:trace_id() once it is exposed
        // in the c API.
        return ByteBufUtil.hexDump(buffer);
    }

    private Quiche() { }
}
