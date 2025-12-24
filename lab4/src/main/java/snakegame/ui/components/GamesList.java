package snakegame.ui.components;

import snakegame.io.AvailableGamesManager;
import snakegame.io.datatypes.MessageWithSender;
import snakegame.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.function.Consumer;

public class GamesList extends JList<String> {
    public GamesList(AvailableGamesManager availableGamesManager, Consumer<MessageWithSender> onJoinListener) {
        super();
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder());
        setFixedCellHeight(32);
        setFont(new Font("Segoe UI", Font.PLAIN, 12));
        setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setOpaque(true);
                label.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                label.setBackground(isSelected ? Theme.ACCENT : Theme.PANEL);
                label.setForeground(isSelected ? Color.DARK_GRAY : Theme.TEXT);
                return label;
            }
        });
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int selected = locationToIndex(e.getPoint());
                    if (selected >= 0 && selected < getModel().getSize()) {
                        onJoinListener.accept(availableGamesManager.getAllGames().toArray(MessageWithSender[]::new)[selected]);
                    }
                }
            }
        });
        var updater = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
                var copy = new ArrayList<>(availableGamesManager.getAllGames());
                SwingUtilities.invokeLater(() -> setListData(copy.stream().map(messageWithSender ->
                        String.format("%s:%d — %d игроков",
                                messageWithSender.getIp(),
                                messageWithSender.getPort(),
                                messageWithSender.getMessage().getAnnouncement().getPlayers().getPlayersCount()
                        ))
                        .toArray(String[]::new)));
            }
        }, "games-list-updater");
        updater.setDaemon(true);
        updater.start();
    }
}
