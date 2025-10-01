package com.example.reviewscraper.scraper;

import com.example.reviewscraper.model.Review;
import com.example.reviewscraper.util.DateUtils;
import com.example.reviewscraper.util.SelectorConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.time.LocalDate;
import java.util.*;

public class CapterraScraper extends BaseScraper {

    private final SelectorConfig cfg;

    public CapterraScraper(WebDriver driver, int maxPages, long pageDelayMs) throws Exception {
        super(driver, maxPages, pageDelayMs);
        this.cfg = new SelectorConfig("capterra_selectors.json");
    }

    @Override
    public List<Review> scrape(String companyOrUrl, LocalDate start, LocalDate end) throws Exception {
        List<Review> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String productUrl;
        
        // Check if input is already a Capterra URL
        if (companyOrUrl.startsWith("http://") || companyOrUrl.startsWith("https://")) {
            System.out.println("DEBUG: Direct URL provided: " + companyOrUrl);
            productUrl = companyOrUrl;
            
            // Navigate directly to the URL
            driver.get(productUrl);
            Thread.sleep(1000);
            System.out.println("DEBUG: Loaded URL, current page: " + driver.getCurrentUrl());
        } else {
            // Original search flow
            String searchTemplate = Optional.ofNullable(cfg.getString("searchUrlTemplate"))
                    .orElse("https://www.capterra.in/search?search={company}");

            String searchUrl = searchTemplate.replace("{company}",
                    java.net.URLEncoder.encode(companyOrUrl, java.nio.charset.StandardCharsets.UTF_8));

            driver.get(searchUrl);
            System.out.println("DEBUG: opened searchUrl=" + searchUrl + " current=" + driver.getCurrentUrl() + " title=" + driver.getTitle());

            String productLinkSel = Optional.ofNullable(cfg.getString("productLink"))
                    .orElse("a[href*='/reviews/'], a[href*='/software/'], a[href*='/p/'], a[href*='/products/']");
            List<WebElement> links = safeFindElements(By.cssSelector(productLinkSel), 10);
            System.out.println("DEBUG: productLinkSel = " + productLinkSel + " -> found links = " + links.size());
            
            if (links.isEmpty()) {
                String current = driver.getCurrentUrl();
                if (current != null && (current.contains("/reviews/") || current.contains("/software/"))) {
                    System.out.println("DEBUG: search redirected to product page: " + current);
                    productUrl = current;
                } else {
                    System.out.println("Capterra: no product links found for: " + companyOrUrl);
                    return out;
                }
            } else {
                productUrl = links.get(0).getAttribute("href");
            }

            if (productUrl == null || productUrl.isBlank()) {
                System.out.println("Capterra: product link had no href");
                return out;
            }
        }

        // Normalize to reviews URL
        String reviewsUrl = productUrl;
        String altReviewsUrl = null;
        
        if (!productUrl.contains("/reviews")) {
            if (productUrl.contains("/software/")) {
                reviewsUrl = productUrl;
                altReviewsUrl = productUrl.replaceFirst("/software/", "/reviews/");
                System.out.println("DEBUG: productUrl is /software/; reviewsUrl=" + reviewsUrl + " altReviewsUrl=" + altReviewsUrl);
            } else {
                reviewsUrl = productUrl.endsWith("/") ? productUrl + "reviews" : productUrl + "/reviews";
            }
        }
        System.out.println("DEBUG: Final reviewsUrl=" + reviewsUrl);

        // Load selectors from config
        String reviewBlockSel = Optional.ofNullable(cfg.getString("reviewBlock")).orElse("#reviews > div");
        String titleSel = Optional.ofNullable(cfg.getString("title")).orElse("h3");
        String bodySel = Optional.ofNullable(cfg.getString("body")).orElse("p");
        String dateSel = Optional.ofNullable(cfg.getString("date")).orElse("time, .date");
        String dateAttrPrefer = cfg.getString("dateAttrPrefer");
        String ratingSel = Optional.ofNullable(cfg.getString("rating")).orElse("[data-rating], .rating, [class*='star']");
        String ratingAttrPrefer = cfg.getString("ratingAttrPrefer");
        String reviewerSel = Optional.ofNullable(cfg.getString("reviewer")).orElse(".reviewer, .user, .author");

        // Pagination loop
        for (int page = 1; page <= this.maxPages; page++) {
            String pageUrl = reviewsUrl + (page > 1 ? "?page=" + page : "");
            driver.get(pageUrl);
            System.out.println("DEBUG: Loading page " + page + ": " + pageUrl);

            try { Thread.sleep(this.pageDelayMs); } catch (InterruptedException ignored) {}

            // Scroll to help with lazy loading
            try {
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(Math.min(1200, this.pageDelayMs));
            } catch (Exception ignored) {}

            List<WebElement> blocks = safeFindElements(By.cssSelector(reviewBlockSel), 15);
            if (blocks.isEmpty()) {
                System.out.println("Capterra: no review blocks found at " + pageUrl + " with selector " + reviewBlockSel);
                
                if (altReviewsUrl != null && !altReviewsUrl.equals(reviewsUrl)) {
                    System.out.println("DEBUG: trying altReviewsUrl=" + altReviewsUrl);
                    reviewsUrl = altReviewsUrl;
                    altReviewsUrl = null;
                    page = 0;
                    continue;
                }
                break;
            }

            System.out.println("DEBUG: Found " + blocks.size() + " review blocks on page " + page);

            for (WebElement block : blocks) {
                try {
                    Review r = new Review();

                    safeFindElement(block, By.cssSelector(titleSel)).ifPresent(e -> {
                        String t = e.getText();
                        if (t != null) r.setTitle(t.trim());
                    });

                    safeFindElement(block, By.cssSelector(bodySel)).ifPresent(e -> {
                        String txt = e.getText();
                        if (txt != null) r.setReview(txt.trim());
                    });

                    Optional<WebElement> dateEl = safeFindElement(block, By.cssSelector(dateSel));
                    String rawDate = null;
                    if (dateEl.isPresent()) {
                        WebElement el = dateEl.get();
                        if (dateAttrPrefer != null && !dateAttrPrefer.isBlank()) {
                            String attr = el.getAttribute(dateAttrPrefer);
                            if (attr != null && !attr.isBlank()) rawDate = attr.trim();
                        }
                        if (rawDate == null) {
                            String t = el.getText();
                            if (t != null) rawDate = t.trim();
                        }
                    }

                    LocalDate parsed = DateUtils.parse(rawDate);
                    if (parsed != null && !dateInRange(parsed, start, end)) {
                        continue;
                    }
                    r.setDate(parsed != null ? parsed.toString() : rawDate);
                    r.getExtra().put("rawDate", rawDate);

                    safeFindElement(block, By.cssSelector(reviewerSel)).ifPresent(e -> {
                        String rv = e.getText();
                        if (rv != null) r.setReviewer(rv.trim());
                    });

                    safeFindElement(block, By.cssSelector(ratingSel)).ifPresent(e -> {
                        String txt = null;
                        if (ratingAttrPrefer != null && !ratingAttrPrefer.isBlank()) {
                            txt = e.getAttribute(ratingAttrPrefer);
                        }
                        if (txt == null || txt.isBlank()) {
                            txt = Optional.ofNullable(e.getAttribute("data-rating")).orElse(e.getText());
                        }
                        if (txt != null) {
                            String digits = txt.replaceAll("[^0-9.]", "");
                            if (!digits.isEmpty()) {
                                try { r.setRating(Double.parseDouble(digits)); } catch (NumberFormatException ignored) {}
                            }
                        }
                    });

                    String key = dedupeKey(parsed, r.getReview(), r.getTitle(), r.getReviewer());
                    if (seen.add(key)) {
                        r.getExtra().put("productPage", productUrl);
                        r.getExtra().put("source", "capterra");
                        r.getExtra().put("sourceUrl", pageUrl);
                        out.add(r);
                    }

                } catch (Exception ex) {
                    System.err.println("Capterra: error parsing block: " + ex.getMessage());
                }
            }

            try { Thread.sleep(this.pageDelayMs); } catch (InterruptedException ignored) {}
        }

        return out;
    }

    private String dedupeKey(LocalDate parsed, String reviewText, String title, String reviewer) {
        StringBuilder sb = new StringBuilder();
        if (parsed != null) sb.append(parsed.toString());
        sb.append("|");
        if (title != null) sb.append(title.trim());
        sb.append("|");
        if (reviewText != null) {
            String snippet = reviewText.trim();
            if (snippet.length() > 120) snippet = snippet.substring(0, 120);
            sb.append(snippet);
        }
        sb.append("|");
        if (reviewer != null) sb.append(reviewer.trim());
        return sb.toString();
    }
}