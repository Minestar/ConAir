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

import java.io.IOException;
import java.util.List;

import de.minestar.conair.network.server.api.EventListener;
import de.minestar.conair.network.server.api.PluginManager;
import de.minestar.conair.network.server.api.ServerPlugin;

public class DedicatedTCPServer {

    private static final String DEFAULT_PLUGINFOLDER = "plugins" + System.getProperty("file.separator");

    private int _port;
    private TCPServer _server;
    private Thread _serverThread;
    private PluginManager _pluginManager;

    public DedicatedTCPServer(int port) throws IOException {
        this(port, DEFAULT_PLUGINFOLDER);
    }

    public DedicatedTCPServer(int port, String pluginFolder) throws IOException {
        this(port, null, pluginFolder);
    }

    public DedicatedTCPServer(int port, List<String> whiteList) throws IOException {
        this(port, whiteList, DEFAULT_PLUGINFOLDER);
    }

    public DedicatedTCPServer(int port, List<String> whiteList, String pluginFolder) throws IOException {
        try {
            _port = port;
            _server = new TCPServer(port, whiteList);

            if (!pluginFolder.endsWith(System.getProperty("file.separator"))) {
                pluginFolder += System.getProperty("file.separator");
            }

            // load plugins
            _pluginManager = new PluginManager(this, pluginFolder);
            _pluginManager.loadPlugins();

            _server.setPluginManager(_pluginManager);

            // start Thread
            _serverThread = new Thread(_server);
            _serverThread.start();
        } catch (Exception e) {
            stop();
            throw e;
        }
    }

    @SuppressWarnings("deprecation")
    public void stop() {
        if (_server != null) {
            // disable plugins
            _pluginManager.disablePlugins();

            // stop the server
            _server.stop();
            _server = null;
            if (_serverThread != null) {
                _serverThread.stop();
                _serverThread = null;
            }
        }
    }

    public void registerListener(EventListener eventListener, ServerPlugin serverPlugin) {
        _pluginManager.registerEvents(eventListener, serverPlugin);
    }

    public int getPort() {
        return _port;
    }
}
