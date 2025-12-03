package app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import models.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PlacesService {
    private static final String GRAPHHOPPER_API_KEY = "6100d12a-51a1-479b-b878-1bf7731df19c";
    private static final String OPENWEATHER_API_KEY = "dccd0eabfa798da6326143f4b89fbd6c";
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PlacesService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    //поиск локации
    public CompletableFuture<List<Location>> searchLocationsAsync(String query) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = String.format(
            "https://graphhopper.com/api/1/geocode?q=%s&locale=ru&key=%s",
            encodedQuery, GRAPHHOPPER_API_KEY
        );

        return sendAsyncRequest(url)
            .thenApply(this::parseLocations);
    }

    //погода
    private CompletableFuture<Weather> fetchWeatherAsync(double lat, double lon) {
        System.out.println("[" + Thread.currentThread().getName() + "] Запрос погоды...");
        String url = String.format(Locale.ROOT,
            "https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&lang=ru&appid=%s",
            lat, lon, OPENWEATHER_API_KEY
        );

        return sendAsyncRequest(url)
            .thenApply(json -> {
                System.out.println("[" + Thread.currentThread().getName() + "] Погода получена");
                return parseWeather(json);
            });
    }

    //интерестинг места
    private CompletableFuture<List<Place>> fetchNearbyPlacesAsync(double lat, double lon) {
        System.out.println("[" + Thread.currentThread().getName() + "] Запрос мест...");
        // encode вертикальную черту как %7C, иначе URI.create бросит ошибку
        String url = String.format(Locale.ROOT,
            "https://ru.wikipedia.org/w/api.php?action=query&list=geosearch" +
            "&gscoord=%f%%7C%f&gsradius=5000&gslimit=10&format=json",
            lat, lon
        );

        return sendAsyncRequest(url)
            .thenApply(json -> {
                System.out.println("[" + Thread.currentThread().getName() + "] Список мест получен");
                return parsePlaces(json);
            });
    }

    //описания этих мест
    private CompletableFuture<String> fetchDescriptionAsync(long pageId) {
        System.out.println("[" + Thread.currentThread().getName() + "] Запрос описания для pageId=" + pageId);
        String url = String.format(
            "https://ru.wikipedia.org/w/api.php?action=query&prop=extracts" +
            "&exintro=1&explaintext=1&pageids=%d&format=json",
            pageId
        );

        return sendAsyncRequest(url)
            .thenApply(json -> {
                System.out.println("[" + Thread.currentThread().getName() + "] Описание получено для pageId=" + pageId);
                return parseDescription(json, pageId);
            });
    }

    //параллельное выполнение независимых запросов (п.2)
    public CompletableFuture<CompleteResult> fetchAllDataParallelAsync(double lat, double lon) {
        CompletableFuture<Weather> weatherFuture = fetchWeatherAsync(lat, lon);
        CompletableFuture<List<Place>> placesFuture = fetchNearbyPlacesAsync(lat, lon);

        return weatherFuture.thenCombine(placesFuture, (weather, places) -> {
            return new Object() {
                Weather weather() { return weather; }
                List<Place> places() { return places; }
            };
        }).thenCompose(data -> 
            fetchDescriptionsAsync(data.places())
                .thenApply(placesWithDesc -> new CompleteResult(data.weather(), placesWithDesc))
        );
    }

    //асинхронная цепочка для описаний (п.п. 4 и 5)
    private CompletableFuture<List<PlaceWithDescription>> fetchDescriptionsAsync(List<Place> places) {
        System.out.println("[" + Thread.currentThread().getName() + "] Старт запросов описаний для " + places.size() + " мест");
        List<CompletableFuture<PlaceWithDescription>> futures = places.stream()
            .map(place -> fetchDescriptionAsync(place.getPageId())
                .thenApply(description -> new PlaceWithDescription(place, description))
                .exceptionally(ex -> new PlaceWithDescription(place, "Ошибка загрузки описания"))
            )
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()))
            .whenComplete((r, ex) -> System.out
                .println("[" + Thread.currentThread().getName() + "] Все описания получены"));
    }

    private CompletableFuture<JsonNode> sendAsyncRequest(String url) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "PlacesApp/1.0")
            .GET()
            .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body)
            .thenApply(this::parseJson);
    }

    //парсинг JSON
    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка парсинга JSON", e);
        }
    }

    private List<Location> parseLocations(JsonNode json) {
        List<Location> locations = new ArrayList<>();
        JsonNode hits = json.get("hits");
        
        if (hits != null && hits.isArray()) {
            for (JsonNode hit : hits) {
                JsonNode point = hit.get("point");
                String name = hit.has("name") ? hit.get("name").asText() : "";
                String country = hit.has("country") ? hit.get("country").asText() : "";
                String state = hit.has("state") ? hit.get("state").asText() : "";
                
                if (point != null) {
                    double lat = point.get("lat").asDouble();
                    double lon = point.get("lng").asDouble();
                    locations.add(new Location(name, lat, lon, country, state));
                }
            }
        }
        return locations;
    }

    private Weather parseWeather(JsonNode json) {
        JsonNode weather = json.get("weather").get(0);
        String description = weather.get("description").asText();
        
        JsonNode main = json.get("main");
        double temp = main.get("temp").asDouble();
        double feelsLike = main.get("feels_like").asDouble();
        int humidity = main.get("humidity").asInt();
        
        double windSpeed = json.get("wind").get("speed").asDouble();
        
        return new Weather(description, temp, feelsLike, humidity, windSpeed);
    }

    private List<Place> parsePlaces(JsonNode json) {
        List<Place> places = new ArrayList<>();
        JsonNode geoSearch = json.get("query").get("geosearch");
        
        if (geoSearch != null && geoSearch.isArray()) {
            for (JsonNode item : geoSearch) {
                long pageId = item.get("pageid").asLong();
                String title = item.get("title").asText();
                double distance = item.get("dist").asDouble();
                places.add(new Place(pageId, title, distance));
            }
        }
        return places;
    }

    private String parseDescription(JsonNode json, long pageId) {
        JsonNode pages = json.get("query").get("pages");
        JsonNode page = pages.get(String.valueOf(pageId));
        
        if (page != null && page.has("extract")) {
            String extract = page.get("extract").asText();
            return extract.length() > 300 ? extract.substring(0, 297) + "..." : extract;
        }
        return "Описание отсутствует";
    }
}
