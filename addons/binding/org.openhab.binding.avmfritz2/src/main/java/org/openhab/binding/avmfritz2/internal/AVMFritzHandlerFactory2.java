/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

// 06mar23 ptro localise httpClient to solved queingstuck problem when FritzBox does not respond

package org.openhab.binding.avmfritz2.internal;

import static org.openhab.binding.avmfritz2.internal.BindingConstants.*;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.eclipse.smarthome.io.net.http.HttpClientFactory;
import org.openhab.binding.avmfritz2.internal.discovery.AVMFritzDiscoveryService;
import org.openhab.binding.avmfritz2.internal.handler.AVMFritzBaseBridgeHandler;
import org.openhab.binding.avmfritz2.internal.handler.BoxHandler;
import org.openhab.binding.avmfritz2.internal.handler.DeviceHandler;
import org.openhab.binding.avmfritz2.internal.handler.GroupHandler;
import org.openhab.binding.avmfritz2.internal.handler.Powerline546EHandler;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// import org.apache.commons.lang.exception.ExceptionUtils;	// ptr 05mar24 for testing httpstop/start

/**
 * The {@link AVMFritzHandlerFactory2} is responsible for creating things and thing handlers.
 *
 * @author Robert Bausdorf - Initial contribution
 */
@Component(service = ThingHandlerFactory.class, configurationPid = "binding.avmfritz2")
public class AVMFritzHandlerFactory2 extends BaseThingHandlerFactory {
    /**
     * Logger
     */
    private final Logger logger = LoggerFactory.getLogger(getClass());
    /**
     * Service registration map
     */
    private Map<ThingUID, ServiceRegistration<?>> discoveryServiceRegs = new HashMap<>();
    /**
     * shared instance of HTTP client for asynchronous calls
     */
    private HttpClient httpClient;

    /**
     * Provides the supported thing types
     */
    @Override
    public boolean supportsThingType(@NonNull ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    /**
     * Create handler of things.
     */
    @Override
    protected ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();
        if (BRIDGE_THING_TYPE.equals(thingTypeUID)) {
            BoxHandler handler = new BoxHandler((Bridge) thing, httpClient);
            registerDeviceDiscoveryService(handler);
            return handler;
        } else if (PL546E_STANDALONE_THING_TYPE.equals(thingTypeUID)) {
            Powerline546EHandler handler = new Powerline546EHandler((Bridge) thing, httpClient);
            registerDeviceDiscoveryService(handler);
            return handler;
        } else if (SUPPORTED_DEVICE_THING_TYPES_UIDS.contains(thing.getThingTypeUID())) {
            return new DeviceHandler(thing);
        } else if (SUPPORTED_GROUP_THING_TYPES_UIDS.contains(thingTypeUID)) {
            return new GroupHandler(thing);
        } else {
            logger.error("ThingHandler not found for {}", thing.getThingTypeUID());
        }
        return null;
    }

    /**
     * Remove handler of things.
     */
    @Override
    protected synchronized void removeHandler(@NonNull ThingHandler thingHandler) {
        if (thingHandler instanceof AVMFritzBaseBridgeHandler) {
            ServiceRegistration<?> serviceReg = discoveryServiceRegs.remove(thingHandler.getThing().getUID());
            if (serviceReg != null) {
                // remove discovery service, if bridge handler is removed
                AVMFritzDiscoveryService service = (AVMFritzDiscoveryService) bundleContext
                        .getService(serviceReg.getReference());
                serviceReg.unregister();
                if (service != null) {
                    service.deactivate();
                }
            }
        }
    }

    /**
     * Register a new discovery service for a new FRITZ!Box.
     *
     * @param handler
     */
    private synchronized void registerDeviceDiscoveryService(AVMFritzBaseBridgeHandler handler) {
        AVMFritzDiscoveryService discoveryService = new AVMFritzDiscoveryService(handler);
        discoveryServiceRegs.put(handler.getThing().getUID(), bundleContext
                .registerService(DiscoveryService.class.getName(), discoveryService, new Hashtable<String, Object>()));
    }

    @Reference
    protected void setHttpClientFactory(HttpClientFactory httpClientFactory) {

//	section changed start localised HttpClient.
//        this.httpClient = httpClientFactory.getCommonHttpClient();	// ptro 06mar24 replaced by httpClient.stop/start
// stop/start is allowed in eclipse when we add compilerArgs <arg>-err:-forbidden</arg> to pom.xml
        this.httpClient = new HttpClient();		// ptro 05mar24 try our own http client
		logger.info("setHttpClientFactory {} start() httpClient='{} , this.httpClient {} ", httpClientFactory, httpClient, this.httpClient );
        try {
            httpClient.start();
        } catch (Exception e) {
            // just dirty logging for testing purposes.
			logger.debug("failed httpClient.start: '{}': ", e.getLocalizedMessage(), e);
            // logger.debug("{}", ExceptionUtils.getFullStackTrace(e));
        }
    }

    protected void unsetHttpClientFactory(HttpClientFactory httpClientFactory) {
		logger.info("unsetHttpClientFactory2 {} httpClient='{} , this.httpClient {} ", httpClientFactory, httpClient, this.httpClient );

//	section added to stop localised HttpClient.
//		stop/start is allowed in eclipse when we add compilerArgs <arg>-err:-forbidden</arg> to pom.xml
        try {									// ptro 06mar24 try our own http client
            httpClient.stop();
        } catch (Exception e) {
            // just dirty logging for testing purposes.
			logger.debug("failed httpClient.stop: '{}': ", e.getLocalizedMessage(), e);
            // logger.debug("{}", ExceptionUtils.getFullStackTrace(e));
        }

        this.httpClient = null;

    }
}
