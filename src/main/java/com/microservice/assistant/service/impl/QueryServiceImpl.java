package com.microservice.assistant.service.impl;

import com.microservice.assistant.model.Context;
import com.microservice.assistant.model.DocumentSegment;
import com.microservice.assistant.model.Query;
import com.microservice.assistant.model.QueryResponse;
import com.microservice.assistant.service.ContextService;
import com.microservice.assistant.service.QueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QueryServiceImpl implements QueryService {
    
    private final ContextService contextService;
    private final Map<String, QueryResponse> responses = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate;
    
    @Value("${alm.api.url:https://api.rabbithole.cred.club/v1/chat/completions}")
    private String almApiUrl;
    
    @Value("${alm.api.key:sk-t27d_0VNByGL8lon6JDxtw}")
    private String almApiKey;
    
    @Value("${alm.model:gemini-2-0-flash}")
    private String almModel;
    
    @Autowired
    public QueryServiceImpl(ContextService contextService) {
        this.contextService = contextService;
        this.restTemplate = new RestTemplate();
    }
    
    @Override
    public QueryResponse processQuery(Query query) {
        // Find relevant segments from the context
        List<DocumentSegment> relevantSegments = new ArrayList<>();
        
        if (query.getContextId() != null && !query.getContextId().isEmpty()) {
            // Search in specific context
            relevantSegments = contextService.searchContext(
                    query.getContextId(), query.getQuestion(), 10);
        } else if (query.isUseAllContexts()) {
            // Search across all contexts
            for (Context context : contextService.getAllContexts())
            {
                List<DocumentSegment> segments = contextService.searchContext(
                        context.getId(), query.getQuestion(), 5);
                relevantSegments.addAll(segments);
            }
        }
        
        // Call ALM model with context and question
        String answer = generateAnswer(query.getQuestion(), relevantSegments);
        
        // Create and store response
        String responseId = UUID.randomUUID().toString();
        QueryResponse response = new QueryResponse(
                responseId,
                answer,
                query.getQuestion(),
                relevantSegments,
                new HashMap<>(),
                System.currentTimeMillis(),
                0.85 // Placeholder confidence score
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
    
    private String generateAnswer(String question, List<DocumentSegment> segments) {
        // Format context from segments
        StringBuilder contextBuilder = new StringBuilder();
        for (DocumentSegment segment : segments) {
            contextBuilder.append("File: ").append(segment.getSourceFile())
                    .append(" (Lines ").append(segment.getStartLine())
                    .append("-").append(segment.getEndLine())
                    .append(")\n")
                    .append(segment.getContent())
                    .append("\n\n");
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
            if (segments.isEmpty()) {
                return "I don't have enough information to answer that question about your microservices.";
            } else {
                return "Based on the available information about your microservice, I found relevant context " +
                       "but couldn't get a response from the AI model. Please try again later.";
            }
        }
    }
} 