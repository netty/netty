package io.netty.handler.codec.http3;

import javax.annotation.Nullable;

public enum Http3SettingIdentifier {

    /**
     * QPACK maximum table capacity setting identifier (<b>0x1</b>).
     * <p>
     * Defined in <a href="https://datatracker.ietf.org/doc/html/rfc9204#section-5">
     * RFC 9204, Section 5 (SETTINGS_QPACK_MAX_TABLE_CAPACITY)</a> and registered in
     * the <a href="https://www.iana.org/assignments/http3-parameters/http3-parameters.xhtml#settings">
     * HTTP/3 SETTINGS registry (IANA)</a>.
     * <br>
     * Controls the maximum size of the dynamic table used by QPACK.
     */
    HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY(0x1),

    /**
     * Maximum field section size setting identifier (<b>0x6</b>).
     * <p>
     * Defined in <a href="https://datatracker.ietf.org/doc/html/rfc9114#section-7.2.4.1">
     * RFC 9114, Section 7.2.4.1 (SETTINGS_MAX_FIELD_SECTION_SIZE)</a> , also referenced
     * in the <a href="https://datatracker.ietf.org/doc/html/rfc9114#section-7.2.4.1">
     * HTTP/3 SETTINGS registry (RFC 9114, Section 7.2.4.1)</a> and registered in
     * the <a href="https://www.iana.org/assignments/http3-parameters/http3-parameters.xhtml#settings">
     * HTTP/3 SETTINGS registry (IANA)</a>.
     * <br>
     * Specifies the upper bound on the total size of HTTP field sections accepted by a peer.
     */
    HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE(0x6),

    /**
     * QPACK blocked streams setting identifier (<b>0x7</b>).
     * <p>
     * Defined in <a href="https://datatracker.ietf.org/doc/html/rfc9204#section-5">
     * RFC 9204, Section 5 (SETTINGS_QPACK_BLOCKED_STREAMS)</a> and registered in
     * the <a href="https://www.iana.org/assignments/http3-parameters/http3-parameters.xhtml#settings">
     * HTTP/3 SETTINGS registry (IANA)</a>.
     * <br>
     * Indicates the maximum number of streams that can be blocked waiting for QPACK instructions.
     */
    HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS(0x7),

    /**
     * ENABLE_CONNECT_PROTOCOL setting identifier (<b>0x8</b>).
     * <p>
     * Defined and registered in <a href="https://datatracker.ietf.org/doc/html/rfc9220#section-5">
     * RFC 9220, Section 5 (IANA Considerations)</a> and registered in
     * the <a href="https://www.iana.org/assignments/http3-parameters/http3-parameters.xhtml#settings">
     * HTTP/3 SETTINGS registry (IANA)</a>.
     * <br>
     * Enables use of the CONNECT protocol in HTTP/3 when set to 1; disabled when 0.
     */
    HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL(0x8),

    HTTP3_SETTINGS_H3_DATAGRAM(0x33);

    private final long id;

    Http3SettingIdentifier(long id) {
        this.id = id;
    }

    public long id() {
        return id;
    }

    @Nullable
    public static Http3SettingIdentifier fromId(long id) {
        for (Http3SettingIdentifier s : values()) {
            if (s.id == id) {
                return s;
            }
        }
        return null; // unknown setting → ignored when receiving
    }
}
