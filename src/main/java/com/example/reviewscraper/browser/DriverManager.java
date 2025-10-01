package com.example.reviewscraper.browser;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chrome.ChromeDriver;

import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced driver factory with anti-detection measures for Cloudflare.
 */
public class DriverManager {
    
    public static WebDriver createChromeDriver(boolean headless) {
        return createChromeDriver(headless, null);
    }
    
    public static WebDriver createChromeDriver(boolean headless, String proxyServer) {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        
        // Basic options
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-blink-features=AutomationControlled");
        
        // Better user agent (looks more like a real browser)
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        
        // Window size
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--start-maximized");
        
        // Additional anti-detection arguments
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        
        // Proxy support
        if (proxyServer != null && !proxyServer.isEmpty()) {
            options.addArguments("--proxy-server=" + proxyServer);
            System.out.println("Using proxy: " + proxyServer);
        }
        
        // Exclude automation switches
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation", "enable-logging"});
        
        // Set preferences to appear more human-like
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        options.setExperimentalOption("prefs", prefs);
        
        // Additional experimental option to hide webdriver flag
        options.setExperimentalOption("useAutomationExtension", false);
        
        ChromeDriver driver = new ChromeDriver(options);
        
        // Execute CDP commands to further mask automation
        Map<String, Object> params = new HashMap<>();
        params.put("source", 
            "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
            "Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});" +
            "Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']});" +
            "window.chrome = { runtime: {} };"
        );
        driver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params);
        
        driver.manage().window().setSize(new Dimension(1920, 1080));
        
        return driver;
    }
}