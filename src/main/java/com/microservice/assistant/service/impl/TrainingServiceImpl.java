package com.microservice.assistant.service.impl;

import com.microservice.assistant.model.Context;
import com.microservice.assistant.model.DocumentSegment;
import com.microservice.assistant.model.DocumentSegment.SegmentType;
import com.microservice.assistant.model.QueryResponse;
import com.microservice.assistant.model.TrainingData;
import com.microservice.assistant.model.TrainingData.TrainingType;
import com.microservice.assistant.service.ContextService;
import com.microservice.assistant.service.TrainingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class TrainingServiceImpl implements TrainingService {

    private final Map<String, String> trainingStatus = new ConcurrentHashMap<>();
    private final Map<String, TrainingData> trainingData = new ConcurrentHashMap<>();
    private final ContextService contextService;
    
    @Autowired
    public TrainingServiceImpl(ContextService contextService) {
        this.contextService = contextService;
    }
    
    @Override
    public boolean train(TrainingData data) {
        // Generate training ID for tracking
        String trainingId = UUID.randomUUID().toString();
        trainingStatus.put(trainingId, "QUEUED");
        trainingData.put(trainingId, data);
        
        // Process training in a separate thread to not block the API response
        new Thread(() -> {
            try {
                trainingStatus.put(trainingId, "PROCESSING");
                
                // Process based on training type
                processTrainingData(data);
                
                trainingStatus.put(trainingId, "COMPLETED");
            } catch (Exception e) {
                trainingStatus.put(trainingId, "FAILED: " + e.getMessage());
            }
        }).start();
        
        return true;
    }
    
    @Override
    public boolean trainWithBase64Document(String contextId, String contextName, String fileName, 
                                          String base64Content, List<String> tags) {
        try {
            // Decode the base64 content
            byte[] documentBytes = Base64.getDecoder().decode(base64Content);
            String documentContent = new String(documentBytes);
            
            // Create training data object
            TrainingData data = new TrainingData();
            data.setContextId(contextId);
            data.setContent(documentContent);
            data.setSourceFile(fileName);
            data.setType(TrainingType.DOCUMENT);
            data.setTags(tags);
            
            // Create metadata with source information
            Map<String, String> metadata = new HashMap<>();
            metadata.put("source", "base64");
            metadata.put("contentLength", String.valueOf(documentBytes.length));
            data.setMetadata(metadata);
            
            // Use the standard training process
            return train(data);
        } catch (IllegalArgumentException e) {
            // Failed to decode base64
            return false;
        } catch (Exception e) {
            // Other errors
            return false;
        }
    }
    
    private void processTrainingData(TrainingData data) {
        // Allow creating new contexts if contextId isn't provided but only for DOCUMENT type
        if (data.getContextId() == null || data.getContextId().isEmpty()) {
            if (data.getType() == TrainingType.DOCUMENT) {
                // Generate a context name from source file if available, or use a default
                String contextName = data.getSourceFile() != null ? 
                    "Context for " + data.getSourceFile() : 
                    "Context created at " + System.currentTimeMillis();
                    
                Context newContext = contextService.createContext(contextName);
                // Update the training data with the new context ID
                data.setContextId(newContext.getId());
            } else {
                throw new IllegalArgumentException("Context ID is required for training type: " + data.getType());
            }
        }
        
        // Get context to update
        Context context = contextService.getContext(data.getContextId())
                .orElseThrow(() -> new IllegalArgumentException("Context not found: " + data.getContextId()));
        
        // Process based on training type
        switch (data.getType()) {
            case DOCUMENT:
                processDocumentTraining(context, data);
                break;
            case QUERY_RESPONSE_PAIR:
                processQueryResponseTraining(context, data);
                break;
            case FEEDBACK:
                processFeedbackTraining(context, data);
                break;
            case METADATA:
                processMetadataTraining(context, data);
                break;
            default:
                throw new IllegalArgumentException("Unsupported training type: " + data.getType());
        }
    }
    
    private void processDocumentTraining(Context context, TrainingData data) {
        // Create new document segments from the training content
        List<DocumentSegment> newSegments = parseContentIntoSegments(
                data.getContent(), 
                data.getSourceFile(), 
                determineSegmentType(data.getSourceFile(), data.getContent(), data.getTags())
        );
        
        // Add new segments to context
        context.getSegments().addAll(newSegments);
        context.setUpdatedAt(System.currentTimeMillis());
        
        // Update metadata from training data
        if (data.getMetadata() != null && !data.getMetadata().isEmpty()) {
            context.getMetadata().putAll(data.getMetadata());
        }
    }
    
    private void processQueryResponseTraining(Context context, TrainingData data) {
        // Parse the content to extract query and response
        String[] parts = data.getContent().split("\\n---\\n", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Query-response pair must be formatted as 'query\\n---\\nresponse'");
        }
        
        String query = parts[0].trim();
        String response = parts[1].trim();
        
        // Create a special segment that captures this QA pair
        DocumentSegment qaSegment = new DocumentSegment(
                UUID.randomUUID().toString(),
                "Q: " + query + "\n\nA: " + response,
                data.getSourceFile() != null ? data.getSourceFile() : "qa-pairs.txt",
                1,
                2,
                SegmentType.DOCUMENTATION,
                null
        );
        
        // Add this segment with higher priority/weight in context
        context.getSegments().add(qaSegment);
        context.setUpdatedAt(System.currentTimeMillis());
        
        // Also extract key terms from the query to enhance metadata
        Map<String, String> queryTerms = extractKeyTerms(query);
        if (!queryTerms.isEmpty()) {
            context.getMetadata().putAll(queryTerms);
        }
    }
    
    private void processFeedbackTraining(Context context, TrainingData data) {
        // This is feedback on previous responses
        // We can use this to improve context by:
        // 1. Extracting relevant keywords from positive feedback
        // 2. Removing or reducing priority of segments that lead to negative feedback
        
        if (data.getContent() == null || data.getContent().isEmpty()) {
            return; // No content to process
        }
        
        // Parse feedback format: "rating:comment" (e.g., "4:This answer was helpful")
        String[] parts = data.getContent().split(":", 2);
        if (parts.length != 2) {
            return; // Invalid format
        }
        
        try {
            int rating = Integer.parseInt(parts[0].trim());
            String comment = parts[1].trim();
            
            if (rating >= 4) {
                // Positive feedback - extract key terms to enhance context metadata
                Map<String, String> terms = extractKeyTerms(comment);
                context.getMetadata().putAll(terms);
            } else if (rating <= 2 && data.getTags() != null && !data.getTags().isEmpty()) {
                // Negative feedback - deprioritize certain segments if tags indicate what was problematic
                deprioritizeSegments(context, data.getTags());
            }
            
            context.setUpdatedAt(System.currentTimeMillis());
        } catch (NumberFormatException e) {
            // Invalid rating format, skip processing
        }
    }
    
    private void processMetadataTraining(Context context, TrainingData data) {
        // Simply update the context metadata with the provided metadata
        if (data.getMetadata() != null && !data.getMetadata().isEmpty()) {
            context.getMetadata().putAll(data.getMetadata());
            context.setUpdatedAt(System.currentTimeMillis());
        }
    }
    
    private List<DocumentSegment> parseContentIntoSegments(String content, String fileName, SegmentType type) {
        List<DocumentSegment> segments = new ArrayList<>();
        
        // Split content into logical segments
        // For simplicity, we'll use line count as the segmentation strategy
        // In a real implementation, you'd use smarter segmentation based on context
        String[] lines = content.split("\\n");
        int segmentSize = 20; // Lines per segment
        
        for (int i = 0; i < lines.length; i += segmentSize) {
            int endLine = Math.min(i + segmentSize, lines.length);
            String segmentContent = String.join("\n", 
                    java.util.Arrays.copyOfRange(lines, i, endLine));
            
            // Skip empty segments
            if (segmentContent.trim().isEmpty()) {
                continue;
            }
            
            DocumentSegment segment = new DocumentSegment(
                    UUID.randomUUID().toString(),
                    segmentContent,
                    fileName,
                    i + 1,
                    endLine,
                    type,
                    null
            );
            
            segments.add(segment);
        }
        
        return segments;
    }
    
    private SegmentType determineSegmentType(String fileName, String content, List<String> tags) {
        // First check if tags explicitly specify the type
        if (tags != null && !tags.isEmpty()) {
            for (String tag : tags) {
                tag = tag.toLowerCase();
                if (tag.equals("config") || tag.equals("configuration")) {
                    return SegmentType.CONFIGURATION;
                } else if (tag.equals("code")) {
                    return SegmentType.CODE;
                } else if (tag.equals("api") || tag.equals("endpoint")) {
                    return SegmentType.API_DEFINITION;
                } else if (tag.equals("doc") || tag.equals("documentation")) {
                    return SegmentType.DOCUMENTATION;
                } else if (tag.equals("dependency") || tag.equals("dependencies")) {
                    return SegmentType.DEPENDENCY;
                }
            }
        }
        
        // If tags don't specify type, infer from filename and content
        if (fileName != null) {
            if (fileName.endsWith(".yaml") || fileName.endsWith(".yml") || 
                    fileName.endsWith(".properties") || fileName.endsWith(".json")) {
                return SegmentType.CONFIGURATION;
            } else if (fileName.endsWith(".md") || fileName.endsWith(".txt")) {
                return SegmentType.DOCUMENTATION;
            } else if (fileName.contains("pom.xml") || fileName.contains("build.gradle")) {
                return SegmentType.DEPENDENCY;
            } else if (fileName.contains("api") || fileName.contains("controller") ||
                    content.contains("@RestController") || content.contains("@Controller")) {
                return SegmentType.API_DEFINITION;
            } else if (fileName.endsWith(".java") || fileName.endsWith(".js") || 
                    fileName.endsWith(".py") || fileName.endsWith(".go")) {
                return SegmentType.CODE;
            }
        }
        
        // Default to documentation if we can't determine type
        return SegmentType.DOCUMENTATION;
    }
    
    private Map<String, String> extractKeyTerms(String text) {
        Map<String, String> terms = new HashMap<>();
        
        // This is a simple implementation. In a real system, you would:
        // 1. Use NLP to extract entities and key phrases
        // 2. Filter out stop words
        // 3. Apply more sophisticated extraction algorithms
        
        // For now, we'll just extract words longer than 5 characters that aren't common
        List<String> stopWords = List.of("about", "above", "after", "again", "against", 
                "would", "could", "should", "their", "there", "these", "those", "other");
        
        String[] words = text.split("\\s+");
        for (String word : words) {
            word = word.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (word.length() > 5 && !stopWords.contains(word)) {
                terms.put("term_" + word, word);
            }
        }
        
        return terms;
    }
    
    private void deprioritizeSegments(Context context, List<String> problemTags) {
        // In a real implementation, you might:
        // 1. Mark certain segments as less reliable
        // 2. Adjust their ranking in search results
        // 3. Add metadata to indicate potential issues
        
        // For this example, we'll simply remove segments that match the problem tags
        // (In a real system, you'd likely use a more nuanced approach)
        if (context.getSegments().isEmpty() || problemTags.isEmpty()) {
            return;
        }
        
        List<DocumentSegment> toRemove = context.getSegments().stream()
                .filter(segment -> {
                    for (String tag : problemTags) {
                        if (segment.getContent().toLowerCase().contains(tag.toLowerCase())) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
        
        context.getSegments().removeAll(toRemove);
    }
    
    @Override
    public String getTrainingStatus(String trainingId) {
        return trainingStatus.getOrDefault(trainingId, "NOT_FOUND");
    }
    
    @Override
    public String getTrainingMetrics(String contextId) {
        StringBuilder metricsBuilder = new StringBuilder();
        metricsBuilder.append("{\n");
        metricsBuilder.append("  \"totalTrainingJobs\": ").append(trainingStatus.size()).append(",\n");
        
        long completed = trainingStatus.values().stream()
                .filter(status -> status.equals("COMPLETED"))
                .count();
        
        metricsBuilder.append("  \"completedJobs\": ").append(completed).append(",\n");
        
        if (contextId != null && !contextId.isEmpty()) {
            long contextJobs = trainingData.values().stream()
                    .filter(data -> contextId.equals(data.getContextId()))
                    .count();
            
            metricsBuilder.append("  \"contextJobs\": ").append(contextJobs).append(",\n");
            
            // Add context-specific metrics
            Context context = contextService.getContext(contextId).orElse(null);
            if (context != null) {
                metricsBuilder.append("  \"contextInfo\": {\n");
                metricsBuilder.append("    \"segmentCount\": ").append(context.getSegments().size()).append(",\n");
                metricsBuilder.append("    \"metadataKeys\": ").append(context.getMetadata().size()).append(",\n");
                
                // Count segments by type
                Map<SegmentType, Long> typeCounts = context.getSegments().stream()
                        .collect(Collectors.groupingBy(DocumentSegment::getType, Collectors.counting()));
                
                metricsBuilder.append("    \"segmentTypes\": {\n");
                for (Map.Entry<SegmentType, Long> entry : typeCounts.entrySet()) {
                    metricsBuilder.append("      \"").append(entry.getKey().name()).append("\": ")
                            .append(entry.getValue()).append(",\n");
                }
                metricsBuilder.append("    }\n");
                metricsBuilder.append("  },\n");
            }
        }
        
        // Count by training type
        Map<TrainingType, Long> typeCount = trainingData.values().stream()
                .collect(Collectors.groupingBy(TrainingData::getType, Collectors.counting()));
        
        metricsBuilder.append("  \"trainingTypes\": {\n");
        for (Map.Entry<TrainingType, Long> entry : typeCount.entrySet()) {
            metricsBuilder.append("    \"").append(entry.getKey().name()).append("\": ")
                    .append(entry.getValue()).append(",\n");
        }
        metricsBuilder.append("  }\n");
        
        metricsBuilder.append("}\n");
        
        return metricsBuilder.toString();
    }
    
    @Override
    public List<TrainingData> searchTrainingData(String contextId, String query, int limit) {
        // This method returns training data relevant to the given query
        List<TrainingData> result = new ArrayList<>();
        
        // First collect all training data for this context
        List<TrainingData> contextTrainingData = trainingData.values().stream()
                .filter(data -> contextId.equals(data.getContextId()))
                .collect(Collectors.toList());
        
        if (contextTrainingData.isEmpty()) {
            return result; // No training data for this context
        }
        
        // Prioritize different types of training data
        // 1. First check for exact query matches in QA pairs (highest relevance)
        for (TrainingData data : contextTrainingData) {
            if (data.getType() == TrainingType.QUERY_RESPONSE_PAIR) {
                String[] parts = data.getContent().split("\\n---\\n", 2);
                if (parts.length == 2) {
                    String storedQuery = parts[0].trim().toLowerCase();
                    String userQuery = query.toLowerCase();
                    
                    // Check for exact or close matches
                    if (storedQuery.equals(userQuery) || 
                            storedQuery.contains(userQuery) || 
                            userQuery.contains(storedQuery)) {
                        result.add(data);
                        
                        // If we found an exact match, this is extremely relevant
                        if (storedQuery.equals(userQuery) && result.size() >= limit) {
                            return result;
                        }
                    }
                }
            }
        }
        
        // 2. Check metadata for relevant information
        for (TrainingData data : contextTrainingData) {
            if (data.getType() == TrainingType.METADATA && result.size() < limit) {
                // Check if metadata contains terms relevant to the query
                if (data.getMetadata() != null && !data.getMetadata().isEmpty()) {
                    boolean relevant = data.getMetadata().values().stream()
                            .anyMatch(value -> {
                                if (value instanceof String) {
                                    String strValue = (String) value;
                                    return strValue.toLowerCase().contains(query.toLowerCase());
                                }
                                return false;
                            });
                    
                    if (relevant) {
                        result.add(data);
                    }
                }
            }
        }
        
        // 3. Check document content for relevance
        for (TrainingData data : contextTrainingData) {
            if (data.getType() == TrainingType.DOCUMENT && result.size() < limit) {
                if (data.getContent() != null && 
                        data.getContent().toLowerCase().contains(query.toLowerCase())) {
                    result.add(data);
                }
            }
        }
        
        // 4. Finally, add feedback if it seems relevant
        for (TrainingData data : contextTrainingData) {
            if (data.getType() == TrainingType.FEEDBACK && result.size() < limit) {
                if (data.getContent() != null && 
                        data.getContent().toLowerCase().contains(query.toLowerCase())) {
                    result.add(data);
                }
            }
        }
        
        // Limit results
        return result.stream().limit(limit).collect(Collectors.toList());
    }
} 