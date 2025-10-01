package com.example.reviewscraper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileWriter;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReviewScraperSelenium - scrape using Selenium and CSS selectors only.
 * Usage:
 *   mvn package
 *   java -jar target/<your-jar>.jar "Company Name" 2024-01-01 2024-12-31 g2
 *
 * Notes:
 * - Uses WebDriverManager to manage chromedriver automatically.
 * - Default runs headless. Pass optional 5th arg "headless=false" to see the browser.
 */
public class ReviewScraper {

    public static class Review {
        public String title;
        public String review;
        public String date; // yyyy-MM-dd or raw string
        public String reviewer;
        public Double rating;
        public Map<String, Object> extra = new HashMap<>();
    }

    // ---------- Driver manager ----------
    public static class DriverManager {
        public static WebDriver createChromeDriver(boolean headless) {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            if (headless) options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1400,900");
            options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            return new ChromeDriver(options);
        }
    }

    // ---------- Utilities ----------
    private static LocalDate tryParseIso(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        try {
            // try plain ISO first
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ignored) {}
        // relative: "3 months ago"
        Pattern rel = Pattern.compile("(?:a|an|\\d+)\\s+(day|days|month|months|year|years)\\s+ago", Pattern.CASE_INSENSITIVE);
        Matcher m = rel.matcher(raw.toLowerCase(Locale.ROOT));
        if (m.find()) {
            String qtyToken = raw.split("\\s+")[0].toLowerCase(Locale.ROOT);
            int qty = (qtyToken.equals("a") || qtyToken.equals("an")) ? 1 : Integer.parseInt(qtyToken);
            String unit = m.group(1);
            LocalDate now = LocalDate.now();
            if (unit.startsWith("day")) return now.minusDays(qty);
            if (unit.startsWith("month")) return now.minusMonths(qty);
            if (unit.startsWith("year")) return now.minusYears(qty);
        }
        // fallback: find yyyy-mm-dd inside
        Matcher iso = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(raw);
        if (iso.find()) {
            try { return LocalDate.parse(iso.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }

    private static boolean inRange(LocalDate d, LocalDate start, LocalDate end) {
        if (d == null) return false;
        return (!d.isBefore(start)) && (!d.isAfter(end));
    }

    // ---------- Scraper helpers using CSS selectors (Selenium) ----------
    private static List<WebElement> safeFindElements(WebDriver driver, By selector, long timeoutSec) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSec));
            wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(selector));
            return driver.findElements(selector);
        } catch (TimeoutException te) {
            return Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static Optional<WebElement> safeFindElement(WebElement root, By selector) {
        try {
            return Optional.of(root.findElement(selector));
        } catch (NoSuchElementException e) {
            return Optional.empty();
        }
    }

    // ---------- Concrete scrapers (CSS-based) ----------
    private static List<Review> scrapeG2(WebDriver driver, String company, LocalDate start, LocalDate end, int maxPages, long pageDelayMs) throws InterruptedException {
        List<Review> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String searchUrl = "https://www.g2.com/search?query=" + java.net.URLEncoder.encode(company, java.nio.charset.StandardCharsets.UTF_8);
        driver.get(searchUrl);

        // find first product link using CSS selector
        List<WebElement> results = safeFindElements(driver, By.cssSelector("a[href*='/products/']"), 5);
        if (results.isEmpty()) return out;
        String productUrl = results.get(0).getAttribute("href");
        if (!productUrl.endsWith("/reviews")) productUrl = productUrl.endsWith("/") ? productUrl + "reviews" : productUrl + "/reviews";

        for (int page = 1; page <= maxPages; page++) {
            String pageUrl = productUrl + (page > 1 ? "?page=" + page : "");
            driver.get(pageUrl);
            Thread.sleep(800);

            // review blocks - selectors are best-effort and may need tweaking
            List<WebElement> blocks = safeFindElements(driver, By.cssSelector("div.paper-review, div.review, li.review-item, article, div[data-testid*='review']"), 5);
            if (blocks.isEmpty()) break;

            for (WebElement b : blocks) {
                try {
                    Review r = new Review();

                    safeFindElement(b, By.cssSelector("h3, .review__title, .headline")).ifPresent(e -> r.title = e.getText().trim());
                    safeFindElement(b, By.cssSelector("div.review__body, .review-body, p, .description")).ifPresent(e -> r.review = e.getText().trim());

                    Optional<WebElement> dateEl = safeFindElement(b, By.cssSelector("time, .review-date, .date, .posted-on"));
                    String rawDate = null;
                    if (dateEl.isPresent()) {
                        WebElement el = dateEl.get();
                        // prefer datetime attribute if present
                        String dtAttr = el.getAttribute("datetime");
                        if (dtAttr != null && !dtAttr.trim().isEmpty()) {
                            rawDate = dtAttr.trim();
                        } else {
                            String txt = el.getText();
                            if (txt != null && !txt.trim().isEmpty()) rawDate = txt.trim();
                        }
                    } else {
                        // fallback: try other selectors inside the review block
                        Optional<WebElement> alt = safeFindElement(b, By.cssSelector(".meta, span"));
                        if (alt.isPresent()) {
                            String at = alt.get().getText();
                            if (at != null && !at.trim().isEmpty()) rawDate = at.trim();
                        }
                    }

                    LocalDate parsed = tryParseIso(rawDate);
                    if (parsed != null && !inRange(parsed, start, end)) continue;
                    r.date = (parsed != null) ? parsed.toString() : rawDate;

                    // reviewer (unchanged)
                    safeFindElement(b, By.cssSelector(".consumer-name, .reviewer, .author, .user"))
                        .ifPresent(e -> r.reviewer = e.getText().trim());

                    // rating (unchanged)
                    safeFindElement(b, By.cssSelector(".rating, [data-rating], .stars, [class*='star']")).ifPresent(e -> {
                        String txt = Optional.ofNullable(e.getAttribute("aria-label")).orElse(e.getText());
                        String digits = txt.replaceAll("[^0-9.]", "");
                        if (!digits.isEmpty()) {
                            try { r.rating = Double.parseDouble(digits); } catch (NumberFormatException ignored) {}
                        }
                    });

                   

                    String key = (r.date != null ? r.date : "") + "|" + (r.review != null ? r.review.substring(0, Math.min(120, r.review.length())) : "");
                    if (seen.add(key)) {
                        r.extra.put("source", "g2");
                        r.extra.put("productPage", productUrl);
                        out.add(r);
                    }
                } catch (Exception ignore) {}
            }

            Thread.sleep(pageDelayMs);
        }

        return out;
    }

    private static List<Review> scrapeCapterra(WebDriver driver, String company, LocalDate start, LocalDate end, int maxPages, long pageDelayMs) throws InterruptedException {
        List<Review> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String searchUrl = "https://www.capterra.com/search?search=" + java.net.URLEncoder.encode(company, java.nio.charset.StandardCharsets.UTF_8);
        driver.get(searchUrl);

        List<WebElement> links = safeFindElements(driver, By.cssSelector("a[href*='/p/'], a[href*='/products/']"), 5);
        if (links.isEmpty()) return out;
        String productUrl = links.get(0).getAttribute("href");
        if (!productUrl.contains("/reviews")) productUrl = productUrl.endsWith("/") ? productUrl + "reviews" : productUrl + "/reviews";

        for (int page = 1; page <= maxPages; page++) {
            String pageUrl = productUrl + (page > 1 ? "?page=" + page : "");
            driver.get(pageUrl);
            Thread.sleep(800);

            List<WebElement> blocks = safeFindElements(driver, By.cssSelector(".review-card, .review, article, li.review, [data-qa='review']"), 5);
            if (blocks.isEmpty()) break;

            for (WebElement b : blocks) {
                try {
                    Review r = new Review();
                    safeFindElement(b, By.cssSelector(".review-title, h3")).ifPresent(e -> r.title = e.getText().trim());
                    safeFindElement(b, By.cssSelector(".review-body, p, .comment")).ifPresent(e -> r.review = e.getText().trim());

                    Optional<WebElement> dateEl = safeFindElement(b, By.cssSelector("time, .date, .review-date"));
                    String rawDate = dateEl.map(WebElement::getText).orElse(null);
                    LocalDate parsed = tryParseIso(rawDate);
                    if (parsed != null && !inRange(parsed, start, end)) continue;
                    r.date = parsed != null ? parsed.toString() : rawDate;

                    safeFindElement(b, By.cssSelector(".reviewer, .user, .author")).ifPresent(e -> r.reviewer = e.getText().trim());

                    safeFindElement(b, By.cssSelector("[class*=star], .rating, [data-rating]")).ifPresent(e -> {
                        String txt = Optional.ofNullable(e.getAttribute("aria-label")).orElse(e.getText());
                        String digits = txt.replaceAll("[^0-9.]", "");
                        if (!digits.isEmpty()) {
                            try { r.rating = Double.parseDouble(digits); } catch (NumberFormatException ignored) {}
                        }
                    });

                    String key = (r.date != null ? r.date : "") + "|" + (r.review != null ? r.review.substring(0, Math.min(120, r.review.length())) : "");
                    if (seen.add(key)) {
                        r.extra.put("source", "capterra");
                        r.extra.put("productPage", productUrl);
                        out.add(r);
                    }
                } catch (Exception ignore) {}
            }

            Thread.sleep(pageDelayMs);
        }

        return out;
    }

    private static List<Review> scrapeTrustRadius(WebDriver driver, String company, LocalDate start, LocalDate end, int maxPages, long pageDelayMs) throws InterruptedException {
        List<Review> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String searchUrl = "https://www.trustradius.com/search?search=" + java.net.URLEncoder.encode(company, java.nio.charset.StandardCharsets.UTF_8);
        driver.get(searchUrl);

        List<WebElement> links = safeFindElements(driver, By.cssSelector("a[href*='/products/'], a[href*='/product/']"), 5);
        if (links.isEmpty()) return out;
        String productUrl = links.get(0).getAttribute("href");
        if (!productUrl.contains("/reviews")) productUrl = productUrl.endsWith("/") ? productUrl + "reviews" : productUrl + "/reviews";

        for (int page = 1; page <= maxPages; page++) {
            String pageUrl = productUrl + (page > 1 ? "?page=" + page : "");
            driver.get(pageUrl);
            Thread.sleep(800);

            List<WebElement> blocks = safeFindElements(driver, By.cssSelector(".review, .tr-review, article, li.review"), 5);
            if (blocks.isEmpty()) break;

            for (WebElement b : blocks) {
                try {
                    Review r = new Review();
                    safeFindElement(b, By.cssSelector(".review-title, h3")).ifPresent(e -> r.title = e.getText().trim());
                    safeFindElement(b, By.cssSelector(".review-body, .pros-cons, p")).ifPresent(e -> r.review = e.getText().trim());

                    Optional<WebElement> dateEl = safeFindElement(b, By.cssSelector("time, .date, .review-date"));
                    String rawDate = dateEl.map(WebElement::getText).orElse(null);
                    LocalDate parsed = tryParseIso(rawDate);
                    if (parsed != null && !inRange(parsed, start, end)) continue;
                    r.date = parsed != null ? parsed.toString() : rawDate;

                    safeFindElement(b, By.cssSelector(".user, .author, .reviewer")).ifPresent(e -> r.reviewer = e.getText().trim());

                    safeFindElement(b, By.cssSelector(".rating, [data-rating], [class*=star]")).ifPresent(e -> {
                        String txt = Optional.ofNullable(e.getAttribute("aria-label")).orElse(e.getText());
                        String digits = txt.replaceAll("[^0-9.]", "");
                        if (!digits.isEmpty()) {
                            try { r.rating = Double.parseDouble(digits); } catch (NumberFormatException ignored) {}
                        }
                    });

                    String key = (r.date != null ? r.date : "") + "|" + (r.review != null ? r.review.substring(0, Math.min(120, r.review.length())) : "");
                    if (seen.add(key)) {
                        r.extra.put("source", "trustradius");
                        r.extra.put("productPage", productUrl);
                        out.add(r);
                    }
                } catch (Exception ignore) {}
            }

            Thread.sleep(pageDelayMs);
        }

        return out;
    }

    // ---------- Runner ----------
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
        } catch (DateTimeParseException ex) {
            System.err.println("Invalid dates, use yyyy-MM-dd");
            return;
        }
        if (end.isBefore(start)) {
            System.err.println("end must be same or after start");
            return;
        }
        String source = args[3].toLowerCase(Locale.ROOT);
        boolean headless = args.length < 5 || !args[4].equalsIgnoreCase("headless=false");
        int maxPages = args.length >= 6 ? Integer.parseInt(args[5]) : 10;
        long delayMs = args.length >= 7 ? Long.parseLong(args[6]) : 800L;

        WebDriver driver = null;
        try {
            driver = DriverManager.createChromeDriver(headless);

            List<Review> reviews;
            switch (source) {
                case "g2":
                    reviews = scrapeG2(driver, company, start, end, maxPages, delayMs);
                    break;
                case "capterra":
                    reviews = scrapeCapterra(driver, company, start, end, maxPages, delayMs);
                    break;
                case "trustradius":
                case "trust":
                    reviews = scrapeTrustRadius(driver, company, start, end, maxPages, delayMs);
                    break;
                default:
                    System.err.println("Unsupported source: " + source);
                    return;
            }

            System.out.println("Collected " + reviews.size() + " reviews.");
            Gson g = new GsonBuilder().setPrettyPrinting().create();
            String filename = String.format("reviews_%s_%s_%s_%s.json", source, company.replaceAll("\\s+","_"), start, end);
            try (FileWriter fw = new FileWriter(filename)) {
                g.toJson(reviews, fw);
            }
            System.out.println("Wrote " + filename);
        } catch (Exception ex) {
            System.err.println("Failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
        } finally {
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }
    }
}
