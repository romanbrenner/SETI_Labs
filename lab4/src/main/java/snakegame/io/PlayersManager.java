package snakegame.io;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import me.ippolitov.fit.snakes.SnakesProto;
import snakegame.io.datatypes.PlayerSignature;

import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PlayersManager {

    private static class PlayerWrapper {
        private final SnakesProto.GamePlayer player;
        private Long lastSeen;

        private PlayerWrapper(SnakesProto.GamePlayer player, Long lastSeen) {
            this.player = player;
            this.lastSeen = lastSeen;
        }

        public SnakesProto.GamePlayer getPlayer() {
            return player;
        }

        public Long getLastSeen() {
            return lastSeen;
        }

        public void setLastSeen(Long lastSeen) {
            this.lastSeen = lastSeen;
        }
    }

    private final Subject<Collection<SnakesProto.GamePlayer>> playersSubject = BehaviorSubject.create();
    private final HashMap<Integer, PlayerWrapper> players;
    private final Consumer<SnakesProto.GamePlayer> onPlayerDeadListener;
    private final Thread checkDeadWorkerThread;

    private int maxPlayerId = 0;
    private volatile int myId = -1;

    public PlayersManager(Consumer<SnakesProto.GamePlayer> onPlayerDeadListener) {
        this.players = new HashMap<>();
        this.onPlayerDeadListener = onPlayerDeadListener;
        checkDeadWorkerThread = new Thread(this::checkDeadWorker);
        checkDeadWorkerThread.start();
        Observable.interval(500, TimeUnit.MILLISECONDS).subscribe(time -> playersSubject.onNext(getPlayers()));
    }

    void stop() {
        checkDeadWorkerThread.interrupt();
    }

    public Optional<Integer> getIdBySignature(PlayerSignature signature) {
        return players.values().stream().filter(playerWrapper -> new PlayerSignature(playerWrapper.getPlayer()).equals(signature)).map(playerWrapper -> playerWrapper.getPlayer().getId()).findAny();
    }

    void touchPlayer(PlayerSignature signature) {
        players.values().stream().filter(playerWrapper -> new PlayerSignature(playerWrapper.getPlayer()).equals(signature)).findAny().ifPresent(
                playerWrapper -> playerWrapper.setLastSeen(System.currentTimeMillis())
        );
    }

    public int getNextPlayerId() {
        return maxPlayerId + 1;
    }

    public void updatePlayer(SnakesProto.GamePlayer player) {
        maxPlayerId = Math.max(maxPlayerId, player.getId());
        synchronized (players) {
            players.put(
                    player.getId(),
                    new PlayerWrapper(
                            player,
                            System.currentTimeMillis()
                    )
            );
        }
    }

    void updatePlayerWithoutTouch(SnakesProto.GamePlayer player) {
        maxPlayerId = Math.max(maxPlayerId, player.getId());
        synchronized (players) {
            players.put(
                    player.getId(),
                    new PlayerWrapper(
                            player,
                            players.containsKey(player.getId()) ? players.get(player.getId()).getLastSeen() : System.currentTimeMillis()
                    )
            );
        }
    }

    public void changeRole(int playerId, SnakesProto.NodeRole role) {
        synchronized (players) {
            var player = players.get(playerId);
            if (player != null) {
                players.put(playerId,
                        new PlayerWrapper(
                                SnakesProto.GamePlayer.newBuilder(player.getPlayer()).setRole(role).build(),
                                player.getLastSeen()
                        )
                );
            }
        }
    }

    public Collection<SnakesProto.GamePlayer> getPlayers() {
        return players.values().stream()
                .map(PlayerWrapper::getPlayer)
                .collect(Collectors.toList());
    }

    public Optional<SnakesProto.GamePlayer> getMaster() {
        return players.values().stream().map(PlayerWrapper::getPlayer).filter(
                player -> player.getRole() == SnakesProto.NodeRole.MASTER
        ).findAny();
    }

    public Optional<SnakesProto.GamePlayer> getDeputy() {
        return players.values().stream().map(PlayerWrapper::getPlayer).filter(
                player -> player.getRole() == SnakesProto.NodeRole.DEPUTY
        ).findAny();
    }

    public Optional<SnakesProto.GamePlayer> getNormal() {
        return players.values().stream().map(PlayerWrapper::getPlayer).filter(
                player -> player.getRole() == SnakesProto.NodeRole.NORMAL
        ).findAny();
    }

    void checkDeadWorker() {
        while (true) {
            try {
                Thread.sleep(Config.NODE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                break;
            }
            synchronized (players) {
                if (myId == -1) {
                    continue;
                }
                var currentTime = System.currentTimeMillis();
                players.entrySet().stream()
                        .filter(e -> e.getValue().getPlayer().getId() != myId)
                        .filter(e -> currentTime - e.getValue().getLastSeen() > Config.NODE_TIMEOUT_MS)
                        .max((a, b) -> a.getValue().getPlayer().getRole() == SnakesProto.NodeRole.MASTER ? 1 : b.getValue().getPlayer().getRole() == SnakesProto.NodeRole.MASTER ? -1 : 0)
                        .ifPresent(e -> {
                            players.remove(e.getKey());
                            this.onPlayerDeadListener.accept(e.getValue().getPlayer());
                        });
            }
        }
    }

    public Subject<Collection<SnakesProto.GamePlayer>> getPlayersSubject() {
        return playersSubject;
    }

    public int getMyId() {
        return myId;
    }

    public void setMyId(int myId) {
        this.myId = myId;
    }
}
