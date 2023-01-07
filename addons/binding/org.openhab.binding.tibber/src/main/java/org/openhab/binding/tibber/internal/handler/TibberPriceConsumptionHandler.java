/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.tibber.internal.handler;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.jdt.annotation.NonNullByDefault;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link TibberPriceConsumptionHandler} class contains fields mapping price info parameters.
 *
 * @author Stian Kjoglum - Initial contribution
 */
@NonNullByDefault
public class TibberPriceConsumptionHandler {

/*
	{viewer {home(id:"c237669e-90a8-4be2-ab4b-97e49715f652") 
				{ currentSubscription {priceInfo {current {total startsAt level } 
					tomorrow { startsAt total } 
					}} 
					daily: consumption(resolution: DAILY, last: 1) {nodes {from to cost unitPrice consumption consumptionUnit}} 
					hourly: consumption(resolution: HOURLY, last: 1) {nodes {from to cost unitPrice consumption consumptionUnit}} 
				}
			}

	}
*/

    private final Logger logger = LoggerFactory.getLogger(TibberPriceConsumptionHandler.class);

    public InputStream connectionInputStream(String homeId) {
        String connectionquery = "{\"query\": \"{viewer {home (id: \\\"" + homeId + "\\\") {id }}}\"}";
        logger.debug("API1 request1a: {}", connectionquery );
        return new ByteArrayInputStream(connectionquery.getBytes(StandardCharsets.UTF_8));
    }

    public InputStream getInputStream(String homeId) {
        String Query = "{\"query\": \"{viewer {home (id: \\\"" + homeId + "\\\") {"
                + " currentSubscription {priceInfo {current {total startsAt level }"
				  + " today { startsAt total }"
                  + " tomorrow { startsAt total }"
                  + " }}"
//                + " daily:	consumption(resolution: DAILY, last: 1) 	{nodes {from to cost unitPrice consumption consumptionUnit } }"
//                + " hourly:	consumption(resolution: HOURLY, last: 1) 	{nodes {from to cost unitPrice consumption consumptionUnit } }"
//                + " daily:	consumption(resolution: DAILY, last: 31) 	{nodes {from to cost unitPrice consumption consumptionUnit } }"
//                + " hourly:	"consumption(resolution: HOURLY, last: 24)	{nodes {from to cost unitPrice consumption consumptionUnit } }"
// ------------------- test note name daily/hourly etc is symbolicname for naming the arraynodes 
                + " daily: consumption(resolution: DAILY, last: 2, filterEmptyNodes: true)    {nodes {from to cost unitPrice consumption }}"
//                 + " hourly: consumption(resolution: HOURLY, last: 48, filterEmptyNodes: true) {nodes {from to cost consumption}}"
                + " weekly: consumption(resolution: WEEKLY, last: 2, filterEmptyNodes: true)  {nodes {from to cost unitPrice consumption }}"
                + " monthly: consumption(resolution: MONTHLY, last: 2, filterEmptyNodes: true) {nodes {from to cost unitPrice consumption }}"
//                + " yearly: consumption(resolution: ANNUAL, last: 2) {nodes {from to cost consumption }}"
// -------------------
//                + " features {realTimeConsumptionEnabled}"	// true/false tibberpulse
//                + " numberOfResidents"	// persons set by account
//                + " size"					// surface set by account
//                + " appNickname"			// name set by account
//                + " meteringPointData {"	// meter data
//                + "    consumptionEan"	// meter number
//                + "    energyTaxType"		// tax type "normal"
//                + "    vatType"			// tax type "normal"
//                + "    estimatedAnnualConsumption"	// set by account
//                + " }"
// ---------------------
                + " }}}\"}";
        logger.debug("API2 request2a: {}", Query);
        return new ByteArrayInputStream(Query.getBytes(StandardCharsets.UTF_8));
    }

/* // original
    public InputStream getInputStream(String homeId) {
        String Query = "{\"query\": \"{viewer {home (id: \\\"" + homeId + "\\\") {"
				+ " currentSubscription {priceInfo {current {total startsAt level }} tomorrow { startsAt total } }"
				+ " daily: consumption(resolution: DAILY, last: 1) {nodes {from to cost unitPrice consumption consumptionUnit}}" 
				+ " hourly: consumption(resolution: HOURLY, last: 1) {nodes {from to cost unitPrice consumption consumptionUnit}}"

      // "consumption(resolution: DAILY, last: 31) { nodes { from to cost unitPrice unitPriceVAT consumption consumptionUnit } }
      // "consumption(resolution: HOURLY, last: 24) { nodes { from to cost unitPrice unitPriceVAT consumption consumptionUnit } }

				+ "}}}\"}";
        return new ByteArrayInputStream(Query.getBytes(StandardCharsets.UTF_8));
    }
*/

    public InputStream getRealtimeInputStream(String homeId) {
        String realtimeenabledquery = "{\"query\": \"{viewer {home (id: \\\"" + homeId
                + "\\\") {features {realTimeConsumptionEnabled }}}}\"}";
        logger.debug("API3 request3a: {}", realtimeenabledquery);
        return new ByteArrayInputStream(realtimeenabledquery.getBytes(StandardCharsets.UTF_8));
    }
}
