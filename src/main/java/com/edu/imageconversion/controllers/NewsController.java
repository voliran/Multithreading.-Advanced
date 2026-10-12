package com.edu.imageconversion.controllers;

import com.edu.imageconversion.dto.NewsAggregateResponse;
import com.edu.imageconversion.services.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
public class NewsController {
    private static final Logger logger = LogManager.getLogger(NewsController.class);

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @Operation(
            summary = "Aggregate news from multiple sources",
            description = "Параллельно собирает новости с Hacker News, Spaceflight News, Dev.to и Lobsters " +
                    "с помощью Executor Framework (FixedThreadPool) и объединяет их в один ответ."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Новости успешно собраны",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = NewsAggregateResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Некорректные параметры запроса", content = @Content),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера", content = @Content)
    })
    @GetMapping("/aggregate")
    public ResponseEntity<NewsAggregateResponse> aggregateNews(
            @Parameter(description = "Опциональный поисковый запрос / тег")
            @RequestParam(value = "query", required = false) String query,
            @Parameter(description = "Максимум новостей с каждого источника (1–20)")
            @RequestParam(value = "limit", defaultValue = "5") int limit) {

        if (limit < 1 || limit > 20) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        try {
            NewsAggregateResponse response = newsService.aggregateNews(query, limit);
            logger.info(
                    "News aggregation finished in {} ms: {} items, {} sources ok, {} failed",
                    response.getDurationMs(),
                    response.getItems().size(),
                    response.getSourcesSucceeded(),
                    response.getSourcesFailed()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during news aggregation", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
