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
package io.netty.handler.codec.rest;

import java.security.Key;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.xml.bind.DatatypeConverter;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Promise;

/**
 * Global arguments from REST main handler (and optionally the client)
 *
 */
public class RestArgument {
    public static enum REST_ROOT_FIELD {
        /**
         * method identified in Header (when standard http method is not to be taken in consideration)
         */
        ARG_METHOD("X-method"),
        /**
         * Key used
         */
        ARG_X_AUTH_KEY("X-Auth-Key"),
        /**
         * User
         */
        ARG_X_AUTH_USER("X-Auth-User"),
        /**
         * Internal Key used (not to be passed through wire)
         */
        ARG_X_AUTH_INTERNALKEY("X-Auth-InternalKey"),
        /**
         * Timestamp in ISO 8601 format
         */
        ARG_X_AUTH_TIMESTAMP("X-Auth-Timestamp");

        public String field;
        REST_ROOT_FIELD(String field) {
            this.field = field;
        }
    }

    HttpRequest request;
    boolean hasBody;
    HttpMethod method;
    String uriPath;
    String basePath;
    List<String> subPathes = new ArrayList<String>();
    Map<String, List<String>> args = new HashMap<String, List<String>>();
    HttpResponseStatus status = HttpResponseStatus.OK;
    Object body;
    Object responseBody;
    /**
     * Default value
     */
    String contentType = "text/plain";

    /**
     * Used by client side
     */
    Promise<Object> promise;

    public RestArgument(HttpRequest request) {
        if (request != null) {
            setRequest(request);
        }
    }

    /**
     * @param request
     *            Request from which REST arguments will be extracted
     */
    public void setRequest(HttpRequest request) {
        this.request = request;
        hasBody = request instanceof FullHttpRequest
                && ((FullHttpRequest) request).content() != Unpooled.EMPTY_BUFFER;
        method = request.method();
        QueryStringDecoder decoderQuery = new QueryStringDecoder(request.uri());
        uriPath = decoderQuery.path();
        // compute path main uri
        String basepath = uriPath;
        int pos = basepath.indexOf('/');
        if (pos >= 0) {
            if (pos == 0) {
                int pos2 = basepath.indexOf('/', 1);
                if (pos2 < 0) {
                    basepath = basepath.substring(1);
                } else {
                    basepath = basepath.substring(1, pos2);
                }
            } else {
                basepath = basepath.substring(0, pos);
            }
        }
        if (basepath.isEmpty()) {
            // default root
            basepath = "/";
        }
        this.basePath = basepath;
        // compute sub path args
        if (pos == 0) {
            pos = uriPath.indexOf('/', 1);
        }
        if (pos >= 0) {
            int pos2 = uriPath.indexOf('/', pos + 1);
            if (pos2 > 0) {
                while (pos2 > 0) {
                    subPathes.add(uriPath.substring(pos + 1, pos2));
                    pos = pos2;
                    pos2 = uriPath.indexOf('/', pos + 1);
                }
            }
            pos2 = uriPath.indexOf('?', pos + 1);
            if (pos2 > 0 && pos2 > pos + 1) {
                subPathes.add(uriPath.substring(pos + 1, pos2));
            } else {
                String last = uriPath.substring(pos + 1);
                if (!last.isEmpty()) {
                    subPathes.add(last);
                }
            }
        }
        args.putAll(decoderQuery.parameters());
        // set values from Header for "Rest" values
        for (Entry<String, String> entry : request.headers().entries()) {
            String key = entry.getKey();
            if (! key.equals(HttpHeaders.Names.COOKIE)) {
                if (key.equalsIgnoreCase(REST_ROOT_FIELD.ARG_X_AUTH_KEY.field)) {
                    addArg(REST_ROOT_FIELD.ARG_X_AUTH_KEY.field, entry.getValue());
                    continue;
                }
                if (key.equalsIgnoreCase(REST_ROOT_FIELD.ARG_X_AUTH_USER.field)) {
                    addArg(REST_ROOT_FIELD.ARG_X_AUTH_USER.field, entry.getValue());
                    continue;
                }
                if (key.equalsIgnoreCase(REST_ROOT_FIELD.ARG_X_AUTH_TIMESTAMP.field)) {
                    addArg(REST_ROOT_FIELD.ARG_X_AUTH_TIMESTAMP.field, entry.getValue());
                    continue;
                }
                if (key.equalsIgnoreCase(REST_ROOT_FIELD.ARG_METHOD.field)) {
                    // substitute default Method
                    method = HttpMethod.valueOf(entry.getValue());
                    continue;
                }
            }
        }
    }

    public void clean() {
        request = null;
        subPathes.clear();
        args.clear();
        method = null;
        uriPath = null;
        basePath = null;
        body = null;
        responseBody = null;
    }

    /**
     * @return the request
     */
    public HttpRequest request() {
        return request;
    }

    /**
     * @return True if the request has a body
     */
    public boolean hasBody() {
        return hasBody;
    }

    /**
     * @return the method
     */
    public HttpMethod method() {
        if (method == null) {
            return HttpMethod.TRACE;
        }
        return method;
    }

    /**
     * To enable the change of the method through other argument, in particular when client is not able
     * to produce the correct native method
     * @param method
     */
    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    /**
     * @return the uri without args
     */
    public String uriPath() {
        return uriPath;
    }

    /**
     * @return the basePath of the uri
     */
    public String basePath() {
        return basePath;
    }

    /**
     * @return the subPathes (including basePath at first position)
     */
    public List<String> subPathes() {
        return subPathes;
    }

    /**
     * @return the set of associated keys
     */
    public Set<String> argKeys() {
        return args.keySet();
    }

    /**
     * @param fieldName
     * @return the associated list of Values if any
     */
    public List<String> argList(String fieldName) {
        return args.get(fieldName);
    }

    /**
     * @param fieldName
     * @return the associated first Value if any
     */
    public String arg(String fieldName) {
        List<String> list = args.get(fieldName);
        if (list != null && ! list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    /**
     * Add the pathRank item from subPathes list to args map using fieldname as access key
     *
     * @param fieldname as the future fieldname associated with the entry from subPathes
     * @param pathRank
     *            0 <= pathRank < subPathes.size()
     */
    public void addArgFromUri(String fieldname, int pathRank) {
        String value = subPathes.get(pathRank);
        addArg(fieldname, value);
    }

    /**
     * Add the list of values with the associated fieldname
     */
    public void addArgFromList(String fieldname, List<String> list) {
        List<String> listsrc = args.get(fieldname);
        if (listsrc == null) {
            listsrc = new ArrayList<String>();
        }
        listsrc.addAll(list);
        args.put(fieldname, listsrc);
    }

    /**
     * Add one value (possibly added to existing values) for the fieldname
     */
    public void addArg(String fieldname, String value) {
        List<String> list = args.get(fieldname);
        if (list == null) {
            list = new ArrayList<String>();
        }
        list.add(value);
        args.put(fieldname, list);
    }

    /**
     * @return the current status
     */
    public HttpResponseStatus status() {
        return status;
    }

    /**
     * @param status the status to set
     */
    public void setStatus(HttpResponseStatus status) {
        this.status = status;
    }

    /**
     * @return the body if set (else null)
     */
    public Object body() {
        return body;
    }

    /**
     * @param body the body to set (if not null, hasBody will be True)
     */
    public void setBody(Object body) {
        this.body = body;
        hasBody = body != null;
    }

    /**
     * @return the responseBody if set
     */
    public Object responseBody() {
        return responseBody;
    }

    /**
     * @param responseBody the responseBody to set
     */
    public void setResponseBody(Object responseBody) {
        this.responseBody = responseBody;
    }

    /**
     * @return the contentType
     */
    public String contentType() {
        return contentType;
    }

    /**
     * @param contentType the contentType to set
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * @return the corresponding promise (for client side)
     */
    public Promise<Object> getPromise() {
        return promise;
    }

    /**
     * @param promise the promise to set (for client side)
     */
    public void setPromise(Promise<Object> promise) {
        this.promise = promise;
    }

    /**
     * @return the ISO 8601 representation of date using Calendar and DatatypeConverter
     */
    public static String dateToIso8601(Date date) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTime(date);
        return DatatypeConverter.printDateTime(calendar);
    }

    /**
     * @return the date corresponding to the ISO 8601 date using DatatypeConverter
     */
    public static Date iso8601ToDate(String isodate) {
        return DatatypeConverter.parseDateTime(isodate).getTime();
    }
    /**
     * The encoder is completed with extra necessary URI part containing ARG_X_AUTH_TIMESTAMP &
     * ARG_X_AUTH_KEY (for client side)
     *
     * @param encoder QueryStringEncoder initialized with the correct partial future URI
     * @param user
     *            might be null
     * @return an array of 2 values in that order: ARG_X_AUTH_TIMESTAMP and ARG_X_AUTH_KEY
     * @throws RestInvalidAuthenticationException
     *             if the computation of the authentication failed
     */
    public static String[] getBaseAuthent(RestConfiguration configuration, QueryStringEncoder encoder,
            String user) throws RestInvalidAuthenticationException {
        return getBaseAuthent(configuration.hmacSha(), configuration.hmacAlgorithm(),
                encoder, user, configuration.restPrivateKey());
        
    }
    /**
     * The encoder is completed with extra necessary URI part containing ARG_X_AUTH_TIMESTAMP &
     * ARG_X_AUTH_KEY (for client side)
     *
     * @param hmac
     *            SHA-1 or SHA-256 or equivalent key to create the signature
     * @param algo algorithm for the key ("HmacSHA256" for instance)
     * @param encoder QueryStringEncoder initialized with the correct partial future URI
     * @param user
     *            might be null
     * @param extraKey
     *            might be null
     * @return an array of 2 values in that order: ARG_X_AUTH_TIMESTAMP and ARG_X_AUTH_KEY
     * @throws RestInvalidAuthenticationException
     *             if the computation of the authentication failed
     */
    public static String[] getBaseAuthent(Key hmac, String algo, QueryStringEncoder encoder,
            String user, String extraKey) throws RestInvalidAuthenticationException {
        QueryStringDecoder decoderQuery = new QueryStringDecoder(encoder.toString());
        Map<String, List<String>> map = decoderQuery.parameters();
        TreeMap<String, String> treeMap = new TreeMap<String, String>();
        for (Entry<String, List<String>> entry : map.entrySet()) {
            String keylower = entry.getKey().toLowerCase();
            List<String> values = entry.getValue();
            if (values != null && !values.isEmpty()) {
                String last = values.get(values.size() - 1);
                treeMap.put(keylower, last);
            }
        }
        Date date = new Date();
        String isodate = dateToIso8601(date);
        treeMap.put(REST_ROOT_FIELD.ARG_X_AUTH_TIMESTAMP.field.toLowerCase(), isodate);
        if (user != null) {
            treeMap.put(REST_ROOT_FIELD.ARG_X_AUTH_USER.field.toLowerCase(), user);
        }
        try {
            String key = computeKey(hmac, algo, extraKey, treeMap, decoderQuery.path());
            String[] result = { isodate, key };
            return result;
        } catch (Exception e) {
            throw new RestInvalidAuthenticationException(e);
        }
    }

    /**
     * Check Time only (no signature)
     *
     * @throws RestInvalidAuthenticationException
     */
    public void checkTime(RestConfiguration configuration) throws RestInvalidAuthenticationException {
        checkTime(configuration.restTimeLimit());
    }
    /**
     * Check Time only (no signature)
     *
     * @throws RestInvalidAuthenticationException
     */
    public void checkTime(long maxtime) throws RestInvalidAuthenticationException {
        if (maxtime <= 0) {
            return;
        }
        String sdate = arg(REST_ROOT_FIELD.ARG_X_AUTH_TIMESTAMP.field);
        if (sdate == null) {
            throw new RestInvalidAuthenticationException("timestamp absent while required");
        }
        Date received = iso8601ToDate(sdate);
        Date dateTime = new Date();
        if (Math.abs(dateTime.getTime() - received.getTime()) >= maxtime) {
            throw new RestInvalidAuthenticationException(
                    "timestamp is not compatible with the maximum delay allowed");
        }
    }

    /**
     * This implementation of authentication is as follow: if X_AUTH is included in the URI or
     * Header<br>
     * 0) Check that timestamp is correct (|curtime - timestamp| < maxinterval) from
     * ARG_X_AUTH_TIMESTAMP, if maxInterval is 0, not mandatory<br>
     * 1) Get all URI args (except ARG_X_AUTH_KEY itself, but including timestamp), lowered case, in
     * alphabetic order<br>
     * 2) Add an extra Key if not null (from ARG_X_AUTH_INTERNALKEY)<br>
     * 3) Compute an hash (SHA-1 or SHA-256)<br>
     * 4) Compare this hash with ARG_X_AUTH_KEY<br>
     *
     * @throws RestInvalidAuthenticationException
     *             if the authentication failed
     */
    public void checkBaseAuthent(RestConfiguration configuration)
            throws RestInvalidAuthenticationException {
        checkBaseAuthent(configuration.hmacSha(), configuration.hmacAlgorithm(),
                configuration.restPrivateKey(), configuration.restTimeLimit());
    }

    /**
     * This implementation of authentication is as follow: if X_AUTH is included in the URI or
     * Header<br>
     * 0) Check that timestamp is correct (|curtime - timestamp| < maxinterval) from
     * ARG_X_AUTH_TIMESTAMP, if maxInterval is 0, not mandatory<br>
     * 1) Get all URI args (except ARG_X_AUTH_KEY itself, but including timestamp), lowered case, in
     * alphabetic order<br>
     * 2) Add an extra Key if not null (from ARG_X_AUTH_INTERNALKEY)<br>
     * 3) Compute an hash (SHA-1 or SHA-256)<br>
     * 4) Compare this hash with ARG_X_AUTH_KEY<br>
     *
     * @param hmac
     *            SHA-1 or SHA-256 or equivalent key to create the signature
     * @param algo algorithm for the key ("HmacSHA256" for instance)
     * @param extraKey
     *            will be added as ARG_X_AUTH_INTERNALKEY, might be null
     * @param maxInterval
     *            ARG_X_AUTH_TIMESTAMP will be tested if value > 0
     * @throws RestInvalidAuthenticationException
     *             if the authentication failed
     */
    public void checkBaseAuthent(Key hmac, String algo, String extraKey, long maxInterval)
            throws RestInvalidAuthenticationException {
        TreeMap<String, String> treeMap = new TreeMap<String, String>();
        String argPath = uriPath;
        Set<Entry<String, List<String>>> set = args.entrySet();
        Date dateTime = new Date();
        Date received = null;
        for (Entry<String, List<String>> entry : set) {
            String key = entry.getKey();
            if (key.equalsIgnoreCase(REST_ROOT_FIELD.ARG_X_AUTH_KEY.field)) {
                continue;
            }
            List<String> values = entry.getValue();
            String value = null;
            if (! values.isEmpty()) {
                value = values.get(0);
                if (key.equalsIgnoreCase(REST_ROOT_FIELD.ARG_X_AUTH_TIMESTAMP.field)) {
                    received = iso8601ToDate(value);
                }
                String keylower = key.toLowerCase();
                treeMap.put(keylower, value);
            }
        }
        String user = arg(REST_ROOT_FIELD.ARG_X_AUTH_USER.field);
        if (user != null && !user.isEmpty()) {
            treeMap.put(REST_ROOT_FIELD.ARG_X_AUTH_USER.field.toLowerCase(), user);
        }
        if (maxInterval > 0 && received != null) {
            if (Math.abs(dateTime.getTime() - received.getTime()) >= maxInterval) {
                throw new RestInvalidAuthenticationException(
                        "timestamp is not compatible with the maximum delay allowed");
            }
        } else if (maxInterval > 0) {
            throw new RestInvalidAuthenticationException("timestamp absent while required");
        }
        String key = computeKey(hmac, algo, extraKey, treeMap, argPath);
        if (!key.equalsIgnoreCase(arg(REST_ROOT_FIELD.ARG_X_AUTH_KEY.field))) {
            throw new RestInvalidAuthenticationException("Invalid Authentication Key");
        }
    }

    /**
     * Computes the key in String format from various arguments
     * @param hmac
     *            SHA-1 or SHA-256 or equivalent key to create the signature
     * @param algo algorithm for the key ("HmacSHA256" for instance)
     * @param extraKey
     *            might be null
     * @param treeMap
     * @param argPath
     * @throws RestInvalidAuthenticationException
     */
    protected static String computeKey(Key hmac, String algo, String extraKey,
            TreeMap<String, String> treeMap, String argPath)
            throws RestInvalidAuthenticationException {
        Set<String> keys = treeMap.keySet();
        StringBuilder builder = new StringBuilder(argPath);
        if (!keys.isEmpty() || extraKey != null) {
            builder.append('?');
        }
        boolean first = true;
        for (String keylower : keys) {
            if (first) {
                first = false;
            } else {
                builder.append('&');
            }
            builder.append(keylower);
            builder.append('=');
            builder.append(treeMap.get(keylower));
        }
        if (extraKey != null) {
            if (!keys.isEmpty()) {
                builder.append("&");
            }
            builder.append(REST_ROOT_FIELD.ARG_X_AUTH_INTERNALKEY.field);
            builder.append("=");
            builder.append(extraKey);
        }
        try {
            Mac mac = Mac.getInstance(algo);
            mac.init(hmac);
            byte[] bytes = mac.doFinal(builder.toString().getBytes(CharsetUtil.UTF_8));
            return ByteBufUtil.hexDump(bytes);
        } catch (Exception e) {
            throw new RestInvalidAuthenticationException(e);
        }
    }

}
