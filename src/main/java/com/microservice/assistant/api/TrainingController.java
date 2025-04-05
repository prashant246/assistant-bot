package com.microservice.assistant.api;

import com.microservice.assistant.model.TrainingData;
import com.microservice.assistant.model.TrainingData.TrainingType;
import com.microservice.assistant.service.TrainingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for training operations to improve context quality
 */
@RestController
@RequestMapping("/api/training")
public class TrainingController {

    private final TrainingService trainingService;

    @Autowired
    public TrainingController(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    /**
     * Submit training data to improve context quality
     * 
     * Training data can be one of the following types:
     * - DOCUMENT: Add new document content to a context
     * - QUERY_RESPONSE_PAIR: Add a known good Q&A pair to improve answers
     * - FEEDBACK: Provide feedback on previous answers to refine context
     * - METADATA: Add metadata to improve context organization
     * 
     * @param trainingData Data to train with
     * @return Response with training job information
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> train(@RequestBody TrainingData trainingData) {
        boolean success = trainingService.train(trainingData);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "Training initiated successfully" : "Failed to initiate training");
        response.put("type", trainingData.getType().name());
        response.put("contextId", trainingData.getContextId());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Example endpoint showing format for different training types
     * 
     * @return Response with training examples
     */
    @GetMapping("/examples")
    public ResponseEntity<Map<String, Object>> getTrainingExamples() {
        Map<String, Object> examples = new HashMap<>();
        
        // Document training example
        Map<String, Object> documentExample = new HashMap<>();
        documentExample.put("contextId", "your-context-id");
        documentExample.put("type", TrainingType.DOCUMENT.name());
        documentExample.put("content", "# User Service API\n\nThe user service exposes endpoints for user management...");
        documentExample.put("sourceFile", "UserService.md");
        documentExample.put("tags", List.of("api", "documentation", "user-service"));
        
        // Query-Response pair example
        Map<String, Object> qaExample = new HashMap<>();
        qaExample.put("contextId", "your-context-id");
        qaExample.put("type", TrainingType.QUERY_RESPONSE_PAIR.name());
        qaExample.put("content", "What authentication method does the user service use?\n---\nThe user service uses JWT tokens for authentication with a 24-hour expiration.");
        
        // Feedback example
        Map<String, Object> feedbackExample = new HashMap<>();
        feedbackExample.put("contextId", "your-context-id");
        feedbackExample.put("type", TrainingType.FEEDBACK.name());
        feedbackExample.put("content", "4:The answer about authentication was very helpful");
        feedbackExample.put("tags", List.of("authentication", "jwt"));
        
        // Metadata example
        Map<String, Object> metadataExample = new HashMap<>();
        metadataExample.put("contextId", "your-context-id");
        metadataExample.put("type", TrainingType.METADATA.name());
        metadataExample.put("metadata", Map.of(
            "service_name", "User Service",
            "version", "2.3.1",
            "team", "Authentication Team",
            "dependencies", "Postgres, Redis, Auth Service"
        ));
        
        examples.put("document", documentExample);
        examples.put("queryResponse", qaExample);
        examples.put("feedback", feedbackExample);
        examples.put("metadata", metadataExample);
        
        return ResponseEntity.ok(examples);
    }

    @GetMapping("/status/{trainingId}")
    public ResponseEntity<Map<String, Object>> getTrainingStatus(@PathVariable String trainingId) {
        String status = trainingService.getTrainingStatus(trainingId);
        
        Map<String, Object> response = Map.of(
                "trainingId", trainingId,
                "status", status
        );
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/metrics")
    public ResponseEntity<String> getTrainingMetrics(
            @RequestParam(required = false) String contextId) {
        
        String metrics = trainingService.getTrainingMetrics(contextId);
        return ResponseEntity.ok(metrics);
    }
} 