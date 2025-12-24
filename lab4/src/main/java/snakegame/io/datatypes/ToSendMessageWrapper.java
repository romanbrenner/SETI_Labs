package snakegame.io.datatypes;

import me.ippolitov.fit.snakes.SnakesProto;

public class ToSendMessageWrapper {
    private long msgSeq;
    private SnakesProto.GameMessage message;
    private String ip;
    private Integer port;
    private Long sentAt;
    private int retryCount = 3;

    public static Builder builder() {
        return new Builder();
    }

    public long getMsgSeq() {
        return msgSeq;
    }

    public void setMsgSeq(long msgSeq) {
        this.msgSeq = msgSeq;
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

    public Long getSentAt() {
        return sentAt;
    }

    public void setSentAt(Long sentAt) {
        this.sentAt = sentAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public static class Builder {
        private final ToSendMessageWrapper instance = new ToSendMessageWrapper();

        public Builder msgSeq(long msgSeq) {
            instance.msgSeq = msgSeq;
            return this;
        }

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

        public Builder sentAt(Long sentAt) {
            instance.sentAt = sentAt;
            return this;
        }

        public Builder retryCount(int retryCount) {
            instance.retryCount = retryCount;
            return this;
        }

        public ToSendMessageWrapper build() {
            return instance;
        }
    }
}
