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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelPromise;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class Quiche {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Quiche.class);
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
        // This needs to be kept in sync with what is defined in netty_quic_quiche.c
        String libName = "netty_quiche" + '_' + PlatformDependent.normalizedOs()
                + '_' + PlatformDependent.normalizedArch();
        ClassLoader cl = PlatformDependent.getClassLoader(Quiche.class);
        try {
            NativeLibraryLoader.load(libName, cl);
        } catch (UnsatisfiedLinkError e) {
            logger.debug("Failed to load {}", libName, e);
            throw e;
        }
    }

    static final int QUICHE_PROTOCOL_VERSION = QuicheNativeStaticallyReferencedJniMethods.quiche_protocol_version();
    static final int QUICHE_MAX_CONN_ID_LEN = QuicheNativeStaticallyReferencedJniMethods.quiche_max_conn_id_len();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L266">QUICHE_SHUTDOWN_READ</a>.
     */
    static final int QUICHE_SHUTDOWN_READ = QuicheNativeStaticallyReferencedJniMethods.quiche_shutdown_read();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L267">QUICHE_SHUTDOWN_WRITE</a>.
     */
    static final int QUICHE_SHUTDOWN_WRITE = QuicheNativeStaticallyReferencedJniMethods.quiche_shutdown_write();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L59">QUICHE_ERR_DONE</a>.
     */
    static final int QUICHE_ERR_DONE = QuicheNativeStaticallyReferencedJniMethods.quiche_err_done();

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L62">QUICHE_ERR_BUFFER_TOO_SHORT</a>.
     */
    static final int QUICHE_ERR_BUFFER_TOO_SHORT =
            QuicheNativeStaticallyReferencedJniMethods.quiche_err_buffer_too_short();

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L65">QUICHE_ERR_UNKNOWN_VERSION</a>.
     */
    static final int QUICHE_ERR_UNKNOWN_VERSION =
            QuicheNativeStaticallyReferencedJniMethods.quiche_err_unknown_version();

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L69">QUICHE_ERR_INVALID_FRAME</a>.
     */
    static final int QUICHE_ERR_INVALID_FRAME = QuicheNativeStaticallyReferencedJniMethods.quiche_err_invalid_frame();

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L72">QUICHE_ERR_INVALID_PACKET</a>.
     */
    static final int QUICHE_ERR_INVALID_PACKET = QuicheNativeStaticallyReferencedJniMethods.quiche_err_invalid_packet();

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L76">QUICHE_ERR_INVALID_STATE</a>.
     */
    static final int QUICHE_ERR_INVALID_STATE = QuicheNativeStaticallyReferencedJniMethods.quiche_err_invalid_state();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L80">
     *     QUICHE_ERR_INVALID_STREAM_STATE</a>.
     */
    static final int QUICHE_ERR_INVALID_STREAM_STATE =
            QuicheNativeStaticallyReferencedJniMethods.quiche_err_invalid_stream_state();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L83">
     *     QUICHE_ERR_INVALID_TRANSPORT_PARAM</a>.
     */
    static final int QUICHE_ERR_INVALID_TRANSPORT_PARAM =
            QuicheNativeStaticallyReferencedJniMethods.quiche_err_invalid_transport_param();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L86">
     *     QUICHE_ERR_CRYPTO_FAIL</a>.
     */
    static final int QUICHE_ERR_CRYPTO_FAIL = QuicheNativeStaticallyReferencedJniMethods.quiche_err_crypto_fail();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L89">
     *     QUICHE_ERR_TLS_FAIL</a>.
     */
    static final int QUICHE_ERR_TLS_FAIL = QuicheNativeStaticallyReferencedJniMethods.quiche_err_tls_fail();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L92">
     *     QUICHE_ERR_FLOW_CONTROL</a>.
     */
    static final int QUICHE_ERR_FLOW_CONTROL = QuicheNativeStaticallyReferencedJniMethods.quiche_err_flow_control();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#95">
     *     QUICHE_ERR_STREAM_LIMIT</a>.
     */
    static final int QUICHE_ERR_STREAM_LIMIT = QuicheNativeStaticallyReferencedJniMethods.quiche_err_stream_limit();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#98">
     *     QUICHE_ERR_FINAL_SIZE</a>.
     */
    static final int QUICHE_ERR_FINAL_SIZE = QuicheNativeStaticallyReferencedJniMethods.quiche_err_final_size();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#101">
     *     QUICHE_ERR_CONGESTION_CONTROL</a>.
     */
    static final int QUICHE_ERR_CONGESTION_CONTROL =
            QuicheNativeStaticallyReferencedJniMethods.quiche_err_congestion_control();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L176">
     *     QUICHE_CC_RENO</a>.
     */
    static final int QUICHE_CC_RENO = QuicheNativeStaticallyReferencedJniMethods.quiche_cc_reno();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L177">
     *     QUICHE_CC_CUBIC</a>.
     */
    static final int QUICHE_CC_CUBIC = QuicheNativeStaticallyReferencedJniMethods.quiche_cc_cubic();

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
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L206">quiche_accept</a> but
     * used for accepting QUIC connections without token validation.
     */
    static native long quiche_accept_no_token(long scidAddr, int scidLen, long configAddr);

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
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/
     * 35e38d987c1e53ef2bd5f23b754c50162b5adac8/include/quiche.h#L278">quiche_conn_stream_priority</a>.
     */
    static native int quiche_conn_stream_priority(
            long connAddr, long streamId, byte urgency, boolean incremental);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/
     * 35e38d987c1e53ef2bd5f23b754c50162b5adac8/include/quiche.h#L312">quiche_conn_trace_id</a>.
     */
    static native byte[] quiche_conn_trace_id(long connAddr);

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
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L301">
     *     quiche_conn_application_proto</a>.
     */
    static native byte[] quiche_conn_application_proto(long connAddr);

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
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L361">quiche_conn_stats</a>.
     * The implementation relies on all fields of
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L340">quiche_stats</a> being numerical.
     * The assumption made allows passing primitive array rather than dealing with objects.
     */
    static native long[] quiche_conn_stats(long connAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L288">quiche_conn_timeout_as_nanos</a>.
     */
    static native long quiche_conn_timeout_as_nanos(long connAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L294">quiche_conn_on_timeout</a>.
     */
    static native void quiche_conn_on_timeout(long connAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L282">quiche_conn_readable</a>.
     */
    static native long quiche_conn_readable(long connAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L329">quiche_stream_iter_next</a>.
     *
     * This method will fill the {@code streamIds} array and return the number of streams that were filled into
     * the array. If the number is the same as the length of the array you should call it again until it returns
     * less to ensure you process all the streams later on.
     */
    static native int quiche_stream_iter_next(long iterAddr, long[] streamIds);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L332">quiche_stream_iter_free</a>.
     *
     */
    static native void quiche_stream_iter_free(long iterAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L358">
     *     quiche_conn_dgram_max_writable_len</a>.
     */
    static native int quiche_conn_dgram_max_writable_len(long connAddr);

    /**
     * See
     * <a href=https://github.com/cloudflare/quiche/blob/
     * 9d0c677ef1411b24d720b5c8b73bcc94b5535c29/include/quiche.h#L381">
     *     quiche_conn_dgram_recv_front_len</a>.
     */
    static native int quiche_conn_dgram_recv_front_len(long connAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L361">
     *     quiche_conn_dgram_recv</a>.
     */
    static native int quiche_conn_dgram_recv(long connAddr, long buf, int size);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L3651">
     *     quiche_conn_dgram_send</a>.
     */
    static native int quiche_conn_dgram_send(long connAddr, long buf, int size);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L115">quiche_config_new</a>.
     */
    static native long quiche_config_new(int version);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L118">
     *     quiche_config_load_cert_chain_from_pem_file</a>.
     */
    static native int quiche_config_load_cert_chain_from_pem_file(long configAddr, String path);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#122">
     *     quiche_config_load_priv_key_from_pem_file</a>.
     */
    static native int quiche_config_load_priv_key_from_pem_file(long configAddr, String path);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#126">
     *     quiche_config_verify_peer</a>.
     */
    static native void quiche_config_verify_peer(long configAddr, boolean value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#129">
     *     quiche_config_grease</a>.
     */
    static native void quiche_config_grease(long configAddr, boolean value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#135">
     *     quiche_config_enable_early_data</a>.
     */
    static native void quiche_config_enable_early_data(long configAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#138">
     *     quiche_config_set_application_protos</a>.
     */
    static native int quiche_config_set_application_protos(long configAddr, byte[] protos);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#143">
     *     quiche_config_set_max_idle_timeout</a>.
     */
    static native void quiche_config_set_max_idle_timeout(long configAddr, long value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/
     * 35e38d987c1e53ef2bd5f23b754c50162b5adac8/include/quiche.h#L150">
     *     quiche_config_set_max_recv_udp_payload_size</a>.
     */
    static native void quiche_config_set_max_recv_udp_payload_size(long configAddr, long value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/
     * 35e38d987c1e53ef2bd5f23b754c50162b5adac8/include/quiche.h#L153">
     *     quiche_config_set_max_recv_udp_payload_size</a>.
     */
    static native void quiche_config_set_max_send_udp_payload_size(long configAddr, long value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#149">
     *     quiche_config_set_initial_max_data</a>.
     */
    static native void quiche_config_set_initial_max_data(long configAddr, long value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#152">
     *     quiche_config_set_initial_max_stream_data_bidi_local</a>.
     */
    static native void quiche_config_set_initial_max_stream_data_bidi_local(long configAddr, long value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#155">
     *     quiche_config_set_initial_max_stream_data_bidi_remote</a>.
     */
    static native void quiche_config_set_initial_max_stream_data_bidi_remote(long configAddr, long value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#158">
     *     quiche_config_set_initial_max_stream_data_uni</a>.
     */
    static native void quiche_config_set_initial_max_stream_data_uni(long configAddr, long value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#161">
     *     quiche_config_set_initial_max_streams_bidi</a>.
     */
    static native void quiche_config_set_initial_max_streams_bidi(long configAddr, long value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#164">
     *     quiche_config_set_initial_max_streams_uni</a>.
     */
    static native void quiche_config_set_initial_max_streams_uni(long configAddr, long value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#167">
     *     quiche_config_set_ack_delay_exponent</a>.
     */
    static native void quiche_config_set_ack_delay_exponent(long configAddr, long value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#170">
     *     quiche_config_set_max_ack_delay</a>.
     */
    static native void quiche_config_set_max_ack_delay(long configAddr, long value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#173">
     *     quiche_config_set_disable_active_migration</a>.
     */
    static native void quiche_config_set_disable_active_migration(long configAddr, boolean value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#181">
     *     quiche_config_set_cc_algorithm</a>.
     */
    static native void quiche_config_set_cc_algorithm(long configAddr, int algo);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#184">
     *     quiche_config_enable_hystart</a>.
     */
    static native void quiche_config_enable_hystart(long configAddr, boolean value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L187">
     *     quiche_config_enable_dgram</a>.
     */
    static native void quiche_config_enable_dgram(long configAddr, boolean enable,
                                                  int recv_queue_len, int send_queue_len);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#192">
     *     quiche_config_free</a>.
     */
    static native void quiche_config_free(long configAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L108">quiche_config_new</a>.
     */
    private static native void quiche_enable_debug_logging(QuicheLogger logger);

    private static native long buffer_memory_address(ByteBuffer buffer);

    /**
     * Returns the memory address if the {@link ByteBuf}
     */
    static long memoryAddress(ByteBuf buf) {
        assert buf.isDirect();
        return buf.hasMemoryAddress() ? buf.memoryAddress() :
                buffer_memory_address(buf.internalNioBuffer(buf.readerIndex(), buf.readableBytes()));
    }

    static long memoryAddress(ByteBuffer buf) {
        assert buf.isDirect();
        return buffer_memory_address(buf);
    }

    @SuppressWarnings("deprecation")
    static ByteBuf allocateNativeOrder(int capacity) {
        // Just use Unpooled as the life-time of these buffers is long.
        ByteBuf buffer = Unpooled.directBuffer(capacity);

        // As we use the buffers as pointers to int etc we need to ensure we use the right oder so we will
        // see the right value when we read primitive values.
        return PlatformDependent.BIG_ENDIAN_NATIVE_ORDER ? buffer : buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    static String errorAsString(int err) {
        return QuicError.valueOf(err).message();
    }

    static Exception newException(int err) {
        final QuicError error = QuicError.valueOf(err);
        final QuicException reason = new QuicException(error);
        if (err == QUICHE_ERR_TLS_FAIL) {
            final SSLHandshakeException sslExc = new SSLHandshakeException(error.message());
            sslExc.initCause(reason);
            return sslExc;
        }
        if (err == QUICHE_ERR_CRYPTO_FAIL) {
            return new SSLException(error.message(), reason);
        }
        return reason;
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

    private Quiche() { }
}
