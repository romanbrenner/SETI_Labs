package models;

public class PlaceWithDescription {
    private final Place place;
    private final String description;

    public PlaceWithDescription(Place place, String description) {
        this.place = place;
        this.description = description;
    }

    public Place getPlace() { return place; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return String.format("%s (%.0f м): %s", 
            place.getTitle(), place.getDistance(), 
            description != null && !description.isEmpty() ? description : "Нет описания");
    }
}
