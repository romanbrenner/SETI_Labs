package ui;

import app.PlacesService;
import models.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class PlacesApp extends JFrame {
    private final PlacesService service;
    private JTextField searchField;
    private JButton searchButton;
    private JList<Location> locationsList;
    private DefaultListModel<Location> locationsModel;
    private JTextArea resultArea;
    private JButton fetchDataButton;

    public PlacesApp() {
        this.service = new PlacesService();
        initUI();
    }

    private void initUI() {
        setTitle("Поиск мест и погоды");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        //панель для поиска
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchField = new JTextField();
        searchButton = new JButton("Найти локации");
        searchPanel.add(new JLabel("Введите название места:"), BorderLayout.NORTH);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        //тут список локаций
        locationsModel = new DefaultListModel<>();
        locationsList = new JList<>(locationsModel);
        locationsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane locationsScroll = new JScrollPane(locationsList);
        locationsScroll.setBorder(BorderFactory.createTitledBorder("Найденные локации"));

        //кнопка
        fetchDataButton = new JButton("Получить погоду и места");
        fetchDataButton.setEnabled(false);

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Результаты"));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(searchPanel, BorderLayout.NORTH);
        topPanel.add(locationsScroll, BorderLayout.CENTER);
        topPanel.add(fetchDataButton, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
        add(resultScroll, BorderLayout.CENTER);

        searchButton.addActionListener(e -> searchLocations());
        fetchDataButton.addActionListener(e -> fetchCompleteData());
        locationsList.addListSelectionListener(e -> 
            fetchDataButton.setEnabled(!locationsList.isSelectionEmpty())
        );
        searchField.addActionListener(e -> searchLocations());

        JPanel padding = new JPanel();
        padding.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setContentPane(padding);
        padding.setLayout(new BorderLayout());
        padding.add(topPanel, BorderLayout.NORTH);
        padding.add(resultScroll, BorderLayout.CENTER);
    }

    private void searchLocations() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Введите название места");
            return;
        }

        searchButton.setEnabled(false);
        resultArea.setText("Поиск локаций...");
        locationsModel.clear();

        service.searchLocationsAsync(query)
            .thenAccept(locations -> SwingUtilities.invokeLater(() -> {
                if (locations.isEmpty()) {
                    resultArea.setText("Локации не найдены");
                } else {
                    locations.forEach(locationsModel::addElement);
                    resultArea.setText("Найдено локаций: " + locations.size() + 
                                     "\nВыберите локацию из списка");
                }
                searchButton.setEnabled(true);
            }))
            .exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    resultArea.setText("Ошибка поиска: " + ex.getMessage());
                    searchButton.setEnabled(true);
                });
                return null;
            });
    }

    private void fetchCompleteData() {
        Location selected = locationsList.getSelectedValue();
        if (selected == null) return;

        fetchDataButton.setEnabled(false);
        resultArea.setText("Загрузка данных...");

        //одна блокировка на получении конечного результата
        service.fetchAllDataParallelAsync(selected.getLat(), selected.getLon())
            .thenAccept(result -> SwingUtilities.invokeLater(() -> 
                displayResults(selected, result)
            ))
            .exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    resultArea.setText("Ошибка загрузки данных: " + ex.getMessage());
                    fetchDataButton.setEnabled(true);
                });
                return null;
            });
    }

    private void displayResults(Location location, CompleteResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(location).append(" ===\n\n");
        
        sb.append("ПОГОДА:\n");
        sb.append(result.getWeather()).append("\n\n");
        
        sb.append("ИНТЕРЕСНЫЕ МЕСТА РЯДОМ:\n");
        if (result.getPlaces().isEmpty()) {
            sb.append("Интересные места не найдены\n");
        } else {
            int count = 1;
            for (PlaceWithDescription place : result.getPlaces()) {
                sb.append(count++).append(". ").append(place).append("\n\n");
            }
        }
        
        resultArea.setText(sb.toString());
        resultArea.setCaretPosition(0);
        fetchDataButton.setEnabled(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PlacesApp app = new PlacesApp();
            app.setVisible(true);
        });
    }
}
