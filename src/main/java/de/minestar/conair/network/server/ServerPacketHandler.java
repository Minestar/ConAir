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

import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;

import de.minestar.conair.network.PacketBuffer;
import de.minestar.conair.network.PacketType;
import de.minestar.conair.network.packets.NetworkPacket;
import de.minestar.conair.network.server.packets.RAWPacket;

public final class ServerPacketHandler {

    protected final PacketBuffer _packetBuffer;

    public static final byte PACKET_SEPERATOR = 3;

    public ServerPacketHandler(ByteBuffer buffer) {
        _packetBuffer = new PacketBuffer(buffer);
    }

    // //////////////////////////////////////////////////////////
    //
    // Both packets
    //
    // //////////////////////////////////////////////////////////

    public boolean isPacketComplete(ByteBuffer buffer) {
        buffer.flip();
        int len = 0;
        if (buffer.hasRemaining()) {
            len = buffer.getInt();
        } else {
            return false;
        }
        if (buffer.remaining() <= len - 4) {
            return false;
        }
        return (buffer.get(len) == PACKET_SEPERATOR);
    }

    public <P extends NetworkPacket> boolean packPacket(P packet) {
        _packetBuffer.clear();
        boolean result = packet.pack(_packetBuffer);
        _packetBuffer.getBuffer().flip();
        return result;
    }

    // //////////////////////////////////////////////////////////
    //
    // NetworkPacket
    //
    // //////////////////////////////////////////////////////////

    public final <P extends NetworkPacket> P extractPacket(ByteBuffer src) {
        src.rewind();
        int len = src.getInt();
        int limit = src.limit();
        src.limit(len);
        _packetBuffer.clear();
        _packetBuffer.writeByteBuffer(src);
        _packetBuffer.getBuffer().flip();
        src.limit(limit);
        src.compact();

        // create packet
        P packet = createPacket(len);

        // reset
        src.rewind();
        _packetBuffer.clear();

        // return
        return packet;
    }

    @SuppressWarnings("unchecked")
    private final <P extends NetworkPacket> P createPacket(int datalength) {
        try {
            // reduce the datalength, because we read two integers first.
            // One integer is 4 bytes long
            datalength -= 8;

            // get packettype
            int packetID = _packetBuffer.readInt();
            Class<P> packetClazz = PacketType.getClassByID(packetID);

            // packet not found...
            if (packetClazz == null) {
                // read data...
                byte[] data = new byte[datalength];
                _packetBuffer.readBytes(data);

                // ... and create a new PacketBuffer
                PacketBuffer newBuffer = new PacketBuffer(data.length);
                newBuffer.writeBytes(data);
                newBuffer.getBuffer().rewind();

                // finally create the packet and return it
                return (P) new RAWPacket(packetID, newBuffer.getBuffer());
            }

            // get the constructor
            Constructor<P> packetConstructor = packetClazz.getDeclaredConstructor(int.class, PacketBuffer.class);
            if (packetConstructor == null) {
                return null;
            }

            // read data...
            byte[] data = new byte[datalength];
            _packetBuffer.readBytes(data);

            // ... and create a new PacketBuffer
            PacketBuffer newBuffer = new PacketBuffer(data.length);
            newBuffer.writeBytes(data);
            newBuffer.getBuffer().rewind();

            // finally create the packet and return it
            return packetConstructor.newInstance(packetID, newBuffer);
        } catch (Exception e) {
            return null;
        }
    }

    // //////////////////////////////////////////////////////////
    //
    // NetworkPacket
    //
    // //////////////////////////////////////////////////////////
}
