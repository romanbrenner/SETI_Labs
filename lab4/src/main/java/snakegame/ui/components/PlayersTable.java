package snakegame.ui.components;

import snakegame.io.PlayersManager;
import snakegame.io.PlayersRateComp;
import snakegame.ui.Theme;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import me.ippolitov.fit.snakes.SnakesProto;

public class PlayersTable extends JTable {
    public PlayersTable(PlayersManager playersManager) {
        setShowGrid(false);
        setFont(new Font("Segoe UI", Font.PLAIN, 12));
        setRowHeight(26);
        setBackground(Theme.PANEL);
        setForeground(Theme.TEXT);
        setFillsViewportHeight(true);
        setDefaultEditor(Object.class, null);
        getTableHeader().setReorderingAllowed(false);
        getTableHeader().setBackground(Theme.PANEL);
        getTableHeader().setForeground(Theme.TEXT);
        getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        playersManager.getPlayersSubject().subscribe(players -> SwingUtilities.invokeLater(() -> {
                    var model = new DefaultTableModel();
                    model.setColumnCount(6);
                    model.setRowCount(players.size() + 1);
                    var i = 0;

                    model.setValueAt("id", i, 0);
                    model.setValueAt("Name", i, 1);
                    model.setValueAt("Score", i, 2);
                    model.setValueAt("IP", i, 3);
                    model.setValueAt("Port", i, 4);
                    model.setValueAt("Role", i, 5);
                    i++;
                    ArrayList<SnakesProto.GamePlayer> playersList = new ArrayList<>(players);
                    Collections.sort(playersList, new PlayersRateComp());
                    boolean first = true;
                    for (var player : playersList) {
                        model.setValueAt(player.getId() == playersManager.getMyId() ? player.getId() + " (you)" : player.getId(), i, 0);
                        model.setValueAt(first ? player.getScore() + new String(Character.toChars(0x1F3C6)) : player.getScore(), i, 2);
                        model.setValueAt(player.getName(), i, 1);
                        model.setValueAt(player.getIpAddress(), i, 3);
                        model.setValueAt(player.getPort(), i, 4);
                        model.setValueAt(player.getRole(), i, 5);
                        i++;
                        first = false;
                    }
                    setModel(model);
                }
        ));
    }
}
