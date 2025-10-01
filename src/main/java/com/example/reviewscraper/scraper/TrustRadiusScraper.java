package com.example.reviewscraper.scraper;

import com.example.reviewscraper.model.Review;
import com.example.reviewscraper.util.DateUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.time.LocalDate;
import java.util.*;

/**
 * TrustRadius scraper.
 */
public class TrustRadiusScraper extends BaseScraper {

    public TrustRadiusScraper(WebDriver driver, int maxPages, long delayMs) {
        super(driver, maxPages, delayMs);
    }

    @Override
    public List<Review> scrape(String company, LocalDate start, LocalDate end) throws Exception {
        List<Review> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String searchUrl = "https://www.trustradius.com/search?search=" + java.net.URLEncoder.encode(company, java.nio.charset.StandardCharsets.UTF_8);
        driver.get(searchUrl);

        List<WebElement> links = safeFindElements(By.cssSelector("a[href*='/products/'], a[href*='/product/']"), 5);
        if (links.isEmpty()) return out;
        String productUrl = links.get(0).getAttribute("href");
        if (!productUrl.contains("/reviews")) productUrl = productUrl.endsWith("/") ? productUrl + "reviews" : productUrl + "/reviews";

        for (int page = 1; page <= maxPages; page++) {
            String pageUrl = productUrl + (page>1 ? "?page=" + page : "");
            driver.get(pageUrl);
            Thread.sleep(pageDelayMs);

            List<WebElement> blocks = safeFindElements(By.cssSelector(".review, .tr-review, article, li.review"), 5);
            if (blocks.isEmpty()) break;

            for (WebElement b : blocks) {
                try {
                    Review r = new Review();
                    safeFindElement(b, By.cssSelector(".review-title, h3")).ifPresent(e -> r.setTitle(e.getText().trim()));
                    safeFindElement(b, By.cssSelector(".review-body, .pros-cons, p")).ifPresent(e -> r.setReview(e.getText().trim()));

                    Optional<WebElement> dateEl = safeFindElement(b, By.cssSelector("time, .date, .review-date"));
                    String rawDate = dateEl.map(WebElement::getText).orElse(null);
                    LocalDate parsed = DateUtils.parse(rawDate);
                    if (parsed != null && !dateInRange(parsed, start, end)) continue;
                    r.setDate(parsed != null ? parsed.toString() : rawDate);

                    safeFindElement(b, By.cssSelector(".user, .author, .reviewer")).ifPresent(e -> r.setReviewer(e.getText().trim()));

                    safeFindElement(b, By.cssSelector(".rating, [data-rating], [class*=star]")).ifPresent(e -> {
                        String txt = Optional.ofNullable(e.getAttribute("aria-label")).orElse(e.getText());
                        String digits = txt.replaceAll("[^0-9.]", "");
                        if (!digits.isEmpty()) {
                            try { r.setRating(Double.parseDouble(digits)); } catch (NumberFormatException ignored) {}
                        }
                    });

                    String key = dedupeKey(parsed, r.getReview());
                    if (seen.add(key)) {
                        r.getExtra().put("productPage", productUrl);
                        r.getExtra().put("source","trustradius");
                        out.add(r);
                    }

                } catch (Exception ex) {}
            }
        }
        return out;
    }
}
