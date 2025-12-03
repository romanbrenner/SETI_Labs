package models;

public class Place {
    private final long pageId;
    private final String title;
    private final double distance;

    public Place(long pageId, String title, double distance) {
        this.pageId = pageId;
        this.title = title;
        this.distance = distance;
    }

    public long getPageId() { return pageId; }
    public String getTitle() { return title; }
    public double getDistance() { return distance; }
}
