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
#include <quiche.h>
#include "netty_jni_util.h"
#include "netty_quic_boringssl.h"
#include "netty_quic.h"

// Add define if NETTY_BUILD_STATIC is defined so it is picked up in netty_jni_util.c
#ifdef NETTY_BUILD_STATIC
#define NETTY_JNI_UTIL_BUILD_STATIC
#endif

#define STATICALLY_CLASSNAME "io/netty/incubator/codec/quic/QuicheNativeStaticallyReferencedJniMethods"
#define QUICHE_CLASSNAME "io/netty/incubator/codec/quic/Quiche"
#define LIBRARYNAME "netty_quiche"

static jclass    quiche_logger_class;
static jmethodID quiche_logger_class_log;
static jobject   quiche_logger;
static JavaVM     *global_vm = NULL;

jint quic_get_java_env(JNIEnv **env)
{
    return (*global_vm)->GetEnv(global_vm, (void **)env, NETTY_JNI_UTIL_JNI_VERSION);
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

static jint netty_quiche_err_congestion_control(JNIEnv* env, jclass clazz) {
    return QUICHE_ERR_CONGESTION_CONTROL;
}

static jint netty_quiche_cc_reno(JNIEnv* env, jclass clazz) {
    return QUICHE_CC_RENO;
}

static jint netty_quiche_cc_cubic(JNIEnv* env, jclass clazz) {
    return QUICHE_CC_CUBIC;
}

static jstring netty_quiche_version(JNIEnv* env, jclass clazz) {
    return (*env)->NewStringUTF(env, quiche_version());
}

static jboolean netty_quiche_version_is_supported(JNIEnv* env, jclass clazz, jint version) {
    return quiche_version_is_supported(version) == true ? JNI_TRUE : JNI_FALSE;
}

static jint netty_quiche_header_info(JNIEnv* env, jclass clazz, jlong buf, jint buf_len, jint dcil, jlong version,
                 jlong type, jlong scid, jlong scid_len, jlong dcid, jlong dcid_len, jlong token, jlong token_len) {
    return (jint) quiche_header_info((const uint8_t *) buf, (size_t) buf_len, (size_t) dcil,
                                         (uint32_t *) version, (uint8_t *) type,
                                         (uint8_t *) scid, (size_t *) scid_len,
                                         (uint8_t *) dcid, (size_t *) dcid_len,
                                         (uint8_t *) token, (size_t *) token_len);
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

static jlong netty_quiche_conn_new_with_tls(JNIEnv* env, jclass clazz, jlong scid, jint scid_len, jlong odcid, jint odcid_len, jlong config, jlong ssl, jboolean isServer) {
    const uint8_t * odcid_pointer = NULL;
    if (odcid_len != -1) {
        odcid_pointer = (const uint8_t *) odcid;
    }
    quiche_conn *conn = quiche_conn_new_with_tls((const uint8_t *) scid, (size_t) scid_len,
                                 odcid_pointer, (size_t) odcid_len,
                                 (quiche_config *) config, (void*) ssl, isServer == JNI_TRUE ? true : false);
    if (conn == NULL) {
        return -1;
    }
    return (jlong) conn;
}

static jbyteArray netty_quiche_conn_trace_id(JNIEnv* env, jclass clazz, jlong conn) {
    const uint8_t *trace_id = NULL;
    size_t trace_id_len = 0;

    quiche_conn_trace_id((quiche_conn *) conn, &trace_id, &trace_id_len);
    if (trace_id == NULL || trace_id_len == 0) {
        return NULL;
    }
     jbyteArray array = (*env)->NewByteArray(env, trace_id_len);
     if (array == NULL) {
        return NULL;
     }
     (*env)->SetByteArrayRegion(env,array, 0, trace_id_len, (jbyte*) trace_id);
     return array;
}

static jint netty_quiche_conn_recv(JNIEnv* env, jclass clazz, jlong conn, jlong buf, jint buf_len) {
    return (jint) quiche_conn_recv((quiche_conn *) conn, (uint8_t *) buf, (size_t) buf_len);
}

static jint netty_quiche_conn_send(JNIEnv* env, jclass clazz, jlong conn, jlong out, jint out_len) {
    return (jint) quiche_conn_send((quiche_conn *) conn, (uint8_t *) out, (size_t) out_len);
}

static void netty_quiche_conn_free(JNIEnv* env, jclass clazz, jlong conn) {
    quiche_conn_free((quiche_conn *) conn);
}

static jint netty_quiche_conn_stream_priority(JNIEnv* env, jclass clazz, jlong conn, jlong stream_id, jbyte urgency, jboolean incremental) {
    return (jint) quiche_conn_stream_priority((quiche_conn *) conn, (uint64_t) stream_id,  (uint8_t) urgency, incremental == JNI_TRUE ? true : false);
}

static jint netty_quiche_conn_stream_recv(JNIEnv* env, jclass clazz, jlong conn, jlong stream_id, jlong out, int buf_len, jlong finAddr) {
    return (jint) quiche_conn_stream_recv((quiche_conn *) conn, (uint64_t) stream_id,  (uint8_t *) out, (size_t) buf_len, (bool *) finAddr);
}

static jint netty_quiche_conn_stream_send(JNIEnv* env, jclass clazz, jlong conn, jlong stream_id, jlong buf, int buf_len, jboolean fin) {
    return (jint) quiche_conn_stream_send((quiche_conn *) conn, (uint64_t) stream_id,  (uint8_t *) buf, (size_t) buf_len, fin == JNI_TRUE ? true : false);
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

static jlongArray netty_quiche_conn_stats(JNIEnv* env, jclass clazz, jlong conn) {
    quiche_stats stats = {0,0,0,0,0,0};
    quiche_conn_stats((quiche_conn *) conn, &stats);

    jlongArray statsArray = (*env)->NewLongArray(env, 6);
    if (statsArray == NULL) {
        // This will put an OOME on the stack
        return NULL;
    }
    jlong statsArrayElements[] = {
        (jlong)stats.recv,
        (jlong)stats.sent,
        (jlong)stats.lost,
        (jlong)stats.rtt,
        (jlong)stats.cwnd,
        (jlong)stats.delivery_rate,
    };
    (*env)->SetLongArrayRegion(env, statsArray, 0, 6, statsArrayElements);
    return statsArray;
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

static void netty_quiche_config_enable_hystart(JNIEnv* env, jclass clazz, jlong config, jboolean value) {
    quiche_config_enable_hystart((quiche_config*) config, value == JNI_TRUE ? true : false);
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

// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod statically_referenced_fixed_method_table[] = {
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
  { "quiche_err_congestion_control", "()I", (void *) netty_quiche_err_congestion_control },
  { "quiche_cc_reno", "()I", (void *) netty_quiche_cc_reno },
  { "quiche_cc_cubic", "()I", (void *) netty_quiche_cc_cubic }
};

static const jint statically_referenced_fixed_method_table_size = sizeof(statically_referenced_fixed_method_table) / sizeof(statically_referenced_fixed_method_table[0]);
static const JNINativeMethod fixed_method_table[] = {
  { "quiche_version", "()Ljava/lang/String;", (void *) netty_quiche_version },
  { "quiche_version_is_supported", "(I)Z", (void *) netty_quiche_version_is_supported },
  { "quiche_header_info", "(JIIJJJJJJJJ)I", (void *) netty_quiche_header_info },
  { "quiche_negotiate_version", "(JIJIJI)I", (void *) netty_quiche_negotiate_version },
  { "quiche_retry", "(JIJIJIJIIJI)I", (void *) netty_quiche_retry },
  { "quiche_conn_trace_id", "(J)[B", (void *) netty_quiche_conn_trace_id },
  { "quiche_conn_new_with_tls", "(JIJIJJZ)J", (void *) netty_quiche_conn_new_with_tls },
  { "quiche_conn_recv", "(JJI)I", (void *) netty_quiche_conn_recv },
  { "quiche_conn_send", "(JJI)I", (void *) netty_quiche_conn_send },
  { "quiche_conn_free", "(J)V", (void *) netty_quiche_conn_free },
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
  { "quiche_conn_stats", "(J)[J", (void *) netty_quiche_conn_stats },
  { "quiche_conn_timeout_as_nanos", "(J)J", (void *) netty_quiche_conn_timeout_as_nanos },
  { "quiche_conn_on_timeout", "(J)V", (void *) netty_quiche_conn_on_timeout },
  { "quiche_conn_readable", "(J)J", (void *) netty_quiche_conn_readable },
  { "quiche_stream_iter_free", "(J)V", (void *) netty_quiche_stream_iter_free },
  { "quiche_stream_iter_next", "(J[J)I", (void *) netty_quiche_stream_iter_next },
  { "quiche_conn_dgram_max_writable_len", "(J)I", (void* ) netty_quiche_conn_dgram_max_writable_len },
  { "quiche_conn_dgram_recv_front_len", "(J)I", (void* ) netty_quiche_conn_dgram_recv_front_len },
  { "quiche_conn_dgram_recv", "(JJI)I", (void* ) netty_quiche_conn_dgram_recv },
  { "quiche_conn_dgram_send", "(JJI)I", (void* ) netty_quiche_conn_dgram_send },
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
  { "quiche_config_enable_hystart", "(JZ)V", (void *) netty_quiche_config_enable_hystart },
  { "quiche_config_free", "(J)V", (void *) netty_quiche_config_free },
  { "buffer_memory_address", "(Ljava/nio/ByteBuffer;)J", (void *) netty_buffer_memory_address}
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
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/incubator/codec/quic/QuicheLogger;)V", dynamicTypeName, error);
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

static jint netty_quiche_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    int ret = JNI_ERR;
    int staticallyRegistered = 0;
    int nativeRegistered = 0;
    int boringsslLoaded = 0;

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

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/incubator/codec/quic/QuicheLogger", name, done);
    NETTY_JNI_UTIL_LOAD_CLASS(env, quiche_logger_class, name, done);
    NETTY_JNI_UTIL_GET_METHOD(env, quiche_logger_class, quiche_logger_class_log, "log", "(Ljava/lang/String;)V", done);
    // Initialize this module

    // Load all c modules that we depend upon
    if (netty_boringssl_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }
    boringsslLoaded = 1;

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

        NETTY_JNI_UTIL_UNLOAD_CLASS(env, quiche_logger_class);
        netty_jni_util_free_dynamic_methods_table(dynamicMethods, fixed_method_table_size, dynamicMethodsTableSize());
    }
    return ret;
}

static void netty_quiche_JNI_OnUnload(JNIEnv* env, const char* packagePrefix) {
    netty_boringssl_JNI_OnUnload(env, packagePrefix);

    NETTY_JNI_UTIL_UNLOAD_CLASS(env, quiche_logger_class);

    netty_jni_util_unregister_natives(env, packagePrefix, STATICALLY_CLASSNAME);
    netty_jni_util_unregister_natives(env, packagePrefix, QUICHE_CLASSNAME);

    if (quiche_logger != NULL) {
        (*env)->DeleteGlobalRef(env, quiche_logger);
        quiche_logger = NULL;
    }
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

#ifndef NETTY_BUILD_STATIC
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    global_vm = vm;
    return netty_jni_util_JNI_OnLoad(vm, reserved, LIBRARYNAME, netty_quiche_JNI_OnLoad);
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_quiche_JNI_OnUnload);
    global_vm = NULL;
}
#endif /* NETTY_BUILD_STATIC */
