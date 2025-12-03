package models;

import java.util.List;

public class CompleteResult {
    private final Weather weather;
    private final List<PlaceWithDescription> places;

    public CompleteResult(Weather weather, List<PlaceWithDescription> places) {
        this.weather = weather;
        this.places = places;
    }

    public Weather getWeather() { return weather; }
    public List<PlaceWithDescription> getPlaces() { return places; }
}
