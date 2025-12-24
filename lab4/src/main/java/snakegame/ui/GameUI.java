package snakegame.ui;

import me.ippolitov.fit.snakes.SnakesProto;
import snakegame.io.Config;
import snakegame.io.PlayerController;
import snakegame.io.datatypes.MessageWithSender;
import snakegame.snake.SnakeView;
import snakegame.snake.SnakeViewController;
import snakegame.ui.components.GamesList;
import snakegame.ui.components.PlayersTable;
import snakegame.ui.components.SnakeCanvas;

import javax.swing.*;

import java.awt.*;
import java.io.IOException;
import java.net.BindException;

public class GameUI {
    private PlayerController player;
    private SnakeView snakeView;
    private int listenPort;
    private String userName;
    private JLabel roleBadge;
    private JLabel statusLabel;

    private void joinGame(MessageWithSender gameMessage) {
        player.joinGame(gameMessage);
    }

    private JPanel createCard(String title, JComponent content) {
        var card = new JPanel(new BorderLayout());
        card.setBackground(Theme.PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.GRID, 1, true),
                BorderFactory.createEmptyBorder(8, 10, 10, 10)
        ));

        var titleLabel = new JLabel(title);
        titleLabel.setForeground(Theme.TEXT);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        card.add(titleLabel, BorderLayout.NORTH);

        content.setBackground(Theme.PANEL);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildHeader() {
        var header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                var g2 = (Graphics2D) g;
                var gradient = new GradientPaint(
                        0, 0, Theme.PANEL.darker(),
                        getWidth(), getHeight(), Theme.PANEL.brighter(), true
                );
                g2.setPaint(gradient);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            }
        };
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        var title = new JLabel("Snake Online");
        title.setForeground(Theme.TEXT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));

        statusLabel = new JLabel(String.format(
                "%s • UDP port %d • multicast %s:%d",
                userName,
                listenPort,
                Config.MULTICAST_GROUP_IP,
                Config.MULTICAST_PORT
        ));
        statusLabel.setForeground(Theme.TEXT);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        roleBadge = new JLabel(player.getRole().name());
        roleBadge.setOpaque(true);
        roleBadge.setBackground(Theme.ACCENT);
        roleBadge.setForeground(Color.DARK_GRAY);
        roleBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        roleBadge.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        roleBadge.setHorizontalAlignment(SwingConstants.CENTER);

        var controlsHint = new JLabel("W/A/S/D — движение • Host — создать лобби • двойной клик — подключиться");
        controlsHint.setForeground(Theme.TEXT);

        var left = new JPanel(new GridLayout(2, 1));
        left.setOpaque(false);
        left.add(title);
        left.add(statusLabel);

        header.add(left, BorderLayout.CENTER);
        header.add(roleBadge, BorderLayout.EAST);
        header.add(controlsHint, BorderLayout.SOUTH);
        return header;
    }

    private void initUI() {
        var frame = new JFrame("Snake Online");
        Taskbar taskbar = null;
        if (Taskbar.isTaskbarSupported()) {
            try {
                taskbar = Taskbar.getTaskbar();
            } catch (UnsupportedOperationException ignored) {
                taskbar = null;
            }
        }
        var url = GameUI.class.getResource("/snake.png");
        if (url != null) {
            ImageIcon icon = new ImageIcon(url);

            frame.setIconImage(icon.getImage());

            if (taskbar != null && taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                try {
                    taskbar.setIconImage(icon.getImage());
                } catch (UnsupportedOperationException ignored) {}
            }
        } else {
            System.out.println("Icon not found: /snake.png");
        }


        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        var contents = new JPanel(new BorderLayout(12, 12));
        contents.setBackground(Theme.BACKGROUND);
        contents.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        contents.add(buildHeader(), BorderLayout.NORTH);

        // region control panel
        var controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBackground(Theme.BACKGROUND);
        controlPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        var playersTable = new PlayersTable(player.getPlayersManager());
        playersTable.setPreferredScrollableViewportSize(new Dimension(340, 170));
        var playersScroll = new JScrollPane(playersTable);
        playersScroll.setBorder(BorderFactory.createEmptyBorder());
        playersScroll.getViewport().setBackground(Theme.PANEL);
        var playersCard = createCard("Игроки и роли", playersScroll);
        playersCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        var gamesList = new GamesList(player.getAvailableGamesManager(), this::joinGame);
        gamesList.setVisibleRowCount(7);
        var gamesScroll = new JScrollPane(gamesList);
        gamesScroll.setBorder(BorderFactory.createEmptyBorder());
        gamesScroll.getViewport().setBackground(Theme.PANEL);

        var joinWrapper = new JPanel(new BorderLayout(0, 8));
        joinWrapper.setOpaque(false);
        joinWrapper.add(gamesScroll, BorderLayout.CENTER);
        var createButton = new JButton("Host новую игру");
        createButton.setBackground(Theme.ACCENT);
        createButton.setForeground(Color.DARK_GRAY);
        createButton.setFocusPainted(false);
        createButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        createButton.addActionListener(v -> player.createGame());
        joinWrapper.add(createButton, BorderLayout.SOUTH);

        var joinCard = createCard("Лобби по сети", joinWrapper);
        joinCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        controlPanel.add(playersCard);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        controlPanel.add(joinCard);
        // endregion

        var canvasWrapper = new JPanel(new BorderLayout());
        canvasWrapper.setBackground(Theme.BACKGROUND);
        canvasWrapper.add((Component) snakeView, BorderLayout.CENTER);
        canvasWrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.GRID, 1, true),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        contents.add(canvasWrapper, BorderLayout.CENTER);
        contents.add(controlPanel, BorderLayout.EAST);

        // Final
        frame.setContentPane(contents);
        frame.pack();
        frame.setSize(1100, 620);
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        player.getRoleSubject().subscribe(role -> SwingUtilities.invokeLater(() -> {
            roleBadge.setText(role.name());
            roleBadge.setBackground(role == SnakesProto.NodeRole.MASTER ? Theme.ACCENT : Theme.ACCENT_MUTED);
        }));
    }

    GameUI() throws IOException {
        var i = 0;
        userName = JOptionPane.showInputDialog("Введите ваше имя: ");
        if (userName == null || userName.isBlank()) {
            userName = "Player" + (int) (Math.random() * 90 + 10);
        }
        for (i = 0; i < 10; i++) {
            try {
                listenPort = 5000 + i;
                player = new PlayerController(userName, listenPort, SnakesProto.NodeRole.NORMAL);
            } catch (BindException e) {
                continue;
            }
            break;
        }
        if (i == 10) {
            throw new RuntimeException("All ports are taken");
        }
        snakeView = new SnakeCanvas(player.getControlSubject());
        snakeView.setState(SnakesProto.GameState.getDefaultInstance());
        new SnakeViewController(player, snakeView);
        SwingUtilities.invokeLater(this::initUI);
        SwingUtilities.invokeLater(() -> ((Component) snakeView).requestFocusInWindow());
    }

    public static void main(String[] args) throws IOException {
        new GameUI();
    }
}
