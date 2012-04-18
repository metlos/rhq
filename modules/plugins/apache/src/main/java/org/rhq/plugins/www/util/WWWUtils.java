/*
 * RHQ Management Platform
 * Copyright (C) 2005-2012 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.plugins.www.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.rhq.core.util.stream.StreamUtil;

/**
 * Helper class that contains methods that send HTTP requests and evaluate results.
 *
 * @author Ian Springer
 */
public class WWWUtils {

    private static final Log LOG = LogFactory.getLog(WWWUtils.class);

    // prevent instantiation
    private WWWUtils() {
    }

    /**
     * Sends a HEAD request for the passed URL and returns true if the URL was reachable.
     *
     * @param httpURL a http or https URL to check
     * @param timeout timeout, in milliseconds
     * @return true if connecting to the URL succeeds, or false otherwise
     */
    public static boolean isAvailable(URL httpURL, int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("Timeout cannot be negative.");
        }

        if (timeout == 0) {
            LOG.debug("Pinging [" + httpURL + "] with no timeout...");
        } else {
            LOG.debug("Pinging [" + httpURL + "] with timeout of " + timeout + " milliseconds...");
        }

        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) httpURL.openConnection();
        } catch (IOException e) {
            LOG.error("Failed to open URLConnection for [" + httpURL + "].", e);
            return false;
        }

        try {
            connection.setRequestMethod("HEAD");
        } catch (ProtocolException e) {
            try {
                connection.setRequestMethod("GET");
            } catch (ProtocolException e1) {
                LOG.error("Failed to set request method to HEAD or GET on URLConnection for [" + httpURL + "].", e1);
                return false;
            }
        }

        connection.setConnectTimeout(timeout);
        // Hold off on setting the read timeout.

        if (connection instanceof HttpsURLConnection) {
            disableCertificateVerification((HttpsURLConnection) connection);
        }

        // First just connect to the HTTP server.
        long connectStartTime = System.currentTimeMillis();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Connecting to [" + httpURL + "]...");
        }
        try {
            connection.connect();
        } catch (IOException e) {
            if (e instanceof SocketTimeoutException) {
                long connectDuration = System.currentTimeMillis() - connectStartTime;
                LOG.debug("Attempt to connect to [" + httpURL + "] timed out after " + connectDuration
                        + " milliseconds.");
            } else {
                LOG.error("An error occurred while attempting to connect to [" + httpURL + "].", e);
            }
            return false;
        }
        int connectDuration = (int) (System.currentTimeMillis() - connectStartTime);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Connected to [" + httpURL + "] in " + connectDuration + " milliseconds.");
        }

        if ((timeout > 0) && (connectDuration >= timeout)) {
            LOG.debug("Attempt to ping [" + httpURL + "] timed out after " + connectDuration + " milliseconds.");
        }

        try {
            int readTimeout = (timeout > 0) ? (timeout - connectDuration) : 0;
            connection.setReadTimeout(readTimeout);

            // Now actually send the request and read the response.
            long readStartTime = System.currentTimeMillis();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Sending " + connection.getRequestMethod() + " request to [" + httpURL + "]...");
            }
            try {
                int responseCode = connection.getResponseCode();
                if (responseCode == -1) {
                    LOG.error("Ping request to [" + httpURL + "] returned an invalid response: "
                            + getResponseBody(connection));
                } else if (responseCode >= 500) {
                    LOG.error("Ping request to [" + httpURL + "] returned a response with server error " + responseCode
                            + " (" + connection.getResponseMessage() + "): " + getResponseBody(connection));
                } else if (responseCode >= 400) {
                    LOG.warn("Ping request to [" + httpURL + "] returned a response with client error " + responseCode
                            + " (" + connection.getResponseMessage() + ").");
                }
            } catch (IOException e) {
                if (e instanceof SocketTimeoutException) {
                    long readDuration = System.currentTimeMillis() - readStartTime;
                    LOG.debug("Attempt to read response from " + connection.getRequestMethod()
                            + " request to [" + httpURL + "] timed out after " + readDuration
                            + " milliseconds.");
                } else {
                    LOG.error("An error occurred while attempting to read response from " + connection.getRequestMethod()
                            + " to [" + httpURL + "].", e);
                }
                return false;
            }
            long readDuration = System.currentTimeMillis() - readStartTime;
            if (LOG.isDebugEnabled()) {
                LOG.debug("Read response from " + connection.getRequestMethod() + " request to [" + httpURL
                        + "] in " + readDuration + " milliseconds.");
            }
        } finally {
            // We don't care about keeping the connection around. We're only going to be pinging the server once every
            // few minutes.
            connection.disconnect();
        }

        return true;
    }

    /**
     * Get the content of the 'Server' header.
     *
     * @param httpURL a http or https URL to get the header from
     * @return the contents of the header or null if anything went wrong or the field was not present.
     */
    public static String getServerHeader(URL httpURL) {
        String ret;

        try {
            HttpURLConnection connection = (HttpURLConnection) httpURL.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(1000);

            connection.connect();
            // get the response code to actually trigger sending the Request.
            connection.getResponseCode();
            ret = connection.getHeaderField("Server");
        } catch (IOException e) {
            ret = null;
        }
        return ret;
    }

    private static TrustManager NO_OP_TRUST_MANAGER = new X509TrustManager() {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType) {
            return;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType) {
            return;
        }
    };

    private static HostnameVerifier NO_OP_HOSTNAME_VERIFIER = new HostnameVerifier() {
        @Override
        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    };

    // This method has been added in support of https://bugzilla.redhat.com/show_bug.cgi?id=690430.
    private static void disableCertificateVerification(HttpsURLConnection connection) {
        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[]{NO_OP_TRUST_MANAGER}, new java.security.SecureRandom());
            connection.setSSLSocketFactory(sslContext.getSocketFactory());
            connection.setHostnameVerifier(NO_OP_HOSTNAME_VERIFIER);
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to disable certificate and hostname validation.", e);
            } else {
                LOG.warn("Failed to disable certificate and hostname validation (enable DEBUG logging to see stack trace): "
                        + e);
            }
        }
    }

    private static String getResponseBody(HttpURLConnection connection) {
        String responseBody;
        try {
            InputStream inputStream = (connection.getInputStream() != null) ? connection.getInputStream() :
                    connection.getErrorStream();
            responseBody = (inputStream != null) ? StreamUtil.slurp(new InputStreamReader(inputStream)) : "";
        } catch (IOException e) {
            responseBody = "";
        }
        return responseBody;
    }

}
