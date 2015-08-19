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

package de.minestar.conair.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.CorruptedFrameException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.minestar.conair.api.Packet;
import de.minestar.conair.api.event.Listener;
import de.minestar.conair.api.event.RegisterEvent;
import de.minestar.conair.common.ConAirMember;
import de.minestar.conair.common.PacketSender;
import de.minestar.conair.common.event.EventExecutor;
import de.minestar.conair.common.event.listener.ProgressListener;
import de.minestar.conair.common.packets.SplittedPacket;
import de.minestar.conair.common.packets.SplittedPacketHandler;
import de.minestar.conair.common.packets.WrappedPacket;


class ConAirServerHandler extends SimpleChannelInboundHandler<WrappedPacket> {

    private final ConAirServer _server;
    private final SplittedPacketHandler _splittedPacketHandler;

    private final Set<String> _registeredPacketClasses;
    private final Map<Class<? extends Packet>, Map<Class<? extends Listener>, EventExecutor>> _registeredPacketListeners;
    private final Map<String, List<ProgressListener>> _progressListeners;


    ConAirServerHandler(final ConAirServer server) {
        _server = server;
        _registeredPacketListeners = Collections.synchronizedMap(new HashMap<>());
        _registeredPacketClasses = Collections.synchronizedSet(new HashSet<>());
        _progressListeners = Collections.synchronizedMap(new HashMap<>());
        _splittedPacketHandler = new SplittedPacketHandler();
    }


    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        // Mark channel as not initialized - waiting for handshake
        ctx.channel().attr(ConAirServerAttributes.HANDSHAKE_COMPLETED).getAndSet(Boolean.FALSE);
    }


    /**
     * Method is invoked, when a client sends a packet to the server
     */
    @SuppressWarnings("unchecked")
    @Override
    public void channelRead0(ChannelHandlerContext ctx, WrappedPacket wrappedPacket) throws Exception {

        // handle splitted packets
        if (wrappedPacket.getPacketClassName().equals(SplittedPacket.class.getName())) {
            Optional<SplittedPacket> optionalPacket = wrappedPacket.getPacket(_server._pluginManagerFactory);

            // handle progress listeners
            SplittedPacket splittedPacket = optionalPacket.get();
            if (_registeredPacketClasses.contains(splittedPacket.getPacketClass())) {
                final List<ProgressListener> list = _progressListeners.get(splittedPacket.getPacketClass());
                if (list != null) {
                    for (final ProgressListener listener : list) {
                        try {
                            listener.onProgress((Class<? extends Packet>) _server._pluginManagerFactory.classForName(splittedPacket.getPacketClass()), splittedPacket.getCurrentPacketId(), splittedPacket.getTotalPackets());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            final WrappedPacket reconstructedPacket = _splittedPacketHandler.handle(wrappedPacket, optionalPacket, _server._pluginManagerFactory);
            if (reconstructedPacket != null) {
                if (wrappedPacket.getTargets().isEmpty() || reconstructedPacket.getTargets().contains(_server.getName())) {
                    // Returns true, if the packet is handled ONLY by the server
                    handleServerPacket(ctx, reconstructedPacket);
                }
            }
        }

        // handle packets dedicated for the server
        if (wrappedPacket.getTargets().isEmpty() || wrappedPacket.getTargets().contains(_server.getName())) {
            // Returns true, if the packet is handled ONLY by the server
            handleServerPacket(ctx, wrappedPacket);
            if (wrappedPacket.getTargets().size() == 1) {
                return;
            }
        }

        // Wrap the packet and store the source name (maybe useful for target to
        // know, who the source is)
        WrappedPacket packet = WrappedPacket.rePack(wrappedPacket, wrappedPacket.getSource(), getClientName(ctx.channel()));
        // Broadcast packet - except for the channel, which is the sender of the
        // packet and which haven't finished handhake with the server.
        if (wrappedPacket.getTargets().isEmpty()) {
            for (Channel target : _server._clientChannels) {
                if (target != ctx.channel()) {
                    target.writeAndFlush(packet);
                }
            }
        } else {
            // Send packet to designated clients
            for (Channel target : _server._clientChannels) {
                if (target != ctx.channel() && wrappedPacket.getTargets().contains(getClientName(target))) {
                    target.writeAndFlush(packet);
                }
            }
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof CorruptedFrameException) {
            System.err.println("Invalid packet received!");
        }
        cause.printStackTrace();
        ctx.close();
    }


    private boolean handleServerPacket(ChannelHandlerContext ctx, WrappedPacket wrappedPacket) {
        if (!_registeredPacketClasses.contains(wrappedPacket.getPacketClassName())) {
            // The packet is not registered
            return false;
        }

        Optional<Packet> result = wrappedPacket.getPacket(_server._pluginManagerFactory);
        if (!result.isPresent()) {
            System.err.println("Error while parsing " + wrappedPacket + "!");
            return true;
        }
        // Inform listener
        Packet packet = result.get();
        Map<Class<? extends Listener>, EventExecutor> map = _registeredPacketListeners.get(packet.getClass());
        if (map != null) {
            for (final EventExecutor executor : map.values()) {
                executor.execute(_server, _server.getMember(wrappedPacket.getSource()), packet);
            }
        }
        return true;
    }


    private String getClientName(Channel channel) {
        return channel.attr(ConAirServerAttributes.CLIENT_NAME).get();
    }


    <L extends Listener> void registerPacketListener(L listener) {
        final Method[] declaredMethods = listener.getClass().getDeclaredMethods();
        for (final Method method : declaredMethods) {
            // ignore static methods & we need exactly three params and a public method
            if (Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 3) {
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
                _registeredPacketClasses.add(packetClass.getName());

                // register the EventExecutor
                Map<Class<? extends Listener>, EventExecutor> map = _registeredPacketListeners.get(packetClass);
                if (map == null) {
                    map = Collections.synchronizedMap(new HashMap<>());
                    _registeredPacketListeners.put(packetClass, map);
                }
                map.put(listener.getClass(), new EventExecutor(listener, method));
            }
        }
    }


    <L extends Listener> void unregisterPacketListener(Class<L> listenerClass) {
        final Method[] declaredMethods = listenerClass.getDeclaredMethods();
        for (final Method method : declaredMethods) {
            // ignore static methods & we need exactly three params and a public method
            if (Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 3) {
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

                // register the EventExecutor
                Map<Class<? extends Listener>, EventExecutor> map = _registeredPacketListeners.get(packetClass);
                if (map != null) {
                    map.remove(listenerClass);
                }
            }
        }
    }


    <P extends Packet> void registerProgressListener(Class<P> packetClass, ProgressListener listener) {
        List<ProgressListener> list = _progressListeners.get(packetClass.getName());
        if (list == null) {
            list = new ArrayList<>();
            _progressListeners.put(packetClass.getName(), list);
        }
        list.add(listener);
    }


    <P extends Packet> void unregisterProgressListener(Class<P> packetClass, ProgressListener listener) {
        List<ProgressListener> list = _progressListeners.get(packetClass.getName());
        if (list != null) {
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i).equals(listener)) {
                    list.remove(i);
                }
            }
            if (list.isEmpty()) {
                _progressListeners.remove(packetClass.getName());
            }
        }
    }
}
