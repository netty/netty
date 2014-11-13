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
package io.netty.channel.epoll;

/**
 * <p>
 * struct tcp_info
 * {
 *      __u8    tcpi_state;
 *      __u8    tcpi_ca_state;
 *      __u8    tcpi_retransmits;
 *      __u8    tcpi_probes;
 *      __u8    tcpi_backoff;
 *      __u8    tcpi_options;
 *      __u8    tcpi_snd_wscale : 4, tcpi_rcv_wscale : 4;
 *
 *      __u32   tcpi_rto;
 *      __u32   tcpi_ato;
 *      __u32   tcpi_snd_mss;
 *      __u32   tcpi_rcv_mss;
 *
 *      __u32   tcpi_unacked;
 *      __u32   tcpi_sacked;
 *      __u32   tcpi_lost;
 *      __u32   tcpi_retrans;
 *      __u32   tcpi_fackets;
 *
 *      __u32   tcpi_last_data_sent;
 *      __u32   tcpi_last_ack_sent;
 *      __u32   tcpi_last_data_recv;
 *      __u32   tcpi_last_ack_recv;
 *
 *      __u32   tcpi_pmtu;
 *      __u32   tcpi_rcv_ssthresh;
 *      __u32   tcpi_rtt;
 *      __u32   tcpi_rttvar;
 *      __u32   tcpi_snd_ssthresh;
 *      __u32   tcpi_snd_cwnd;
 *      __u32   tcpi_advmss;
 *      __u32   tcpi_reordering;
 *
 *      __u32   tcpi_rcv_rtt;
 *      __u32   tcpi_rcv_space;
 *
 *      __u32   tcpi_total_retrans;
 * };
 * </p>
 */
public final class EpollTcpInfo {

    final int[] info = new int[32];

    public int state() {
        return info[0] & 0xFF;
    }

    public int caState() {
        return info[1] & 0xFF;
    }

    public int retransmits() {
        return info[2] & 0xFF;
    }

    public int probes() {
        return info[3] & 0xFF;
    }

    public int backoff() {
        return info[4] & 0xFF;
    }

    public int options() {
        return info[5] & 0xFF;
    }

    public int sndWscale() {
        return info[6] & 0xFF;
    }

    public int rcvWscale() {
        return info[7] & 0xFF;
    }

    public long rto() {
        return info[8] & 0xFFFFFFFFL;
    }

    public long ato() {
        return info[9] & 0xFFFFFFFFL;
    }

    public long sndMss() {
        return info[10] & 0xFFFFFFFFL;
    }

    public long rcvMss() {
        return info[11] & 0xFFFFFFFFL;
    }

    public long unacked() {
        return info[12] & 0xFFFFFFFFL;
    }

    public long sacked() {
        return info[13] & 0xFFFFFFFFL;
    }

    public long lost() {
        return info[14] & 0xFFFFFFFFL;
    }

    public long retrans() {
        return info[15] & 0xFFFFFFFFL;
    }

    public long fackets() {
        return info[16] & 0xFFFFFFFFL;
    }

    public long lastDataSent() {
        return info[17] & 0xFFFFFFFFL;
    }

    public long lastAckSent() {
        return info[18] & 0xFFFFFFFFL;
    }

    public long lastDataRecv() {
        return info[19] & 0xFFFFFFFFL;
    }

    public long lastAckRecv() {
        return info[20] & 0xFFFFFFFFL;
    }

    public long pmtu() {
        return info[21] & 0xFFFFFFFFL;
    }

    public long rcvSsthresh() {
        return info[22] & 0xFFFFFFFFL;
    }

    public long rtt() {
        return info[23] & 0xFFFFFFFFL;
    }

    public long rttvar() {
        return info[24] & 0xFFFFFFFFL;
    }

    public long sndSsthresh() {
        return info[25] & 0xFFFFFFFFL;
    }

    public long sndCwnd() {
        return info[26] & 0xFFFFFFFFL;
    }

    public long advmss() {
        return info[27] & 0xFFFFFFFFL;
    }

    public long reordering() {
        return info[28] & 0xFFFFFFFFL;
    }

    public long rcvRtt() {
        return info[29] & 0xFFFFFFFFL;
    }

    public long rcvSpace() {
        return info[30] & 0xFFFFFFFFL;
    }

    public long totalRetrans() {
        return info[31] & 0xFFFFFFFFL;
    }
}
