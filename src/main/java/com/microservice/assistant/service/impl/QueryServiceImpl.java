package com.microservice.assistant.service.impl;

import com.microservice.assistant.model.*;
import com.microservice.assistant.service.ContextService;
import com.microservice.assistant.service.QueryService;
import com.microservice.assistant.service.TrainingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QueryServiceImpl implements QueryService {
    
    private final ContextService contextService;
    private final TrainingService trainingService;
    private final Map<String, QueryResponse> responses = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate;
    
    @Value("${alm.api.url:https://api.rabbithole.cred.club/v1/chat/completions}")
    private String almApiUrl;
    
    @Value("${alm.api.key:sk-t27d_0VNByGL8lon6JDxtw}")
    private String almApiKey;
    
    @Value("${alm.model:gemini-2-0-flash}")
    private String almModel;
    
    @Autowired
    public QueryServiceImpl(ContextService contextService, TrainingService trainingService) {
        this.contextService = contextService;
        this.trainingService = trainingService;
        this.restTemplate = new RestTemplate();
    }
    
    @Override
    public QueryResponse processQuery(Query query) {
        // Find relevant segments from the context
        List<DocumentSegment> relevantSegments = new ArrayList<>();
        List<TrainingData> relevantTrainingData = new ArrayList<>();
        
        if (query.getContextId() != null && !query.getContextId().isEmpty()) {
            // Search in specific context
            relevantSegments = contextService.searchContext(
                    query.getContextId(), query.getQuestion(), 10);
            
            // Get relevant training data for this context
            relevantTrainingData = trainingService.searchTrainingData(
                    query.getContextId(), query.getQuestion(), 5);
        } else if (query.isUseAllContexts()) {
            // Search across all contexts
            for (Context context: contextService.getAllContexts()){
                List<DocumentSegment> segments = contextService.searchContext(
                        context.getId(), query.getQuestion(), 5);
                relevantSegments.addAll(segments);
                
                // Get relevant training data for each context
                List<TrainingData> trainingData = trainingService.searchTrainingData(
                        context.getId(), query.getQuestion(), 3);
                relevantTrainingData.addAll(trainingData);
            }
        }
        
        // Call ALM model with context and question
        String answer = generateAnswer(query.getQuestion(), relevantSegments, relevantTrainingData);
        
        // Create and store response
        String responseId = UUID.randomUUID().toString();
        QueryResponse response = new QueryResponse(
                responseId,
                answer,
                query.getQuestion(),
                relevantSegments,
                new HashMap<>(),
                System.currentTimeMillis(),
                0.85  // Placeholder confidence score
        );


        responses.put(responseId, response);
        return response;
    }
    
    @Override
    public QueryResponse getResponse(String responseId) {
        return responses.get(responseId);
    }
    
    @Override
    public boolean rateResponse(String responseId, int rating, String feedback) {
        QueryResponse response = responses.get(responseId);
        if (response == null) {
            return false;
        }
        
        // Store feedback in response metadata
        Map<String, Object> metadata = response.getMetadata();
        metadata.put("rating", rating);
        if (feedback != null && !feedback.isEmpty()) {
            metadata.put("feedback", feedback);
        }
        
        // In a real implementation, we would send this feedback to the training service
        return true;
    }
    
    private String generateAnswer(String question, List<DocumentSegment> segments, List<TrainingData> trainingData) {
        // Format context from segments and training data
        StringBuilder contextBuilder = new StringBuilder();
        
        // First add training data as they're often more directly relevant
        if (!trainingData.isEmpty()) {
            contextBuilder.append("### Training Information\n\n");
            
            // Process QA pairs first as they're most directly relevant
            List<TrainingData> qaPairs = trainingData.stream()
                    .filter(td -> td.getType() == TrainingData.TrainingType.QUERY_RESPONSE_PAIR)
                    .collect(Collectors.toList());
            
            if (!qaPairs.isEmpty()) {
                contextBuilder.append("#### Specific Questions and Answers:\n\n");
                for (TrainingData qa : qaPairs) {
                    String[] parts = qa.getContent().split("\\n---\\n", 2);
                    if (parts.length == 2) {
                        contextBuilder.append("Q: ").append(parts[0].trim()).append("\n");
                        contextBuilder.append("A: ").append(parts[1].trim()).append("\n\n");
                    }
                }
            }
            
            // Process document type training data
            List<TrainingData> docs = trainingData.stream()
                    .filter(td -> td.getType() == TrainingData.TrainingType.DOCUMENT)
                    .collect(Collectors.toList());
            
            if (!docs.isEmpty()) {
                contextBuilder.append("#### Additional Information:\n\n");
                for (TrainingData doc : docs) {
                    contextBuilder.append("Source: ").append(doc.getSourceFile()).append("\n");
                    contextBuilder.append(doc.getContent()).append("\n\n");
                }
            }
            
            // Process metadata
            List<TrainingData> metadataEntries = trainingData.stream()
                    .filter(td -> td.getType() == TrainingData.TrainingType.METADATA)
                    .collect(Collectors.toList());
            
            if (!metadataEntries.isEmpty()) {
                contextBuilder.append("#### Service Metadata:\n\n");
                for (TrainingData meta : metadataEntries) {
                    if (meta.getMetadata() != null) {
                        meta.getMetadata().forEach((key, value) -> {
                            contextBuilder.append(key).append(": ").append(value).append("\n");
                        });
                        contextBuilder.append("\n");
                    }
                }
            }
            
            contextBuilder.append("\n\n");
        }
        
        // Then add document segments
        if (!segments.isEmpty()) {
            contextBuilder.append("### Document Segments\n\n");
            for (DocumentSegment segment : segments) {
                contextBuilder.append("File: ").append(segment.getSourceFile())
                        .append(" (Lines ").append(segment.getStartLine())
                        .append("-").append(segment.getEndLine())
                        .append(")\n")
                        .append(segment.getContent())
                        .append("\n\n");
            }
        }
        
        String context = contextBuilder.toString();
        
        try {
            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + almApiKey);
            
            // Prepare the request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", almModel);
            
            List<Map<String, String>> messages = new ArrayList<>();
            
            // System message with context
            if (!context.isEmpty()) {
                Map<String, String> systemMessage = new HashMap<>();
                systemMessage.put("role", "system");
                systemMessage.put("content", "You are a microservice assistant. " +
                        "Use the following context to answer the user's question: \n\n" + context);
                messages.add(systemMessage);
            }
            
            // User question
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", question);
            messages.add(userMessage);
            
            requestBody.put("messages", messages);
            
            // Make the API call
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            Map<String, Object> response = restTemplate.postForObject(almApiUrl, request, Map.class);
            
            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, String> message = (Map<String, String>) choice.get("message");
                    if (message != null && message.containsKey("content")) {
                        return message.get("content");
                    }
                }
            }
            
            return "I encountered an issue while processing your question with the AI model.";
            
        } catch (Exception e) {
            // Fallback to basic response if API call fails
            if (segments.isEmpty() && trainingData.isEmpty()) {
                return "I don't have enough information to answer that question about your microservices.";
            } else {
                return "Based on the available information about your microservice, I found relevant context " +
                       "but couldn't get a response from the AI model. Please try again later.";
            }
        }
    }
} 