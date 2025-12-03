package models;

public class Location {
    private final String name;
    private final double lat;
    private final double lon;
    private final String country;
    private final String state;

    public Location(String name, double lat, double lon, String country, String state) {
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.country = country;
        this.state = state;
    }

    public String getName() { return name; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public String getCountry() { return country; }
    public String getState() { return state; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name);
        if (state != null && !state.isEmpty()) {
            sb.append(", ").append(state);
        }
        if (country != null && !country.isEmpty()) {
            sb.append(", ").append(country);
        }
        return sb.toString();
    }
}
