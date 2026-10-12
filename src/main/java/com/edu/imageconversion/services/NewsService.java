package com.edu.imageconversion.services;

import com.edu.imageconversion.dto.NewsAggregateResponse;
import com.edu.imageconversion.dto.NewsItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
public class NewsService {
    private static final Logger logger = LogManager.getLogger(NewsService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int FETCH_TIMEOUT_SECONDS = 15;

    private final ExecutorService executorService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public NewsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.executorService = Executors.newFixedThreadPool(4);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Параллельно собирает новости с нескольких публичных API через Executor Framework.
     */
    public NewsAggregateResponse aggregateNews(String query, int limitPerSource) {
        long startTime = System.currentTimeMillis();

        List<Callable<SourceResult>> tasks = List.of(
                () -> fetchHackerNews(limitPerSource),
                () -> fetchSpaceflightNews(query, limitPerSource),
                () -> fetchDevTo(query, limitPerSource),
                () -> fetchLobsters(limitPerSource)
        );

        List<NewsItem> allItems = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int succeeded = 0;
        int failed = 0;

        try {
            List<Future<SourceResult>> futures = executorService.invokeAll(tasks, FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            for (Future<SourceResult> future : futures) {
                try {
                    if (future.isCancelled()) {
                        failed++;
                        errors.add("Источник не успел ответить в отведённое время");
                        continue;
                    }
                    SourceResult result = future.get();
                    if (result.error() != null) {
                        failed++;
                        errors.add(result.source() + ": " + result.error());
                    } else {
                        succeeded++;
                        allItems.addAll(result.items());
                    }
                } catch (ExecutionException e) {
                    failed++;
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    errors.add("Ошибка выполнения задачи: " + cause.getMessage());
                    logger.error("News fetch task failed", cause);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failed++;
                    errors.add("Ожидание результатов было прервано");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.add("Параллельный сбор новостей был прерван");
        }

        NewsAggregateResponse response = new NewsAggregateResponse();
        response.setItems(allItems);
        response.setDurationMs(System.currentTimeMillis() - startTime);
        response.setSourcesSucceeded(succeeded);
        response.setSourcesFailed(failed);
        response.setErrors(errors);
        return response;
    }

    private SourceResult fetchHackerNews(int limit) {
        String source = "Hacker News";
        try {
            JsonNode ids = getJson("https://hacker-news.firebaseio.com/v0/topstories.json");
            if (ids == null || !ids.isArray()) {
                return SourceResult.failure(source, "Некорректный ответ topstories");
            }

            int count = Math.min(limit, ids.size());
            List<NewsItem> items = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                try {
                    NewsItem item = fetchHackerNewsItem(ids.get(i).asLong());
                    if (item != null) {
                        items.add(item);
                    }
                } catch (Exception ignored) {
                }
            }
            return SourceResult.success(source, items);
        } catch (Exception e) {
            logger.warn("Failed to fetch {}", source, e);
            return SourceResult.failure(source, e.getMessage());
        }
    }

    private NewsItem fetchHackerNewsItem(long id) throws Exception {
        JsonNode item = getJson("https://hacker-news.firebaseio.com/v0/item/" + id + ".json");
        if (item == null || item.get("title") == null) {
            return null;
        }
        String title = item.get("title").asText();
        String url = item.hasNonNull("url")
                ? item.get("url").asText()
                : "https://news.ycombinator.com/item?id=" + id;
        String publishedAt = item.has("time")
                ? Instant.ofEpochSecond(item.get("time").asLong()).toString()
                : null;
        return new NewsItem(title, url, "Hacker News", publishedAt);
    }

    private SourceResult fetchSpaceflightNews(String query, int limit) {
        String source = "Spaceflight News";
        try {
            StringBuilder url = new StringBuilder("https://api.spaceflightnewsapi.net/v4/articles/?limit=")
                    .append(limit);
            if (query != null && !query.isBlank()) {
                url.append("&search=").append(encode(query));
            }

            JsonNode root = getJson(url.toString());
            if (root == null || root.get("results") == null) {
                return SourceResult.failure(source, "Некорректный ответ API");
            }

            List<NewsItem> items = new ArrayList<>();
            for (JsonNode article : root.get("results")) {
                items.add(new NewsItem(
                        textOrEmpty(article, "title"),
                        textOrEmpty(article, "url"),
                        source,
                        textOrNull(article, "published_at")
                ));
            }
            return SourceResult.success(source, items);
        } catch (Exception e) {
            logger.warn("Failed to fetch {}", source, e);
            return SourceResult.failure(source, e.getMessage());
        }
    }

    private SourceResult fetchDevTo(String query, int limit) {
        String source = "Dev.to";
        try {
            String url = (query != null && !query.isBlank())
                    ? "https://dev.to/api/articles?per_page=" + limit + "&tag=" + encode(query.trim().toLowerCase())
                    : "https://dev.to/api/articles?per_page=" + limit;

            JsonNode root = getJson(url);
            if (root == null || !root.isArray()) {
                return SourceResult.failure(source, "Некорректный ответ API");
            }

            List<NewsItem> items = new ArrayList<>();
            for (JsonNode article : root) {
                items.add(new NewsItem(
                        textOrEmpty(article, "title"),
                        textOrEmpty(article, "url"),
                        source,
                        textOrNull(article, "published_at")
                ));
            }
            return SourceResult.success(source, items);
        } catch (Exception e) {
            logger.warn("Failed to fetch {}", source, e);
            return SourceResult.failure(source, e.getMessage());
        }
    }

    private SourceResult fetchLobsters(int limit) {
        String source = "Lobsters";
        try {
            JsonNode root = getJson("https://lobste.rs/hottest.json");
            if (root == null || !root.isArray()) {
                return SourceResult.failure(source, "Некорректный ответ API");
            }

            List<NewsItem> items = new ArrayList<>();
            int count = Math.min(limit, root.size());
            for (int i = 0; i < count; i++) {
                JsonNode story = root.get(i);
                items.add(new NewsItem(
                        textOrEmpty(story, "title"),
                        textOrEmpty(story, "url").isBlank()
                                ? textOrEmpty(story, "comments_url")
                                : textOrEmpty(story, "url"),
                        source,
                        textOrNull(story, "created_at")
                ));
            }
            return SourceResult.success(source, items);
        } catch (Exception e) {
            logger.warn("Failed to fetch {}", source, e);
            return SourceResult.failure(source, e.getMessage());
        }
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "Multithreading-Advanced-NewsAggregator/1.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " for " + url);
        }
        return objectMapper.readTree(response.body());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String textOrEmpty(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : "";
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private record SourceResult(String source, List<NewsItem> items, String error) {
        static SourceResult success(String source, List<NewsItem> items) {
            return new SourceResult(source, items, null);
        }

        static SourceResult failure(String source, String error) {
            return new SourceResult(source, List.of(), error);
        }
    }
}
