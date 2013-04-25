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

package de.minestar.conair.network.packets;

import de.minestar.conair.network.NetworkPacket;
import de.minestar.conair.network.PacketBuffer;
import de.minestar.conair.network.PacketType;

public class HelloWorldPacket extends NetworkPacket {

    private String text;

    public HelloWorldPacket(String text) {
        super(PacketType.HelloWorld);
        this.text = text;
    }

    public HelloWorldPacket(PacketBuffer buffer) {
        super(PacketType.HelloWorld, buffer);
    }

    @Override
    public void onSend(PacketBuffer buffer) {
        buffer.putString(this.text);
    }

    @Override
    public void onReceive(PacketBuffer buffer) {
        this.text = buffer.getString();

    }

    public String getText() {
        return text;
    }

}
