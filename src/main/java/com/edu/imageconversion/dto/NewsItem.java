package com.edu.imageconversion.dto;

public class NewsItem {
    private String title;
    private String url;
    private String source;
    private String publishedAt;

    public NewsItem() {
    }

    public NewsItem(String title, String url, String source, String publishedAt) {
        this.title = title;
        this.url = url;
        this.source = source;
        this.publishedAt = publishedAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
    }
}
