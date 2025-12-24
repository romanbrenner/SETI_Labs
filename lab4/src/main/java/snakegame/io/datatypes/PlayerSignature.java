package snakegame.io.datatypes;

import me.ippolitov.fit.snakes.SnakesProto;

import java.util.Objects;

public class PlayerSignature {
    private final String ip;
    private final int port;

    public PlayerSignature(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public PlayerSignature(SnakesProto.GamePlayer player) {
        this(player.getIpAddress(), player.getPort());
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerSignature that = (PlayerSignature) o;
        return port == that.port && Objects.equals(ip, that.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, port);
    }
}
