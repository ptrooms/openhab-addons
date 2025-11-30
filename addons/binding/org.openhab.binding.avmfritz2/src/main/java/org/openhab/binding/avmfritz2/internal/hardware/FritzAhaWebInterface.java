/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

// failed ptro 06mar24 added resiliance and tracking threadPool.getIdleThreads() in a+sync/get+Post

package org.openhab.binding.avmfritz2.internal.hardware;

import static org.eclipse.jetty.http.HttpMethod.*;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
// import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
// import org.eclipse.jetty.util.thread.ThreadPool;			// failed ptro 06mar24 trackin for messages

// does not work: eclipse prohibits Access restriction: The type is not API (restriction on classpath entry 
// import	org.eclipse.jetty.util.thread.ThreadPool;	// ptro 04mar24 test to check sizes


import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.openhab.binding.avmfritz2.internal.config.AVMFritzConfiguration;
import org.openhab.binding.avmfritz2.internal.handler.AVMFritzBaseBridgeHandler;
import org.openhab.binding.avmfritz2.internal.hardware.callbacks.FritzAhaCallback;
import org.openhab.binding.avmfritz2.internal.hardware.callbacks.FritzAhaSetHeatingTemperatureCallback;
import org.openhab.binding.avmfritz2.internal.hardware.callbacks.FritzAhaSetSwitchCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class handles requests to a FRITZ!OS web interface for interfacing with AVM home automation devices. It manages
 * authentication and wraps commands.
 *
 * @author Robert Bausdorf, Christian Brauers - Initial contribution
 * @author Christoph Weitkamp - Added support for AVM FRITZ!DECT 300 and Comet
 *         DECT
 * @author Christoph Weitkamp - Added support for groups
 */
public class FritzAhaWebInterface {

   	/**
	 * Logger.
	 */
    private final Logger logger = LoggerFactory.getLogger(FritzAhaWebInterface.class);
    
    /**
     * Configuration of the bridge from {@link AVMFritzBaseBridgeHandler}
     */
    protected AVMFritzConfiguration config;
    /**
     * Current session ID
     */
    protected String sid;
    /**
     * Shared instance of HTTP client for asynchronous calls
     */
    // protected HttpClient httpClient;		// try to make acessible
	private HttpClient httpClient;
    /**
     * Bridge thing handler for updating thing status
     */
    protected AVMFritzBaseBridgeHandler handler;

    private static final String WEBSERVICE_PATH = "login_sid.lua";
    // Uses RegEx to handle bad FRITZ!Box XML
    /**
     * RegEx Pattern to grab the session ID from a login XML response
     */
    protected static final Pattern SID_PATTERN = Pattern.compile("<SID>([a-fA-F0-9]*)</SID>");
    /**
     * RegEx Pattern to grab the challenge from a login XML response
     */
    protected static final Pattern CHALLENGE_PATTERN = Pattern.compile("<Challenge>(\\w*)</Challenge>");
    /**
     * RegEx Pattern to grab the access privilege for home automation functions from a login XML response
     */
    protected static final Pattern ACCESS_PATTERN = Pattern
            .compile("<Name>HomeAuto</Name>\\s*?<Access>([0-9])</Access>");

    /**
     * This method authenticates with the FRITZ!OS Web Interface and updates the session ID accordingly
     *
     * @return New session ID
     */
    @Nullable
    public String authenticate() {

		// ptro 06mar24 : before any authentication, we start with an OFFLINE and side=null
		// 2024-04-08 16:29:50.015 [WARN ] [smarthome.model.script.AAZ-FritzBox2] - 
		//		Fritz-Box2 Status Status Is:OFFLINE Detail:COMMUNICATION_ERROR Description:FRITZ!Box being re-athenticated, sid:=null
        handler.setStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "FRITZ!Box being re-athenticated, sid:=null");
		sid = null;		// ensure this is null at start
		logger.debug("ptro authenticate: begin 001"  ); // ptro 04mar24
        if ( !(config.getPassword() != null)) {     // ptro 28okt24 rewritten == null
            handler.setStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Please configure password first");
            return null;
        }

		logger.debug("ptro authenticate: begin 002 WEBSERVICE_PATH={}", WEBSERVICE_PATH  ); // ptro 04mar24
        String loginXml = syncGet(getURL(WEBSERVICE_PATH, addSID("")));						// ptro 08apr24
		logger.debug("ptro authenticate: begin 002a, loginXml={}", loginXml ); // ptro 04mar24

        if ( !(loginXml != null)) {     // ptro 28okt24 rewritten == null
			logger.debug("ptro authenticate: begin 002c COMMUNICATION_ERROR"  ); // ptro 04mar24
            handler.setStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "FRITZ!Box does not respond");
			logger.debug("ptro authenticate: begin 002d"  ); // ptro 04mar24
            return null;
        }
		logger.debug("ptro authenticate: begin 003"  ); // ptro 04mar24
        Matcher sidmatch = SID_PATTERN.matcher(loginXml);
        if (!sidmatch.find()) {
            handler.setStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "FRITZ!Box does not respond with SID");
            return null;
        }
		logger.debug("ptro authenticate: begin 004"  ); // ptro 04mar24
        sid = sidmatch.group(1);
        Matcher accmatch = ACCESS_PATTERN.matcher(loginXml);
        if (accmatch.find()) {
            if ("2".equals(accmatch.group(1))) {
                handler.setStatusInfo(ThingStatus.ONLINE, ThingStatusDetail.NONE,
                        "Resuming FRITZ!Box connection with SID " + sid);
                return sid;
            }
        }
		logger.debug("ptro authenticate: begin 005"  ); // ptro 04mar24
        Matcher challengematch = CHALLENGE_PATTERN.matcher(loginXml);
        if (!challengematch.find()) {
            handler.setStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "FRITZ!Box does not respond with challenge for authentication");
            return null;
        }
		logger.debug("ptro authenticate: begin 007"  ); // ptro 04mar24
        String challenge = challengematch.group(1);
        String response = createResponse(challenge);
        loginXml = syncGet(getURL(WEBSERVICE_PATH,
                (config.getUser() != null && !"".equals(config.getUser()) ? ("username=" + config.getUser() + "&") : "")
                        + "response=" + response));
        if ( !(loginXml != null)) {     // ptro 28okt24 rewritten == null
            handler.setStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "FRITZ!Box does not respond");
            return null;
        }
		logger.debug("ptro authenticate: begin 008"  ); // ptro 04mar24
        sidmatch = SID_PATTERN.matcher(loginXml);
        if (!sidmatch.find()) {
            handler.setStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "FRITZ!Box does not respond with SID");
            return null;
        }
		logger.debug("ptro authenticate: begin 009"  ); // ptro 04mar24
        sid = sidmatch.group(1);
        accmatch = ACCESS_PATTERN.matcher(loginXml);
        if (accmatch.find()) {
            if ("2".equals(accmatch.group(1))) {
                handler.setStatusInfo(ThingStatus.ONLINE, ThingStatusDetail.NONE,
                        "Established FRITZ!Box connection with SID " + sid);
                return sid;
            }
        }
		logger.debug("ptro authenticate: begin 009"  ); // ptro 04mar24
        handler.setStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                "User " + config.getUser() + " has no access to FRITZ!Box home automation functions");
		logger.debug("ptro authenticate: begin 010"  ); // ptro 04mar24
        return null;
    }

    /**
     * Checks the authentication status of the web interface
     *
     * @return
     */
    public boolean isAuthenticated() {
        return !( !(sid != null) );     // ptro 28okt24 rewritten == null
    }

    public AVMFritzConfiguration getConfig() {
        return config;
    }

    public void setConfig(AVMFritzConfiguration config) {
        this.config = config;
    }

/*
// does not work: eclipse prohibits Access restriction: The type is not API (restriction on classpath entry 
// in addition we do not know the entry poiny
// threadpool queries
	public int maxThreads() {
	  return threadPool.getMaxThreads();
	}

	public int idleThreads() {
	  return threadPool.getIdleThreads();
	}

	public int threadIdleTimeout() {
	  return threadPool.getIdleTimeout();
	}
*/

    /**
     * Creates the proper response to a given challenge based on the password stored
     *
     * @param challenge Challenge string as returned by the FRITZ!OS login script
     * @return Response to the challenge
     */
    protected String createResponse(String challenge) {
        String handshake = challenge.concat("-").concat(config.getPassword());
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            logger.error("This version of Java does not support MD5 hashing");
            return "";
        }
        byte[] handshakeHash;
        try {
            handshakeHash = md5.digest(handshake.getBytes("UTF-16LE"));
        } catch (UnsupportedEncodingException e) {
            logger.error("This version of Java does not understand UTF-16LE encoding");
            return "";
        }
        String response = challenge.concat("-");
        for (byte handshakeByte : handshakeHash) {
            response = response.concat(String.format("%02x", handshakeByte));
        }
        return response;
    }

    /**
     * Constructor to set up interface
     *
     * @param config Bridge configuration
     */
    public FritzAhaWebInterface(AVMFritzConfiguration config, AVMFritzBaseBridgeHandler handler,
            HttpClient httpClient) {
        this.config = config;
        this.handler = handler;


	// activated start 08apr24

		//	Resolved by pom.xml tycho-compiler-plugin< --> <configuration> <compilerArgs> <arg>-err:-forbidden</arg>
		// does not work: eclipse prohibits Access restriction: The type is not API (restriction on classpath entry 
		// private ThreadPool threadPool;	// ptro 04mar24 playing with theadpool values
		// Eclipse has a mechanism called access restrictions to prevent you from accidentally 
		//		using classes which Eclipse thinks are not part of the public API.

        if (httpClient != null) {
			logger.debug("ptro initialise: Stopping  httpClient.stop()" ); // ptro 04mar24
            try {
                httpClient.stop();
			} catch (Exception e) {
				logger.debug("failed httpClient.stop: '{}': ", e.getLocalizedMessage(), e);
		     }
		    logger.debug("ptro initialise: Starting  httpClient.start()" ); // ptro 04mar24
		}
	    try {
			httpClient.start();
	    } catch (Exception e) {
			logger.debug("failed httpClient.start: '{}': ", e.getLocalizedMessage(), e);
        }
	    logger.debug("ptro initialise: Starting  httpClient.start()" ); // ptro 04mar24

	// activated end 08apr24

        this.httpClient = httpClient;
        sid = null;
        logger.debug("ptro initialise: Starting with config={} handler={} httpClient={}", config, handler, httpClient ); // ptro 04mar24
        authenticate();
        logger.debug("Starting with SID {}", sid);  // ptro 06mar24
    }

    /**
     * Constructs an URL from the stored information and a specified path
     *
     * @param path Path to include in URL
     * @return URL
     */
    public String getURL(String path) {
        return config.getProtocol() + "://" + config.getIpAddress()
                + (config.getPort() != null ? ":" + config.getPort() : "") + "/" + path;
    }

    /**
     * Constructs an URL from the stored information, a specified path and a specified argument string
     *
     * @param path Path to include in URL
     * @param args String of arguments, in standard HTTP format (arg1=value1&arg2=value2&...)
     * @return URL
     */
    public String getURL(String path, String args) {
        return getURL(path + "?" + args);
    }

    public String addSID(@Nullable String args) {

        if ( !(sid != null)) {      // ptro 28okt24 rewritten == null
            return args;
        } else {
            return ("".equals(args) ? ("sid=") : (args + "&sid=")) + sid;
        }
    }

    /**
     * Sends a HTTP GET request using the synchronous client
     *
     * @param path Path of the requested resource
     * @return response
     */
    @Nullable
    public String syncGet(String url) {
        try {
			logger.debug("ptro syncGet: httpClient.newRequest={},  timeout={}", url, config.getSyncTimeout() ); // ptro 04mar24
			// logger.debug("ptro syncGet1: threadPool.getIdleThreads()={}", threadPool.getIdleThreads() ); // failed ptro 06mar24

			// url=	<?xml version="1.0" encoding="utf-8"?>
			//		<SessionInfo><SID>0000000000000000</SID>
			//			<Challenge>20309b28</Challenge>
			//			<BlockTime>0</BlockTime>
			//			<Rights></Rights>
			//		</SessionInfo>
			// timeout=2000
			// read: https://archive.eclipse.org/jetty/9.4.3.v20170317/apidocs/org/eclipse/jetty/client/HttpClient.html#newRequest-java.net.URI-
			//
            ContentResponse contentResponse = httpClient.newRequest(url)
					.timeout(config.getSyncTimeout(), TimeUnit.MILLISECONDS)
					.method(GET)
					.send();
			// problem  ----- request to url is hold in queue, even when there's not contentResponse

			logger.debug("ptro syncGet2: contentResponse={}", contentResponse ); // ptro 04mar24
            String content = contentResponse.getContentAsString();
            logger.debug("Response complete: {}", content);
            return content;
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            // logger.debug("Failed to GET url '{}': ", url, e.getLocalizedMessage(), e);
            logger.warn("ptro syncGet Failed to GET url '{}; do stop/start to cancel': ", url, e.getLocalizedMessage(), e);	// ptro 05mar24
			httpClient.dump();

			// stop/start here ?????
				/* does not function
					try {
						httpClient.stop();
					} catch (Exception e1) {
							logger.debug("failed httpClient.stop: '{}': ", e1.getLocalizedMessage(), e1);
					}
					try {
						httpClient.start();
					} catch (Exception e2) {
						logger.debug("failed httpClient.start: '{}': ", e2.getLocalizedMessage(), e2);
					}
				*/


            return null;
        }
    }

    /**
     * Sends a HTTP GET request using the asynchronous client
     *
     * @param path     Path of the requested resource
     * @param args     Arguments for the request
     * @param callback Callback to handle the response with
     */
    public FritzAhaContentExchange asyncGet(String path, String args, FritzAhaCallback callback) {
		// logger.debug("ptro asyncGet: threadPool.getIdleThreads()={}", threadPool.getIdleThreads() ); // failed ptro 06mar24
        if (!isAuthenticated()) {
			// 2024-04-08 16:31:36.737 [WARN ] [ternal.hardware.FritzAhaWebInterface] - FritzAhaContentExchange asyncGet will re-authenticate 
			logger.warn("FritzAhaContentExchange asyncGet will re-authenticate");	// ptro 06mar24 try to clarify things
            authenticate();
        }
        FritzAhaContentExchange getExchange = new FritzAhaContentExchange(callback);
        httpClient.newRequest(getURL(path, addSID(args))).method(GET)
				.onResponseSuccess(getExchange)
                .onResponseFailure(getExchange) // .onComplete(getExchange)
                .send(getExchange);
		logger.debug("FritzAhaContentExchange asyncGet={}", getExchange);		// ptro 06mar24 try to clarify things
        return getExchange;
    }

    public FritzAhaContentExchange asyncGet(FritzAhaCallback callback) {
        return asyncGet(callback.getPath(), callback.getArgs(), callback);
    }

    /**
     * Sends a HTTP POST request using the asynchronous client
     *
     * @param path     Path of the requested resource
     * @param args     Arguments for the request
     * @param callback Callback to handle the response with
     */
    public FritzAhaContentExchange asyncPost(String path, String args, FritzAhaCallback callback) {
		// logger.debug("ptro asyncPost: threadPool.getIdleThreads()={}", threadPool.getIdleThreads() ); // failed ptro 06mar24
        if (!isAuthenticated()) {
			logger.warn("FritzAhaContentExchange asyncPost will re-authenticate");	// ptro 06mar24 try to clarify things
            authenticate();
        }
        FritzAhaContentExchange postExchange = new FritzAhaContentExchange(callback);
        httpClient.newRequest(getURL(path)).timeout(config.getAsyncTimeout(), TimeUnit.SECONDS).method(POST)
                .onResponseSuccess(postExchange).onResponseFailure(postExchange) // .onComplete(postExchange)
                .content(new StringContentProvider(addSID(args), "UTF-8")).send(postExchange);
		logger.debug("FritzAhaContentExchange asyncPost={}", postExchange);		// ptro 06mar24 try to understand things
        return postExchange;
    }

    public FritzAhaContentExchange setSwitch(String ain, boolean switchOn) {
        FritzAhaSetSwitchCallback callback = new FritzAhaSetSwitchCallback(this, ain, switchOn);
        return asyncGet(callback);
    }

    public FritzAhaContentExchange setSetTemp(String ain, BigDecimal temperature) {
        FritzAhaSetHeatingTemperatureCallback callback = new FritzAhaSetHeatingTemperatureCallback(this, ain,
                temperature);
        return asyncGet(callback);
    }
}
