package com.example.reviewscraper.util;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.Random;

/**
 * Utility to add human-like behavior to avoid bot detection.
 */
public class HumanBehavior {
    
    private static final Random random = new Random();
    
    /**
     * Random delay between min and max milliseconds.
     */
    public static void randomDelay(long minMs, long maxMs) {
        try {
            long delay = minMs + (long) (random.nextDouble() * (maxMs - minMs));
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Simulate human-like scrolling on a page.
     */
    public static void humanScroll(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        
        // Get page height
        Long totalHeight = (Long) js.executeScript("return document.body.scrollHeight");
        
        if (totalHeight == null) return;
        
        // Scroll in increments with random delays
        long currentPosition = 0;
        long scrollStep = 300 + random.nextInt(200); // Random scroll amount
        
        while (currentPosition < totalHeight) {
            currentPosition += scrollStep;
            js.executeScript("window.scrollTo(0, " + currentPosition + ");");
            randomDelay(200, 500);
            
            // Occasionally scroll back up a bit (human-like)
            if (random.nextDouble() < 0.1) {
                long backScroll = currentPosition - (100 + random.nextInt(100));
                js.executeScript("window.scrollTo(0, " + backScroll + ");");
                randomDelay(150, 350);
                currentPosition = backScroll;
            }
        }
        
        // Scroll to bottom
        js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
        randomDelay(500, 1000);
    }
    
    /**
     * Simulate mouse movement (using JavaScript).
     */
    public static void simulateMouseMovement(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        
        // Create and dispatch mouse move events
        for (int i = 0; i < 5; i++) {
            int x = random.nextInt(800) + 100;
            int y = random.nextInt(600) + 100;
            
            String script = String.format(
                "var evt = new MouseEvent('mousemove', {" +
                "  clientX: %d," +
                "  clientY: %d," +
                "  bubbles: true" +
                "});" +
                "document.dispatchEvent(evt);",
                x, y
            );
            
            js.executeScript(script);
            randomDelay(100, 300);
        }
    }
    
    /**
     * Wait for page to fully load and appear human-like.
     */
    public static void waitForPageLoad(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        
        // Wait for page ready state
        for (int i = 0; i < 30; i++) {
            String readyState = js.executeScript("return document.readyState").toString();
            if ("complete".equals(readyState)) {
                break;
            }
            randomDelay(200, 400);
        }
        
        // Additional random delay
        randomDelay(1000, 2000);
        
        // Simulate some mouse movement
        simulateMouseMovement(driver);
        
        // Small random scroll
        js.executeScript("window.scrollBy(0, " + (100 + random.nextInt(200)) + ");");
        randomDelay(300, 600);
    }
}