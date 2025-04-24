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
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#ifdef _WIN32
#include <winsock2.h>
#include <ws2tcpip.h>
// This needs to be included for quiche_recv_info and quiche_send_info structs.
#include <time.h>
#else
#include <arpa/inet.h>
#include <sys/socket.h>
#include <netinet/in.h>
// This needs to be included for quiche_recv_info and quiche_send_info structs.
#include <sys/time.h>
#endif // _WIN32


#include <quiche.h>
#include "netty_jni_util.h"
#include "netty_quic_boringssl.h"
#include "netty_quic.h"

// Add define if NETTY_QUIC_BUILD_STATIC is defined so it is picked up in netty_jni_util.c
#ifdef NETTY_QUIC_BUILD_STATIC
#define NETTY_JNI_UTIL_BUILD_STATIC
#endif

#define STATICALLY_CLASSNAME "io/netty/handler/codec/quic/QuicheNativeStaticallyReferencedJniMethods"
#define QUICHE_CLASSNAME "io/netty/handler/codec/quic/Quiche"

// This needs to be kept in sync with what is defined in Quiche.java
// and pom.xml as jniLibPrefix.
#define LIBRARYNAME "netty_quiche42"

static jweak    quiche_logger_class_weak = NULL;
static jmethodID quiche_logger_class_log = NULL;
static jobject   quiche_logger = NULL;
static JavaVM     *global_vm = NULL;

static jclass integer_class = NULL;
static jmethodID integer_class_valueof = NULL;

static jclass boolean_class = NULL;
static jmethodID boolean_class_valueof = NULL;

static jclass long_class = NULL;
static jmethodID long_class_valueof = NULL;

static jclass inet4address_class = NULL;
static jmethodID inet4address_class_get_by_address = NULL;

static jclass inet6address_class = NULL;
static jmethodID inet6address_class_get_by_address = NULL;

static jclass inetsocketaddress_class = NULL;
static jmethodID inetsocketaddress_class_constructor = NULL;

static jclass object_class = NULL;


static char const* staticPackagePrefix = NULL;


jint quic_get_java_env(JNIEnv **env)
{
    return (*global_vm)->GetEnv(global_vm, (void **)env, NETTY_JNI_UTIL_JNI_VERSION);
}

static jint netty_quiche_afInet(JNIEnv* env, jclass clazz) {
    return AF_INET;
}

static jint netty_quiche_afInet6(JNIEnv* env, jclass clazz) {
    return AF_INET6;
}

static jint netty_quiche_sizeofSockaddrIn(JNIEnv* env, jclass clazz) {
    return sizeof(struct sockaddr_in);
}

static jint netty_quiche_sizeofSockaddrIn6(JNIEnv* env, jclass clazz) {
    return sizeof(struct sockaddr_in6);
}

static jint netty_quiche_sockaddrInOffsetofSinFamily(JNIEnv* env, jclass clazz) {
    return offsetof(struct sockaddr_in, sin_family);
}

static jint netty_quiche_sockaddrInOffsetofSinPort(JNIEnv* env, jclass clazz) {
    return offsetof(struct sockaddr_in, sin_port);
}

static jint netty_quiche_sockaddrInOffsetofSinAddr(JNIEnv* env, jclass clazz) {
    return offsetof(struct sockaddr_in, sin_addr);
}

static jint netty_quiche_inAddressOffsetofSAddr(JNIEnv* env, jclass clazz) {
    return offsetof(struct in_addr, s_addr);
}

static jint netty_quiche_sockaddrIn6OffsetofSin6Family(JNIEnv* env, jclass clazz) {
    return offsetof(struct sockaddr_in6, sin6_family);
}

static jint netty_quiche_sockaddrIn6OffsetofSin6Port(JNIEnv* env, jclass clazz) {
    return offsetof(struct sockaddr_in6, sin6_port);
}

static jint netty_quiche_sockaddrIn6OffsetofSin6Flowinfo(JNIEnv* env, jclass clazz) {
    return offsetof(struct sockaddr_in6, sin6_flowinfo);
}

static jint netty_quiche_sockaddrIn6OffsetofSin6Addr(JNIEnv* env, jclass clazz) {
    return offsetof(struct sockaddr_in6, sin6_addr);
}

static jint netty_quiche_sockaddrIn6OffsetofSin6ScopeId(JNIEnv* env, jclass clazz) {
    return offsetof(struct sockaddr_in6, sin6_scope_id);
}

static jint netty_quiche_in6AddressOffsetofS6Addr(JNIEnv* env, jclass clazz) {
    return offsetof(struct in6_addr, s6_addr);
}

static jint netty_quiche_sizeofSockaddrStorage(JNIEnv* env, jclass clazz) {
    return sizeof(struct sockaddr_storage);
}
static jint netty_quiche_sizeofSizeT(JNIEnv* env, jclass clazz) {
    return sizeof(size_t);
}

static jint netty_quiche_sizeofSocklenT(JNIEnv* env, jclass clazz) {
    return sizeof(socklen_t);
}

static jint netty_quiche_sizeofTimespec(JNIEnv* env, jclass clazz) {
    return sizeof(struct timespec);
}

static jint netty_quiche_sizeofTimeT(JNIEnv* env, jclass clazz) {
    return sizeof(time_t);
}

static jint netty_quiche_sizeofLong(JNIEnv* env, jclass clazz) {
    return sizeof(long);
}

static jint netty_quiche_timespecOffsetofTvSec(JNIEnv* env, jclass clazz) {
    return offsetof(struct timespec, tv_sec);
}

static jint timespecOffsetofTvNsec(JNIEnv* env, jclass clazz) {
    return offsetof(struct timespec, tv_nsec);
}

static jint netty_quicheRecvInfoOffsetofFrom(JNIEnv* env, jclass clazz) {
    return offsetof(quiche_recv_info, from);
}

static jint netty_quicheRecvInfoOffsetofFromLen(JNIEnv* env, jclass clazz) {
    return offsetof(quiche_recv_info, from_len);
}

static jint netty_quicheRecvInfoOffsetofTo(JNIEnv* env, jclass clazz) {
    return offsetof(quiche_recv_info, to);
}

static jint netty_quicheRecvInfoOffsetofToLen(JNIEnv* env, jclass clazz) {
    return offsetof(quiche_recv_info, to_len);
}

static jint netty_sizeofQuicheRecvInfo(JNIEnv* env, jclass clazz) {
    return sizeof(quiche_recv_info);
}

static jint netty_quicheSendInfoOffsetofTo(JNIEnv* env, jclass clazz) {
    return offsetof(quiche_send_info, to);
}

static jint netty_quicheSendInfoOffsetofToLen(JNIEnv* env, jclass clazz) {
    return offsetof(quiche_send_info, to_len);
}

static jint netty_quicheSendInfoOffsetofFrom(JNIEnv* env, jclass clazz) {
    return offsetof(quiche_send_info, from);
}

static jint netty_quicheSendInfoOffsetofFromLen(JNIEnv* env, jclass clazz) {
    return offsetof(quiche_send_info, from_len);
}

static jint netty_quicheSendInfoOffsetofAt(JNIEnv* env, jclass clazz) {
    return offsetof(quiche_send_info, at);
}

static jint netty_sizeofQuicheSendInfo(JNIEnv* env, jclass clazz) {
    return sizeof(quiche_send_info);
}

static jint netty_quiche_max_conn_id_len(JNIEnv* env, jclass clazz) {
    return QUICHE_MAX_CONN_ID_LEN;
}

static jint netty_quiche_protocol_version(JNIEnv* env, jclass clazz) {
    return QUICHE_PROTOCOL_VERSION;
}

static jint netty_quiche_shutdown_read(JNIEnv* env, jclass clazz) {
    return QUICHE_SHUTDOWN_READ;
}

static jint netty_quiche_shutdown_write(JNIEnv* env, jclass clazz) {
    return QUICHE_SHUTDOWN_WRITE;
}

static jint netty_quiche_err_done(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_DONE;
}

static jint netty_quiche_err_buffer_too_short(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_BUFFER_TOO_SHORT;
}

static jint netty_quiche_err_unknown_version(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_UNKNOWN_VERSION;
}

static jint netty_quiche_err_invalid_frame(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_INVALID_FRAME;
}

static jint netty_quiche_err_invalid_packet(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_INVALID_PACKET;
}

static jint netty_quiche_err_invalid_state(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_INVALID_STATE;
}

static jint netty_quiche_err_invalid_stream_state(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_INVALID_STREAM_STATE;
}

static jint netty_quiche_err_invalid_transport_param(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_INVALID_TRANSPORT_PARAM;
}

static jint netty_quiche_err_crypto_fail(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_CRYPTO_FAIL;
}

static jint netty_quiche_err_tls_fail(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_TLS_FAIL;
}

static jint netty_quiche_err_flow_control(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_FLOW_CONTROL;
}

static jint netty_quiche_err_stream_limit(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_STREAM_LIMIT;
}

static jint netty_quiche_err_final_size(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_FINAL_SIZE;
}

static jint netty_quiche_err_stream_reset(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_STREAM_RESET;
}

static jint netty_quiche_err_stream_stopped(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_STREAM_STOPPED;
}

static jint netty_quiche_err_congestion_control(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_CONGESTION_CONTROL;
}

static jint netty_quiche_err_id_limit(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_ID_LIMIT;
}

static jint netty_quiche_err_out_of_identifiers(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_OUT_OF_IDENTIFIERS;
}

static jint netty_quiche_err_key_update(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_KEY_UPDATE;
}

static jint netty_quiche_err_crypto_buffer_exceeded(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_CRYPTO_BUFFER_EXCEEDED;
}

static jint netty_quiche_cc_reno(JNIEnv* env, jclass clazz) {
    return QUICHE_CC_RENO;
}

static jint netty_quiche_cc_cubic(JNIEnv* env, jclass clazz) {
    return QUICHE_CC_CUBIC;
}

static jint netty_quiche_cc_bbr(JNIEnv* env, jclass clazz) {
    return QUICHE_CC_BBR;
}

static jint netty_quiche_path_event_type_new(JNIEnv* env, jclass clazz) {
    return QUICHE_PATH_EVENT_NEW;
}

static jint netty_quiche_path_event_type_validated(JNIEnv* env, jclass clazz) {
    return QUICHE_PATH_EVENT_VALIDATED;
}

static jint netty_quiche_path_event_type_failed_validation(JNIEnv* env, jclass clazz) {
    return QUICHE_PATH_EVENT_FAILED_VALIDATION;
}

static jint netty_quiche_path_event_type_closed(JNIEnv* env, jclass clazz) {
    return QUICHE_PATH_EVENT_CLOSED;
}

static jint netty_quiche_path_event_type_reused_source_connection_id(JNIEnv* env, jclass clazz) {
    return QUICHE_PATH_EVENT_REUSED_SOURCE_CONNECTION_ID;
}

static jint netty_quiche_path_event_type_peer_migrated(JNIEnv* env, jclass clazz) {
    return QUICHE_PATH_EVENT_PEER_MIGRATED;
}

static jstring netty_quiche_version(JNIEnv* env, jclass clazz) {
    return (*env)->NewStringUTF(env, quiche_version());
}

static jboolean netty_quiche_version_is_supported(JNIEnv* env, jclass clazz, jint version) {
    return quiche_version_is_supported(version) == true ? JNI_TRUE : JNI_FALSE;
}

static jboolean netty_quiche_conn_set_qlog_path(JNIEnv* env, jclass clazz, jlong conn, jstring path,
                          jstring log_title, jstring log_desc) {
    const char *nativePath = (*env)->GetStringUTFChars(env, path, 0);
    const char *nativeLogTitle = (*env)->GetStringUTFChars(env, log_title, 0);
    const char *nativeLogDesc = (*env)->GetStringUTFChars(env, log_desc, 0);
    bool ret = quiche_conn_set_qlog_path((quiche_conn *) conn, nativePath,
                          nativeLogTitle, nativeLogDesc);
    (*env)->ReleaseStringUTFChars(env, path, nativePath);
    (*env)->ReleaseStringUTFChars(env, log_title, nativeLogTitle);
    (*env)->ReleaseStringUTFChars(env, log_desc, nativeLogDesc);

    return ret == true ? JNI_TRUE : JNI_FALSE;
}

static jint netty_quiche_negotiate_version(JNIEnv* env, jclass clazz, jlong scid, jint scid_len, jlong dcid, jint dcid_len, jlong out, jint out_len) {
    return (jint) quiche_negotiate_version((const uint8_t *) scid, (size_t) scid_len,
                                                   (const uint8_t *) dcid, (size_t) dcid_len,
                                                   (uint8_t *)out, (size_t) out_len);
}

static jint netty_quiche_retry(JNIEnv* env, jclass clazz, jlong scid, jint scid_len, jlong dcid, jint dcid_len,
                jlong new_scid, jint new_scid_len, jlong token, jint token_len, jint version, jlong out, jint out_len) {
    return (jint) quiche_retry((const uint8_t *) scid, (size_t) scid_len,
                               (const uint8_t *) dcid, (size_t) dcid_len,
                               (const uint8_t *) new_scid, (size_t) new_scid_len,
                               (const uint8_t *) token, (size_t) token_len,
                               (uint32_t) version, (uint8_t *) out, (size_t) out_len);
}

static jlong netty_quiche_conn_new_with_tls(JNIEnv* env, jclass clazz, jlong scid, jint scid_len, jlong odcid, jint odcid_len, jlong local, jint local_len, jlong peer, jint peer_len, jlong config, jlong ssl, jboolean isServer) {
    const uint8_t * odcid_pointer = NULL;
    if (odcid_len != -1) {
        odcid_pointer = (const uint8_t *) odcid;
    }
    const struct sockaddr *local_pointer = (const struct sockaddr*) local;
    const struct sockaddr *peer_pointer = (const struct sockaddr*) peer;
    quiche_conn *conn = quiche_conn_new_with_tls((const uint8_t *) scid, (size_t) scid_len,
                                 odcid_pointer, (size_t) odcid_len,
                                 local_pointer, (socklen_t) local_len,
                                 peer_pointer, (socklen_t) peer_len,
                                 (quiche_config *) config, (void*) ssl, isServer == JNI_TRUE ? true : false);
    if (conn == NULL) {
        return -1;
    }
    return (jlong) conn;
}

static jbyteArray to_byte_array(JNIEnv* env, const uint8_t* bytes, size_t len) {
    if (bytes == NULL || len == 0) {
        return NULL;
    }
     jbyteArray array = (*env)->NewByteArray(env, len);
     if (array == NULL) {
        return NULL;
     }
     (*env)->SetByteArrayRegion(env,array, 0, len, (jbyte*) bytes);
     return array;
}

static jint netty_quiche_conn_send_quantum(JNIEnv* env, jclass clazz, jlong conn) {
    return (jint) quiche_conn_send_quantum((quiche_conn *) conn);
}

static jbyteArray netty_quiche_conn_trace_id(JNIEnv* env, jclass clazz, jlong conn) {
    const uint8_t *trace_id = NULL;
    size_t trace_id_len = 0;

    quiche_conn_trace_id((quiche_conn *) conn, &trace_id, &trace_id_len);
    return to_byte_array(env, trace_id, trace_id_len);
}

static jbyteArray netty_quiche_conn_source_id(JNIEnv* env, jclass clazz, jlong conn) {
    const uint8_t *id = NULL;
    size_t len = 0;

    quiche_conn_source_id((quiche_conn *) conn, &id, &len);
    return to_byte_array(env, id, len);
}

static jbyteArray netty_quiche_conn_destination_id(JNIEnv* env, jclass clazz, jlong conn) {
    const uint8_t *id = NULL;
    size_t len = 0;

    quiche_conn_destination_id((quiche_conn *) conn, &id, &len);
    return to_byte_array(env, id, len);
}

static jint netty_quiche_conn_recv(JNIEnv* env, jclass clazz, jlong conn, jlong buf, jint buf_len, jlong info) {
    return (jint) quiche_conn_recv((quiche_conn *) conn, (uint8_t *) buf, (size_t) buf_len, (quiche_recv_info*) info);
}

static jint netty_quiche_conn_send(JNIEnv* env, jclass clazz, jlong conn, jlong out, jint out_len, jlong info) {
    return (jint) quiche_conn_send((quiche_conn *) conn, (uint8_t *) out, (size_t) out_len, (quiche_send_info*) info);
}

static void netty_quiche_conn_free(JNIEnv* env, jclass clazz, jlong conn) {
    quiche_conn_free((quiche_conn *) conn);
}

static jobjectArray netty_quiche_conn_peer_error0(JNIEnv* env, jclass clazz, jlong conn) {
    bool is_app = false;
    uint64_t error_code = 0;
    const uint8_t *reason = NULL;
    size_t reason_len = 0;

    bool peer_error = quiche_conn_peer_error((quiche_conn *) conn,
                            &is_app,
                            &error_code,
                            &reason,
                            &reason_len);
    if (peer_error) {
        jobjectArray array = (*env)->NewObjectArray(env, 3, object_class, NULL);
        if (array == NULL) {
            return NULL;
        }
        (*env)->SetObjectArrayElement(env, array, 0, (*env)->CallStaticObjectMethod(env, boolean_class, boolean_class_valueof, is_app ? JNI_TRUE : JNI_FALSE));
        (*env)->SetObjectArrayElement(env, array, 1, (*env)->CallStaticObjectMethod(env, integer_class, integer_class_valueof, (jint) error_code));
        (*env)->SetObjectArrayElement(env, array, 2, to_byte_array(env, reason, reason_len));
        return array;
    }
    return NULL;
}

static jlong netty_quiche_conn_peer_streams_left_bidi(JNIEnv* env, jclass clazz, jlong conn) {
    return (jlong) quiche_conn_peer_streams_left_bidi((quiche_conn *) conn);
}

static jlong netty_quiche_conn_peer_streams_left_uni(JNIEnv* env, jclass clazz, jlong conn) {
    return (jlong) quiche_conn_peer_streams_left_uni((quiche_conn *) conn);
}

static jint netty_quiche_conn_stream_priority(JNIEnv* env, jclass clazz, jlong conn, jlong stream_id, jbyte urgency, jboolean incremental) {
    return (jint) quiche_conn_stream_priority((quiche_conn *) conn, (uint64_t) stream_id,  (uint8_t) urgency, incremental == JNI_TRUE ? true : false);
}

static jint netty_quiche_conn_stream_recv(JNIEnv* env, jclass clazz, jlong conn, jlong stream_id, jlong out, int buf_len, jlong finAddr) {
    uint64_t error_code;
    return (jint) quiche_conn_stream_recv((quiche_conn *) conn, (uint64_t) stream_id,  (uint8_t *) out, (size_t) buf_len, (bool *) finAddr, &error_code);
}

static jint netty_quiche_conn_stream_send(JNIEnv* env, jclass clazz, jlong conn, jlong stream_id, jlong buf, int buf_len, jboolean fin) {
    uint64_t error_code;
    return (jint) quiche_conn_stream_send((quiche_conn *) conn, (uint64_t) stream_id,  (uint8_t *) buf, (size_t) buf_len, fin == JNI_TRUE ? true : false, &error_code);
}

static jint netty_quiche_conn_stream_shutdown(JNIEnv* env, jclass clazz, jlong conn, jlong stream_id, jint direction, jlong err) {
    return (jint) quiche_conn_stream_shutdown((quiche_conn *) conn, (uint64_t) stream_id,  (enum quiche_shutdown) direction, (uint64_t) err);
}

static jint netty_quiche_conn_stream_capacity(JNIEnv* env, jclass clazz, jlong conn, jlong stream_id) {
    return (jint) quiche_conn_stream_capacity((quiche_conn *) conn, (uint64_t) stream_id);
}

static jboolean netty_quiche_conn_stream_finished(JNIEnv* env, jclass clazz, jlong conn, jlong stream_id) {
    return quiche_conn_stream_finished((quiche_conn *) conn, (uint64_t) stream_id) == true ? JNI_TRUE : JNI_FALSE;
}

static jint netty_quiche_conn_close(JNIEnv* env, jclass clazz, jlong conn, jboolean app, jlong err, jlong reason, jint reason_len) {
    return quiche_conn_close((quiche_conn *) conn, app == JNI_TRUE ? true : false, err, (const uint8_t *) reason, (size_t) reason_len);
}

static jboolean netty_quiche_conn_is_established(JNIEnv* env, jclass clazz, jlong conn) {
    return quiche_conn_is_established((quiche_conn *) conn) == true ? JNI_TRUE : JNI_FALSE;
}

static jboolean netty_quiche_conn_is_in_early_data(JNIEnv* env, jclass clazz, jlong conn) {
    return quiche_conn_is_in_early_data((quiche_conn *) conn) == true ? JNI_TRUE : JNI_FALSE;
}

static jboolean netty_quiche_conn_is_closed(JNIEnv* env, jclass clazz, jlong conn) {
    return quiche_conn_is_closed((quiche_conn *) conn) == true ? JNI_TRUE : JNI_FALSE;
}

static jboolean netty_quiche_conn_is_timed_out(JNIEnv* env, jclass clazz, jlong conn) {
    return quiche_conn_is_timed_out((quiche_conn *) conn) == true ? JNI_TRUE : JNI_FALSE;
}

static jlongArray netty_quiche_conn_stats(JNIEnv* env, jclass clazz, jlong conn) {
    // See https://github.com/cloudflare/quiche/blob/master/quiche/include/quiche.h#L467
    quiche_stats stats = {0,0,0,0,0,0,0,0,0};
    quiche_conn_stats((quiche_conn *) conn, &stats);

    jlongArray statsArray = (*env)->NewLongArray(env, 9);
    if (statsArray == NULL) {
        // This will put an OOME on the stack
        return NULL;
    }
    jlong statsArrayElements[] = {
        (jlong)stats.recv,
        (jlong)stats.sent,
        (jlong)stats.lost,
        (jlong)stats.retrans,
        (jlong)stats.sent_bytes,
        (jlong)stats.recv_bytes,
        (jlong)stats.lost_bytes,
        (jlong)stats.stream_retrans_bytes,
        (jlong)stats.paths_count
    };
    (*env)->SetLongArrayRegion(env, statsArray, 0, 9, statsArrayElements);
    return statsArray;
}

static jlongArray netty_quiche_conn_peer_transport_params(JNIEnv* env, jclass clazz, jlong conn) {
    // See https://github.com/cloudflare/quiche/blob/master/quiche/include/quiche.h#L563
    quiche_transport_params params = {0,0,0,0,0,0,0,0,0,0,false,0,0};
    if (!quiche_conn_peer_transport_params((quiche_conn *) conn, &params)) {
        return NULL;
    }

    jlongArray paramsArray = (*env)->NewLongArray(env, 13);
    if (paramsArray == NULL) {
        // This will put an OOME on the stack
        return NULL;
    }
    jlong paramsArrayElements[] = {
        (jlong)params.peer_max_idle_timeout,
        (jlong)params.peer_max_udp_payload_size,
        (jlong)params.peer_initial_max_data,
        (jlong)params.peer_initial_max_stream_data_bidi_local,
        (jlong)params.peer_initial_max_stream_data_bidi_remote,
        (jlong)params.peer_initial_max_stream_data_uni,
        (jlong)params.peer_initial_max_streams_bidi,
        (jlong)params.peer_initial_max_streams_uni,
        (jlong)params.peer_ack_delay_exponent,
        (jlong)params.peer_disable_active_migration ? 1: 0,
        (jlong)params.peer_active_conn_id_limit,
        (jlong)params.peer_max_datagram_frame_size
    };
    (*env)->SetLongArrayRegion(env, paramsArray, 0, 13, paramsArrayElements);
    return paramsArray;
}



static jlong netty_quiche_conn_timeout_as_nanos(JNIEnv* env, jclass clazz, jlong conn) {
    return quiche_conn_timeout_as_nanos((quiche_conn *) conn);
}

static void netty_quiche_conn_on_timeout(JNIEnv* env, jclass clazz, jlong conn) {
    quiche_conn_on_timeout((quiche_conn *) conn);
}

static jlong netty_quiche_conn_readable(JNIEnv* env, jclass clazz, jlong conn) {
    quiche_stream_iter* iter = quiche_conn_readable((quiche_conn *) conn);
    if (iter == NULL) {
        return -1;
    }
    return (jlong) iter;
}

static jlong netty_quiche_conn_writable(JNIEnv* env, jclass clazz, jlong conn) {
    quiche_stream_iter* iter = quiche_conn_writable((quiche_conn *) conn);
    if (iter == NULL) {
        return -1;
    }
    return (jlong) iter;
}

static void netty_quiche_stream_iter_free(JNIEnv* env, jclass clazz, jlong iter) {
    quiche_stream_iter_free((quiche_stream_iter*) iter);
}

static jint netty_quiche_stream_iter_next(JNIEnv* env, jclass clazz, jlong iter, jlongArray streams) {
    quiche_stream_iter* it = (quiche_stream_iter*) iter;
    if (it == NULL) {
        return 0;
    }
    int len = (*env)->GetArrayLength(env, streams);
    if (len == 0) {
        return 0;
    }
    jlong* elements = (*env)->GetLongArrayElements(env, streams, NULL);
    int i = 0;
    while (i < len && quiche_stream_iter_next(it, (uint64_t*) elements + i)) {
        i++;
    }
    (*env)->ReleaseLongArrayElements(env, streams, elements, 0);
    return i;
}

static jint netty_quiche_conn_dgram_max_writable_len(JNIEnv* env, jclass clazz, jlong conn) {
    return (jint) quiche_conn_dgram_max_writable_len((quiche_conn *) conn);
}

static jint netty_quiche_conn_dgram_recv_front_len(JNIEnv* env, jclass clazz, jlong conn) {
    return (jint) quiche_conn_dgram_recv_front_len((quiche_conn*) conn);
}

static jint netty_quiche_conn_dgram_recv(JNIEnv* env, jclass clazz, jlong conn, jlong buf, jint buf_len) {
    return (jint) quiche_conn_dgram_recv((quiche_conn *) conn, (uint8_t *) buf, (size_t) buf_len);
}

static jint netty_quiche_conn_dgram_send(JNIEnv* env, jclass clazz, jlong conn, jlong buf, jint buf_len) {
    return (jint) quiche_conn_dgram_send((quiche_conn *) conn, (uint8_t *) buf, (size_t) buf_len);
}

static jint netty_quiche_conn_set_session(JNIEnv* env, jclass clazz, jlong conn, jbyteArray sessionBytes) {
    int buf_len = (*env)->GetArrayLength(env, sessionBytes);
    uint8_t* buf = (uint8_t*) (*env)->GetByteArrayElements(env, sessionBytes, 0);
    int result = (jint) quiche_conn_set_session((quiche_conn *) conn, (uint8_t *) buf, (size_t) buf_len);
    (*env)->ReleaseByteArrayElements(env, sessionBytes, (jbyte*) buf, JNI_ABORT);
    return result;
}

static jint netty_quiche_conn_max_send_udp_payload_size(JNIEnv* env, jclass clazz, jlong conn) {
    return (jint) quiche_conn_max_send_udp_payload_size((quiche_conn *) conn);
}

static jint netty_quiche_conn_scids_left(JNIEnv* env, jclass clazz, jlong conn) {
    return (jint) quiche_conn_scids_left((quiche_conn *) conn);
}

static jlong netty_quiche_conn_new_scid(JNIEnv* env, jclass clazz, jlong conn, jlong scid, jint scid_len, jbyteArray reset_token, jboolean retire_if_needed, jlong sequenceAddr) {
    uint8_t* buf = (uint8_t*) (*env)->GetByteArrayElements(env, reset_token, 0);

    uint64_t* seq;
    if (sequenceAddr < 0) {
        uint64_t tmp;
        seq = &tmp;
    } else {
        seq = (uint64_t *) sequenceAddr;
    }
    jlong ret = quiche_conn_new_scid((quiche_conn *) conn, (const uint8_t *) scid, scid_len, buf, retire_if_needed == JNI_TRUE ? true : false, seq);
    (*env)->ReleaseByteArrayElements(env, reset_token, (jbyte*)buf, JNI_ABORT);
    return ret;
}

static jbyteArray netty_quiche_conn_retired_scid_next(JNIEnv* env, jclass clazz, jlong conn) {
    const uint8_t *id = NULL;
    size_t len = 0;

    if (quiche_conn_retired_scid_next((quiche_conn *) conn, &id, &len)) {
        return to_byte_array(env, id, len);
    }
    return NULL;
}

static jlong netty_quiche_conn_path_event_next(JNIEnv* env, jclass clazz, jlong conn) {
    const struct quiche_path_event *ev = quiche_conn_path_event_next((quiche_conn *) conn);
    if (ev == NULL) {
        return -1;
    }
    return (jlong) ev;
}

static jint netty_quiche_path_event_type(JNIEnv* env, jclass clazz, jlong ev) {
    return (jint) quiche_path_event_type((quiche_path_event *) ev);
}

static jobject netty_new_socket_address(JNIEnv* env, const struct sockaddr_storage* addr) {
    jobject address = NULL;
    jint port;
    if (addr->ss_family == AF_INET) {
        struct sockaddr_in* s = (struct sockaddr_in*) addr;
        port = ntohs(s->sin_port);
        jbyteArray array = to_byte_array(env, (uint8_t*) &s->sin_addr.s_addr, (size_t) 4);
        address = (*env)->CallStaticObjectMethod(env, inet4address_class, inet4address_class_get_by_address, array);
    } else {
        struct sockaddr_in6* s = (struct sockaddr_in6*) addr;
        port = ntohs(s->sin6_port);
        jbyteArray array = to_byte_array(env, (uint8_t*) &s->sin6_addr.s6_addr, (size_t) 16);
        address = (*env)->CallStaticObjectMethod(env, inet6address_class, inet6address_class_get_by_address, NULL, array, (jint) s->sin6_scope_id);
    }

    return (*env)->NewObject(env, inetsocketaddress_class, inetsocketaddress_class_constructor, address, port);
}

static jobjectArray netty_quiche_conn_path_stats(JNIEnv* env, jclass clazz, jlong conn, jlong idx) {
    quiche_path_stats stats = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    if (quiche_conn_path_stats((quiche_conn *) conn, idx, &stats) != 0) {
        // The idx is not valid. 
        return NULL;
    }

    jobject localAddr = netty_new_socket_address(env, &stats.local_addr);
    if (localAddr == NULL) {
        return NULL;
    }
    jobject peerAddr = netty_new_socket_address(env, &stats.peer_addr);
    if (peerAddr == NULL) {
        return NULL;
    }

    jobjectArray array = (*env)->NewObjectArray(env, 16, object_class, NULL);
    if (array == NULL) {
        return NULL;
    }
    (*env)->SetObjectArrayElement(env, array, 0, localAddr);
    (*env)->SetObjectArrayElement(env, array, 1, peerAddr);
    (*env)->SetObjectArrayElement(env, array, 2, (*env)->CallStaticObjectMethod(env, long_class, long_class_valueof, (jlong) stats.validation_state));
    (*env)->SetObjectArrayElement(env, array, 3, (*env)->CallStaticObjectMethod(env, boolean_class, boolean_class_valueof, stats.active ? JNI_TRUE : JNI_FALSE));
    (*env)->SetObjectArrayElement(env, array, 4, (*env)->CallStaticObjectMethod(env, long_class, long_class_valueof, (jlong) stats.recv));
    (*env)->SetObjectArrayElement(env, array, 5, (*env)->CallStaticObjectMethod(env, long_class, long_class_valueof, (jlong) stats.sent));
    (*env)->SetObjectArrayElement(env, array, 6, (*env)->CallStaticObjectMethod(env, long_class, long_class_valueof, (jlong) stats.lost));
    (*env)->SetObjectArrayElement(env, array, 7, (*env)->CallStaticObjectMethod(env, long_class, long_class_valueof, (jlong) stats.retrans));
    (*env)->SetObjectArrayElement(env, array, 8, (*env)->CallStaticObjectMethod(env, long_class, long_class_valueof, (jlong) stats.rtt));
    (*env)->SetObjectArrayElement(env, array, 9, (*env)->CallStaticObjectMethod(env, long_class, long_class_valueof, (jlong) stats.cwnd));
    (*env)->SetObjectArrayElement(env, array, 10, (*env)->CallStaticObjectMethod(env, long_class, long_class_valueof, (jlong) stats.sent_bytes));
    (*env)->SetObjectArrayElement(env, array, 11, (*env)->CallStaticObjectMethod(env, long_class, long_class_valueof, (jlong) stats.recv_bytes));
    (*env)->SetObjectArrayElement(env, array, 12, (*env)->CallStaticObjectMethod(env, long_class, long_class_valueof, (jlong) stats.lost_bytes));
    (*env)->SetObjectArrayElement(env, array, 13, (*env)->CallStaticObjectMethod(env, long_class, long_class_valueof, (jlong) stats.stream_retrans_bytes));
    (*env)->SetObjectArrayElement(env, array, 14, (*env)->CallStaticObjectMethod(env, long_class, long_class_valueof, (jlong) stats.pmtu));
    (*env)->SetObjectArrayElement(env, array, 15, (*env)->CallStaticObjectMethod(env, long_class, long_class_valueof, (jlong) stats.delivery_rate));
    return array;
}

static jobjectArray netty_quiche_path_event_new(JNIEnv* env, jclass clazz, jlong ev) {
    struct sockaddr_storage local;
    socklen_t local_len;
    struct sockaddr_storage peer;
    socklen_t peer_len;

    quiche_path_event_new((quiche_path_event *) ev, &local, &local_len, &peer, &peer_len);

    jobject localAddr = netty_new_socket_address(env, &local);
    if (localAddr == NULL) {
        return NULL;
    }
    jobject peerAddr = netty_new_socket_address(env, &peer);
    if (peerAddr == NULL) {
        return NULL;
    }

    jobjectArray array = (*env)->NewObjectArray(env, 2, object_class, NULL);
    if (array == NULL) {
        return NULL;
    }
    (*env)->SetObjectArrayElement(env, array, 0, localAddr);
    (*env)->SetObjectArrayElement(env, array, 1, peerAddr);
    return array;
}

static jobjectArray netty_quiche_path_event_validated(JNIEnv* env, jclass clazz, jlong ev) {
    struct sockaddr_storage local;
    socklen_t local_len;
    struct sockaddr_storage peer;
    socklen_t peer_len;

    quiche_path_event_validated((quiche_path_event *) ev, &local, &local_len, &peer, &peer_len);

    jobject localAddr = netty_new_socket_address(env, &local);
    if (localAddr == NULL) {
        return NULL;
    }
    jobject peerAddr = netty_new_socket_address(env, &peer);
    if (peerAddr == NULL) {
        return NULL;
    }

    jobjectArray array = (*env)->NewObjectArray(env, 2, object_class, NULL);
    if (array == NULL) {
        return NULL;
    }
    (*env)->SetObjectArrayElement(env, array, 0, localAddr);
    (*env)->SetObjectArrayElement(env, array, 1, peerAddr);
    return array;
}

static jobjectArray netty_quiche_path_event_failed_validation(JNIEnv* env, jclass clazz, jlong ev) {
    struct sockaddr_storage local;
    socklen_t local_len;
    struct sockaddr_storage peer;
    socklen_t peer_len;

    quiche_path_event_failed_validation((quiche_path_event *) ev, &local, &local_len, &peer, &peer_len);

    jobject localAddr = netty_new_socket_address(env, &local);
    if (localAddr == NULL) {
        return NULL;
    }
    jobject peerAddr = netty_new_socket_address(env, &peer);
    if (peerAddr == NULL) {
        return NULL;
    }

    jobjectArray array = (*env)->NewObjectArray(env, 2, object_class, NULL);
    if (array == NULL) {
        return NULL;
    }
    (*env)->SetObjectArrayElement(env, array, 0, localAddr);
    (*env)->SetObjectArrayElement(env, array, 1, peerAddr);
    return array;
}

static jobjectArray netty_quiche_path_event_closed(JNIEnv* env, jclass clazz, jlong ev) {
    struct sockaddr_storage local;
    socklen_t local_len;
    struct sockaddr_storage peer;
    socklen_t peer_len;

    quiche_path_event_closed((quiche_path_event *) ev, &local, &local_len, &peer, &peer_len);

    jobject localAddr = netty_new_socket_address(env, &local);
    if (localAddr == NULL) {
        return NULL;
    }
    jobject peerAddr = netty_new_socket_address(env, &peer);
    if (peerAddr == NULL) {
        return NULL;
    }

    jobjectArray array = (*env)->NewObjectArray(env, 2, object_class, NULL);
    if (array == NULL) {
        return NULL;
    }
    (*env)->SetObjectArrayElement(env, array, 0, localAddr);
    (*env)->SetObjectArrayElement(env, array, 1, peerAddr);
    return array;
}

static jobjectArray netty_quiche_path_event_reused_source_connection_id(JNIEnv* env, jclass clazz, jlong ev) {
    uint64_t id;
    struct sockaddr_storage local_old;
    socklen_t local_old_len;
    struct sockaddr_storage peer_old;
    socklen_t peer_old_len;
    struct sockaddr_storage local;
    socklen_t local_len;
    struct sockaddr_storage peer;
    socklen_t peer_len;

    quiche_path_event_reused_source_connection_id((quiche_path_event *) ev, &id, &local_old, &local_old_len, &peer_old, &peer_old_len, &local, &local_len, &peer, &peer_len);

    jobject localOldAddr = netty_new_socket_address(env, &local_old);
    if (localOldAddr == NULL) {
        return NULL;
    }
    jobject peerOldAddr = netty_new_socket_address(env, &peer_old);
    if (peerOldAddr == NULL) {
        return NULL;
    }
    jobject localAddr = netty_new_socket_address(env, &local);
    if (localAddr == NULL) {
        return NULL;
    }
    jobject peerAddr = netty_new_socket_address(env, &peer);
    if (peerAddr == NULL) {
        return NULL;
    }
    jobjectArray array = (*env)->NewObjectArray(env, 5, object_class, NULL);
    if (array == NULL) {
        return NULL;
    }
    (*env)->SetObjectArrayElement(env, array, 0, (*env)->CallStaticObjectMethod(env, long_class, long_class_valueof, (jlong) id));
    (*env)->SetObjectArrayElement(env, array, 1, localOldAddr);
    (*env)->SetObjectArrayElement(env, array, 2, peerOldAddr);
    (*env)->SetObjectArrayElement(env, array, 3, localAddr);
    (*env)->SetObjectArrayElement(env, array, 4, peerAddr);
    return array;
}

static jobjectArray netty_quiche_path_event_peer_migrated(JNIEnv* env, jclass clazz, jlong ev) {
    struct sockaddr_storage local;
    socklen_t local_len;
    struct sockaddr_storage peer;
    socklen_t peer_len;
    quiche_path_event_peer_migrated((quiche_path_event *) ev, &local, &local_len, &peer, &peer_len);

    jobject localAddr = netty_new_socket_address(env, &local);
    if (localAddr == NULL) {
        return NULL;
    }
    jobject peerAddr = netty_new_socket_address(env, &peer);
    if (peerAddr == NULL) {
        return NULL;
    }

    jobjectArray array = (*env)->NewObjectArray(env, 2, object_class, NULL);
    if (array == NULL) {
        return NULL;
    }
    (*env)->SetObjectArrayElement(env, array, 0, localAddr);
    (*env)->SetObjectArrayElement(env, array, 1, peerAddr);
    return array;
}

static void netty_quiche_path_event_free(JNIEnv* env, jclass clazz, jlong ev) {
    quiche_path_event_free((quiche_path_event *) ev);
}

static jlong netty_quiche_config_new(JNIEnv* env, jclass clazz, jint version) {
    quiche_config* config = quiche_config_new((uint32_t) version);
    return config == NULL ? -1 : (jlong) config;
}

static void netty_quiche_config_enable_dgram(JNIEnv* env, jclass clazz, jlong config, jboolean enabled, jint recv_queue_len, jint send_queue_len) {
    quiche_config_enable_dgram((quiche_config*) config, enabled == JNI_TRUE ? true : false, (size_t) recv_queue_len, (size_t) send_queue_len);
}

static void netty_quiche_config_grease(JNIEnv* env, jclass clazz, jlong config, jboolean value) {
    quiche_config_grease((quiche_config*) config, value == JNI_TRUE ? true : false);
}

static void netty_quiche_config_set_max_idle_timeout(JNIEnv* env, jclass clazz, jlong config, jlong value) {
    quiche_config_set_max_idle_timeout((quiche_config*) config, (uint64_t) value);
}

static void netty_quiche_config_set_max_recv_udp_payload_size(JNIEnv* env, jclass clazz, jlong config, jlong value) {
    quiche_config_set_max_recv_udp_payload_size((quiche_config*) config, (uint64_t) value);
}

static void netty_quiche_config_set_max_send_udp_payload_size(JNIEnv* env, jclass clazz, jlong config, jlong value) {
    quiche_config_set_max_send_udp_payload_size((quiche_config*) config, (uint64_t) value);
}

static void netty_quiche_config_set_initial_max_data(JNIEnv* env, jclass clazz, jlong config, jlong value) {
    quiche_config_set_initial_max_data((quiche_config*) config, (uint64_t) value);
}

static void netty_quiche_config_set_initial_max_stream_data_bidi_local(JNIEnv* env, jclass clazz, jlong config, jlong value) {
    quiche_config_set_initial_max_stream_data_bidi_local((quiche_config*) config, (uint64_t) value);
}

static void netty_quiche_config_set_initial_max_stream_data_bidi_remote(JNIEnv* env, jclass clazz, jlong config, jlong value) {
    quiche_config_set_initial_max_stream_data_bidi_remote((quiche_config*) config, (uint64_t) value);
}

static void netty_quiche_config_set_initial_max_stream_data_uni(JNIEnv* env, jclass clazz, jlong config, jlong value) {
    quiche_config_set_initial_max_stream_data_uni((quiche_config*) config, (uint64_t) value);
}

static void netty_quiche_config_set_initial_max_streams_bidi(JNIEnv* env, jclass clazz, jlong config, jlong value) {
    quiche_config_set_initial_max_streams_bidi((quiche_config*) config, (uint64_t) value);
}

static void netty_quiche_config_set_initial_max_streams_uni(JNIEnv* env, jclass clazz, jlong config, jlong value) {
    quiche_config_set_initial_max_streams_uni((quiche_config*) config, (uint64_t) value);
}

static void netty_quiche_config_set_ack_delay_exponent(JNIEnv* env, jclass clazz, jlong config, jlong value) {
    quiche_config_set_ack_delay_exponent((quiche_config*) config, (uint64_t) value);
}

static void netty_quiche_config_set_max_ack_delay(JNIEnv* env, jclass clazz, jlong config, jlong value) {
    quiche_config_set_max_ack_delay((quiche_config*) config, (uint64_t) value);
}

static void netty_quiche_config_set_disable_active_migration(JNIEnv* env, jclass clazz, jlong config, jboolean value) {
    quiche_config_set_disable_active_migration((quiche_config*) config, value == JNI_TRUE ? true : false);
}

static void netty_quiche_config_set_cc_algorithm(JNIEnv* env, jclass clazz, jlong config, jint algo) {
    quiche_config_set_cc_algorithm((quiche_config*) config, (enum quiche_cc_algorithm) algo);
}

static void netty_quiche_config_set_initial_congestion_window_packets(JNIEnv* env, jclass clazz, jlong config, jint value) {
    quiche_config_set_initial_congestion_window_packets((quiche_config*) config, (size_t) value);
}

static void netty_quiche_config_enable_hystart(JNIEnv* env, jclass clazz, jlong config, jboolean value) {
    quiche_config_enable_hystart((quiche_config*) config, value == JNI_TRUE ? true : false);
}

static void netty_quiche_config_set_active_connection_id_limit(JNIEnv* env, jclass clazz, jlong config, jlong value) {
    quiche_config_set_active_connection_id_limit((quiche_config*) config, (uint64_t) value);
}

static void netty_quiche_config_set_stateless_reset_token(JNIEnv* env, jclass clazz, jlong config, jbyteArray token) {
    uint8_t* buf = (uint8_t*) (*env)->GetByteArrayElements(env, token, 0);
    quiche_config_set_stateless_reset_token((quiche_config*) config, buf);
    (*env)->ReleaseByteArrayElements(env, token, (jbyte*)buf, JNI_ABORT);
}

static void netty_quiche_config_free(JNIEnv* env, jclass clazz, jlong config) {
    quiche_config_free((quiche_config*) config);
}

static void log_to_java(const char *line, void *argp) {
    JNIEnv* env = NULL;
    quic_get_java_env(&env);
    if (env == NULL) {
        return;
    }

    jstring message =  (*env)->NewStringUTF(env, line);
    if (message == NULL) {
        // out of memory.
        return;
    }
    (*env)->CallVoidMethod(env, quiche_logger, quiche_logger_class_log, message);
}

static void netty_quiche_enable_debug_logging(JNIEnv* env, jclass clazz, jobject logger) {
    if (quiche_logger != NULL) {
        return;
    }
    if ((quiche_logger = (*env)->NewGlobalRef(env, logger)) == NULL) {
        return;
    }

    quiche_enable_debug_logging(log_to_java, NULL);
}

static jlong netty_buffer_memory_address(JNIEnv* env, jclass clazz, jobject buffer) {
    return (jlong) (*env)->GetDirectBufferAddress(env, buffer);
}

// Based on https://gist.github.com/kazuho/45eae4f92257daceb73e.
static jint netty_sockaddr_cmp(JNIEnv* env, jclass clazz,  jlong addr1, jlong addr2) {
    struct sockaddr* x = (struct sockaddr*) addr1;
    struct sockaddr* y = (struct sockaddr*) addr2;

    if (x == NULL && y == NULL) {
        return 0;
    }
    if (x != NULL && y == NULL) {
        return 1;
    }
    if (x == NULL && y != NULL) {
        return -1;
    }

#define CMP(a, b) if (a != b) return a < b ? -1 : 1

    CMP(x->sa_family, y->sa_family);

    if (x->sa_family == AF_INET) {
        struct sockaddr_in *xin = (void*)x, *yin = (void*)y;
        CMP(ntohl(xin->sin_addr.s_addr), ntohl(yin->sin_addr.s_addr));
        CMP(ntohs(xin->sin_port), ntohs(yin->sin_port));
    } else if (x->sa_family == AF_INET6) {
        struct sockaddr_in6 *xin6 = (void*)x, *yin6 = (void*)y;
        int r = memcmp(xin6->sin6_addr.s6_addr, yin6->sin6_addr.s6_addr, sizeof(xin6->sin6_addr.s6_addr));
        if (r != 0)
            return r;
        CMP(ntohs(xin6->sin6_port), ntohs(yin6->sin6_port));
        CMP(xin6->sin6_flowinfo, yin6->sin6_flowinfo);
        CMP(xin6->sin6_scope_id, yin6->sin6_scope_id);
    }

#undef CMP
    return 0;
}

// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod statically_referenced_fixed_method_table[] = {
  { "afInet", "()I", (void *) netty_quiche_afInet },
  { "afInet6", "()I", (void *) netty_quiche_afInet6 },
  { "sizeofSockaddrIn", "()I", (void *) netty_quiche_sizeofSockaddrIn },
  { "sizeofSockaddrIn6", "()I", (void *) netty_quiche_sizeofSockaddrIn6 },
  { "sockaddrInOffsetofSinFamily", "()I", (void *) netty_quiche_sockaddrInOffsetofSinFamily },
  { "sockaddrInOffsetofSinPort", "()I", (void *) netty_quiche_sockaddrInOffsetofSinPort },
  { "sockaddrInOffsetofSinAddr", "()I", (void *) netty_quiche_sockaddrInOffsetofSinAddr },
  { "inAddressOffsetofSAddr", "()I", (void *) netty_quiche_inAddressOffsetofSAddr },
  { "sockaddrIn6OffsetofSin6Family", "()I", (void *) netty_quiche_sockaddrIn6OffsetofSin6Family },
  { "sockaddrIn6OffsetofSin6Port", "()I", (void *) netty_quiche_sockaddrIn6OffsetofSin6Port },
  { "sockaddrIn6OffsetofSin6Flowinfo", "()I", (void *) netty_quiche_sockaddrIn6OffsetofSin6Flowinfo },
  { "sockaddrIn6OffsetofSin6Addr", "()I", (void *) netty_quiche_sockaddrIn6OffsetofSin6Addr },
  { "sockaddrIn6OffsetofSin6ScopeId", "()I", (void *) netty_quiche_sockaddrIn6OffsetofSin6ScopeId },
  { "in6AddressOffsetofS6Addr", "()I", (void *) netty_quiche_in6AddressOffsetofS6Addr },
  { "sizeofSockaddrStorage", "()I", (void *) netty_quiche_sizeofSockaddrStorage },
  { "sizeofSizeT", "()I", (void *) netty_quiche_sizeofSizeT },
  { "sizeofSocklenT", "()I", (void *) netty_quiche_sizeofSocklenT },
  { "quicheRecvInfoOffsetofFrom", "()I", (void *) netty_quicheRecvInfoOffsetofFrom },
  { "quicheRecvInfoOffsetofFromLen", "()I", (void *) netty_quicheRecvInfoOffsetofFromLen },
  { "quicheRecvInfoOffsetofTo", "()I", (void *) netty_quicheRecvInfoOffsetofTo },
  { "quicheRecvInfoOffsetofToLen", "()I", (void *) netty_quicheRecvInfoOffsetofToLen },
  { "sizeofQuicheRecvInfo", "()I", (void *) netty_sizeofQuicheRecvInfo },
  { "quicheSendInfoOffsetofTo", "()I", (void *) netty_quicheSendInfoOffsetofTo },
  { "quicheSendInfoOffsetofToLen", "()I", (void *) netty_quicheSendInfoOffsetofToLen },
  { "quicheSendInfoOffsetofFrom", "()I", (void *) netty_quicheSendInfoOffsetofFrom },
  { "quicheSendInfoOffsetofFromLen", "()I", (void *) netty_quicheSendInfoOffsetofFromLen },
  { "quicheSendInfoOffsetofAt", "()I", (void *) netty_quicheSendInfoOffsetofAt },

  { "sizeofQuicheSendInfo", "()I", (void *) netty_sizeofQuicheSendInfo },
  { "sizeofTimespec", "()I", (void *) netty_quiche_sizeofTimespec },
  { "sizeofTimeT", "()I", (void *) netty_quiche_sizeofTimeT },
  { "sizeofLong", "()I", (void *) netty_quiche_sizeofLong },
  { "timespecOffsetofTvSec", "()I", (void *) netty_quiche_timespecOffsetofTvSec },
  { "timespecOffsetofTvNsec", "()I", (void *) timespecOffsetofTvNsec },
  { "quiche_protocol_version", "()I", (void *) netty_quiche_protocol_version },
  { "quiche_max_conn_id_len", "()I", (void *) netty_quiche_max_conn_id_len },
  { "quiche_shutdown_read", "()I", (void *) netty_quiche_shutdown_read },
  { "quiche_shutdown_write", "()I", (void *) netty_quiche_shutdown_write },
  { "quiche_err_done", "()I", (void *) netty_quiche_err_done },
  { "quiche_err_buffer_too_short", "()I", (void *) netty_quiche_err_buffer_too_short },
  { "quiche_err_unknown_version", "()I", (void *) netty_quiche_err_unknown_version },
  { "quiche_err_invalid_frame", "()I", (void *) netty_quiche_err_invalid_frame },
  { "quiche_err_invalid_packet", "()I", (void *) netty_quiche_err_invalid_packet },
  { "quiche_err_invalid_state", "()I", (void *) netty_quiche_err_invalid_state },
  { "quiche_err_invalid_stream_state", "()I", (void *) netty_quiche_err_invalid_stream_state },
  { "quiche_err_invalid_transport_param", "()I", (void *) netty_quiche_err_invalid_transport_param },
  { "quiche_err_crypto_fail", "()I", (void *) netty_quiche_err_crypto_fail },
  { "quiche_err_tls_fail", "()I", (void *) netty_quiche_err_tls_fail },
  { "quiche_err_flow_control", "()I", (void *) netty_quiche_err_flow_control },
  { "quiche_err_stream_limit", "()I", (void *) netty_quiche_err_stream_limit },
  { "quiche_err_final_size", "()I", (void *) netty_quiche_err_final_size },
  { "quiche_err_stream_stopped", "()I", (void *) netty_quiche_err_stream_stopped },
  { "quiche_err_stream_reset", "()I", (void *) netty_quiche_err_stream_reset },
  { "quiche_err_congestion_control", "()I", (void *) netty_quiche_err_congestion_control },
  { "quiche_err_id_limit", "()I", (void *) netty_quiche_err_id_limit },
  { "quiche_err_out_of_identifiers", "()I", (void *) netty_quiche_err_out_of_identifiers },
  { "quiche_err_key_update", "()I", (void *) netty_quiche_err_key_update },
  { "quiche_err_crypto_buffer_exceeded", "()I", (void *) netty_quiche_err_crypto_buffer_exceeded },
  { "quiche_cc_reno", "()I", (void *) netty_quiche_cc_reno },
  { "quiche_cc_cubic", "()I", (void *) netty_quiche_cc_cubic },
  { "quiche_cc_bbr", "()I", (void *) netty_quiche_cc_bbr },
  { "quiche_path_event_new", "()I", (void *) netty_quiche_path_event_type_new },
  { "quiche_path_event_validated", "()I", (void *) netty_quiche_path_event_type_validated },
  { "quiche_path_event_failed_validation", "()I", (void *) netty_quiche_path_event_type_failed_validation },
  { "quiche_path_event_closed", "()I", (void *) netty_quiche_path_event_type_closed },
  { "quiche_path_event_reused_source_connection_id", "()I", (void *) netty_quiche_path_event_type_reused_source_connection_id },
  { "quiche_path_event_peer_migrated", "()I", (void *) netty_quiche_path_event_type_peer_migrated }
};

static const jint statically_referenced_fixed_method_table_size = sizeof(statically_referenced_fixed_method_table) / sizeof(statically_referenced_fixed_method_table[0]);
static const JNINativeMethod fixed_method_table[] = {
  { "quiche_version", "()Ljava/lang/String;", (void *) netty_quiche_version },
  { "quiche_version_is_supported", "(I)Z", (void *) netty_quiche_version_is_supported },
  { "quiche_negotiate_version", "(JIJIJI)I", (void *) netty_quiche_negotiate_version },
  { "quiche_retry", "(JIJIJIJIIJI)I", (void *) netty_quiche_retry },
  { "quiche_conn_set_qlog_path", "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z", (void *) netty_quiche_conn_set_qlog_path },
  { "quiche_conn_send_quantum", "(J)I", (void *) netty_quiche_conn_send_quantum },
  { "quiche_conn_trace_id", "(J)[B", (void *) netty_quiche_conn_trace_id },
  { "quiche_conn_source_id", "(J)[B", (void *) netty_quiche_conn_source_id },
  { "quiche_conn_destination_id", "(J)[B", (void *) netty_quiche_conn_destination_id },
  { "quiche_conn_new_with_tls", "(JIJIJIJIJJZ)J", (void *) netty_quiche_conn_new_with_tls },
  { "quiche_conn_recv", "(JJIJ)I", (void *) netty_quiche_conn_recv },
  { "quiche_conn_send", "(JJIJ)I", (void *) netty_quiche_conn_send },
  { "quiche_conn_free", "(J)V", (void *) netty_quiche_conn_free },
  { "quiche_conn_peer_error0", "(J)[Ljava/lang/Object;", (void *) netty_quiche_conn_peer_error0 },
  { "quiche_conn_peer_streams_left_bidi", "(J)J", (void *) netty_quiche_conn_peer_streams_left_bidi },
  { "quiche_conn_peer_streams_left_uni", "(J)J", (void *) netty_quiche_conn_peer_streams_left_uni },
  { "quiche_conn_stream_priority", "(JJBZ)I", (void *) netty_quiche_conn_stream_priority },
  { "quiche_conn_stream_recv", "(JJJIJ)I", (void *) netty_quiche_conn_stream_recv },
  { "quiche_conn_stream_send", "(JJJIZ)I", (void *) netty_quiche_conn_stream_send },
  { "quiche_conn_stream_shutdown", "(JJIJ)I", (void *) netty_quiche_conn_stream_shutdown },
  { "quiche_conn_stream_capacity", "(JJ)I", (void *) netty_quiche_conn_stream_capacity },
  { "quiche_conn_stream_finished", "(JJ)Z", (void *) netty_quiche_conn_stream_finished },
  { "quiche_conn_close", "(JZJJI)I", (void *) netty_quiche_conn_close },
  { "quiche_conn_is_established", "(J)Z", (void *) netty_quiche_conn_is_established },
  { "quiche_conn_is_in_early_data", "(J)Z", (void *) netty_quiche_conn_is_in_early_data },
  { "quiche_conn_is_closed", "(J)Z", (void *) netty_quiche_conn_is_closed },
  { "quiche_conn_is_timed_out", "(J)Z", (void *) netty_quiche_conn_is_timed_out },
  { "quiche_conn_stats", "(J)[J", (void *) netty_quiche_conn_stats },
  { "quiche_conn_peer_transport_params", "(J)[J", (void *) netty_quiche_conn_peer_transport_params },
  { "quiche_conn_timeout_as_nanos", "(J)J", (void *) netty_quiche_conn_timeout_as_nanos },
  { "quiche_conn_on_timeout", "(J)V", (void *) netty_quiche_conn_on_timeout },
  { "quiche_conn_readable", "(J)J", (void *) netty_quiche_conn_readable },
  { "quiche_conn_writable", "(J)J", (void *) netty_quiche_conn_writable },
  { "quiche_stream_iter_free", "(J)V", (void *) netty_quiche_stream_iter_free },
  { "quiche_stream_iter_next", "(J[J)I", (void *) netty_quiche_stream_iter_next },
  { "quiche_conn_dgram_max_writable_len", "(J)I", (void* ) netty_quiche_conn_dgram_max_writable_len },
  { "quiche_conn_dgram_recv_front_len", "(J)I", (void* ) netty_quiche_conn_dgram_recv_front_len },
  { "quiche_conn_dgram_recv", "(JJI)I", (void* ) netty_quiche_conn_dgram_recv },
  { "quiche_conn_dgram_send", "(JJI)I", (void* ) netty_quiche_conn_dgram_send },
  { "quiche_conn_set_session", "(J[B)I", (void* ) netty_quiche_conn_set_session },
  { "quiche_conn_max_send_udp_payload_size", "(J)I", (void* ) netty_quiche_conn_max_send_udp_payload_size },
  { "quiche_conn_scids_left", "(J)I", (void* ) netty_quiche_conn_scids_left },
  { "quiche_conn_new_scid", "(JJI[BZJ)J", (void* ) netty_quiche_conn_new_scid },
  { "quiche_conn_retired_scid_next", "(J)[B", (void* ) netty_quiche_conn_retired_scid_next },
  { "quiche_config_new", "(I)J", (void *) netty_quiche_config_new },
  { "quiche_config_enable_dgram", "(JZII)V", (void *) netty_quiche_config_enable_dgram },
  { "quiche_config_grease", "(JZ)V", (void *) netty_quiche_config_grease },
  { "quiche_config_set_max_idle_timeout", "(JJ)V", (void *) netty_quiche_config_set_max_idle_timeout },
  { "quiche_config_set_max_recv_udp_payload_size", "(JJ)V", (void *) netty_quiche_config_set_max_recv_udp_payload_size },
  { "quiche_config_set_max_send_udp_payload_size", "(JJ)V", (void *) netty_quiche_config_set_max_send_udp_payload_size },
  { "quiche_config_set_initial_max_data", "(JJ)V", (void *) netty_quiche_config_set_initial_max_data },
  { "quiche_config_set_initial_max_stream_data_bidi_local", "(JJ)V", (void *) netty_quiche_config_set_initial_max_stream_data_bidi_local },
  { "quiche_config_set_initial_max_stream_data_bidi_remote", "(JJ)V", (void *) netty_quiche_config_set_initial_max_stream_data_bidi_remote },
  { "quiche_config_set_initial_max_stream_data_uni", "(JJ)V", (void *) netty_quiche_config_set_initial_max_stream_data_uni },
  { "quiche_config_set_initial_max_streams_bidi", "(JJ)V", (void *) netty_quiche_config_set_initial_max_streams_bidi },
  { "quiche_config_set_initial_max_streams_uni", "(JJ)V", (void *) netty_quiche_config_set_initial_max_streams_uni },
  { "quiche_config_set_ack_delay_exponent", "(JJ)V", (void *) netty_quiche_config_set_ack_delay_exponent },
  { "quiche_config_set_max_ack_delay", "(JJ)V", (void *) netty_quiche_config_set_max_ack_delay },
  { "quiche_config_set_disable_active_migration", "(JZ)V", (void *) netty_quiche_config_set_disable_active_migration },
  { "quiche_config_set_cc_algorithm", "(JI)V", (void *) netty_quiche_config_set_cc_algorithm },
  { "quiche_config_set_initial_congestion_window_packets", "(JI)V", (void *) netty_quiche_config_set_initial_congestion_window_packets },
  { "quiche_config_enable_hystart", "(JZ)V", (void *) netty_quiche_config_enable_hystart },
  { "quiche_config_set_active_connection_id_limit", "(JJ)V", (void *) netty_quiche_config_set_active_connection_id_limit },
  { "quiche_config_set_stateless_reset_token", "(J[B)V", (void *) netty_quiche_config_set_stateless_reset_token },
  { "quiche_config_free", "(J)V", (void *) netty_quiche_config_free },
  { "buffer_memory_address", "(Ljava/nio/ByteBuffer;)J", (void *) netty_buffer_memory_address},
  { "sockaddr_cmp", "(JJ)I", (void *) netty_sockaddr_cmp},
  { "quiche_conn_path_event_next", "(J)J", (void *) netty_quiche_conn_path_event_next },
  { "quiche_path_event_type", "(J)I", (void *) netty_quiche_path_event_type },
  { "quiche_conn_path_stats", "(JJ)[Ljava/lang/Object;", (void *) netty_quiche_conn_path_stats },
  { "quiche_path_event_new", "(J)[Ljava/lang/Object;", (void *) netty_quiche_path_event_new },
  { "quiche_path_event_validated", "(J)[Ljava/lang/Object;", (void *) netty_quiche_path_event_validated },
  { "quiche_path_event_failed_validation", "(J)[Ljava/lang/Object;", (void *) netty_quiche_path_event_failed_validation },
  { "quiche_path_event_closed", "(J)[Ljava/lang/Object;", (void *) netty_quiche_path_event_closed },
  { "quiche_path_event_reused_source_connection_id", "(J)[Ljava/lang/Object;", (void *) netty_quiche_path_event_reused_source_connection_id },
  { "quiche_path_event_peer_migrated", "(J)[Ljava/lang/Object;", (void *) netty_quiche_path_event_peer_migrated },
  { "quiche_path_event_free", "(J)V", (void *) netty_quiche_path_event_free }
};

static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);

static jint dynamicMethodsTableSize() {
    return fixed_method_table_size + 1;
}

static JNINativeMethod* createDynamicMethodsTable(const char* packagePrefix) {
    char* dynamicTypeName = NULL;
    int len = sizeof(JNINativeMethod) * dynamicMethodsTableSize();
    JNINativeMethod* dynamicMethods = malloc(len);
    if (dynamicMethods == NULL) {
        goto error;
    }
    memset(dynamicMethods, 0, len);
    memcpy(dynamicMethods, fixed_method_table, sizeof(fixed_method_table));

    JNINativeMethod* dynamicMethod = &dynamicMethods[fixed_method_table_size];
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/QuicheLogger;)V", dynamicTypeName, error);
    NETTY_JNI_UTIL_PREPEND("(L", dynamicTypeName,  dynamicMethod->signature, error);
    netty_jni_util_free_dynamic_name(&dynamicTypeName);
    dynamicMethod->name = "quiche_enable_debug_logging";
    dynamicMethod->fnPtr = (void *) netty_quiche_enable_debug_logging;
    return dynamicMethods;
error:
    netty_jni_util_free_dynamic_methods_table(dynamicMethods, fixed_method_table_size, dynamicMethodsTableSize());
    free(dynamicTypeName);
    return NULL;
}

// JNI Method Registration Table End

// IMPORTANT: If you add any NETTY_JNI_UTIL_LOAD_CLASS or NETTY_JNI_UTIL_FIND_CLASS calls you also need to update
//            Quiche to reflect that.
static jint netty_quiche_JNI_OnLoad(JNIEnv* env, char const* packagePrefix) {
    int ret = JNI_ERR;
    int staticallyRegistered = 0;
    int nativeRegistered = 0;
    int boringsslLoaded = 0;

    jclass quiche_logger_class = NULL;
    char* name = NULL;

    // We must register the statically referenced methods first!
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            STATICALLY_CLASSNAME,
            statically_referenced_fixed_method_table,
            statically_referenced_fixed_method_table_size) != 0) {
        goto done;
    }
    staticallyRegistered = 1;

    JNINativeMethod* dynamicMethods = createDynamicMethodsTable(packagePrefix);
    if (dynamicMethods == NULL) {
        goto done;
    }
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            QUICHE_CLASSNAME,
            dynamicMethods,
            dynamicMethodsTableSize()) != 0) {
        goto done;
    }
    nativeRegistered = 1;

    NETTY_JNI_UTIL_LOAD_CLASS(env, integer_class, "java/lang/Integer", done);
    if ((integer_class_valueof = (*env)->GetStaticMethodID(env, integer_class, "valueOf", "(I)Ljava/lang/Integer;")) == NULL) {
        goto done;
    }
    NETTY_JNI_UTIL_LOAD_CLASS(env, boolean_class, "java/lang/Boolean", done);
    if ((boolean_class_valueof = (*env)->GetStaticMethodID(env, boolean_class, "valueOf", "(Z)Ljava/lang/Boolean;")) == NULL) {
        goto done;
    }

    NETTY_JNI_UTIL_LOAD_CLASS(env, long_class, "java/lang/Long", done);
    if ((long_class_valueof = (*env)->GetStaticMethodID(env, long_class, "valueOf", "(J)Ljava/lang/Long;")) == NULL) {
        goto done;
    }

    NETTY_JNI_UTIL_LOAD_CLASS(env, inet4address_class, "java/net/Inet4Address", done);
    if ((inet4address_class_get_by_address = (*env)->GetStaticMethodID(env, inet4address_class, "getByAddress", "([B)Ljava/net/InetAddress;")) == NULL) {
        goto done;
    }

    NETTY_JNI_UTIL_LOAD_CLASS(env, inet6address_class, "java/net/Inet6Address", done);
    if ((inet6address_class_get_by_address = (*env)->GetStaticMethodID(env, inet6address_class, "getByAddress", "(Ljava/lang/String;[BI)Ljava/net/Inet6Address;")) == NULL) {
        goto done;
    }

    NETTY_JNI_UTIL_LOAD_CLASS(env, inetsocketaddress_class, "java/net/InetSocketAddress", done);
    NETTY_JNI_UTIL_GET_METHOD(env, inetsocketaddress_class, inetsocketaddress_class_constructor, "<init>", "(Ljava/net/InetAddress;I)V", done);

    NETTY_JNI_UTIL_LOAD_CLASS(env, object_class, "java/lang/Object", done);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/QuicheLogger", name, done);
    NETTY_JNI_UTIL_LOAD_CLASS_WEAK(env, quiche_logger_class_weak, name, done);
    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(env, quiche_logger_class, quiche_logger_class_weak, done);
    NETTY_JNI_UTIL_GET_METHOD(env, quiche_logger_class, quiche_logger_class_log, "log", "(Ljava/lang/String;)V", done);
    // Initialize this module

    // Load all c modules that we depend upon
    if (netty_boringssl_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }
    boringsslLoaded = 1;

    staticPackagePrefix = packagePrefix;

    ret = NETTY_JNI_UTIL_JNI_VERSION;
done:
    if (ret == JNI_ERR) {
        if (staticallyRegistered == 1) {
            netty_jni_util_unregister_natives(env, packagePrefix, STATICALLY_CLASSNAME);
        }
        if (nativeRegistered == 1) {
            netty_jni_util_unregister_natives(env, packagePrefix, QUICHE_CLASSNAME);
        }
        if (boringsslLoaded == 1) {
            netty_boringssl_JNI_OnUnload(env, packagePrefix);
        }

        NETTY_JNI_UTIL_UNLOAD_CLASS_WEAK(env, quiche_logger_class_weak);
        NETTY_JNI_UTIL_UNLOAD_CLASS(env, integer_class);
        NETTY_JNI_UTIL_UNLOAD_CLASS(env, boolean_class);
        NETTY_JNI_UTIL_UNLOAD_CLASS(env, long_class);
        NETTY_JNI_UTIL_UNLOAD_CLASS(env, inet4address_class);
        NETTY_JNI_UTIL_UNLOAD_CLASS(env, inet6address_class);
        NETTY_JNI_UTIL_UNLOAD_CLASS(env, inetsocketaddress_class);
        NETTY_JNI_UTIL_UNLOAD_CLASS(env, object_class);

        netty_jni_util_free_dynamic_methods_table(dynamicMethods, fixed_method_table_size, dynamicMethodsTableSize());
    }
    NETTY_JNI_UTIL_DELETE_LOCAL(env, quiche_logger_class);
    return ret;
}

static void netty_quiche_JNI_OnUnload(JNIEnv* env) {
    netty_boringssl_JNI_OnUnload(env, staticPackagePrefix);

    NETTY_JNI_UTIL_UNLOAD_CLASS_WEAK(env, quiche_logger_class_weak);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, integer_class);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, boolean_class);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, long_class);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, inet4address_class);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, inet6address_class);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, inetsocketaddress_class);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, object_class);

    netty_jni_util_unregister_natives(env, staticPackagePrefix, STATICALLY_CLASSNAME);
    netty_jni_util_unregister_natives(env, staticPackagePrefix, QUICHE_CLASSNAME);

    if (quiche_logger != NULL) {
        (*env)->DeleteGlobalRef(env, quiche_logger);
        quiche_logger = NULL;
    }
    free((void*) staticPackagePrefix);
    staticPackagePrefix = NULL;
}

// Invoked by the JVM when statically linked

// We build with -fvisibility=hidden so ensure we mark everything that needs to be visible with JNIEXPORT
// https://mail.openjdk.java.net/pipermail/core-libs-dev/2013-February/014549.html

// Invoked by the JVM when statically linked
JNIEXPORT jint JNI_OnLoad_netty_quiche(JavaVM* vm, void* reserved) {
    global_vm = vm;
    return netty_jni_util_JNI_OnLoad(vm, reserved, LIBRARYNAME, netty_quiche_JNI_OnLoad);
}

// Invoked by the JVM when statically linked
JNIEXPORT void JNI_OnUnload_netty_quiche(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_quiche_JNI_OnUnload);
    global_vm = NULL;
}

#ifndef NETTY_QUIC_BUILD_STATIC
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    global_vm = vm;
    return netty_jni_util_JNI_OnLoad(vm, reserved, LIBRARYNAME, netty_quiche_JNI_OnLoad);
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_quiche_JNI_OnUnload);
    global_vm = NULL;
}
#endif /* NETTY_QUIC_BUILD_STATIC */
