package com.edu.imageconversion.dto;

import java.util.ArrayList;
import java.util.List;

public class NewsAggregateResponse {
    private List<NewsItem> items = new ArrayList<>();
    private long durationMs;
    private int sourcesSucceeded;
    private int sourcesFailed;
    private List<String> errors = new ArrayList<>();

    public List<NewsItem> getItems() {
        return items;
    }

    public void setItems(List<NewsItem> items) {
        this.items = items;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getSourcesSucceeded() {
        return sourcesSucceeded;
    }

    public void setSourcesSucceeded(int sourcesSucceeded) {
        this.sourcesSucceeded = sourcesSucceeded;
    }

    public int getSourcesFailed() {
        return sourcesFailed;
    }

    public void setSourcesFailed(int sourcesFailed) {
        this.sourcesFailed = sourcesFailed;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
