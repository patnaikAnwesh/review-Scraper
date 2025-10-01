package com.example.reviewscraper.scraper;

import com.example.reviewscraper.model.Review;
import com.example.reviewscraper.util.DateUtils;
import com.example.reviewscraper.util.SelectorConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.time.LocalDate;
import java.util.*;

/**
 * Capterra scraper that reads CSS selectors from resources/config/capterra_selectors.json
 * Uses BaseScraper helper methods (safeFindElements / safeFindElement).
 */
public class CapterraScraper extends BaseScraper {

    private final SelectorConfig cfg;

    public CapterraScraper(WebDriver driver, int maxPages, long pageDelayMs) throws Exception {
        super(driver, maxPages, pageDelayMs);
        this.cfg = new SelectorConfig("capterra_selectors.json");
    }

    @Override
    public List<Review> scrape(String company, LocalDate start, LocalDate end) throws Exception {
        List<Review> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String searchTemplate = Optional.ofNullable(cfg.getString("searchUrlTemplate"))
                .orElse("https://www.capterra.in/search?search={company}");

        String searchUrl = searchTemplate.replace("{company}",
                java.net.URLEncoder.encode(company, java.nio.charset.StandardCharsets.UTF_8));

        // open search page
        driver.get(searchUrl);
        System.out.println("DEBUG: opened searchUrl=" + searchUrl + " current=" + driver.getCurrentUrl() + " title=" + driver.getTitle());

        // find product link using configured selector (support /software/ etc.)
        String productLinkSel = Optional.ofNullable(cfg.getString("productLink"))
                .orElse("a[href*='/reviews/'], a[href*='/software/'], a[href*='/p/'], a[href*='/products/']");
        List<WebElement> links = safeFindElements(By.cssSelector(productLinkSel), 10);
        System.out.println("DEBUG: productLinkSel = " + productLinkSel + " -> found links = " + links.size());
        for (int i = 0; i < Math.min(6, links.size()); i++) {
            try {
                System.out.println("DEBUG: href[" + i + "] = " + links.get(i).getAttribute("href") + " | text=" + links.get(i).getText());
            } catch (Exception ignored) {}
        }
        if (links.isEmpty()) {
            // If search page redirected directly to a product page, try current URL
            String current = driver.getCurrentUrl();
            if (current != null && (current.contains("/reviews/") || current.contains("/software/"))) {
                System.out.println("DEBUG: search redirected to product page: " + current);
                links = new ArrayList<>();
                // create a fake element list by navigating later directly
            } else {
                System.out.println("Capterra: no product links found for: " + company + " using selector: " + productLinkSel);
                return out;
            }
        }

        // choose productUrl (either from first link or currentUrl if redirected)
        String productUrl;
        if (!links.isEmpty()) {
            productUrl = links.get(0).getAttribute("href");
        } else {
            productUrl = driver.getCurrentUrl();
        }

        if (productUrl == null || productUrl.isBlank()) {
            System.out.println("Capterra: product link had no href");
            return out;
        }

        // Normalize to a reviews page where possible:
        // - if already /reviews -> use it
        // - if /software -> prefer product page (as reviews usually embedded), but prepare altReviewsUrl
        // - otherwise append /reviews
        String reviewsUrl = productUrl;
        String altReviewsUrl = null;
        if (!productUrl.contains("/reviews")) {
            if (productUrl.contains("/software/")) {
                reviewsUrl = productUrl; // often reviews are on same /software/ page
                altReviewsUrl = productUrl.replaceFirst("/software/", "/reviews/");
                System.out.println("DEBUG: productUrl looks like /software/; productUrl=" + productUrl + " altReviewsUrl=" + altReviewsUrl);
            } else {
                reviewsUrl = productUrl.endsWith("/") ? productUrl + "reviews" : productUrl + "/reviews";
            }
        }
        System.out.println("DEBUG: chosen productUrl=" + productUrl + " -> using reviewsUrl=" + reviewsUrl);

        // load selectors from config (with safe defaults)
        String reviewBlockSel = Optional.ofNullable(cfg.getString("reviewBlock")).orElse("#reviews > div");
        String titleSel = Optional.ofNullable(cfg.getString("title")).orElse("h3");
        String bodySel = Optional.ofNullable(cfg.getString("body")).orElse("p");
        String dateSel = Optional.ofNullable(cfg.getString("date")).orElse("time, .date");
        String dateAttrPrefer = cfg.getString("dateAttrPrefer"); // e.g., "datetime"
        String ratingSel = Optional.ofNullable(cfg.getString("rating")).orElse("[data-rating], .rating, [class*='star']");
        String ratingAttrPrefer = cfg.getString("ratingAttrPrefer"); // e.g., "aria-label"
        String reviewerSel = Optional.ofNullable(cfg.getString("reviewer")).orElse(".reviewer, .user, .author");

        // pagination loop
        for (int page = 1; page <= this.maxPages; page++) {
            String pageUrl = reviewsUrl + (page > 1 ? "?page=" + page : "");
            driver.get(pageUrl);

            // polite delay and allow JS to render
            try { Thread.sleep(this.pageDelayMs); } catch (InterruptedException ignored) {}

            // scroll to bottom to help lazy-loading
            try {
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(Math.min(1200, this.pageDelayMs));
            } catch (Exception ignored) {}

            // attempt to find review blocks (longer wait)
            List<WebElement> blocks = safeFindElements(By.cssSelector(reviewBlockSel), 15);
            if (blocks.isEmpty()) {
                System.out.println("Capterra: no review blocks found at " + pageUrl + " with selector " + reviewBlockSel);
                // try altReviewsUrl if provided and we haven't tried it yet
                if (altReviewsUrl != null && !altReviewsUrl.equals(reviewsUrl)) {
                    System.out.println("DEBUG: trying altReviewsUrl=" + altReviewsUrl);
                    reviewsUrl = altReviewsUrl;
                    altReviewsUrl = null; // only try once
                    page = 0; // will increment to 1 at loop bottom
                    continue;
                }
                break;
            }

            System.out.println("DEBUG: pageUrl -> blocks found = " + blocks.size());

            for (WebElement block : blocks) {
                try {
                    Review r = new Review();

                    // title
                    safeFindElement(block, By.cssSelector(titleSel)).ifPresent(e -> {
                        String t = e.getText();
                        if (t != null) r.setTitle(t.trim());
                    });

                    // body / review text
                    safeFindElement(block, By.cssSelector(bodySel)).ifPresent(e -> {
                        String txt = e.getText();
                        if (txt != null) r.setReview(txt.trim());
                    });

                    // date: prefer attribute if specified, otherwise text
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
                        // skip if outside range
                        continue;
                    }
                    r.setDate(parsed != null ? parsed.toString() : rawDate);
                    r.getExtra().put("rawDate", rawDate);

                    // reviewer
                    safeFindElement(block, By.cssSelector(reviewerSel)).ifPresent(e -> {
                        String rv = e.getText();
                        if (rv != null) r.setReviewer(rv.trim());
                    });

                    // rating: prefer configured attribute (e.g., aria-label), then data-rating, then text
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

                    // dedupe and add
                    String key = dedupeKey(parsed, r.getReview(), r.getTitle(), r.getReviewer());
                    if (seen.add(key)) {
                        r.getExtra().put("productPage", productUrl);
                        r.getExtra().put("source", "capterra");
                        r.getExtra().put("sourceUrl", pageUrl);
                        out.add(r);
                    }

                } catch (Exception ex) {
                    // log and continue
                    System.err.println("Capterra: error parsing block: " + ex.getMessage());
                }
            }

            // sleep before next page
            try { Thread.sleep(this.pageDelayMs); } catch (InterruptedException ignored) {}
        }

        return out;
    }

    // overload dedupeKey to include title/reviewer (helper)
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
