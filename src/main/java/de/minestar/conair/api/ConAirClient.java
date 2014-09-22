/**
 * The MIT License (MIT)
 * 
 * Copyright (c) 2014 Minestar.de
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.minestar.conair.api;

import java.util.function.BiConsumer;

/**
 * Describe what a ConAir client must can.
 */
public interface ConAirClient {

    /**
     * Establish a connection to ConAir server. Must be called before
     * {@link #sendPacket(Packet)} can get used.
     * 
     * @param clientName
     *            The unique name of this client to identify itself in the
     *            ConAir network, for example "Main" or "ModServer"
     * @param host
     *            The address of the ConAir server as an IP or domain.
     * @param port
     *            The port of the TCP socket.
     * @throws Exception
     *             Something went wrong
     */
    public void connect(String clientName, String host, int port) throws Exception;

    /**
     * Send a packet to the ConAir server, who will deliver the packet to the
     * targets. If targets are empty, the packet will be broadcasted to every
     * registered client, but not this client.
     * 
     * @param packet
     *            The data to send.
     * @param targets
     *            The target
     * @throws Exception
     *             Something went wrong
     */
    public void sendPacket(Packet packet, String... targets) throws Exception;

    /**
     * Register listener for a Packet type to receive and handle.
     * 
     * @param packetClass
     *            The class of the packet this listener registers to
     * @param handler
     *            Packet handler for this type
     */
    public <T extends Packet> void registerPacketListener(Class<T> packetClass, BiConsumer<T, String> handler);

    /**
     * Disconnects from the ConAir server and close connection.
     * 
     * @throws Exception
     *             Something went wrong.
     */
    public void disconnect() throws Exception;

}
