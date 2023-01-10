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


/*

 org.eclipse.smarthome.core.common,
	--> [ERROR] Failed to execute goal org.eclipse.tycho:tycho-packaging-plugin:1.2.0:package-plugin (default-package-plugin) on project org.openhab.binding.tibber: Error assembling JAR: invalid he
ader field -> [Help 1]


Eclipse has a mechanism called access restrictions to prevent you from accidentally using classes which Eclipse thinks are not part of the public API

// display effective pom: $ mvn help:effective-pom -o

<plugin>
    <groupId>org.eclipse.tycho</groupId>
    <artifactId>tycho-compiler-plugin</artifactId>
    <version>${tycho.version}</version>
    <configuration>
        <compilerArgument>-warn:+discouraged,forbidden</compilerArgument>
    </configuration>
</plugin>

*/

import static org.openhab.binding.tibber.internal.TibberBindingConstants.*;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import org.openhab.binding.tibber.internal.config.TibberConfiguration;

import org.eclipse.smarthome.core.library.types.DecimalType;

import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.binding.BridgeHandler;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerCallback;
// import org.eclipse.smarthome.core.library.types.OnOffType;
// import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
// import org.eclipse.smarthome.core.library.types.IncreaseDecreaseType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// import org.eclipse.jdt.annotation.NonNullByDefault;
// import org.eclipse.jdt.annotation.Nullable;

import java.math.RoundingMode;

import org.eclipse.smarthome.core.common.ThreadPoolManager;
import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.unit.SmartHomeUnits;
import org.eclipse.smarthome.io.net.http.HttpUtil;

/*
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
*/

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;


/*
	import org.openhab.core.common.ThreadPoolManager;
	import org.openhab.core.io.net.http.HttpUtil;
	import org.openhab.core.library.types.DateTimeType;
	import org.openhab.core.library.types.DecimalType;
	import org.openhab.core.library.types.QuantityType;
	import org.openhab.core.library.types.StringType;
	import org.openhab.core.library.unit.SmartHomeUnits;
	import org.openhab.core.thing.ChannelUID;
	import org.openhab.core.thing.Thing;
	import org.openhab.core.thing.ThingStatus;
	import org.openhab.core.thing.ThingStatusDetail;
	import org.openhab.core.thing.ThingStatusInfo;
	import org.openhab.core.thing.binding.BaseThingHandler;
	import org.openhab.core.types.Command;
	import org.openhab.core.types.RefreshType;

*/

/* 
+-----------------------------------------------++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++/**
 * The {@link TibberHandler} is responsible for handling queries to/from Tibber API.
 *
 * @author Stian Kjoglum - Initial contribution
 */
@NonNullByDefault
public class TibberHandler extends BaseThingHandler {
    private static final int REQUEST_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(20);
    private final Logger logger = LoggerFactory.getLogger(TibberHandler.class);
    private final Properties httpHeader = new Properties();
    private final SslContextFactory sslContextFactory = new SslContextFactory(true);
    private final Executor websocketExecutor = ThreadPoolManager.getPool("tibber.websocket");
    private TibberConfiguration tibberConfig = new TibberConfiguration();
    private @Nullable TibberWebSocketListener socket;
    private @Nullable Session session;
    private @Nullable WebSocketClient client;
    private @Nullable ScheduledFuture<?> pollingJob;
    private @Nullable Future<?> sessionFuture;
    private String rtEnabled = "false";
    public TibberHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
        tibberConfig = getConfigAs(TibberConfiguration.class);

        getTibberParameters();
        startRefresh(tibberConfig.getRefresh());
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            startRefresh(tibberConfig.getRefresh());
        } else {
            logger.debug("Tibber API is read-only and does not handle commands");
        }
    }

    public void getTibberParameters() {
        String response = "";
        try {
            httpHeader.put("cache-control", "no-cache");
            httpHeader.put("content-type", JSON_CONTENT_TYPE);
            httpHeader.put("Authorization", "Bearer " + tibberConfig.getToken());

            TibberPriceConsumptionHandler tibberQuery = new TibberPriceConsumptionHandler();
            InputStream connectionStream = tibberQuery.connectionInputStream(tibberConfig.getHomeid());
            response = HttpUtil.executeUrl("POST", BASE_URL, httpHeader, connectionStream, null,
                    REQUEST_TIMEOUT);
            logger.debug("API1 response1b: {}", response);

            if (!response.contains("error") && !response.contains("<html>")) {
                updateStatus(ThingStatus.ONLINE);

                getURLInput(BASE_URL);

                InputStream inputStream = tibberQuery.getRealtimeInputStream(tibberConfig.getHomeid());
                String jsonResponse = HttpUtil.executeUrl("POST", BASE_URL, httpHeader, inputStream, null,
                        REQUEST_TIMEOUT);

                JsonObject object = (JsonObject) new JsonParser().parse(jsonResponse);
                rtEnabled = object.getAsJsonObject("data").getAsJsonObject("viewer").getAsJsonObject("home")
                        .getAsJsonObject("features").get("realTimeConsumptionEnabled").toString();

                if ("true".equals(rtEnabled)) {
                    logger.debug("Pulse associated with HomeId: Live stream will be started");
                    open();
                } else {
                    logger.debug("No Pulse associated with HomeId: No live stream will be started");
                }
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Problems connecting/communicating with server: " + response);
            }
        } catch (IOException | JsonSyntaxException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }


/*
	Note: this is using ID
	{"data":
		{"viewer":
			{"home":
				{"currentSubscription":
					{"priceInfo":
						{"current":
							{"total":1.3812,
							"startsAt":"2022-12-20T01:00:00.000+01:00"}}},

						 "today": [
									  {
										"tax": 0.1541,
										"energy": -0.0036,
										"startsAt": "2023-01-01T00:00:00.000+01:00",
										"total": 0.1505
									  }, 
									  {
										"tax": 0.1622,
										"energy": 0.035,
										"startsAt": "2023-01-01T23:00:00.000+01:00",
										"total": 0.1972
									  }
									],

							"daily":{"
								nodes":[{"from":"2022-12-19T00:00:00.000+01:00",
								"to":"2022-12-20T00:00:00.000+01:00",
								"cost":152.6103103,
								"unitPrice":2.593958,
								"consumption":58.833,
								"consumptionUnit":"kWh"}]},
							"hourly":{
								"nodes":[{"from":"2022-12-20T00:00:00.000+01:00",
								"to":"2022-12-20T01:00:00.000+01:00",
								"cost":3.388555,"unitPrice":1.598375,
								"consumption":2.12,
								"consumptionUnit":"kWh"}]}}}}} 
*/

    public void getURLInput(String url) throws IOException {
        String response = "";
        TibberPriceConsumptionHandler tibberQuery = new TibberPriceConsumptionHandler();

        InputStream inputStream = tibberQuery.getInputStream(tibberConfig.getHomeid());
        String jsonResponse = HttpUtil.executeUrl("POST", url, httpHeader, inputStream, null, REQUEST_TIMEOUT);
        logger.trace("API2 response2b: {}", jsonResponse);

        if (!jsonResponse.contains("error") && !jsonResponse.contains("<html>")) {
            if (getThing().getStatus() == ThingStatus.OFFLINE || getThing().getStatus() == ThingStatus.INITIALIZING) {
                updateStatus(ThingStatus.ONLINE);
            }

            JsonObject object = (JsonObject) new JsonParser().parse(jsonResponse);

            if (jsonResponse.contains("total")) {
                try {
                    JsonObject myObject = object.getAsJsonObject("data").getAsJsonObject("viewer")
                            .getAsJsonObject("home").getAsJsonObject("currentSubscription").getAsJsonObject("priceInfo")
                            .getAsJsonObject("current");

                    updateState(CURRENT_TOTAL, new DecimalType(myObject.get("total").toString()));
                    // updateState(TODAY_AVERAGE, new DecimalType(myObject.get("total").toString()));

                    updateState(CURRENT_LEVEL, new StringType(myObject.get("level").toString()));
			        logger.debug("API total: {}  level: {}", myObject.get("total").toString(), myObject.get("level").toString() );

                    String timestamp = myObject.get("startsAt").toString().substring(1, 20);
                    updateState(CURRENT_STARTSAT, new DateTimeType(timestamp));

                    String cheap_timestamp = timestamp;
                    // BigDecimal cheap_cost = new BigDecimal("0.00");		// read https://docs.oracle.com/javase/7/docs/api/java/math/BigDecimal.html
					BigDecimal cheap_cost = BigDecimal.valueOf( myObject.get("total").getAsDouble() );
                    boolean cheap_calculate = false;
                    // cheap_cost = new DecimalType(myObject.get("total")
/*
	if starttime >= now
 	 scan today starting normalised at current hour + refreshtime
	 determine from array today's best moment
	 if tomorrow != better set start to tomorrow
	 else set starttime at tommorrow best time


	 lock this clocktime and wait until passed+2 hours
*/
				    if (jsonResponse.contains("today") && !jsonResponse.contains("\"today\":[]")) {
                        JsonArray today = object.getAsJsonObject("data").getAsJsonObject("viewer")
                                .getAsJsonObject("home").getAsJsonObject("currentSubscription").getAsJsonObject("priceInfo")
                                .getAsJsonArray("today");
                        updateState(TODAY_PRICES, new StringType(today.toString()));
						String h_values = "";  
						BigDecimal h_cnt = new BigDecimal("0");		// read https://docs.oracle.com/javase/7/docs/api/java/math/BigDecimal.html
						BigDecimal h_avg = new BigDecimal("0.00");  
						for (JsonElement h_today: today ) {						// loop
				            if (h_today.getAsJsonObject().get("total") != null ) {	// no null
				                h_values = h_values + " " + h_today.getAsJsonObject().get("total").getAsString();
								h_avg = h_avg.add(BigDecimal.valueOf( h_today.getAsJsonObject().get("total").getAsDouble() ))  ;
								h_cnt = h_cnt.add(new BigDecimal("1")) ;

                                if (!cheap_calculate && cheap_timestamp.equals(h_today.getAsJsonObject().get("startsAt").toString().substring(1, 20)) ) {
                                    cheap_calculate = true;
			                        // logger.trace("API6A index={} date={} cost={}", h_cnt, cheap_timestamp,  cheap_cost );
                                } else {
		                            if ( cheap_calculate && (cheap_cost.setScale(2, RoundingMode.UP)).compareTo( ((BigDecimal.valueOf(h_today.getAsJsonObject().get("total").getAsDouble())).setScale(2, RoundingMode.UP)) ) > 0 ) {
		                                   cheap_timestamp = h_today.getAsJsonObject().get("startsAt").toString().substring(1, 20);
		                                   // cheap_cost = new BigDecimal.valueOf(h_today.getAsJsonObject().get("total").getAsDouble() ) ;
		                                   cheap_cost = BigDecimal.valueOf(h_today.getAsJsonObject().get("total").getAsDouble() ) ;
					                       // logger.trace("API6B index={} date={} cost={}", h_cnt, cheap_timestamp,  cheap_cost );
		                            }
								}
                                // logger.trace("API6C index={} date={} val={}", h_cnt, cheap_timestamp, (BigDecimal.valueOf(h_today.getAsJsonObject().get("total").getAsDouble())).setScale(2, RoundingMode.UP) );
							}
						}
						if ( !(h_avg.compareTo(new BigDecimal("0.00")) == 0) && h_cnt.intValue() > 0 ) {
						// if (h_avg.compareTo(new BigDecimal("0.00")) != 0 && h_cnt.intValue() > 0 ) {
						// if (h_avg != new BigDecimal("0.00") && h_cnt.intValue() > 0 ) {
							h_avg = h_avg.divide(h_cnt, RoundingMode.HALF_UP);
	                        // updateState(TODAY_AVERAGE, new StringType(h_avg.toString()) );
							// updateChannel(TODAY_AVERAGE, h_avg.toString());
							updateState(TODAY_AVERAGE, new DecimalType(h_avg.toString()));
							// updateState(DAILY_COST, new DecimalType(h_avg.toString()));
							logger.trace("API3b today avg={}", h_avg );
						}
                        logger.trace("API3 today avg={} count={} array={}", h_avg, h_cnt, h_values );
				    } // end of today

				    if (jsonResponse.contains("tomorrow") && !jsonResponse.contains("\"tomorrow\":[]")) {
                        JsonArray tomorrow = object.getAsJsonObject("data").getAsJsonObject("viewer")
                                .getAsJsonObject("home").getAsJsonObject("currentSubscription").getAsJsonObject("priceInfo")
                                .getAsJsonArray("tomorrow");
                        updateState(TOMORROW_PRICES, new StringType(tomorrow.toString()));
						String h_values = "";  
						BigDecimal h_cnt = new BigDecimal("0");		// read https://docs.oracle.com/javase/7/docs/api/java/math/BigDecimal.html
						BigDecimal h_avg = new BigDecimal("0.00");  // read https://docs.oracle.com/javase/7/docs/api/java/math/BigDecimal.html 
						for (JsonElement h_tomorrow: tomorrow ) {						// loop
				            if (h_tomorrow.getAsJsonObject().get("total") != null ) {	// no null
				                h_values = h_values + " " + h_tomorrow.getAsJsonObject().get("total").getAsString();
								h_avg = h_avg.add(BigDecimal.valueOf( h_tomorrow.getAsJsonObject().get("total").getAsDouble() ))  ;
								h_cnt = h_cnt.add(new BigDecimal("1")) ;

                                if (!cheap_calculate && cheap_timestamp.equals(h_tomorrow.getAsJsonObject().get("startsAt").toString().substring(1, 20)) ) {
                                    cheap_calculate = true;
                                } else {
		                            // if ( cheap_calculate && cheap_cost.compareTo( BigDecimal.valueOf(h_tomorrow.getAsJsonObject().get("total").getAsDouble() )) >= 0 ) {
		                            if ( cheap_calculate && (cheap_cost.setScale(2, RoundingMode.UP)).compareTo( ((BigDecimal.valueOf(h_tomorrow.getAsJsonObject().get("total").getAsDouble())).setScale(2, RoundingMode.UP) )) > 0 ) {
		                                   cheap_timestamp = h_tomorrow.getAsJsonObject().get("startsAt").toString().substring(1, 20);
		                                   // cheap_cost = new DecimalType(h_tomorrow.getAsJsonObject().get("total").getAsDouble() );
		                                   // cheap_cost = new DecimalType( BigDecimal.valueOf(h_today.getAsJsonObject().get("total").getAsDouble() ) );
		                                   // cheap_cost = new BigDecimal.valueOf(h_tomorrow.getAsJsonObject().get("total").getAsDouble() ) ;
		                                   cheap_cost = BigDecimal.valueOf(h_tomorrow.getAsJsonObject().get("total").getAsDouble() ) ;
		                            }  
								}
							}
						}

						if ( !(h_avg.compareTo(new BigDecimal("0.00")) == 0) && h_cnt.intValue() > 0 ) {
						// if (h_avg != new BigDecimal("0.00") && h_cnt.intValue() > 0 ) {
							h_avg = h_avg.divide(h_cnt, RoundingMode.HALF_UP);
	                        // updateState(TOMORROW_AVERAGE, new StringType(h_avg.toString()) );
							// updateChannel(TOMORROW_AVERAGE, h_avg.toString());
							updateState(TOMORROW_AVERAGE, new DecimalType(h_avg.toString()));
							logger.trace("API4b tomorrow avg={}", h_avg );
		                    // updateState(CURRENT_TOTAL, new DecimalType(myObject.get("total").toString()));
                        /*
							} else if (h_avg.compareTo(new BigDecimal("0.00")) != 0 ) {
								updateState(TOMORROW_AVERAGE, new DecimalType(h_avg.toString()));
								  logger.trace("API4b tomorrow avg={} ", h_avg );
		                    } else if ( h_cnt.intValue() > 0 ) {
								updateState(TOMORROW_AVERAGE, new DecimalType(h_avg.toString()));
								  logger.trace("API4c tomorrow avg={} ", h_avg );
						*/
						}
                        logger.trace("API4 tomorrow avg={} count={} array={}", h_avg, h_cnt, h_values );
				    } // end of tomorrow
                    if ( cheap_calculate ) {
                        updateState(CHEAP_STARTSAT, new DateTimeType(cheap_timestamp));
						updateState(CHEAP_PRICE, new DecimalType(cheap_cost.toString()));
	                    logger.debug("API5 Activated {} CHEAP_COST {} at CHEAP_STARTSAT {} ", cheap_calculate, cheap_cost, cheap_timestamp );
					}


		//            if (jsonResponse.contains("hourly")) {
				    if (jsonResponse.contains("hourly") && !jsonResponse.contains("\"hourly\":{\"nodes\":[]")
				            && !jsonResponse.contains("\"hourly\":null")) {
				        try {
				            JsonArray hourly = object.getAsJsonObject("data").getAsJsonObject("viewer")
				                    .getAsJsonObject("home").getAsJsonObject("hourly").getAsJsonArray("nodes");
				            myObject = (JsonObject) hourly.get(hourly.size() - 1);

				            /*
				            JsonObject myObject = (JsonObject) object.getAsJsonObject("data").getAsJsonObject("viewer")
				                    .getAsJsonObject("home").getAsJsonObject("hourly").getAsJsonArray("nodes").get(0);
				            */
				            String timestampHourlyFrom = myObject.get("from").toString().substring(1, 20);
				            updateState(HOURLY_FROM, new DateTimeType(timestampHourlyFrom));

				            String timestampHourlyTo = myObject.get("to").toString().substring(1, 20);
				            updateState(HOURLY_TO, new DateTimeType(timestampHourlyTo));

							logger.debug("HOURLY_FROM: {} , HOURLY_TO {} ", timestampHourlyFrom, timestampHourlyTo );

				            updateChannel(HOURLY_COST, myObject.get("cost").toString());
				            updateChannel(HOURLY_CONSUMPTION, myObject.get("consumption").toString());

				        } catch (JsonSyntaxException e) {
				            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
				                    "Error communicating with Tibber API HOURLY: " + e.getMessage());
				        }
				    }
		//            if (jsonResponse.contains("daily")) {
				   if (jsonResponse.contains("daily") && !jsonResponse.contains("\"daily\":{\"nodes\":[]")
				            && !jsonResponse.contains("\"daily\":null")) {
				        try {

				            JsonArray daily = object.getAsJsonObject("data").getAsJsonObject("viewer")
				                    .getAsJsonObject("home").getAsJsonObject("daily").getAsJsonArray("nodes");
				            myObject = (JsonObject) daily.get(daily.size() - 1);
				           /*  
				            JsonObject myObject = (JsonObject) object.getAsJsonObject("data").getAsJsonObject("viewer")
				                    .getAsJsonObject("home").getAsJsonObject("daily").getAsJsonArray("nodes").get(0);
				           */

				            String timestampDailyFrom = myObject.get("from").toString().substring(1, 20);
				            updateState(DAILY_FROM, new DateTimeType(timestampDailyFrom));

				            String timestampDailyTo = myObject.get("to").toString().substring(1, 20);
				            updateState(DAILY_TO, new DateTimeType(timestampDailyTo));

							logger.debug("DAILY_FROM: {} , DAILY_TO {} ", timestampDailyFrom, timestampDailyTo );

				            updateChannel(DAILY_COST, myObject.get("cost").toString());
				            updateChannel(DAILY_CONSUMPTION, myObject.get("consumption").toString());


				        } catch (JsonSyntaxException e) {
				            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
				                    "Error communicating with Tibber API DAILY: " + e.getMessage());
				        }
				    }
		//            if (jsonResponse.contains("hourly")) {
				    if (jsonResponse.contains("weekly") && !jsonResponse.contains("\"weekly\":{\"nodes\":[]")
				            && !jsonResponse.contains("\"weekly\":null")) {
				        try {
				            JsonArray weekly = object.getAsJsonObject("data").getAsJsonObject("viewer")
				                    .getAsJsonObject("home").getAsJsonObject("weekly").getAsJsonArray("nodes");
				            myObject = (JsonObject) weekly.get(weekly.size() - 1);

				            /*
				            JsonObject myObject = (JsonObject) object.getAsJsonObject("data").getAsJsonObject("viewer")
				                    .getAsJsonObject("home").getAsJsonObject("hourly").getAsJsonArray("nodes").get(0);
				            */
				            String timestampWeeklyFrom = myObject.get("from").toString().substring(1, 20);
				            updateState(WEEKLY_FROM, new DateTimeType(timestampWeeklyFrom));

				            String timestampWeeklyTo = myObject.get("to").toString().substring(1, 20);
				            updateState(WEEKLY_TO, new DateTimeType(timestampWeeklyTo));

							logger.debug("WEEKLY_FROM: {} , WEEKLY_TO {} ", timestampWeeklyFrom, timestampWeeklyTo );

				            updateChannel(WEEKLY_COST, myObject.get("cost").toString());
				            updateChannel(WEEKLY_CONSUMPTION, myObject.get("consumption").toString());

				        } catch (JsonSyntaxException e) {
				            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
				                    "Error communicating with Tibber API WEEKLY: " + e.getMessage());
				        }
				    }
		//            if (jsonResponse.contains("monthly")) {
				   if (jsonResponse.contains("monthly") && !jsonResponse.contains("\"monthly\":{\"nodes\":[]")
				            && !jsonResponse.contains("\"monthly\":null")) {
				        try {

				            JsonArray month = object.getAsJsonObject("data").getAsJsonObject("viewer")
				                    .getAsJsonObject("home").getAsJsonObject("monthly").getAsJsonArray("nodes");
				            myObject = (JsonObject) month.get(month.size() - 1);
				           /*  
				            JsonObject myObject = (JsonObject) object.getAsJsonObject("data").getAsJsonObject("viewer")
				                    .getAsJsonObject("home").getAsJsonObject("month").getAsJsonArray("nodes").get(0);
				           */

				            String timestampMonhtlyFrom = myObject.get("from").toString().substring(1, 20);
				            updateState(MONTHLY_FROM, new DateTimeType(timestampMonhtlyFrom));

				            String timestampMonhtlyTo = myObject.get("to").toString().substring(1, 20);
				            updateState(MONTHLY_TO, new DateTimeType(timestampMonhtlyTo));

							logger.debug("MONTHLY_FROM: {} , MONTHLY_TO {} ", timestampMonhtlyFrom, timestampMonhtlyTo );

				            updateChannel(MONTHLY_COST, myObject.get("cost").toString());
				            updateChannel(MONTHLY_CONSUMPTION, myObject.get("consumption").toString());


				        } catch (JsonSyntaxException e) {
				            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
				                    "Error communicating with Tibber API MONTHLY: " + e.getMessage());
				        }
				    } // end of month


                } catch (JsonSyntaxException e) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "Error communicating with Tibber API: " + e.getMessage());
                }
            }

        } else if (jsonResponse.contains("error")) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Error in response from Tibber API: " + jsonResponse);
            try {
                Thread.sleep(300 * 1000);
                return;
            } catch (InterruptedException e) {
                logger.debug("Tibber OFFLINE, attempting thread sleep: {}", e.getMessage());
            }
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Unexpected response from Tibber: " + jsonResponse);
            try {
                Thread.sleep(300 * 1000);
                return;
            } catch (InterruptedException e) {
                logger.debug("Tibber OFFLINE, attempting thread sleep: {}", e.getMessage());
            }
        }
    }

    public void startRefresh(int refresh) {
        if (pollingJob == null) {
            pollingJob = scheduler.scheduleWithFixedDelay(() -> {
                try {
                    updateRequest();
                } catch (IOException e) {
                    logger.warn("IO Exception: {}", e.getMessage());
                }
            }, 1, refresh, TimeUnit.MINUTES);
        }
    }

    public void updateRequest() throws IOException {
        getURLInput(BASE_URL);
        if ("true".equals(rtEnabled) && !isConnected()) {
            logger.debug("Attempting to reopen Websocket connection");
            open();
        }
    }

    public void updateChannel(String channelID, String channelValue) {
        if (!channelValue.contains("null")) {
            if (channelID.contains("consumption") || channelID.contains("Consumption")
                    || channelID.contains("accumulatedProduction")) {
                updateState(channelID, new QuantityType<>(new BigDecimal(channelValue), SmartHomeUnits.KILOWATT_HOUR));
            } else if (channelID.contains("power") || channelID.contains("Power")) {
                updateState(channelID, new QuantityType<>(new BigDecimal(channelValue), SmartHomeUnits.WATT));
            } else if (channelID.contains("voltage")) {
                updateState(channelID, new QuantityType<>(new BigDecimal(channelValue), SmartHomeUnits.VOLT));
//            } else if (channelID.contains("live_current")) {
            } else if (channelID.contains("current")) {
                updateState(channelID, new QuantityType<>(new BigDecimal(channelValue), SmartHomeUnits.AMPERE));
            } else {
                updateState(channelID, new DecimalType(channelValue));
            }
        }
    }

    public void thingStatusChanged(ThingStatusInfo thingStatusInfo) {
        logger.debug("Thing Status updated to {} for device: {}", thingStatusInfo.getStatus(), getThing().getUID());
        if (thingStatusInfo.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Unable to communicate with Tibber API");
        }
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> pollingJob = this.pollingJob;
        if (pollingJob != null) {
            pollingJob.cancel(true);
            this.pollingJob = null;
        }
        if (isConnected()) {
            close();
            WebSocketClient client = this.client;
            if (client != null) {
                try {
                    logger.debug("Stopping and Terminating Websocket connection");
                    client.stop();
                    client.destroy();
                } catch (Exception e) {
                    logger.warn("Websocket Client Stop Exception: {}", e.getMessage());
                }
                this.client = null;
            }
        }
        super.dispose();
    }

    public void open() {
        if (isConnected()) {
            logger.debug("Open: connection is already open");
        } else {
            sslContextFactory.setTrustAll(true);
            sslContextFactory.setEndpointIdentificationAlgorithm(null);

            WebSocketClient client = this.client;
            if (client == null) {
                client = new WebSocketClient(sslContextFactory, websocketExecutor);
                client.setMaxIdleTimeout(600 * 1000);
                this.client = client;
            }

            TibberWebSocketListener socket = this.socket;
            if (socket == null) {
                socket = new TibberWebSocketListener();
                this.socket = socket;
            }

            ClientUpgradeRequest newRequest = new ClientUpgradeRequest();
            newRequest.setHeader("Authorization", "Bearer " + tibberConfig.getToken());
            newRequest.setSubProtocols("graphql-subscriptions");

            try {
                logger.debug("Starting Websocket connection");
                client.start();
            } catch (Exception e) {
                logger.warn("Websocket Start Exception: {}", e.getMessage());
            }
            try {
                logger.debug("Connecting Websocket connection");
                sessionFuture = client.connect(socket, new URI(SUBSCRIPTION_URL), newRequest);
            } catch (IOException e) {
                logger.warn("Websocket Connect Exception: {}", e.getMessage());
            } catch (URISyntaxException e) {
                logger.warn("Websocket URI Exception: {}", e.getMessage());
            }
        }
    }

    public void close() {
        Session session = this.session;
        if (session != null) {
            String disconnect = "{\"type\":\"connection_terminate\",\"payload\":null}";
            try {
                TibberWebSocketListener socket = this.socket;
                if (socket != null) {
                    logger.debug("Sending websocket disconnect message");
                    socket.sendMessage(disconnect);
                } else {
                    logger.debug("Socket unable to send disconnect message: Socket is null");
                }
            } catch (IOException e) {
                logger.warn("Websocket Close Exception: {}", e.getMessage());
            }
//            session.close(0, "Tibber websocket disposed");
            session.close();
            this.session = null;
            this.socket = null;
        }
        Future<?> sessionFuture = this.sessionFuture;
        if (sessionFuture != null && !sessionFuture.isDone()) {
            sessionFuture.cancel(true);
        }
        WebSocketClient client = this.client;
        if (client != null) {
            try {
                client.stop();
            } catch (Exception e) {
                logger.warn("Failed to stop websocket client: {}", e.getMessage());
            }
        }
    }

    public boolean isConnected() {
        Session session = this.session;
        return session != null && session.isOpen();
    }

    @WebSocket
    // @NonNullByDefault  // ptro pocs nullified as we got an warning "Nullness default is redundant with a default specified for the enclosing type TibberHandler"
    public class TibberWebSocketListener {

        @OnWebSocketConnect
        public void onConnect(Session wssession) {
            TibberHandler.this.session = wssession;
            TibberWebSocketListener socket = TibberHandler.this.socket;
            String connection = "{\"type\":\"connection_init\", \"payload\":\"token=" + tibberConfig.getToken() + "\"}";
            try {
                if (socket != null) {
                    logger.debug("Sending websocket connect message");
                    socket.sendMessage(connection);
                } else {
                    logger.debug("Socket unable to send connect message: Socket is null");
                }
            } catch (IOException e) {
                logger.warn("Send Message Exception: {}", e.getMessage());
            }
        }

        @OnWebSocketClose
        public void onClose(int statusCode, String reason) {
            logger.debug("Closing a WebSocket due to {}", reason);
            WebSocketClient client = TibberHandler.this.client;
            if (client != null && client.isRunning()) {
                try {
                    logger.debug("Stopping and Terminating Websocket connection");
                    client.stop();
                    client.destroy();
                } catch (Exception e) {
                    logger.warn("Websocket Client Stop Exception: {}", e.getMessage());
                }
            }
            TibberHandler.this.session = null;
            TibberHandler.this.client = null;
            TibberHandler.this.socket = null;
        }

        @OnWebSocketError
        public void onWebSocketError(Throwable e) {
            logger.debug("Error during websocket communication: {}", e.getMessage());
            onClose(0, e.getMessage());
        }

        @OnWebSocketMessage
        public void onMessage(String message) {
            if (message.contains("connection_ack")) {
                logger.debug("Connected to Server");
                startSubscription();
            } else if (message.contains("error") || message.contains("terminate")) {
                logger.debug("Error/terminate received from server: {}", message);
                close();
            } else if (message.contains("liveMeasurement")) {
                JsonObject object = (JsonObject) new JsonParser().parse(message);
                JsonObject myObject = object.getAsJsonObject("payload").getAsJsonObject("data")
                        .getAsJsonObject("liveMeasurement");
                if (myObject.has("timestamp")) {
                    String liveTimestamp = myObject.get("timestamp").toString().substring(1, 20);
                    updateState(LIVE_TIMESTAMP, new DateTimeType(liveTimestamp));
                }
                if (myObject.has("power")) {
                    updateChannel(LIVE_POWER, myObject.get("power").toString());
                }
                if (myObject.has("lastMeterConsumption")) {
                    updateChannel(LIVE_LASTMETERCONSUMPTION, myObject.get("lastMeterConsumption").toString());
                }
                if (myObject.has("accumulatedConsumption")) {
                    updateChannel(LIVE_ACCUMULATEDCONSUMPTION, myObject.get("accumulatedConsumption").toString());
                }
                if (myObject.has("accumulatedCost")) {
                    updateChannel(LIVE_ACCUMULATEDCOST, myObject.get("accumulatedCost").toString());
                }
                if (myObject.has("currency")) {
                    updateState(LIVE_CURRENCY, new StringType(myObject.get("currency").toString()));
                }
                if (myObject.has("minPower")) {
                    updateChannel(LIVE_MINPOWER, myObject.get("minPower").toString());
                }
                if (myObject.has("averagePower")) {
                    updateChannel(LIVE_AVERAGEPOWER, myObject.get("averagePower").toString());
                }
                if (myObject.has("maxPower")) {
                    updateChannel(LIVE_MAXPOWER, myObject.get("maxPower").toString());
                }
                if (myObject.has("voltagePhase1")) {
                    updateChannel(LIVE_VOLTAGE1, myObject.get("voltagePhase1").toString());
                }
                if (myObject.has("voltagePhase2")) {
                    updateChannel(LIVE_VOLTAGE2, myObject.get("voltagePhase2").toString());
                }
                if (myObject.has("voltagePhase3")) {
                    updateChannel(LIVE_VOLTAGE3, myObject.get("voltagePhase3").toString());
                }
                if (myObject.has("currentL1")) {
                    updateChannel(LIVE_CURRENT1, myObject.get("currentL1").toString());
                }
                if (myObject.has("currentL2")) {
                    updateChannel(LIVE_CURRENT2, myObject.get("currentL2").toString());
                }
                if (myObject.has("currentL3")) {
                    updateChannel(LIVE_CURRENT3, myObject.get("currentL3").toString());
                }
                if (myObject.has("powerProduction")) {
                    updateChannel(LIVE_POWERPRODUCTION, myObject.get("powerProduction").toString());
                }
                if (myObject.has("accumulatedProduction")) {
                    updateChannel(LIVE_ACCUMULATEDPRODUCTION, myObject.get("accumulatedProduction").toString());
                }
                if (myObject.has("minPowerProduction")) {
                    updateChannel(LIVE_MINPOWERPRODUCTION, myObject.get("minPowerProduction").toString());
                }
                if (myObject.has("maxPowerProduction")) {
                    updateChannel(LIVE_MAXPOWERPRODUCTION, myObject.get("maxPowerProduction").toString());
                }
            } else {
                logger.debug("Unknown live response from Tibber");
            }
        }

        private void sendMessage(String message) throws IOException {
            logger.debug("Send message: {}", message);
            Session session = TibberHandler.this.session;
            if (session != null) {
                session.getRemote().sendString(message);
            }
        }

        public void startSubscription() {
            String query = "{\"id\":\"1\",\"type\":\"start\",\"payload\":{\"variables\":{},\"extensions\":{},\"operationName\":null,\"query\":\"subscription {\\n liveMeasurement(homeId:\\\""
                    + tibberConfig.getHomeid()
                    + "\\\") {\\n timestamp\\n power\\n lastMeterConsumption\\n accumulatedConsumption\\n accumulatedCost\\n currency\\n minPower\\n averagePower\\n maxPower\\n"
                    + "voltagePhase1\\n voltagePhase2\\n voltagePhase3\\n currentL1\\n currentL2\\n currentPhase3\\n powerProduction\\n accumulatedProduction\\n minPowerProduction\\n maxPowerProduction\\n }\\n }\\n\"}}";
            try {
                TibberWebSocketListener socket = TibberHandler.this.socket;
                if (socket != null) {
                    socket.sendMessage(query);
                } else {
                    logger.debug("Socket unable to send subscription message: Socket is null");
                }
            } catch (IOException e) {
                logger.warn("Send Message Exception: {}", e.getMessage());
            }
        }
    }
}
