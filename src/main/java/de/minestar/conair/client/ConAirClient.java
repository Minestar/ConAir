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

package de.minestar.conair.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.minestar.conair.api.ConAir;
import de.minestar.conair.api.ConAirMember;
import de.minestar.conair.api.Packet;
import de.minestar.conair.api.PacketSender;
import de.minestar.conair.api.SmallPacketHandler;
import de.minestar.conair.api.WrappedPacket;
import de.minestar.conair.api.codec.JsonDecoder;
import de.minestar.conair.api.codec.JsonEncoder;
import de.minestar.conair.api.codec.JsonFrameDecoder;
import de.minestar.conair.api.event.EventExecutor;
import de.minestar.conair.api.event.Listener;
import de.minestar.conair.api.event.RegisterEvent;
import de.minestar.conair.api.packets.ConnectedClientsPacket;
import de.minestar.conair.api.packets.ConnectionPacket;
import de.minestar.conair.api.packets.HandshakePacket;
import de.minestar.conair.api.packets.SmallPacket;

public class ConAirClient implements PacketSender {

    private boolean isConnected;

    private final EventLoopGroup group;
    private Channel channel;

    private Set<String> registeredClasses;
    private Map<Class<? extends Packet>, Map<Class<? extends Listener>, EventExecutor>> registeredListener;
    private ConAirMember clientName;
    private Map<String, ConAirMember> _conAirMembers;

    private SmallPacketHandler smallPacketHandler;

    public ConAirClient(String clientName) {
        this.clientName = new ConAirMember(clientName.replaceAll("\"", ""));
        this.isConnected = false;
        this.group = new NioEventLoopGroup();
        this.registeredListener = Collections.synchronizedMap(new HashMap<>());
        this.registeredClasses = Collections.synchronizedSet(new HashSet<>());
        this.smallPacketHandler = new SmallPacketHandler();
        this._conAirMembers = Collections.synchronizedMap(new HashMap<>());
    }

    public ConAirMember get() {
        return clientName;
    }

    /**
     * Establish a connection to ConAir server. Must be called before {@link #sendPacket(Packet)} can get used.
     * 
     * @param clientName
     *            The unique name of this client to identify itself in the ConAir network, for example "Main" or "ModServer"
     * @param host
     *            The address of the ConAir server as an IP or domain.
     * @param port
     *            The port of the TCP socket.
     * @throws Exception
     *             Something went wrong
     */
    public void connect(String host, int port) throws Exception {
        if (isConnected) {
            throw new IllegalStateException("Client is already connected!");
        }
        Bootstrap bootStrap = new Bootstrap();
        bootStrap.group(group).channel(NioSocketChannel.class);
        // Add initializer
        bootStrap.handler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();

                // Wait until the buffer contains the complete JSON object
                pipeline.addLast("frameDecoder", new JsonFrameDecoder(16 * 1024));

                // Decode and encode the buffer bytes arrays to readable strings
                pipeline.addLast("stringDecoder", new StringDecoder(CharsetUtil.UTF_8));
                pipeline.addLast("stringEncoder", new StringEncoder(CharsetUtil.UTF_8));

                // Decode and encode the readable JSON strings as WrappedPacket
                // objects
                pipeline.addLast("jsonDecoder", new JsonDecoder());
                pipeline.addLast("jsonEncoder", new JsonEncoder());

                // And then the logic itself
                pipeline.addLast(new PluginConAirClientHandler());
            }
        });

        channel = bootStrap.connect(host, port).sync().channel();

        // Register at server with unique name
        _conAirMembers.put(ConAir.SERVER.getName(), ConAir.SERVER);
        sendPacket(new HandshakePacket(this.clientName.getName()), ConAir.SERVER);
        isConnected = true;
        this.registerPacketListener(new ClientConnectionListener(this));
    }

    public boolean isConnected() {
        return isConnected;
    }

    private class PluginConAirClientHandler extends SimpleChannelInboundHandler<WrappedPacket> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, WrappedPacket wrappedPacket) throws Exception {
            // handle splitted packets
            if (wrappedPacket.getPacketClassName().equals(SmallPacket.class.getName())) {
                final WrappedPacket reconstructedPacket = smallPacketHandler.handle(wrappedPacket, (SmallPacket) wrappedPacket.getPacket().get());
                if (reconstructedPacket != null) {
                    wrappedPacket = reconstructedPacket;
                }
            }

            onPacketReceived(wrappedPacket);
        }
    }

    private void onPacketReceived(WrappedPacket wrappedPacket) {
        if (!registeredClasses.contains(wrappedPacket.getPacketClassName())) {
            // The packet is not registered in this client
            return;
        }
        Optional<Packet> result = wrappedPacket.getPacket();
        if (!result.isPresent()) {
            System.err.println("Error while parsing " + wrappedPacket + "!");
            return;
        }
        // Inform listener
        Packet packet = result.get();
        Map<Class<? extends Listener>, EventExecutor> map = registeredListener.get(packet.getClass());
        if (map != null) {
            for (final EventExecutor executor : map.values()) {
                executor.execute(this, getMember(wrappedPacket.getSource()), packet);
            }
        }
    }

    /**
     * Send a packet to the ConAir server, who will deliver the packet to the targets. If targets are empty, the packet will be broadcasted to every registered client, but not this client.
     * 
     * @param packet
     *            The data to send.
     * @param targets
     *            The target
     * @throws Exception
     *             Something went wrong
     */
    @Override
    public void sendPacket(Packet packet, ConAirMember... targets) throws Exception {
        List<WrappedPacket> packetList = WrappedPacket.create(packet, clientName, targets);
        for (final WrappedPacket wrappedPacket : packetList) {
            ChannelFuture result = channel.writeAndFlush(wrappedPacket);
            if (result != null) {
                result.sync();
            }
        }
    }

    public <L extends Listener> void registerPacketListener(L listener) {
        final Method[] declaredMethods = listener.getClass().getDeclaredMethods();
        for (final Method method : declaredMethods) {
            // ignore static methods & we need exactly two params
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 3) {
                continue;
            }

            // we need an annotation
            if (method.getAnnotation(RegisterEvent.class) == null) {
                continue;
            }

            // accept the filter if it is true
            if (PacketSender.class.isAssignableFrom(method.getParameterTypes()[0]) && ConAirMember.class.isAssignableFrom(method.getParameterTypes()[1]) && Packet.class.isAssignableFrom(method.getParameterTypes()[2])) {
                @SuppressWarnings("unchecked")
                Class<? extends Packet> packetClass = (Class<? extends Packet>) method.getParameterTypes()[2];

                // register the packet class
                registeredClasses.add(packetClass.getName());

                // register the EventExecutor
                Map<Class<? extends Listener>, EventExecutor> map = registeredListener.get(packetClass);
                if (map == null) {
                    map = Collections.synchronizedMap(new HashMap<>());
                    registeredListener.put(packetClass, map);
                }
                map.put(listener.getClass(), new EventExecutor(listener, method));
            }
        }
    }

    /**
     * Disconnects from the ConAir server and close connection.
     * 
     * @throws Exception
     *             Something went wrong.
     */
    public void disconnect() throws Exception {
        if (!isConnected) {
            throw new IllegalStateException("Client is not connected!");
        }
        channel.close().sync();
        group.shutdownGracefully().sync();
        isConnected = false;
    }

    private static class ClientConnectionListener implements Listener {

        private ConAirClient _client;

        ClientConnectionListener(final ConAirClient client) {
            _client = client;
        }

        @RegisterEvent
        public void onConnectionPacket(final PacketSender receiver, final ConAirMember source, final ConnectionPacket packet) {
            if (packet.isConnect()) {
                _client._conAirMembers.put(packet.getClientName(), new ConAirMember(packet.getClientName()));
            } else {
                _client._conAirMembers.remove(packet.getClientName());
            }
        }

        @RegisterEvent
        public void onConnectedClientsPacket(final PacketSender receiver, final ConAirMember source, final ConnectedClientsPacket packet) {
            for (String clientName : packet.getConnectedClients()) {
                _client._conAirMembers.put(clientName, new ConAirMember(clientName));
            }
        }
    }

    public ConAirMember getMember(final String name) throws IllegalArgumentException {
        if (!_conAirMembers.containsKey(name)) {
            throw new IllegalArgumentException("Member '" + name + "' not found! (" + clientName + ") ");
        }
        return _conAirMembers.get(name);
    }

}