package com.microservice.assistant.api;

import com.microservice.assistant.model.Query;
import com.microservice.assistant.model.QueryResponse;
import com.microservice.assistant.service.QueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/queries")
public class QueryController {

    private final QueryService queryService;

    @Autowired
    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @PostMapping()
    public ResponseEntity<Object> query(@RequestBody Query query) {
        try {
            QueryResponse response = queryService.processQuery(query);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.of(Optional.of(e.getMessage()));
        }
    }

    @GetMapping("/{responseId}")
    public ResponseEntity<QueryResponse> getResponse(@PathVariable("responseId") String responseId) {
        QueryResponse response = queryService.getResponse(responseId);
        
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{responseId}/feedback")
    public ResponseEntity<Void> provideFeedback(
            @PathVariable("responseId") String responseId,
            @RequestBody Map<String, Object> feedback) {
        
        Integer rating = (Integer) feedback.get("rating");
        String feedbackText = (String) feedback.get("feedback");
        
        if (rating == null || rating < 1 || rating > 5) {
            return ResponseEntity.badRequest().build();
        }
        
        boolean success = queryService.rateResponse(responseId, rating, feedbackText);
        
        if (success) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
} 