package models;

public class Weather {
    private final String description;
    private final double temperature;
    private final double feelsLike;
    private final int humidity;
    private final double windSpeed;

    public Weather(String description, double temperature, double feelsLike, 
                   int humidity, double windSpeed) {
        this.description = description;
        this.temperature = temperature;
        this.feelsLike = feelsLike;
        this.humidity = humidity;
        this.windSpeed = windSpeed;
    }

    public String getDescription() { return description; }
    public double getTemperature() { return temperature; }
    public double getFeelsLike() { return feelsLike; }
    public int getHumidity() { return humidity; }
    public double getWindSpeed() { return windSpeed; }

    @Override
    public String toString() {
        return String.format("Погода: %s, Температура: %.1f°C (ощущается как %.1f°C), " +
                           "Влажность: %d%%, Ветер: %.1f м/с",
                description, temperature - 273.15, feelsLike - 273.15, 
                humidity, windSpeed);
    }
}
