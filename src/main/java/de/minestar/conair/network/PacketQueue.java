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

package de.minestar.conair.network;

import java.util.concurrent.ConcurrentLinkedQueue;

public final class PacketQueue {

    private ConcurrentLinkedQueue<NetworkPacket> packetQueue;
    private NetworkPacket activePacket;
    private boolean active;

    public PacketQueue() {
        this.packetQueue = new ConcurrentLinkedQueue<NetworkPacket>();
        this.activePacket = null;
        this.active = false;
    }

    public boolean addPacket(NetworkPacket packet) {
        synchronized (this.packetQueue) {
            if (PacketType.getID(packet.getClass()) != null) {
                this.packetQueue.add(packet);
                return true;
            }
            return false;
        }
    }

    public boolean updateQueue() {
        synchronized (this.packetQueue) {
            if (this.packetQueue.isEmpty()) {
                return false;
            }
            this.activePacket = this.packetQueue.poll();
            this.active = (this.activePacket != null);
            return this.active;
        }
    }

    public boolean isActive() {
        return active;
    }

    public NetworkPacket getActivePacket() {
        return activePacket;
    }

    public boolean packPacket(PacketBuffer packetBuffer) {
        if (this.isActive()) {
            packetBuffer.clear();
            boolean result = this.activePacket.pack(packetBuffer);
            packetBuffer.getBuffer().flip();
            return result;
        }
        return false;
    }

    public int getSize() {
        return this.packetQueue.size();
    }

}