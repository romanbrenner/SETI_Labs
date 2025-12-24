package snakegame.io.datatypes;

import me.ippolitov.fit.snakes.SnakesProto;

public class MessageWithSender {
    private SnakesProto.GameMessage message;
    private String ip;
    private Integer port;
    private boolean onlyView;

    public static Builder builder() {
        return new Builder();
    }

    public SnakesProto.GameMessage getMessage() {
        return message;
    }

    public String getIp() {
        return ip;
    }

    public Integer getPort() {
        return port;
    }

    public boolean isOnlyView() {
        return onlyView;
    }

    public void setOnlyView(boolean onlyView) {
        this.onlyView = onlyView;
    }

    public static class Builder {
        private final MessageWithSender instance = new MessageWithSender();

        public Builder message(SnakesProto.GameMessage message) {
            instance.message = message;
            return this;
        }

        public Builder ip(String ip) {
            instance.ip = ip;
            return this;
        }

        public Builder port(Integer port) {
            instance.port = port;
            return this;
        }

        public Builder onlyView(boolean onlyView) {
            instance.onlyView = onlyView;
            return this;
        }

        public MessageWithSender build() {
            return instance;
        }
    }
}
