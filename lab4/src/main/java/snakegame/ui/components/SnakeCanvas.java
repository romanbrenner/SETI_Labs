package snakegame.ui.components;

import io.reactivex.rxjava3.subjects.Subject;
import me.ippolitov.fit.snakes.SnakesProto;
import snakegame.snake.SnakeView;
import snakegame.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.Function;

public class SnakeCanvas extends JPanel implements SnakeView {
    private final int canvasWidth;
    private final int canvasHeight;

    private SnakesProto.GameState state;

    private static final Color FOOD_COLOR = new Color(255, 186, 90);

    private static final Color[] SNAKE_COLORS = {
            new Color(117, 204, 255),
            new Color(185, 147, 255),
            new Color(120, 236, 182),
            new Color(255, 138, 128),
            new Color(255, 223, 138),
            new Color(176, 216, 255),
            new Color(255, 180, 210),
            new Color(148, 222, 135),
    };

    public SnakeCanvas(Subject<Control> controlSubject) {
        canvasWidth = 520;
        canvasHeight = 360;
        setSize(canvasWidth, canvasHeight);
        setPreferredSize(new Dimension(canvasWidth, canvasHeight));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createLineBorder(Theme.ACCENT, 2, true));
        setDoubleBuffered(true);

        setFocusable(true);
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                requestFocusInWindow();
            }
        });
        InputMap inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0), "up");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), "down");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "left");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "right");
        Function<SnakesProto.Direction, AbstractAction> actionFactory = direction -> new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                controlSubject.onNext(new Control(null, direction));
            }
        };

        actionMap.put("up", actionFactory.apply(SnakesProto.Direction.UP));
        actionMap.put("down", actionFactory.apply(SnakesProto.Direction.DOWN));
        actionMap.put("left", actionFactory.apply(SnakesProto.Direction.LEFT));
        actionMap.put("right", actionFactory.apply(SnakesProto.Direction.RIGHT));
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        drawState((Graphics2D) g);
    }

    private void drawState(Graphics2D canvas) {
        if (state == null) {
            return;
        }

        int width = state.getConfig().getWidth();
        int height = state.getConfig().getHeight();

        int cellWidth = canvasWidth / width;
        int cellHeight = canvasHeight / height;

        var backgroundPaint = new GradientPaint(
                0, 0, Theme.BACKGROUND,
                canvasWidth, canvasHeight, Theme.PANEL, true
        );
        canvas.setPaint(backgroundPaint);
        canvas.fillRect(0, 0, canvasWidth, canvasHeight);

        canvas.setStroke(new BasicStroke(
                0.6f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
                1f,
                new float[]{4f, 6f},
                0f
        ));
        canvas.setColor(Theme.GRID);

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                canvas.drawRect(i * cellWidth, j * cellHeight, cellWidth, cellHeight);
            }
        }

        for (var snake : state.getSnakesList()) {
            canvas.setStroke(new BasicStroke(Math.max(3f, cellWidth / 4f)));
            var firstPoint = true;
            int x = 0, y = 0;
            for (var point : snake.getPointsList()) {
                var ax = x + point.getX();
                var ay = y + point.getY();

                var baseColor = SNAKE_COLORS[snake.getPlayerId() % SNAKE_COLORS.length];
                canvas.setColor(firstPoint ? baseColor.darker() : baseColor);
                int padding = Math.max(1, Math.min(cellWidth, cellHeight) / 6);
                canvas.fillRoundRect(
                        ax * cellWidth + padding,
                        ay * cellHeight + padding,
                        cellWidth - padding * 2,
                        cellHeight - padding * 2,
                        Math.max(8, cellWidth / 3),
                        Math.max(8, cellHeight / 3)
                );
            
                x = ax;
                y = ay;
                firstPoint = false;
            }
        }

        canvas.setColor(FOOD_COLOR);
        for (var food: state.getFoodsList()) {
            canvas.fillOval(
                    food.getX() * cellWidth + cellWidth / 4,
                    food.getY() * cellHeight + cellHeight / 4,
                    cellWidth / 2,
                    cellHeight / 2
            );
        }

        canvas.setColor(new Color(Theme.TEXT.getRed(), Theme.TEXT.getGreen(), Theme.TEXT.getBlue(), 180));
        canvas.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        canvas.drawString("Steer: W/A/S/D • Create a lobby or double-click a broadcasted one", 12, 18);
    }

    @Override
    public void setState(SnakesProto.GameState state) {
        this.state = state;
        repaint();
    }
}
