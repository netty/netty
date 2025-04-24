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
package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.internal.ClassInitializerUtil;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class Quiche {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Quiche.class);
    private static final boolean TRACE_LOGGING_ENABLED = logger.isTraceEnabled();
    private static final IntObjectHashMap<QuicTransportErrorHolder> ERROR_MAPPINGS = new IntObjectHashMap<>();

    static {
        // Preload all classes that will be used in the OnLoad(...) function of JNI to eliminate the possibility of a
        // class-loader deadlock. This is a workaround for https://github.com/netty/netty/issues/11209.

        // This needs to match all the classes that are loaded via NETTY_JNI_UTIL_LOAD_CLASS or looked up via
        // NETTY_JNI_UTIL_FIND_CLASS.
        ClassInitializerUtil.tryLoadClasses(Quiche.class,
                // netty_quic_boringssl
                byte[].class, String.class, BoringSSLCertificateCallback.class,
                BoringSSLCertificateVerifyCallback.class, BoringSSLHandshakeCompleteCallback.class,

                //netty_quic_quiche
                QuicheLogger.class
        );

        try {
            // First, try calling a side-effect free JNI method to see if the library was already
            // loaded by the application.
            quiche_version();
        } catch (UnsatisfiedLinkError ignore) {
            // The library was not previously loaded, load it now.
            loadNativeLibrary();
        }

        // Let's enable debug logging for quiche if the TRACE level is enabled in our logger.
        if (TRACE_LOGGING_ENABLED) {
            quiche_enable_debug_logging(new QuicheLogger(logger));
        }
    }

    private static void loadNativeLibrary() {
        // This needs to be kept in sync with what is defined in netty_quic_quiche.c
        // and pom.xml as jniLibPrefix.
        String libName = "netty_quiche42";
        ClassLoader cl = PlatformDependent.getClassLoader(Quiche.class);

        if (!PlatformDependent.isAndroid()) {
            libName += '_' + PlatformDependent.normalizedOs()
                    + '_' + PlatformDependent.normalizedArch();
        }

        try {
            NativeLibraryLoader.load(libName, cl);
        } catch (UnsatisfiedLinkError e) {
            logger.debug("Failed to load {}", libName, e);
            throw e;
        }
    }

    static final short AF_INET = (short) QuicheNativeStaticallyReferencedJniMethods.afInet();
    static final short AF_INET6 = (short) QuicheNativeStaticallyReferencedJniMethods.afInet6();
    static final int SIZEOF_SOCKADDR_STORAGE = QuicheNativeStaticallyReferencedJniMethods.sizeofSockaddrStorage();
    static final int SIZEOF_SOCKADDR_IN = QuicheNativeStaticallyReferencedJniMethods.sizeofSockaddrIn();
    static final int SIZEOF_SOCKADDR_IN6 = QuicheNativeStaticallyReferencedJniMethods.sizeofSockaddrIn6();
    static final int SOCKADDR_IN_OFFSETOF_SIN_FAMILY =
            QuicheNativeStaticallyReferencedJniMethods.sockaddrInOffsetofSinFamily();
    static final int SOCKADDR_IN_OFFSETOF_SIN_PORT =
            QuicheNativeStaticallyReferencedJniMethods.sockaddrInOffsetofSinPort();
    static final int SOCKADDR_IN_OFFSETOF_SIN_ADDR =
            QuicheNativeStaticallyReferencedJniMethods.sockaddrInOffsetofSinAddr();
    static final int IN_ADDRESS_OFFSETOF_S_ADDR = QuicheNativeStaticallyReferencedJniMethods.inAddressOffsetofSAddr();
    static final int SOCKADDR_IN6_OFFSETOF_SIN6_FAMILY =
            QuicheNativeStaticallyReferencedJniMethods.sockaddrIn6OffsetofSin6Family();
    static final int SOCKADDR_IN6_OFFSETOF_SIN6_PORT =
            QuicheNativeStaticallyReferencedJniMethods.sockaddrIn6OffsetofSin6Port();
    static final int SOCKADDR_IN6_OFFSETOF_SIN6_FLOWINFO =
            QuicheNativeStaticallyReferencedJniMethods.sockaddrIn6OffsetofSin6Flowinfo();
    static final int SOCKADDR_IN6_OFFSETOF_SIN6_ADDR =
            QuicheNativeStaticallyReferencedJniMethods.sockaddrIn6OffsetofSin6Addr();
    static final int SOCKADDR_IN6_OFFSETOF_SIN6_SCOPE_ID =
            QuicheNativeStaticallyReferencedJniMethods.sockaddrIn6OffsetofSin6ScopeId();
    static final int IN6_ADDRESS_OFFSETOF_S6_ADDR =
            QuicheNativeStaticallyReferencedJniMethods.in6AddressOffsetofS6Addr();
    static final int SIZEOF_SOCKLEN_T = QuicheNativeStaticallyReferencedJniMethods.sizeofSocklenT();
    static final int SIZEOF_SIZE_T = QuicheNativeStaticallyReferencedJniMethods.sizeofSizeT();

    static final int SIZEOF_TIMESPEC = QuicheNativeStaticallyReferencedJniMethods.sizeofTimespec();

    static final int SIZEOF_TIME_T = QuicheNativeStaticallyReferencedJniMethods.sizeofTimeT();
    static final int SIZEOF_LONG = QuicheNativeStaticallyReferencedJniMethods.sizeofLong();

    static final int TIMESPEC_OFFSETOF_TV_SEC =
            QuicheNativeStaticallyReferencedJniMethods.timespecOffsetofTvSec();

    static final int TIMESPEC_OFFSETOF_TV_NSEC =
            QuicheNativeStaticallyReferencedJniMethods.timespecOffsetofTvNsec();

    static final int QUICHE_RECV_INFO_OFFSETOF_FROM =
            QuicheNativeStaticallyReferencedJniMethods.quicheRecvInfoOffsetofFrom();
    static final int QUICHE_RECV_INFO_OFFSETOF_FROM_LEN =
            QuicheNativeStaticallyReferencedJniMethods.quicheRecvInfoOffsetofFromLen();

    static final int QUICHE_RECV_INFO_OFFSETOF_TO =
            QuicheNativeStaticallyReferencedJniMethods.quicheRecvInfoOffsetofTo();
    static final int QUICHE_RECV_INFO_OFFSETOF_TO_LEN =
            QuicheNativeStaticallyReferencedJniMethods.quicheRecvInfoOffsetofToLen();

    static final int SIZEOF_QUICHE_RECV_INFO = QuicheNativeStaticallyReferencedJniMethods.sizeofQuicheRecvInfo();
    static final int QUICHE_SEND_INFO_OFFSETOF_TO =
            QuicheNativeStaticallyReferencedJniMethods.quicheSendInfoOffsetofTo();
    static final int QUICHE_SEND_INFO_OFFSETOF_TO_LEN =
            QuicheNativeStaticallyReferencedJniMethods.quicheSendInfoOffsetofToLen();

    static final int QUICHE_SEND_INFO_OFFSETOF_FROM =
            QuicheNativeStaticallyReferencedJniMethods.quicheSendInfoOffsetofFrom();
    static final int QUICHE_SEND_INFO_OFFSETOF_FROM_LEN =
            QuicheNativeStaticallyReferencedJniMethods.quicheSendInfoOffsetofFromLen();

    static final int QUICHE_SEND_INFO_OFFSETOF_AT =
            QuicheNativeStaticallyReferencedJniMethods.quicheSendInfoOffsetofAt();
    static final int SIZEOF_QUICHE_SEND_INFO = QuicheNativeStaticallyReferencedJniMethods.sizeofQuicheSendInfo();

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
     * See <a href="https://github.com/cloudflare/quiche/blob/e179f2aa52d475e037fe47d7b8466b3afde12d76/
     *        include/quiche.h#L101">QUICHE_ERR_STREAM_STOPPED</a>.
     */
    static final int QUICHE_ERR_STREAM_RESET =
            QuicheNativeStaticallyReferencedJniMethods.quiche_err_stream_reset();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.7.0/include/quiche.h#L98">
     *     QUICHE_ERR_STREAM_STOPPED</a>.
     */
    static final int QUICHE_ERR_STREAM_STOPPED =
            QuicheNativeStaticallyReferencedJniMethods.quiche_err_stream_stopped();

    // Too many identifiers were provided.
    static final int QUICHE_ERR_ID_LIMIT =
            QuicheNativeStaticallyReferencedJniMethods.quiche_err_id_limit();

    // Not enough available identifiers.
    static final int QUICHE_ERR_OUT_OF_IDENTIFIERS =
            QuicheNativeStaticallyReferencedJniMethods.quiche_err_out_of_identifiers();

    // Error in key update.
    static final int QUICHE_ERR_KEY_UPDATE =
            QuicheNativeStaticallyReferencedJniMethods.quiche_err_key_update();

    static final int QUICHE_ERR_CRYPTO_BUFFER_EXCEEDED =
            QuicheNativeStaticallyReferencedJniMethods.quiche_err_crypto_buffer_exceeded();

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
     * See <a href="https://github.com/cloudflare/quiche/blob/0.16.0/quiche/include/quiche.h#L206">
     *     QUICHE_CC_BBR</a>.
     */
    static final int QUICHE_CC_BBR = QuicheNativeStaticallyReferencedJniMethods.quiche_cc_bbr();

    static final int QUICHE_PATH_EVENT_NEW = QuicheNativeStaticallyReferencedJniMethods.quiche_path_event_new();
    static final int QUICHE_PATH_EVENT_VALIDATED =
            QuicheNativeStaticallyReferencedJniMethods.quiche_path_event_validated();
    static final int QUICHE_PATH_EVENT_FAILED_VALIDATION =
            QuicheNativeStaticallyReferencedJniMethods.quiche_path_event_failed_validation();
    static final int QUICHE_PATH_EVENT_CLOSED = QuicheNativeStaticallyReferencedJniMethods.quiche_path_event_closed();
    static final int QUICHE_PATH_EVENT_REUSED_SOURCE_CONNECTION_ID =
            QuicheNativeStaticallyReferencedJniMethods.quiche_path_event_reused_source_connection_id();
    static final int QUICHE_PATH_EVENT_PEER_MIGRATED =
            QuicheNativeStaticallyReferencedJniMethods.quiche_path_event_peer_migrated();

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L105">quiche_version</a>.
     */
    @Nullable
    static native String quiche_version();

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L227">quiche_version_is_supported</a>.
     */
    static native boolean quiche_version_is_supported(int version);

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
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L229">quiche_conn_new_with_tls</a>.
     */
    static native long quiche_conn_new_with_tls(long scidAddr, int scidLen, long odcidAddr, int odcidLen,
                                                long localAddr, int localLen,
                                                long peerAddr, int peerLen,
                                                long configAddr, long ssl, boolean isServer);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/master/include/quiche.h#L248">
     *     quiche_conn_set_qlog_path</a>.
     */
    static native boolean quiche_conn_set_qlog_path(long connAddr, String path, String logTitle, String logDescription);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L249">quiche_conn_recv</a>.
     */
    static native int quiche_conn_recv(long connAddr, long bufAddr, int bufLen, long infoAddr);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L262">quiche_conn_send</a>.
     */
    static native int quiche_conn_send(long connAddr, long outAddr, int outLen, long infoAddr);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L373">quiche_conn_free</a>.
     */
    static native void quiche_conn_free(long connAddr);

    @Nullable
    static QuicConnectionCloseEvent quiche_conn_peer_error(long connAddr) {
        Object[] error =  quiche_conn_peer_error0(connAddr);
        if (error == null) {
            return null;
        }
        return new QuicConnectionCloseEvent((Boolean) error[0], (Integer) error[1], (byte[]) error[2]);
    }

    private static native Object @Nullable [] quiche_conn_peer_error0(long connAddr);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.7.0/include/quiche.h#L330">
     *     quiche_conn_peer_streams_left_bidi</a>.
     */
    static native long quiche_conn_peer_streams_left_bidi(long connAddr);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.7.0/include/quiche.h#L334">
     *     quiche_conn_peer_streams_left_uni</a>.
     */
    static native long quiche_conn_peer_streams_left_uni(long connAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.7.0/include/quiche.h#L275">quiche_conn_stream_priority</a>.
     */
    static native int quiche_conn_stream_priority(
            long connAddr, long streamId, byte urgency, boolean incremental);

    static native int quiche_conn_send_quantum(long connAddr);

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/0.7.0/include/quiche.h#L309">quiche_conn_trace_id</a>.
     */
    static native byte @Nullable [] quiche_conn_trace_id(long connAddr);

    static native byte[] quiche_conn_source_id(long connAddr);

    static native byte[] quiche_conn_destination_id(long connAddr);

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
     * <a href="https://github.com/cloudflare/quiche/blob/
     * e9f59a55f5b56999d0e609a73aa8f1b450878a0c/include/quiche.h#L384">quiche_conn_is_timed_out</a>.
     */
    static native boolean quiche_conn_is_timed_out(long connAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L361">quiche_conn_stats</a>.
     * The implementation relies on all fields of
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L340">quiche_stats</a> being numerical.
     * The assumption made allows passing primitive array rather than dealing with objects.
     */
    static native long @Nullable [] quiche_conn_stats(long connAddr);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/master/quiche/include/quiche.h#L567C65-L567C88">
     *     quiche_conn_stats</a>.
     */
    static native long @Nullable [] quiche_conn_peer_transport_params(long connAddr);

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
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L285">quiche_conn_writable</a>.
     */
    static native long quiche_conn_writable(long connAddr);

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
     * <a href="https://github.com/cloudflare/quiche/blob/0.20.0/quiche/include/quiche.h#L672">
     *     quiche_conn_path_stats</a>.
     */
    static native Object @Nullable [] quiche_conn_path_stats(long connAddr, long streamIdx);

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
     * <a href="https://github.com/cloudflare/quiche/blob/0.10.0/include/quiche.h#L267">
     *     quiche_conn_set_session</a>.
     */
    static native int quiche_conn_set_session(long connAddr, byte[] sessionBytes);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.10.0/include/quiche.h#L328">
     *     quiche_conn_max_send_udp_payload_size</a>.
     */
    static native int quiche_conn_max_send_udp_payload_size(long connAddr);

    static native int quiche_conn_scids_left(long connAddr);

    static native long quiche_conn_new_scid(
            long connAddr, long scidAddr, int scidLen, byte[] resetToken, boolean retire_if_needed, long seq);

    static native byte @Nullable [] quiche_conn_retired_scid_next(long connAddr);

    static native long quiche_conn_path_event_next(long connAddr);
    static native int quiche_path_event_type(long pathEvent);
    static native void quiche_path_event_free(long pathEvent);
    static native Object[] quiche_path_event_new(long pathEvent);
    static native Object[] quiche_path_event_validated(long pathEvent);
    static native Object[] quiche_path_event_failed_validation(long pathEvent);
    static native Object[]  quiche_path_event_closed(long pathEvent);
    static native Object[] quiche_path_event_reused_source_connection_id(long pathEvent);
    static native Object[] quiche_path_event_peer_migrated(long pathEvent);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#L115">quiche_config_new</a>.
     */
    static native long quiche_config_new(int version);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#129">
     *     quiche_config_grease</a>.
     */
    static native void quiche_config_grease(long configAddr, boolean value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.6.0/include/quiche.h#143">
     *     quiche_config_set_max_idle_timeout</a>.
     */
    static native void quiche_config_set_max_idle_timeout(long configAddr, long value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.7.0/include/quiche.h#L150">
     *     quiche_config_set_max_recv_udp_payload_size</a>.
     */
    static native void quiche_config_set_max_recv_udp_payload_size(long configAddr, long value);

    /**
     * See
     * <a href="https://github.com/cloudflare/quiche/blob/0.7.0/include/quiche.h#L153">
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
     * <a href="https://github.com/cloudflare/quiche/blob/0.21.0/quiche/include/quiche.h#L222">
     *     quiche_config_set_initial_congestion_window_packets</a>
     *
     */
    static native void quiche_config_set_initial_congestion_window_packets(long configAddr, int numPackets);

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

    // Sets the limit of active connection IDs.
    static native void quiche_config_set_active_connection_id_limit(long configAddr, long value);

    // Sets the initial stateless reset token.
    static native void quiche_config_set_stateless_reset_token(long configAddr, byte[] token);

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

    static native int sockaddr_cmp(long addr, long addr2);

    /**
     * Returns the memory address if the {@link ByteBuf} taking the readerIndex into account.
     *
     * @param buf   the {@link ByteBuf} of which we want to obtain the memory address
     *              (taking its {@link ByteBuf#readerIndex()} into account).
     * @return      the memory address of this {@link ByteBuf}s readerIndex.
     */
    static long readerMemoryAddress(ByteBuf buf) {
        return memoryAddress(buf, buf.readerIndex(), buf.readableBytes());
    }

    /**
     * Returns the memory address if the {@link ByteBuf} taking the writerIndex into account.
     *
     * @param buf   the {@link ByteBuf} of which we want to obtain the memory address
     *              (taking its {@link ByteBuf#writerIndex()} into account).
     * @return      the memory address of this {@link ByteBuf}s writerIndex.
     */
    static long writerMemoryAddress(ByteBuf buf) {
        return memoryAddress(buf, buf.writerIndex(), buf.writableBytes());
    }

    /**
     * Returns the memory address if the {@link ByteBuf} taking the offset into account.
     *
     * @param buf       the {@link ByteBuf} of which we want to obtain the memory address
     *                  (taking the {@code offset} into account).
     * @param offset    the offset of the memory address.
     * @param len       the length of the {@link ByteBuf}.
     * @return          the memory address of this {@link ByteBuf}s offset.
     */
    static long memoryAddress(ByteBuf buf, int offset, int len) {
        assert buf.isDirect();
        if (buf.hasMemoryAddress()) {
            return buf.memoryAddress() + offset;
        }
        return memoryAddressWithPosition(buf.internalNioBuffer(offset, len));
    }

    /**
     * Returns the memory address of the given {@link ByteBuffer} taking its current {@link ByteBuffer#position()} into
     * account.
     *
     * @param buf   the {@link ByteBuffer} of which we want to obtain the memory address
     *              (taking its {@link ByteBuffer#position()} into account).
     * @return      the memory address of this {@link ByteBuffer}s position.
     */
    static long memoryAddressWithPosition(ByteBuffer buf) {
        assert buf.isDirect();
        return buffer_memory_address(buf) + buf.position();
    }

    @SuppressWarnings("deprecation")
    static ByteBuf allocateNativeOrder(int capacity) {
        // Just use Unpooled as the life-time of these buffers is long.
        ByteBuf buffer = Unpooled.directBuffer(capacity);

        // As we use the buffers as pointers to int etc we need to ensure we use the right oder so we will
        // see the right value when we read primitive values.
        return PlatformDependent.BIG_ENDIAN_NATIVE_ORDER ? buffer : buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    static boolean shouldClose(int res)  {
        return res == Quiche.QUICHE_ERR_CRYPTO_FAIL || res == Quiche.QUICHE_ERR_TLS_FAIL;
    }

    /**
     * Returns {@code true} if both {@link ByteBuffer}s have the same {@code sock_addr} stored.
     *
     * @param memory    the first {@link ByteBuffer} which holds a {@code quiche_recv_info}.
     * @param memory2   the second {@link ByteBuffer} which holds a {@code quiche_recv_info}.
     * @return          {@code true} if both {@link ByteBuffer}s have the same {@code sock_addr} stored, {@code false}
     *                  otherwise.
     */
    static boolean isSameAddress(ByteBuffer memory, ByteBuffer memory2, int addressOffset) {
        long address1 = Quiche.memoryAddressWithPosition(memory) + addressOffset;
        long address2 = Quiche.memoryAddressWithPosition(memory2) + addressOffset;
        return SockaddrIn.cmp(address1, address2) == 0;
    }

    static void setPrimitiveValue(ByteBuffer memory, int offset, int valueType, long value) {
        switch (valueType) {
            case 1:
                memory.put(offset, (byte) value);
                break;
            case 2:
                memory.putShort(offset, (short) value);
                break;
            case 4:
                memory.putInt(offset, (int) value);
                break;
            case 8:
                memory.putLong(offset, value);
                break;
            default:
                throw new IllegalStateException();
        }
    }

    static long getPrimitiveValue(ByteBuffer memory, int offset, int valueType) {
        switch (valueType) {
            case 1:
                return memory.get(offset);
            case 2:
                return memory.getShort(offset);
            case 4:
                return memory.getInt(offset);
            case 8:
                return memory.getLong(offset);
            default:
                throw new IllegalStateException();
        }
    }

    // See https://github.com/cloudflare/quiche/commit/1d00ee1bb2256dfd99ba0cb2dfac72fe1e59407f
    static {
        ERROR_MAPPINGS.put(QUICHE_ERR_DONE,
                new QuicTransportErrorHolder(QuicTransportError.NO_ERROR, "QUICHE_ERR_DONE"));
        ERROR_MAPPINGS.put(QUICHE_ERR_INVALID_FRAME,
                new QuicTransportErrorHolder(QuicTransportError.FRAME_ENCODING_ERROR, "QUICHE_ERR_INVALID_FRAME"));
        ERROR_MAPPINGS.put(QUICHE_ERR_INVALID_STREAM_STATE,
                new QuicTransportErrorHolder(QuicTransportError.STREAM_STATE_ERROR, "QUICHE_ERR_INVALID_STREAM_STATE"));
        ERROR_MAPPINGS.put(QUICHE_ERR_INVALID_TRANSPORT_PARAM,
                new QuicTransportErrorHolder(QuicTransportError.TRANSPORT_PARAMETER_ERROR,
                        "QUICHE_ERR_INVALID_TRANSPORT_PARAM"));
        ERROR_MAPPINGS.put(QUICHE_ERR_FLOW_CONTROL,
                new QuicTransportErrorHolder(QuicTransportError.FLOW_CONTROL_ERROR, "QUICHE_ERR_FLOW_CONTROL"));
        ERROR_MAPPINGS.put(QUICHE_ERR_STREAM_LIMIT,
                new QuicTransportErrorHolder(QuicTransportError.STREAM_LIMIT_ERROR, "QUICHE_ERR_STREAM_LIMIT"));
        ERROR_MAPPINGS.put(QUICHE_ERR_ID_LIMIT,
                new QuicTransportErrorHolder(QuicTransportError.CONNECTION_ID_LIMIT_ERROR, "QUICHE_ERR_ID_LIMIT"));
        ERROR_MAPPINGS.put(QUICHE_ERR_FINAL_SIZE,
                new QuicTransportErrorHolder(QuicTransportError.FINAL_SIZE_ERROR, "QUICHE_ERR_FINAL_SIZE"));
        ERROR_MAPPINGS.put(QUICHE_ERR_CRYPTO_BUFFER_EXCEEDED,
                new QuicTransportErrorHolder(QuicTransportError.CRYPTO_BUFFER_EXCEEDED,
                        "QUICHE_ERR_CRYPTO_BUFFER_EXCEEDED"));
        ERROR_MAPPINGS.put(QUICHE_ERR_KEY_UPDATE,
                new QuicTransportErrorHolder(QuicTransportError.KEY_UPDATE_ERROR, "QUICHE_ERR_KEY_UPDATE"));

        // Should the code be something different ?
        ERROR_MAPPINGS.put(QUICHE_ERR_TLS_FAIL,
                new QuicTransportErrorHolder(QuicTransportError.valueOf(0x0100), "QUICHE_ERR_TLS_FAIL"));
        ERROR_MAPPINGS.put(QUICHE_ERR_CRYPTO_FAIL,
                new QuicTransportErrorHolder(QuicTransportError.valueOf(0x0100), "QUICHE_ERR_CRYPTO_FAIL"));

        ERROR_MAPPINGS.put(QUICHE_ERR_BUFFER_TOO_SHORT,
                new QuicTransportErrorHolder(QuicTransportError.PROTOCOL_VIOLATION, "QUICHE_ERR_BUFFER_TOO_SHORT"));
        ERROR_MAPPINGS.put(QUICHE_ERR_UNKNOWN_VERSION,
                new QuicTransportErrorHolder(QuicTransportError.PROTOCOL_VIOLATION, "QUICHE_ERR_UNKNOWN_VERSION"));
        ERROR_MAPPINGS.put(QUICHE_ERR_INVALID_PACKET,
                new QuicTransportErrorHolder(QuicTransportError.PROTOCOL_VIOLATION, "QUICHE_ERR_INVALID_PACKET"));
        ERROR_MAPPINGS.put(QUICHE_ERR_INVALID_STATE,
                new QuicTransportErrorHolder(QuicTransportError.PROTOCOL_VIOLATION, "QUICHE_ERR_INVALID_STATE"));
        ERROR_MAPPINGS.put(QUICHE_ERR_CONGESTION_CONTROL,
                new QuicTransportErrorHolder(QuicTransportError.PROTOCOL_VIOLATION, "QUICHE_ERR_CONGESTION_CONTROL"));
        ERROR_MAPPINGS.put(QUICHE_ERR_STREAM_STOPPED,
                new QuicTransportErrorHolder(QuicTransportError.PROTOCOL_VIOLATION, "QUICHE_ERR_STREAM_STOPPED"));
        ERROR_MAPPINGS.put(QUICHE_ERR_OUT_OF_IDENTIFIERS,
                new QuicTransportErrorHolder(QuicTransportError.PROTOCOL_VIOLATION, "QUICHE_ERR_OUT_OF_IDENTIFIERS"));
    }

    static Exception convertToException(int result) {
        QuicTransportErrorHolder holder = ERROR_MAPPINGS.get(result);
        if (holder == null) {
            // There is no mapping to a transport error, it's something internal so throw it directly.
            return new QuicException(QuicheError.valueOf(result).message());
        }
        Exception exception = new QuicException(holder.error + ": " + holder.quicheErrorName, holder.error);
        if (result == QUICHE_ERR_TLS_FAIL) {
            String lastSslError = BoringSSL.ERR_last_error();
            final SSLHandshakeException sslExc = new SSLHandshakeException(lastSslError);
            sslExc.initCause(exception);
            return sslExc;
        }
        if (result == QUICHE_ERR_CRYPTO_FAIL) {
            return new SSLException(exception);
        }
        return exception;
    }

    private static final class QuicTransportErrorHolder {
        private final QuicTransportError error;
        private final String quicheErrorName;

        QuicTransportErrorHolder(QuicTransportError error, String quicheErrorName) {
            this.error = error;
            this.quicheErrorName = quicheErrorName;
        }
    }

    private Quiche() { }
}
