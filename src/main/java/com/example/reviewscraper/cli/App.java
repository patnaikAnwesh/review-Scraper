package com.example.reviewscraper.cli;

import com.example.reviewscraper.browser.DriverManager;
import com.example.reviewscraper.io.JsonWriter;
import com.example.reviewscraper.model.Review;
import com.example.reviewscraper.scraper.*;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

public class App {
    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Usage: java -jar review-scraper.jar \"Company Name\" <start yyyy-MM-dd> <end yyyy-MM-dd> <source:g2|capterra|trustradius> [headless:true|false] [maxPages] [delayMs]");
            return;
        }

        String company = args[0];
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(args[1]);
            end = LocalDate.parse(args[2]);
        } catch (DateTimeParseException e) {
            System.err.println("Invalid date format. Use yyyy-MM-dd.");
            return;
        }
        if (end.isBefore(start)) {
            System.err.println("end must be same or after start");
            return;
        }
        String source = args[3].toLowerCase();
        boolean headless = args.length < 5 || !args[4].equalsIgnoreCase("headless=false");
        int maxPages = args.length >= 6 ? Integer.parseInt(args[5]) : 10;
        long delayMs = args.length >= 7 ? Long.parseLong(args[6]) : 800L;

        WebDriver driver = null;
        try {
            driver = DriverManager.createChromeDriver(headless);
            Scraper scraper;
            switch (source) {
                case "g2":
                    scraper = new G2Scraper(driver, maxPages, delayMs);
                    break;
                case "capterra":
                    scraper = new CapterraScraper(driver, maxPages, delayMs);
                    break;
                case "trustradius":
                case "trust":
                    scraper = new TrustRadiusScraper(driver, maxPages, delayMs);
                    break;
                default:
                    System.err.println("Unsupported source: " + source);
                    return;
            }

            List<Review> reviews = scraper.scrape(company, start, end);
            System.out.println("Collected " + reviews.size() + " reviews.");

            String filename = String.format("reviews_%s_%s_%s_%s.json", source, company.replaceAll("\\s+","_"), start, end);
            File out = JsonWriter.write(reviews, filename);
            System.out.println("Wrote " + out.getAbsolutePath());
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }
    }
}
