package com.example.reviewscraper.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple Review model used across scrapers.
 */
public class Review {
    private String title;
    private String review;
    private String date;    // ISO yyyy-MM-dd or raw string
    private String reviewer;
    private Double rating;
    private Map<String,Object> extra = new HashMap<>();

    // getters / setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getReview() { return review; }
    public void setReview(String review) { this.review = review; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }
}
