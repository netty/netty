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
    public static DohProvider CLOUDFLARE = new DohProvider("1.1.1.1");
    public static DohProvider QUAD9 = new DohProvider("dns.quad9.net");
    public static DohProvider CIRA_FAMILY = new DohProvider("family.canadianshield.cira.ca");
    public static DohProvider CIRA = new DohProvider("private.canadianshield.cira.ca");
    public static DohProvider CIRA_PROTECT = new DohProvider("protected.canadianshield.cira.ca");
    public static DohProvider CLEANBROWSING = new DohProvider("doh.cleanbrowsing.org",
            "/doh/family-filter");
    public static DohProvider CLEANBROWSING_SECURE = new DohProvider("doh.cleanbrowsing.org",
            "/doh/security-filter");
    public static DohProvider CLEANBROWSING_ADULT = new DohProvider("doh.cleanbrowsing.org",
            "/doh/adult-filter");
    public static DohProvider LIBREDNS = new DohProvider("doh.libredns.gr");
    public static DohProvider LIBREDNS_ADS = new DohProvider("doh.libredns.gr", "/ads");
}

