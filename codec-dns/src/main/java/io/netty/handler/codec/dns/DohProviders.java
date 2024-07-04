package io.netty.handler.codec.dns;

public class DohProviders {

    public static class DohProvider {
        private final boolean usePost;
        private final String host;
        private final int port;
        private final String uri;

        public DohProvider(String host) {
            this(host, "/dns-query", 443);
        }

        public DohProvider(String host, boolean usePost) {
            this(host, "/dns-query", 443, usePost);
        }

        public DohProvider(String host, String uri) {
            this(host, uri, 443);
        }

        public DohProvider(String host, String uri, int port) {
            this(host, uri, port, true);
        }

        public DohProvider(String host, String uri, int port, boolean usePost) {
            this.host = host;
            this.uri = uri;
            this.port = port;
            this.usePost = usePost;
        }

        public boolean usePost() {
            return usePost;
        }

        public String host() {
            return host;
        }

        public int port() {
            return port;
        }

        public String uri() {
            return uri;
        }
    }

    public static DohProvider GOOGLE = new DohProvider("dns.google");
    public static DohProvider GOOGLE_GET = new DohProvider("dns.google", false);
    public static DohProvider CLOUDFLARE = new DohProvider("1.1.1.1");
    public static DohProvider CLOUDFLARE_GET = new DohProvider("1.1.1.1", false);
    public static DohProvider QUAD9 = new DohProvider("dns.quad9.net");
    public static DohProvider QUAD9_GET = new DohProvider("dns.quad9.net", false);
    public static DohProvider CIRA_FAMILY = new DohProvider("family.canadianshield.cira.ca");
    public static DohProvider CIRA_FAMILY_GET = new DohProvider("family.canadianshield.cira.ca", false);
    public static DohProvider CIRA = new DohProvider("private.canadianshield.cira.ca");
    public static DohProvider CIRA_GET = new DohProvider("private.canadianshield.cira.ca", false);
    public static DohProvider CIRA_PROTECT = new DohProvider("protected.canadianshield.cira.ca");
    public static DohProvider CIRA_PROTECT_GET = new DohProvider("protected.canadianshield.cira.ca", false);
    public static DohProvider CLEANBROWSING = new DohProvider("doh.cleanbrowsing.org",
            "/doh/family-filter");
    public static DohProvider CLEANBROWSING_GET = new DohProvider("doh.cleanbrowsing.org",
            "/doh/family-filter", 443, false);
    public static DohProvider CLEANBROWSING_SECURE = new DohProvider("doh.cleanbrowsing.org",
            "/doh/security-filter");
    public static DohProvider CLEANBROWSING_SECURE_GET = new DohProvider("doh.cleanbrowsing.org",
            "/doh/security-filter", 443, false);
    public static DohProvider CLEANBROWSING_ADULT = new DohProvider("doh.cleanbrowsing.org",
            "/doh/adult-filter");
    public static DohProvider CLEANBROWSING_ADULT_GET = new DohProvider("doh.cleanbrowsing.org",
            "/doh/adult-filter", 443, false);
    public static DohProvider LIBREDNS = new DohProvider("doh.libredns.gr");
    public static DohProvider LIBREDNS_GET = new DohProvider("doh.libredns.gr", false);
    public static DohProvider LIBREDNS_ADS = new DohProvider("doh.libredns.gr", "/ads");
    public static DohProvider LIBREDNS_ADS_GET = new DohProvider("doh.libredns.gr", "/ads", 443, false);
}

