package com.example.reviewscraper.scraper;

import com.example.reviewscraper.model.Review;
import com.example.reviewscraper.util.DateUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.time.LocalDate;
import java.util.*;

/**
 * G2 scraper using CSS selectors and Selenium.
 * You may need to tweak CSS selectors for real pages.
 */
public class G2Scraper extends BaseScraper {

    public G2Scraper(WebDriver driver, int maxPages, long delayMs) {
        super(driver, maxPages, delayMs);
    }

    @Override
    public List<Review> scrape(String company, LocalDate start, LocalDate end) throws Exception {
        List<Review> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String searchUrl = "https://www.g2.com/search?query=" + java.net.URLEncoder.encode(company, java.nio.charset.StandardCharsets.UTF_8);
        driver.get(searchUrl);

        List<WebElement> results = safeFindElements(By.cssSelector("a[href*='/products/']"), 5);
        if (results.isEmpty()) return out;

        String productUrl = results.get(0).getAttribute("href");
        if (!productUrl.endsWith("/reviews")) productUrl = productUrl.endsWith("/") ? productUrl + "reviews" : productUrl + "/reviews";

        for (int page = 1; page <= maxPages; page++) {
            String pageUrl = productUrl + (page>1 ? "?page=" + page : "");
            driver.get(pageUrl);
            Thread.sleep(pageDelayMs);

            List<WebElement> blocks = safeFindElements(By.cssSelector("div.paper-review, div.review, li.review-item, article, div[data-testid*='review']"), 5);
            if (blocks.isEmpty()) break;

            for (WebElement b : blocks) {
                try {
                    Review r = new Review();
                    safeFindElement(b, By.cssSelector("h3, .review__title, .headline")).ifPresent(e -> r.setTitle(e.getText().trim()));
                    safeFindElement(b, By.cssSelector("div.review__body, .review-body, p, .description")).ifPresent(e -> r.setReview(e.getText().trim()));

                    Optional<WebElement> dateEl = safeFindElement(b, By.cssSelector("time, .review-date, .date, .posted-on"));
                    String rawDate = null;
                    if (dateEl.isPresent()) {
                        WebElement el = dateEl.get();
                        String dt = el.getAttribute("datetime");
                        rawDate = (dt != null && !dt.isBlank()) ? dt.trim() : el.getText().trim();
                    } else {
                        Optional<WebElement> alt = safeFindElement(b, By.cssSelector(".meta, span"));
                        if (alt.isPresent()) rawDate = alt.get().getText().trim();
                    }

                    LocalDate parsed = DateUtils.parse(rawDate);
                    if (parsed != null && !dateInRange(parsed, start, end)) continue;
                    r.setDate(parsed != null ? parsed.toString() : rawDate);

                    safeFindElement(b, By.cssSelector(".consumer-name, .reviewer, .author, .user")).ifPresent(e -> r.setReviewer(e.getText().trim()));

                    safeFindElement(b, By.cssSelector(".rating, [data-rating], .stars, [class*='star']")).ifPresent(e -> {
                        String txt = Optional.ofNullable(e.getAttribute("aria-label")).orElse(e.getText());
                        String digits = txt.replaceAll("[^0-9.]", "");
                        if (!digits.isEmpty()) {
                            try { r.setRating(Double.parseDouble(digits)); } catch (NumberFormatException ignored) {}
                        }
                    });

                    String key = dedupeKey(parsed, r.getReview());
                    if (seen.add(key)) {
                        r.getExtra().put("productPage", productUrl);
                        r.getExtra().put("source","g2");
                        out.add(r);
                    }
                } catch (Exception ex) {
                    // per-block failures ignored to continue scraping
                }
            }
        }
        return out;
    }
}
