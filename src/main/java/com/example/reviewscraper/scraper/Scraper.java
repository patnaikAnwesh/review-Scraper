package com.example.reviewscraper.scraper;

import com.example.reviewscraper.model.Review;

import java.time.LocalDate;
import java.util.List;

public interface Scraper {
    /**
     * Scrape reviews for `company` between start and end inclusive.
     */
    List<Review> scrape(String company, LocalDate start, LocalDate end) throws Exception;
}
