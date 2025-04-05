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
     * Submit base64-encoded document for training and context building
     * 
     * @param request Request containing base64-encoded document content
     * @return Response with training job information
     */
    @PostMapping("/document/base64")
    public ResponseEntity<Map<String, Object>> trainWithBase64Document(@RequestBody Map<String, Object> request) {
        try {
            // Extract parameters from request
            String contextId = (String) request.get("contextId");
            String contextName = (String) request.get("contextName");
            String fileName = (String) request.get("fileName");
            String base64Content = (String) request.get("content");
            
            // Extract and convert tags if present
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) request.get("tags");
            
            // Validate required fields
            if (fileName == null || fileName.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Filename is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            if (base64Content == null || base64Content.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Base64 content is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Process the document
            boolean success = trainingService.trainWithBase64Document(
                    contextId, contextName, fileName, base64Content, tags);
            
            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "Document training initiated successfully" : "Failed to process document");
            response.put("fileName", fileName);
            response.put("contextId", contextId);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error processing document: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
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
        
        // Base64 document example
        Map<String, Object> base64Example = new HashMap<>();
        base64Example.put("contextId", "your-context-id"); // Optional
        base64Example.put("contextName", "My API Context"); // Optional
        base64Example.put("fileName", "api-spec.yaml");
        base64Example.put("content", "SGVsbG8gV29ybGQ="); // Base64 encoded "Hello World"
        base64Example.put("tags", List.of("api", "specification"));
        
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
        examples.put("base64Document", base64Example);
        examples.put("queryResponse", qaExample);
        examples.put("feedback", feedbackExample);
        examples.put("metadata", metadataExample);
        
        return ResponseEntity.ok(examples);
    }

    @GetMapping("/status/{trainingId}")
    public ResponseEntity<Map<String, Object>> getTrainingStatus(@PathVariable("trainingId") String trainingId) {
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