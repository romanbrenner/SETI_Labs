package com.example.socks;

import org.xbill.DNS.*;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

public class SocksProxy {
    private static final int BUFFER_SIZE = 32 * 1024;
    private static final int DNS_BUFFER_SIZE = 2048;

    private final int port;
    private final Selector selector;
    private final ServerSocketChannel serverChannel;
    private final DatagramChannel dnsChannel;
    private final InetSocketAddress dnsResolver;
    private final Map<SocketChannel, SocksSession> sessions = new HashMap<>();
    private final Map<Integer, SocksSession> dnsPending = new HashMap<>();
    private final Random random = new Random();

    public SocksProxy(int port) throws IOException {
        this.port = port;
        this.selector = Selector.open();

        this.serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        this.dnsChannel = DatagramChannel.open();
        dnsChannel.configureBlocking(false);
        this.dnsResolver = new InetSocketAddress("8.8.8.8", 53);
        dnsChannel.register(selector, SelectionKey.OP_READ);

        System.out.println("SOCKS5 proxy listening on port " + port);
    }

    public void start() throws IOException {
        while (true) {
            selector.select();
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();
                try {
                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isAcceptable()) {
                        handleAccept();
                    } else if (key.channel() == dnsChannel && key.isReadable()) {
                        handleDnsRead();
                    } else {
                        Object att = key.attachment();
                        if (att instanceof SocksSession) {
                            SocksSession session = (SocksSession) att;
                            if (key.isConnectable()) {
                                session.handleRemoteConnect();
                                if (!key.isValid()) continue;
                            }
                            if (key.isReadable()) {
                                if (key.channel() == session.getClientChannel()) {
                                    session.handleClientRead();
                                } else {
                                    session.handleRemoteRead();
                                }
                                if (!key.isValid()) continue;
                            }
                            if (key.isWritable() && key.isValid()) {
                                if (key.channel() == session.getClientChannel()) {
                                    session.handleClientWrite();
                                } else {
                                    session.handleRemoteWrite();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    closeKey(key);
                }
            }
        }
    }

    private void handleAccept() throws IOException {
        SocketChannel client = serverChannel.accept();
        if (client == null) {
            return;
        }
        client.configureBlocking(false);
        SelectionKey key = client.register(selector, SelectionKey.OP_READ);
        SocksSession session = new SocksSession(this, client);
        sessions.put(client, session);
        key.attach(session);
        System.out.println("Accepted client " + client.getRemoteAddress());
    }

    private void handleDnsRead() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(DNS_BUFFER_SIZE);
        SocketAddress from = dnsChannel.receive(buf);
        if (from == null) return;
        buf.flip();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        try {
            Message response = new Message(data);
            int id = response.getHeader().getID();
            SocksSession session = dnsPending.remove(id);
            if (session == null) {
                return;
            }
            org.xbill.DNS.Record[] answers = response.getSectionArray(Section.ANSWER);
            InetAddress ipv4 = null;
            for (org.xbill.DNS.Record r : answers) {
                if (r instanceof ARecord) {
                    ipv4 = ((ARecord) r).getAddress();
                    break;
                }
            }
            if (ipv4 == null) {
                session.failConnect((byte) 0x04);
            } else {
                session.onResolved(ipv4);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void sendDnsQuery(SocksSession session, String host) throws IOException {
        Name name = Name.fromString(host.endsWith(".") ? host : host + ".");
        org.xbill.DNS.Record rec = org.xbill.DNS.Record.newRecord(name, Type.A, DClass.IN);
        Message query = Message.newQuery(rec);
        int id;
        do {
            id = random.nextInt(0xFFFF);
        } while (dnsPending.containsKey(id));
        query.getHeader().setID(id);
        byte[] payload = query.toWire();
        ByteBuffer buf = ByteBuffer.wrap(payload);
        dnsPending.put(id, session);
        dnsChannel.send(buf, dnsResolver);
        System.out.println("DNS query for " + host + " id=" + id);
    }

    void registerRemote(SocksSession session, SocketChannel remote) throws ClosedChannelException {
        remote.register(selector, SelectionKey.OP_CONNECT, session);
    }

    void updateInterests(SocketChannel ch, int ops) {
        SelectionKey key = ch.keyFor(selector);
        if (key != null && key.isValid()) {
            key.interestOps(ops);
        }
    }

    void closeSession(SocksSession session) {
        sessions.remove(session.getClientChannel());
        dnsPending.values().removeIf(s -> s == session);
        session.close();
    }

    private void closeKey(SelectionKey key) {
        try {
            key.channel().close();
        } catch (IOException ignored) {
        }
        key.cancel();
    }
}
