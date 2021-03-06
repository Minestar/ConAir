/*
 * Copyright (C) 2013 MineStar.de 
 * 
 * This file is part of ConAir.
 * 
 * ConAir is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * 
 * ConAir is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ConAir.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.minestar.conair.network.server;

import java.util.HashMap;
import java.util.Map;

import de.minestar.conair.network.PacketType;
import de.minestar.conair.network.packets.NetworkPacket;
import de.minestar.conair.network.packets.RegisterDenyPacket;
import de.minestar.conair.network.packets.RegisterOKPacket;
import de.minestar.conair.network.packets.RegisterRequestPacket;
import de.minestar.conair.network.server.api.PluginManager;

public class ServerSidePacketHandler extends AbstractServerPacketHandler {

    private final HashMap<String, ConnectedServerClient> _registeredClients;
    private PluginManager _pluginManager;

    public ServerSidePacketHandler() {
        _registeredClients = new HashMap<String, ConnectedServerClient>();
    }

    public void setPluginManager(PluginManager pluginManager) {
        _pluginManager = pluginManager;
    }

    public PluginManager getPluginManager() {
        return _pluginManager;
    }

    @Override
    public <P extends NetworkPacket> boolean handlePacket(ConnectedServerClient client, P packet) {
        if (packet.getPacketID() == PacketType.getID(RegisterRequestPacket.class)) {
            handleRegisterRequestPacket(client, (RegisterRequestPacket) packet);
            return true;
        }
        return false;
    }

    private void registerClient(ConnectedServerClient client, String clientName) {
        _registeredClients.put(clientName, client);
    }

    public void unregisterClient(ConnectedServerClient client) {
        String toRemoveClient = null;

        // find the client
        for (Map.Entry<String, ConnectedServerClient> entry : _registeredClients.entrySet()) {
            if (entry.getValue().getName().equalsIgnoreCase(client.getName())) {
                toRemoveClient = entry.getKey();
                break;
            }
        }

        // remove the client, if we have found one
        if (toRemoveClient != null) {
            _registeredClients.remove(toRemoveClient);
        }
    }

    public boolean isClientRegistered(String clientName) {
        return _registeredClients.containsKey(clientName);
    }

    private void handleRegisterRequestPacket(ConnectedServerClient client, RegisterRequestPacket packet) {
        if (!isClientRegistered(packet.getClientName())) {
            registerClient(client, packet.getClientName());
            client.sendPacket(new RegisterOKPacket(packet.getClientName()));
        } else {
            System.out.println("Client '" + packet.getClientName() + "' is already registered!");
            client.sendPacket(new RegisterDenyPacket(packet.getClientName()));
        }
    }
}
