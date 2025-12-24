package snakegame.io;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.EvictingQueue;
import me.ippolitov.fit.snakes.SnakesProto;
import snakegame.io.datatypes.MessageWithSender;
import snakegame.io.datatypes.PlayerSignature;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

public class AvailableGamesManager {
    private final Queue<MessageWithSender> games = EvictingQueue.create(5);
    private final Cache<PlayerSignature, MessageWithSender> allGames = CacheBuilder.newBuilder().maximumSize(10000).expireAfterWrite(1000, TimeUnit.MILLISECONDS).build();
    private final Thread gamesListenerWorkerThread;
    private final MulticastManager multicastManager;

    public AvailableGamesManager(MulticastManager multicastManager) {
        this.multicastManager = multicastManager;

        gamesListenerWorkerThread = new Thread(this::gamesListenerWorker);
        gamesListenerWorkerThread.start();
    }

    private void gamesListenerWorker() {
        while (true) {
            MessageWithSender msg;
            try {
                msg = multicastManager.receivePacket();
            } catch (InterruptedException e) {
                break;
            }
            if (msg.getMessage().hasAnnouncement()) {
                int masterPort = msg.getPort();
                try {
                    var playersList = msg.getMessage().getAnnouncement().getPlayers().getPlayersList();
                    masterPort = playersList.stream()
                            .filter(p -> p.getRole() == SnakesProto.NodeRole.MASTER && p.hasPort())
                            .findFirst()
                            .map(SnakesProto.GamePlayer::getPort)
                            .orElse(msg.getPort());
                } catch (Exception ignored) {}

                var patched = MessageWithSender.builder()
                        .ip(msg.getIp())
                        .port(masterPort)
                        .message(msg.getMessage())
                        .build();
                allGames.put(new PlayerSignature(patched.getIp(), patched.getPort()), patched);
                games.add(patched);
            }
        }
    }

    public void announce(SnakesProto.GameMessage.AnnouncementMsg announcementMsg) {
        multicastManager.sendPacket(
                SnakesProto.GameMessage
                        .newBuilder()
                        .setAnnouncement(
                                announcementMsg
                        )
                        .setMsgSeq(0)
                        .build()
        );
    }

    public Collection<MessageWithSender> getGames() {
        return games;
    }

    public Collection<MessageWithSender> getAllGames() {
        return allGames.asMap().values();
    }

    void stop() {
        gamesListenerWorkerThread.interrupt();
    }
}
