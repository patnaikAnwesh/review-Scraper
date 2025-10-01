package com.example.reviewscraper.scraper;

import com.example.reviewscraper.model.Review;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * Shared helpers for concrete scrapers.
 */
public abstract class BaseScraper implements Scraper {
    protected final WebDriver driver;
    protected final int maxPages;
    protected final long pageDelayMs;

    protected BaseScraper(WebDriver driver, int maxPages, long pageDelayMs) {
        this.driver = driver;
        this.maxPages = maxPages;
        this.pageDelayMs = pageDelayMs;
    }

    protected List<WebElement> safeFindElements(By selector, long timeoutSec) {
        try {
            WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(timeoutSec));
            w.until(ExpectedConditions.presenceOfAllElementsLocatedBy(selector));
            return driver.findElements(selector);
        } catch (TimeoutException te) {
            return Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    protected Optional<WebElement> safeFindElement(WebElement root, By selector) {
        try {
            return Optional.of(root.findElement(selector));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    protected Optional<WebElement> safeFindElement(By selector, long timeoutSec) {
        try {
            WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(timeoutSec));
            w.until(ExpectedConditions.presenceOfElementLocated(selector));
            return Optional.of(driver.findElement(selector));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    protected boolean dateInRange(LocalDate d, LocalDate start, LocalDate end) {
        if (d == null) return false;
        return (!d.isBefore(start)) && (!d.isAfter(end));
    }

    protected String dedupeKey(LocalDate date, String reviewText) {
        String text = reviewText == null ? "" : reviewText.trim();
        return (date != null ? date.toString() : "") + "|" + (text.length()>120 ? text.substring(0,120) : text);
    }
}
