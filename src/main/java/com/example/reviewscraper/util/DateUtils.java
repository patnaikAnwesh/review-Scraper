package com.example.reviewscraper.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robust date parsing for absolute and relative date strings.
 */
public class DateUtils {

    public static LocalDate parse(String raw) {
        if (raw == null) return null;
        raw = raw.trim();

        // try common absolute formats
        String[] patterns = new String[] {
                "yyyy-MM-dd",      // 2024-06-18
                "yyyy/MM/dd",      // 2024/06/18
                "MMMM d, yyyy",    // June 18, 2024
                "MMM d, yyyy",     // Jun 18, 2024
                "d MMM yyyy",      // 18 Jun 2024
                "d MMMM yyyy"      // 18 June 2024 (✅ added this)
        };

        for (String p : patterns) {
            try {
                return LocalDate.parse(raw, DateTimeFormatter.ofPattern(p, Locale.ENGLISH));
            } catch (DateTimeParseException ignored) {}
        }

        // ISO standard
        try {
            return LocalDate.parse(raw);
        } catch (Exception ignored) {}

        // relative: "3 months ago", "a year ago"
        Pattern rel = Pattern.compile("(?:a|an|\\d+)\\s+(day|days|month|months|year|years)\\s+ago",
                Pattern.CASE_INSENSITIVE);
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

        // fallback: extract yyyy-mm-dd substring
        Matcher iso = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(raw);
        if (iso.find()) {
            try { return LocalDate.parse(iso.group(1)); } catch (Exception ignored) {}
        }

        return null;
    }
}
