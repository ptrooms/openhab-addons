/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

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
    public void onSuccess(Response response) {
        logger.debug("HTTP response {}", response.getStatus());
    }

    /**
     * Log request failure
     */
    @Override
	// void 	onFailure​(Request request, java.lang.Throwable failure) 	
	//		Callback method invoked when the request has failed to be sent
    public void onFailure(Response response, Throwable failure) {
        logger.debug("onFailure: {}", failure.getLocalizedMessage());
    }

    /**
     * Call the callbacks execute method on request completion.
     */
    @Override
	// void 	onComplete​(Result result) 	
	//		Callback method invoked when the request and the response have been processed, either successfully or not.
    public void onComplete(Result result) {
        logger.debug("response complete: {}", this.getContentAsString());
        this.callback.execute(result.getResponse().getStatus(), this.getContentAsString());
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
