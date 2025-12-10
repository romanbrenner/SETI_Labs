package com.example.socks;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

/**
 * Состояние одного SOCKS-клиента и логика протокола/прокачки данных.
 */
public class SocksSession {
    private enum State {HANDSHAKE, REQUEST, RESOLVING, CONNECTING, RELAY, CLOSED}

    private final SocksProxy proxy;
    private final SocketChannel client;
    private SocketChannel remote;
    private State state = State.HANDSHAKE;

    // Буфер для протокольных сообщений клиента (handshake/request)
    private final ByteBuffer protoBuf = ByteBuffer.allocate(1024);
    // Буферы для прокачки трафика
    private final ByteBuffer toRemote = ByteBuffer.allocate(32 * 1024);
    private final ByteBuffer toClient = ByteBuffer.allocate(32 * 1024);
    private final ByteBuffer ioBuf = ByteBuffer.allocate(8192);

    private String pendingHost;
    private int pendingPort;
    private boolean clientInputClosed = false;
    private boolean remoteInputClosed = false;

    public SocksSession(SocksProxy proxy, SocketChannel client) {
        this.proxy = proxy;
        this.client = client;
    }

    public SocketChannel getClientChannel() {
        return client;
    }

    public void handleClientRead() throws IOException {
        if (state == State.HANDSHAKE || state == State.REQUEST) {
            int read = client.read(protoBuf);
            if (read == -1) {
                close();
                return;
            }
            if (state == State.HANDSHAKE) {
                tryHandleHandshake();
            } else {
                tryHandleRequest();
            }
            return;
        }

        if (state != State.RELAY || remote == null) {
            close();
            return;
        }

        int read = readIntoBuffer(client, toRemote);
        if (read == -1) {
            clientInputClosed = true;
            shutdownRemoteOutput();
            checkClose();
            return;
        }
        if (toRemote.position() > 0) {
            proxy.updateInterests(remote, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        }
    }

    public void handleClientWrite() throws IOException {
        if (!client.isOpen()) return;
        if (flushBuffer(client, toClient)) {
            proxy.closeSession(this);
            return;
        }
        adjustInterests();
    }

    public void handleRemoteConnect() throws IOException {
        if (remote == null) return;
        if (remote.finishConnect()) {
            sendSuccessReply();
            state = State.RELAY;
            adjustInterests();
        }
    }

    public void handleRemoteRead() throws IOException {
        int read = readIntoBuffer(remote, toClient);
        if (read == -1) {
            remoteInputClosed = true;
            shutdownClientOutput();
            checkClose();
            return;
        }
        if (toClient.position() > 0) {
            proxy.updateInterests(client, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        }
    }

    public void handleRemoteWrite() throws IOException {
        if (remote == null || !remote.isOpen()) return;
        if (flushBuffer(remote, toRemote)) {
            proxy.closeSession(this);
            return;
        }
        adjustInterests();
    }

    public void onResolved(InetAddress address) throws IOException {
        if (state != State.RESOLVING) return;
        InetSocketAddress target = new InetSocketAddress(address, pendingPort);
        connectRemote(target);
    }

    public void failConnect(byte rep) throws IOException {
        if (state == State.CLOSED) return;
        sendReply(rep, null);
        flushBuffer(client, toClient);
        close();
    }

    public void close() {
        state = State.CLOSED;
        try {
            client.close();
        } catch (IOException ignored) { }
        if (remote != null) {
            try {
                remote.close();
            } catch (IOException ignored) { }
        }
    }

    private void tryHandleHandshake() throws IOException {
        protoBuf.flip();
        if (protoBuf.remaining() < 2) {
            protoBuf.compact();
            return;
        }
        byte ver = protoBuf.get();
        byte nMethods = protoBuf.get();
        if (ver != 0x05) {
            close();
            return;
        }
        if (protoBuf.remaining() < nMethods) {
            protoBuf.position(protoBuf.position() - 2);
            protoBuf.compact();
            return;
        }
        boolean noAuth = false;
        for (int i = 0; i < nMethods; i++) {
            byte m = protoBuf.get();
            if (m == 0x00) noAuth = true;
        }
        protoBuf.clear();
        if (!noAuth) {
            byte[] resp = {0x05, (byte) 0xFF};
            toClient.put(resp);
            proxy.updateInterests(client, SelectionKey.OP_WRITE);
            close();
            return;
        }
        byte[] resp = {0x05, 0x00};
        toClient.put(resp);
        state = State.REQUEST;
        proxy.updateInterests(client, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    private void tryHandleRequest() throws IOException {
        protoBuf.flip();
        if (protoBuf.remaining() < 4) {
            protoBuf.compact();
            return;
        }
        protoBuf.mark();
        byte ver = protoBuf.get();
        byte cmd = protoBuf.get();
        protoBuf.get(); // RSV
        byte atyp = protoBuf.get();
        if (ver != 0x05 || cmd != 0x01) {
            sendReply((byte) 0x07, null); // command not supported
            close();
            return;
        }
        String host = null;
        InetAddress ipv4 = null;
        if (atyp == 0x01) { // IPv4
            if (protoBuf.remaining() < 4 + 2) {
                protoBuf.reset();
                protoBuf.compact();
                return;
            }
            byte[] addrBytes = new byte[4];
            protoBuf.get(addrBytes);
            ipv4 = InetAddress.getByAddress(addrBytes);
        } else if (atyp == 0x03) { // DOMAIN
            if (protoBuf.remaining() < 1) {
                protoBuf.reset();
                protoBuf.compact();
                return;
            }
            int len = protoBuf.get() & 0xFF;
            if (protoBuf.remaining() < len + 2) {
                protoBuf.reset();
                protoBuf.compact();
                return;
            }
            byte[] domainBytes = new byte[len];
            protoBuf.get(domainBytes);
            host = new String(domainBytes);
        } else {
            sendReply((byte) 0x08, null); // address type not supported
            close();
            return;
        }
        if (protoBuf.remaining() < 2) {
            protoBuf.reset();
            protoBuf.compact();
            return;
        }
        int port = ((protoBuf.get() & 0xFF) << 8) | (protoBuf.get() & 0xFF);
        protoBuf.clear();

        if (ipv4 != null) {
            connectRemote(new InetSocketAddress(ipv4, port));
        } else {
            pendingHost = host;
            pendingPort = port;
            state = State.RESOLVING;
            proxy.sendDnsQuery(this, host);
        }
    }

    private void connectRemote(InetSocketAddress address) throws IOException {
        remote = SocketChannel.open();
        remote.configureBlocking(false);
        remote.connect(address);
        state = State.CONNECTING;
        proxy.registerRemote(this, remote);
    }

    private void sendSuccessReply() throws IOException {
        SocketAddress local = remote.getLocalAddress();
        InetSocketAddress isa = (InetSocketAddress) local;
        byte[] addr = ((Inet4Address) isa.getAddress()).getAddress();
        byte[] resp = new byte[10];
        resp[0] = 0x05;
        resp[1] = 0x00; // success
        resp[2] = 0x00;
        resp[3] = 0x01; // IPv4
        System.arraycopy(addr, 0, resp, 4, 4);
        resp[8] = (byte) (isa.getPort() >> 8);
        resp[9] = (byte) (isa.getPort());
        toClient.put(resp);
        proxy.updateInterests(client, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    private void sendReply(byte rep, InetSocketAddress bind) throws IOException {
        byte[] resp = new byte[10];
        resp[0] = 0x05;
        resp[1] = rep;
        resp[2] = 0x00;
        resp[3] = 0x01;
        byte[] addr = {0, 0, 0, 0};
        int port = 0;
        if (bind != null) {
            addr = ((Inet4Address) bind.getAddress()).getAddress();
            port = bind.getPort();
        }
        System.arraycopy(addr, 0, resp, 4, 4);
        resp[8] = (byte) (port >> 8);
        resp[9] = (byte) (port);
        toClient.put(resp);
        proxy.updateInterests(client, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    private int readIntoBuffer(SocketChannel ch, ByteBuffer target) throws IOException {
        ioBuf.clear();
        int read = ch.read(ioBuf);
        if (read > 0) {
            ioBuf.flip();
            while (ioBuf.hasRemaining() && target.hasRemaining()) {
                target.put(ioBuf.get());
            }
            adjustInterests();
        }
        return read;
    }

    private boolean flushBuffer(SocketChannel ch, ByteBuffer buffer) throws IOException {
        if (!ch.isOpen()) return true;
        buffer.flip();
        try {
            while (buffer.hasRemaining()) {
                int n = ch.write(buffer);
                if (n == 0) break;
            }
        } catch (IOException e) {
            return true;
        } finally {
            buffer.compact();
        }
        return false;
    }

    private void adjustInterests() {
        if (state == State.CLOSED) return;
        int clientOps = 0;
        int remoteOps = 0;

        boolean canReadClient = toRemote.hasRemaining() || state != State.RELAY;
        boolean canReadRemote = toClient.hasRemaining() || state != State.RELAY;

        if (canReadClient) clientOps |= SelectionKey.OP_READ;
        if (toClient.position() > 0) clientOps |= SelectionKey.OP_WRITE;

        if (remote != null && remote.isConnectionPending()) {
            remoteOps = SelectionKey.OP_CONNECT;
        } else if (remote != null && remote.isOpen()) {
            if (canReadRemote) remoteOps |= SelectionKey.OP_READ;
            if (toRemote.position() > 0) remoteOps |= SelectionKey.OP_WRITE;
        }

        proxy.updateInterests(client, clientOps);
        if (remote != null && remote.isOpen()) {
            proxy.updateInterests(remote, remoteOps);
        }
    }

    private void shutdownRemoteOutput() {
        if (remote != null && remote.isOpen()) {
            try {
                remote.shutdownOutput();
            } catch (IOException ignored) { }
        }
    }

    private void shutdownClientOutput() {
        if (client.isOpen()) {
            try {
                client.shutdownOutput();
            } catch (IOException ignored) { }
        }
    }

    private void checkClose() {
        boolean clientDone = clientInputClosed && toClient.position() == 0;
        boolean remoteDone = remoteInputClosed && toRemote.position() == 0;
        if (clientDone && remoteDone) {
            proxy.closeSession(this);
        }
    }
}
