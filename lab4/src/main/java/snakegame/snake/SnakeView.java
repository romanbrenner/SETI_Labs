package snakegame.snake;

import me.ippolitov.fit.snakes.SnakesProto;

public interface SnakeView {
    class Control {
        private Integer playerId;
        private SnakesProto.Direction direction;

        public Control(Integer playerId, SnakesProto.Direction direction) {
            this.playerId = playerId;
            this.direction = direction;
        }

        public Integer getPlayerId() {
            return playerId;
        }

        public void setPlayerId(Integer playerId) {
            this.playerId = playerId;
        }

        public SnakesProto.Direction getDirection() {
            return direction;
        }
    }

    void setState(SnakesProto.GameState state);
}
