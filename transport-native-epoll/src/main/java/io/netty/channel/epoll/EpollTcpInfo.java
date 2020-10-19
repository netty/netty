/*
 * Copyright 2014 The Netty Project
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

    final long[] info = new long[32];

    public int state() {
        return (int) info[0];
    }

    public int caState() {
        return (int) info[1];
    }

    public int retransmits() {
        return (int) info[2];
    }

    public int probes() {
        return (int) info[3];
    }

    public int backoff() {
        return (int) info[4];
    }

    public int options() {
        return (int) info[5];
    }

    public int sndWscale() {
        return (int) info[6];
    }

    public int rcvWscale() {
        return (int) info[7];
    }

    public long rto() {
        return info[8];
    }

    public long ato() {
        return info[9];
    }

    public long sndMss() {
        return info[10];
    }

    public long rcvMss() {
        return info[11];
    }

    public long unacked() {
        return info[12];
    }

    public long sacked() {
        return info[13];
    }

    public long lost() {
        return info[14];
    }

    public long retrans() {
        return info[15];
    }

    public long fackets() {
        return info[16];
    }

    public long lastDataSent() {
        return info[17];
    }

    public long lastAckSent() {
        return info[18];
    }

    public long lastDataRecv() {
        return info[19];
    }

    public long lastAckRecv() {
        return info[20];
    }

    public long pmtu() {
        return info[21];
    }

    public long rcvSsthresh() {
        return info[22];
    }

    public long rtt() {
        return info[23];
    }

    public long rttvar() {
        return info[24];
    }

    public long sndSsthresh() {
        return info[25];
    }

    public long sndCwnd() {
        return info[26];
    }

    public long advmss() {
        return info[27];
    }

    public long reordering() {
        return info[28];
    }

    public long rcvRtt() {
        return info[29];
    }

    public long rcvSpace() {
        return info[30];
    }

    public long totalRetrans() {
        return info[31];
    }
}
