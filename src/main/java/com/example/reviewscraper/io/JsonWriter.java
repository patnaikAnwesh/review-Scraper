package com.example.reviewscraper.io;

import com.example.reviewscraper.model.Review;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

/**
 * Small JSON writer utility.
 */
public class JsonWriter {
    private static final Gson G = new GsonBuilder().setPrettyPrinting().create();

    public static File write(List<Review> reviews, String filename) throws Exception {
        File f = new File(filename);
        try (FileWriter fw = new FileWriter(f)) {
            G.toJson(reviews, fw);
        }
        return f;
    }
}
