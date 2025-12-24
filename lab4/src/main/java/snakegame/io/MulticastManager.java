package snakegame.io;

import me.ippolitov.fit.snakes.SnakesProto;
import snakegame.io.datatypes.MessageWithSender;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MulticastManager {
    private static final int BUF_SIZE = 65000;

    private final BlockingQueue<SnakesProto.GameMessage> sendQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<MessageWithSender> receiveQueue = new LinkedBlockingQueue<>();

    private final MulticastSocket recvSocket;
    private final MulticastSocket sendSocket;

    private final Thread sendWorkerThread;
    private final Thread receiveWorkerThread;

    public MulticastManager(DatagramSocket ignoredSendSocket) throws IOException {
        this.recvSocket = new MulticastSocket(Config.MULTICAST_PORT);
        this.recvSocket.setReuseAddress(true);
        this.recvSocket.setLoopbackMode(false); // enable loopback

        this.sendSocket = new MulticastSocket();
        this.sendSocket.setReuseAddress(true);
        this.sendSocket.setTimeToLive(1);
        this.sendSocket.setLoopbackMode(false);

        NetworkInterface nif = null;
        try {
            nif = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
        } catch (Exception ignored) {}
        if (nif == null || !nif.supportsMulticast()) {
            var nets = NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                var candidate = nets.nextElement();
                try {
                    if (candidate.supportsMulticast() && candidate.isUp()) {
                        nif = candidate;
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }
        if (nif != null) {
            recvSocket.joinGroup(new InetSocketAddress(Config.MULTICAST_GROUP_IP, Config.MULTICAST_PORT), nif);
            sendSocket.setNetworkInterface(nif);
        } else {
            recvSocket.joinGroup(new InetSocketAddress(Config.MULTICAST_GROUP_IP, Config.MULTICAST_PORT), null);
        }


        sendWorkerThread = new Thread(this::sendWorker);
        sendWorkerThread.start();
        receiveWorkerThread = new Thread(this::receiveWorker);
        receiveWorkerThread.start();
    }

    void stop() {
        sendWorkerThread.interrupt();
        receiveWorkerThread.interrupt();
    }

    public void sendPacket(SnakesProto.GameMessage message) {
        sendQueue.add(message);
    }

    public MessageWithSender receivePacket() throws InterruptedException {
        return receiveQueue.take();
    }

    private void receiveWorker() {
        byte[] receiveBuffer = new byte[BUF_SIZE];
        while (true) {
            var receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            try {
                recvSocket.receive(receivePacket);
                byte[] bytes = new byte[receivePacket.getLength()];
                System.arraycopy(receiveBuffer, 0, bytes, 0, receivePacket.getLength());
                var gameMessage = SnakesProto.GameMessage.parseFrom(bytes);
                receiveQueue.add(
                        MessageWithSender.builder()
                                .ip(receivePacket.getAddress().getHostAddress())
                                .port(receivePacket.getPort())
                                .message(gameMessage)
                                .build()
                );
            } catch (IOException e) {}
        }
    }

    private void sendWorker() {
        while (true) {
            SnakesProto.GameMessage message;
            try {
                message = sendQueue.take();
            } catch (InterruptedException e) {
                break;
            }

            var sendData = message.toByteArray();

            try {
                var packet = new DatagramPacket(
                        sendData,
                        sendData.length,
                        InetAddress.getByName(Config.MULTICAST_GROUP_IP),
                        Config.MULTICAST_PORT
                );

                sendSocket.send(packet);
            } catch (IOException e) {}
        }
    }
}
