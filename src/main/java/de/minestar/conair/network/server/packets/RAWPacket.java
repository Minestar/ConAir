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

package de.minestar.conair.network.server.packets;

import java.nio.ByteBuffer;

import de.minestar.conair.network.PacketBuffer;
import de.minestar.conair.network.packets.NetworkPacket;

public final class RAWPacket extends NetworkPacket {

    private transient PacketBuffer _dataBuffer;
    private transient int _packetID = -1;

    public RAWPacket(int packetID, ByteBuffer buffer) {
        _packetID = packetID;
        _dataBuffer = new PacketBuffer(buffer.capacity());
        _dataBuffer.writeByteBuffer(buffer);
        _dataBuffer.getBuffer().flip();
    }

    public final boolean pack(PacketBuffer buffer) {
        buffer.writeInt(0); // Size
        buffer.writeInt(_packetID); // Type
        buffer.writeByteBuffer(_dataBuffer.getBuffer()); // Content
        buffer.writeInt(0, buffer.getBuffer().position()); // Write size
        buffer.put(NetworkPacket.PACKET_SEPERATOR); // Close packet
        return true;
    }

    @Override
    public boolean isBroadcastPacket() {
        return true;
    }
}
