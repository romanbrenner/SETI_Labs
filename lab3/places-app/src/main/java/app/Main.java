package app;

import models.*;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        PlacesService service = new PlacesService();
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Приложение для поиска мест ===");
        System.out.print("Введите название места: ");
        String query = scanner.nextLine();
        List<Location> locations = service.searchLocationsAsync(query).join();
        
        if (locations.isEmpty()) {
            System.out.println("Локации не найдены");
            return;
        }

        System.out.println("\nНайденные локации:");
        for (int i = 0; i < locations.size(); i++) {
            System.out.printf("%d. %s\n", i + 1, locations.get(i));
        }

        System.out.print("\nВыберите номер локации: ");
        int choice = scanner.nextInt() - 1;
        
        if (choice < 0 || choice >= locations.size()) {
            System.out.println("Неверный выбор");
            return;
        }

        Location selected = locations.get(choice);
        System.out.println("\nЗагрузка данных для: " + selected);

        CompleteResult result = service.fetchAllDataParallelAsync(
            selected.getLat(), selected.getLon()
        ).join();

        System.out.println("\n=== ПОГОДА ===");
        System.out.println(result.getWeather());

        System.out.println("\n=== ИНТЕРЕСНЫЕ МЕСТА ===");
        for (PlaceWithDescription place : result.getPlaces()) {
            System.out.println("\n• " + place);
        }
    }
}
