/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.network.internal.dhcp;

import java.net.SocketException;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A singleton. IPRequestReceivedCallback objects can register and unregister.
 * If the first one is registered and there is no singleton instance, an instance will be created and the
 * receiver thread will be started. If the last IPRequestReceivedCallback is removed, the thread will be stopped
 * after the receive socket is closed.
 * IPRequestReceivedCallback will be called for the address that is registered and matches the
 * DHO_DHCP_REQUESTED_ADDRESS address field.
 *
 * @author David Graeff - Initial contribution
 */
@NonNullByDefault
public class DHCPListenService {
    static @Nullable DHCPPacketListenerServer instance;
    static Map<String, IPRequestReceivedCallback> registeredListeners = new TreeMap<>();
    static Logger logger = LoggerFactory.getLogger(DHCPListenService.class);

    @SuppressWarnings({ "null", "unused" })
    public static synchronized DHCPPacketListenerServer register(String hostAddress,
            IPRequestReceivedCallback dhcpListener) throws SocketException {
        DHCPPacketListenerServer instance = DHCPListenService.instance;
        if (instance == null) {
            instance = new DHCPPacketListenerServer((String ipAddress) -> {
                IPRequestReceivedCallback listener = registeredListeners.get(ipAddress);
                if (listener != null) {
                    listener.dhcpRequestReceived(ipAddress);
                    logger.info("DHCP/ptro request for known address: {}", ipAddress);
                } else {
                    logger.info("DHCP/ptro request for unknown address: {}", ipAddress);
                }
            });
            DHCPListenService.instance = instance;
            instance.start();
        }
        synchronized (registeredListeners) {
            registeredListeners.put(hostAddress, dhcpListener);
        }
        return instance;
    }

    public static void unregister(String hostAddress) {
        synchronized (registeredListeners) {
            registeredListeners.remove(hostAddress);
            if (!registeredListeners.isEmpty()) {
                return;
            }
        }

        final DHCPPacketListenerServer instance = DHCPListenService.instance;
        if (instance != null) {
            instance.close();
        }
        DHCPListenService.instance = null;
    }
}
