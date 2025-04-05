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
        Map<String, Double> contextScores = new HashMap<>();
        
        if (query.getContextId() != null && !query.getContextId().isEmpty()) {
            // Search in specific context
            relevantSegments = contextService.searchContext(
                    query.getContextId(), query.getQuestion(), 10);
            
            // Get relevant training data for this context
            relevantTrainingData = trainingService.searchTrainingData(
                    query.getContextId(), query.getQuestion(), 5);
            
            // Score this context based on number and quality of matches
            scoreContext(contextScores, query.getContextId(), relevantSegments, relevantTrainingData);
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
                
                // Score this context based on number and quality of matches
                scoreContext(contextScores, context.getId(), segments, trainingData);
            }
            
            // Sort segments and training data by their context scores
            if (!contextScores.isEmpty()) {
                sortByContextRelevance(relevantSegments, contextScores);
                sortByContextRelevance(relevantTrainingData, contextScores);
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
        
        // Store context scores in metadata for future reference
        if (!contextScores.isEmpty()) {
            response.getMetadata().put("contextScores", contextScores);
        }

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
    
    private void scoreContext(Map<String, Double> scores, String contextId, 
                             List<DocumentSegment> segments, List<TrainingData> trainingData) {
        double score = 0.0;
        
        // Score based on document segments (1 point per segment)
        score += segments.size();
        
        // Score based on training data (weighted by type)
        for (TrainingData data : trainingData) {
            switch (data.getType()) {
                case QUERY_RESPONSE_PAIR:
                    // QA pairs are most valuable
                    score += 3.0;
                    break;
                case METADATA:
                    // Metadata is quite valuable
                    score += 2.0;
                    break;
                case DOCUMENT:
                    // Documents already counted in segments
                    score += 0.5;
                    break;
                case FEEDBACK:
                    // Feedback is valuable for improving responses
                    score += 1.5;
                    break;
                default:
                    score += 0.5;
            }
        }
        
        // Store or update score
        scores.put(contextId, score);
    }
    
    private <T> void sortByContextRelevance(List<T> items, Map<String, Double> contextScores) {
        // For items that have a getContextId method (like TrainingData)
        if (!items.isEmpty() && items.get(0) instanceof TrainingData) {
            List<TrainingData> trainingItems = (List<TrainingData>) items;
            trainingItems.sort((a, b) -> {
                double scoreA = contextScores.getOrDefault(a.getContextId(), 0.0);
                double scoreB = contextScores.getOrDefault(b.getContextId(), 0.0);
                return Double.compare(scoreB, scoreA); // Higher scores first
            });
        }
        // For document segments, we need to extract context differently
        else if (!items.isEmpty() && items.get(0) instanceof DocumentSegment) {
            // Segments don't directly have contextId, would need additional logic to sort them
            // This would require adding contextId to DocumentSegment or tracking it separately
        }
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
                        contextBuilder.append("Key Information:\n");
                        meta.getMetadata().forEach((key, value) -> {
                            contextBuilder.append("- ").append(key).append(": ").append(value).append("\n");
                        });
                        contextBuilder.append("\n");
                    }
                }
            }
            
            // Process feedback for additional insights
            List<TrainingData> feedbackEntries = trainingData.stream()
                    .filter(td -> td.getType() == TrainingData.TrainingType.FEEDBACK && 
                           td.getContent() != null && 
                           td.getContent().contains(":"))
                    .collect(Collectors.toList());
                    
            if (!feedbackEntries.isEmpty()) {
                contextBuilder.append("#### User Feedback and Corrections:\n\n");
                for (TrainingData feedback : feedbackEntries) {
                    String[] parts = feedback.getContent().split(":", 2);
                    if (parts.length == 2) {
                        try {
                            int rating = Integer.parseInt(parts[0].trim());
                            if (rating >= 4) {
                                contextBuilder.append("Confirmed Information: ")
                                    .append(parts[1].trim()).append("\n\n");
                            } else if (rating <= 2) {
                                contextBuilder.append("Corrected Information: ")
                                    .append(parts[1].trim()).append("\n\n");
                            }
                        } catch (NumberFormatException e) {
                            // If not a valid rating format, include as general feedback
                            contextBuilder.append("User Input: ")
                                .append(feedback.getContent()).append("\n\n");
                        }
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
                        "Answer the user's question based on ALL of the following information: \n\n" + 
                        context + 
                        "\n\nWhen information comes from multiple sources, prioritize: " +
                        "1. Specific Q&A pairs that directly address the question" +
                        "2. User feedback and corrections" +
                        "3. Service metadata and confirmed information" +
                        "4. Document segments and other sources" +
                        "\n\nAlways provide the most accurate and comprehensive answer using all available context.");
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