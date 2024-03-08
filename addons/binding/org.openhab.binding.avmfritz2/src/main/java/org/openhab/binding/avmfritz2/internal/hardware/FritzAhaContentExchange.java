/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

// 06mar23 whenever nocontent and/or response status != 200, we return null to re-enforce errors in WebInterface
// 06mar23 tbd --> 2024-03-06 14:12:11.610 [DEBUG] [nal.hardware.FritzAhaContentExchange] - 
//			HTTP response 403 = response.getStatus()
//			onFailure: No route to host
//			onFailure: org.eclipse.jetty.client.HttpClient@1d4164b is stopped 
// 			after restart session is hanging

// 31jan23 18u00 ptro: this regularly goes wrong upto the point that 1024 requests has been queued and the handler then fails.
// .... Jetty leaking connection when body is not read artipie/http-client#23

package org.openhab.binding.avmfritz2.internal.hardware;

import org.eclipse.jetty.client.api.Response;	// --> https://eclipse.dev/jetty/javadoc/jetty-11//org/eclipse/jetty/client/api/Response.html

import org.eclipse.jetty.client.api.Response.CompleteListener;
import org.eclipse.jetty.client.api.Response.ContentListener;		// https://www.eclipse.org/jetty/javadoc/jetty-10/org/eclipse/jetty/client/api/Response.Listener.html
import org.eclipse.jetty.client.api.Response.FailureListener;		// https://www.eclipse.org/jetty/javadoc/jetty-10/org/eclipse/jetty/client/api/Response.FailureListener.html  == when the request has failed to be sent
import org.eclipse.jetty.client.api.Response.SuccessListener;		// request succeeded event.
import org.eclipse.jetty.client.api.Result;

import org.eclipse.jetty.client.util.BufferingResponseListener;		// https://www.eclipse.org/jetty/javadoc/jetty-11/org/eclipse/jetty/client/util/BufferingResponseListener.html

import org.openhab.binding.avmfritz2.internal.hardware.callbacks.FritzAhaCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of Jetty ContextExchange to handle callbacks
 *
 * @author Robert Bausdorf - Initial contribution
 */
public class FritzAhaContentExchange extends BufferingResponseListener
        implements SuccessListener, FailureListener, ContentListener, CompleteListener {
    /**
     * logger
     */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Callback to execute on complete response
     */
    private FritzAhaCallback callback;

    /**
     * Constructor
     *
     * @param callback Callback which execute method has to be called.
     */
    public FritzAhaContentExchange(FritzAhaCallback callback) {
        this.callback = callback;
    }

    /**
     * Log request success
     */
    @Override
	// void 	onSuccess​(Request request) 	
	//		Callback method invoked when the request has been successfully sent.
    public void onSuccess(Response response) {		// HTTP response 403 
        logger.debug("onSuccess HTTP response {}", response.getStatus());		// ptro 06mar24 to track onSuccess
    }

    /**
     * Log request failure
     */
    @Override
	// void 	onFailure​(Request request, java.lang.Throwable failure) 	
	//		Callback method invoked when the request has failed to be sent
    public void onFailure(Response response, Throwable failure) {
        logger.warn("FritzAhaContentExchange onFailure()={}", failure.getLocalizedMessage()); // ptro 06mar24 to track onFailure
    }

    /**
     * Call the callbacks execute method on request completion.
     */
    @Override
	// void 	onComplete​(Result result) 	
	//		Callback method invoked when the request and the response have been processed, either successfully or not.
    public void onComplete(Result result) {
/*
		if (intValue(result.getResponse().getStatus()) == 200 ) {
	        logger.debug("onComplete response200 getContentAsString:{} , status={} EOF.", this.getContentAsString(), result.getResponse().getStatus());	// HTTP response 403 = "" 

		} else {
	        logger.debug("onComplete response??? getContentAsString:{} , status={} EOF.", this.getContentAsString(), result.getResponse().getStatus());	// HTTP response 403 = "" 
		}
*/
/*

		logger.debug("onComplete response??? getContentAsString:{} , status={} type={}.", 
				this.getContentAsString(), 
				result.getResponse().getStatus(), 
				result.getClass() ) ;	// HTTP response 403 = ""
//				result.getClass().getName() ) ;	// HTTP response 403 = ""  

		// if (result.getResponse().getStatus().compareTo(200) ) {
		if (result.getResponse().getStatus() != 200 ) {
			logger.warn("result.getResponse().getStatus()={}{", result.getResponse().getStatus()); 
		}
		if (result.getResponse().getStatus() < 201 ) {
			logger.debug("result.getResponse().getStatus() < 201 is detected"); 
		}
		if (result.getResponse().getStatus() > 200 ) {
			logger.debug("result.getResponse().getStatus() > 200 is detected"); 
		}
*/
		// ptro 06mar24 enforce null in response if no output or not http response 200
		if (this.getContentAsString() == "" || result.getResponse().getStatus() != 200 ) {
		        logger.warn("onComplete response {} : getContentAsString forced to null.", result.getResponse().getStatus()); 		// ptro 06mar24 to track null response
		        this.callback.execute(result.getResponse().getStatus(), null);
		} else {
		        logger.debug("onComplete response is value {}", result.getResponse().getStatus() ) ; 	// ptro 06mar24 to track OK response
		        this.callback.execute(result.getResponse().getStatus(), this.getContentAsString());
		} 
		// has null if response.getStatus() = 403 ,should alway be 200 to be ok
        // this.callback.execute(result.getResponse().getStatus(), this.getContentAsString());
    }
	// https://eclipse.dev/jetty/javadoc/jetty-9/org/eclipse/jetty/client/api/ContentResponse.html
	// response complete: 
	//	<devicelist version="1">
	//		<group identifier="47:B3:30-900" id="900" functionbitmask="512" fwversion="1.0" manufacturer="AVM" productname="">
	//			<present>1</present>
	//			<name>Huiskamer</name>
	//			<switch>
	//				<state>1</state>
	//	 			<mode>auto</mode>
	//				<lock></lock>
	//			</switch>
	//			<groupinfo>
	// 				<masterdeviceid>0</masterdeviceid>
	// 				<members></members>
	// 			</groupinfo>
	// 		</group>
	//	</devicelist> 
}
