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
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import de.minestar.conair.network.PacketBuffer;
import de.minestar.conair.network.PacketQueue;
import de.minestar.conair.network.packets.NetworkPacket;

public final class ConnectedServerClient {

    private final PacketBuffer _inBuffer = new PacketBuffer(ByteBuffer.allocateDirect(128 * 1024));
    private final PacketBuffer _outBuffer = new PacketBuffer(ByteBuffer.allocateDirect(128 * 1024));

    private String _name;
    private boolean _dataToSend = false;
    private final PacketQueue _packetQueue;

    public ConnectedServerClient(String name) {
        _name = name;
        _packetQueue = new PacketQueue();
    }

    public <P extends NetworkPacket> void sendPacket(P packet) {
        _packetQueue.addUnsafePacket(packet);
        boolean wasEmpty = _packetQueue.getSize() == 1;
        // the queue was empty, so we send the first packet
        if (wasEmpty) {
            if (_packetQueue.updateQueue()) {
                _packetQueue.packPacket(_outBuffer);
                _dataToSend = true;
            }
        }
    }
    public String getName() {
        return _name;
    }

    boolean hasDataToSend() {
        return _dataToSend;
    }

    boolean readFrom(SocketChannel channel) throws Exception {
        int b = 0;
        try {
            b = channel.read(_inBuffer.getBuffer());
        } catch (IOException e) {
            return false;
        }
        return b != -1;
    }

    boolean write(SocketChannel channel) throws IOException {
        int b = 0;
        try {
            b = channel.write(_outBuffer.getBuffer());
        } catch (IOException e) {
            return false;
        }
        if (b == 0) {
            _dataToSend = false;
            _outBuffer.clear();

            if (_packetQueue.updateQueue()) {
                _packetQueue.packPacket(_outBuffer);
                _dataToSend = true;
            }
        }
        return b != -1;
    }

    ByteBuffer getClientBuffer() {
        return _inBuffer.getBuffer();
    }

}
