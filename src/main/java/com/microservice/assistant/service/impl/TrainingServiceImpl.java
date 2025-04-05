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
        
        // Ensure keywords are extracted from the content if not already done
        if (data.getKeywords() == null || data.getKeywords().isEmpty()) {
            extractAndEnhanceKeywords(data);
        }
        
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
        
        // Add keywords to context metadata
        if (data.getKeywords() != null && !data.getKeywords().isEmpty()) {
            for (String keyword : data.getKeywords()) {
                context.getMetadata().put("keyword_" + keyword, keyword);
            }
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
        
        // Calculate relevance scores and sort by relevance
        Map<TrainingData, Double> scoreMap = new HashMap<>();
        for (TrainingData data : contextTrainingData) {
            double score = data.getRelevanceScore(query);
            
            // Apply type-specific boosts
            switch (data.getType()) {
                case QUERY_RESPONSE_PAIR:
                    score *= 1.5; // Boost QA pairs even more
                    break;
                case METADATA:
                    score *= 1.3; // Boost metadata
                    break;
                case KEYWORDS:
                    score *= 1.4; // Boost explicit keywords
                    break;
                default:
                    break;
            }
            
            scoreMap.put(data, score);
        }
        
        // Sort by score and limit results
        return contextTrainingData.stream()
                .sorted((a, b) -> Double.compare(scoreMap.getOrDefault(b, 0.0), scoreMap.getOrDefault(a, 0.0)))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Extract and enhance keywords from training data
     * @param data The training data to process
     */
    private void extractAndEnhanceKeywords(TrainingData data) {
        // Basic keyword extraction is already implemented in TrainingData.extractKeywords()
        // This method adds domain-specific enhancements
        
        // Add keywords from metadata if available
        if (data.getMetadata() != null) {
            for (Map.Entry<String, String> entry : data.getMetadata().entrySet()) {
                if (entry.getValue() instanceof String) {
                    String value = (String) entry.getValue();
                    if (value.length() > 3 && entry.getKey().contains("name") || 
                            entry.getKey().contains("key") || 
                            entry.getKey().contains("tag") || 
                            entry.getKey().contains("id")) {
                        data.addKeyword(value.toLowerCase());
                    }
                }
            }
        }
        
        // Add keywords from tags if available
        if (data.getTags() != null) {
            for (String tag : data.getTags()) {
                if (tag.length() > 3) {
                    data.addKeyword(tag.toLowerCase());
                }
            }
        }
        
        // For specific content types, extract domain terms
        switch (data.getType()) {
            case DOCUMENT:
                extractMicroserviceTerms(data);
                break;
            case QUERY_RESPONSE_PAIR:
                extractQaPairKeywords(data);
                break;
            case FEEDBACK:
                extractFeedbackKeywords(data);
                break;
            default:
                break;
        }
    }
    
    /**
     * Extract microservice-specific terms from document content
     * @param data The training data
     */
    private void extractMicroserviceTerms(TrainingData data) {
        if (data.getContent() == null) return;
        
        // Look for common microservice patterns
        String content = data.getContent().toLowerCase();
        
        // Service names
        if (content.contains("service") || content.contains("api") || content.contains("endpoint")) {
            extractServiceNames(content, data);
        }
        
        // Database terms
        if (content.contains("database") || content.contains("db") || 
                content.contains("sql") || content.contains("mongo") || 
                content.contains("redis") || content.contains("cache")) {
            extractDatabaseTerms(content, data);
        }
        
        // Deployment terms
        if (content.contains("deploy") || content.contains("kubernetes") || 
                content.contains("docker") || content.contains("container") || 
                content.contains("pod") || content.contains("cluster")) {
            extractDeploymentTerms(content, data);
        }
    }
    
    /**
     * Extract service names from content
     * @param content Lowercase content
     * @param data Training data to add keywords to
     */
    private void extractServiceNames(String content, TrainingData data) {
        // Extract service names (simplified example)
        List<String> patterns = List.of(
            "service", "api", "client", "server", "gateway", "proxy", 
            "load balancer", "database", "cache", "auth", "payment", "user", 
            "profile", "order", "cart", "inventory", "recommendation", "search"
        );
        
        for (String pattern : patterns) {
            int index = content.indexOf(pattern);
            if (index >= 0) {
                // Get surrounding words
                int start = Math.max(0, content.lastIndexOf(" ", index));
                int end = content.indexOf(" ", index + pattern.length());
                if (end < 0) end = content.length();
                
                String serviceWord = content.substring(start, end).trim();
                if (serviceWord.length() > pattern.length()) {
                    data.addKeyword(serviceWord);
                    
                    // Add this as structured metadata
                    data.addMetadata("service_name", serviceWord);
                }
            }
        }
        
        // Look for company and product names using the services
        List<String> companies = List.of(
            "amazon", "flipkart", "walmart", "google", "microsoft", "facebook", 
            "twitter", "netflix", "uber", "airbnb", "linkedin", "paypal"
        );
        
        for (String company : companies) {
            if (content.contains(company)) {
                data.addKeyword(company);
                data.addMetadata("used_by", company);
            }
        }
    }
    
    /**
     * Extract database-related terms
     * @param content Lowercase content
     * @param data Training data to add keywords to
     */
    private void extractDatabaseTerms(String content, TrainingData data) {
        List<String> dbTerms = List.of(
            "sql", "mysql", "postgresql", "oracle", "mongodb", "cassandra", 
            "redis", "memcached", "neo4j", "couchdb", "dynamodb", "cosmosdb"
        );
        
        for (String term : dbTerms) {
            if (content.contains(term)) {
                data.addKeyword(term);
                data.addMetadata("database_tech", term);
            }
        }
    }
    
    /**
     * Extract deployment-related terms
     * @param content Lowercase content
     * @param data Training data to add keywords to
     */
    private void extractDeploymentTerms(String content, TrainingData data) {
        List<String> deployTerms = List.of(
            "kubernetes", "k8s", "docker", "container", "pod", "deployment", 
            "aws", "gcp", "azure", "cloud", "ec2", "ecs", "lambda", "serverless"
        );
        
        for (String term : deployTerms) {
            if (content.contains(term)) {
                data.addKeyword(term);
                data.addMetadata("deployment_tech", term);
            }
        }
    }
    
    /**
     * Extract keywords from QA pairs with special handling
     * @param data Training data
     */
    private void extractQaPairKeywords(TrainingData data) {
        if (data.getContent() == null || !data.getContent().contains("---")) return;
        
        String[] parts = data.getContent().split("\\n---\\n", 2);
        if (parts.length != 2) return;
        
        String question = parts[0].trim().toLowerCase();
        String answer = parts[1].trim().toLowerCase();
        
        // Questions about "where" often indicate location/usage information
        if (question.startsWith("where") || question.contains("where is") || 
                question.contains("where do") || question.contains("where can")) {
            data.addKeyword("location");
            
            // Extract potential location entities from the answer
            extractLocationEntities(answer, data);
        }
        
        // Questions about "how" often indicate process/implementation
        if (question.startsWith("how") || question.contains("how do") || 
                question.contains("how can") || question.contains("how to")) {
            data.addKeyword("implementation");
            data.addKeyword("process");
        }
        
        // Questions about "what" often indicate definition/explanation
        if (question.startsWith("what") || question.contains("what is")) {
            data.addKeyword("definition");
        }
        
        // Questions about "who" often indicate ownership/responsibility
        if (question.startsWith("who") || question.contains("who is")) {
            data.addKeyword("responsibility");
            data.addKeyword("ownership");
        }
    }
    
    /**
     * Extract location entities from text
     * @param text The text to process
     * @param data Training data to add keywords to
     */
    private void extractLocationEntities(String text, TrainingData data) {
        // Simple extraction of location phrases
        List<String> locationIndicators = List.of(
            "in the", "at the", "inside", "within", "deployed to", "hosted on", "used by", "located in"
        );
        
        for (String indicator : locationIndicators) {
            int index = text.indexOf(indicator);
            if (index >= 0) {
                int start = index + indicator.length();
                int end = text.indexOf(".", start);
                if (end < 0) end = Math.min(text.length(), start + 50);
                
                String locationPhrase = text.substring(start, end).trim();
                if (!locationPhrase.isEmpty()) {
                    data.addKeyword(locationPhrase);
                    data.addMetadata("location", locationPhrase);
                    
                    // Check for company names in this phrase
                    List<String> companies = List.of(
                        "amazon", "flipkart", "walmart", "google", "microsoft"
                    );
                    
                    for (String company : companies) {
                        if (locationPhrase.contains(company)) {
                            data.addKeyword(company);
                            data.addMetadata("used_by", company);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Extract keywords from feedback
     * @param data Training data
     */
    private void extractFeedbackKeywords(TrainingData data) {
        if (data.getContent() == null) return;
        
        String content = data.getContent().toLowerCase();
        
        // Extract rating if available
        if (content.contains(":")) {
            String[] parts = content.split(":", 2);
            try {
                int rating = Integer.parseInt(parts[0].trim());
                data.addMetadata("rating", String.valueOf(rating));
                
                // For positive feedback, extract what was correct
                if (rating >= 4 && parts.length > 1) {
                    data.addKeyword("correct_information");
                    extractEntityPhrases(parts[1], data);
                }
                // For negative feedback, extract what was incorrect
                else if (rating <= 2 && parts.length > 1) {
                    data.addKeyword("incorrect_information");
                    extractEntityPhrases(parts[1], data);
                }
            } catch (NumberFormatException ignored) {
                // Not a rating format, treat as general feedback
                extractEntityPhrases(content, data);
            }
        } else {
            // General feedback without rating
            extractEntityPhrases(content, data);
        }
    }
    
    /**
     * Extract likely entity phrases from text
     * @param text The text to process
     * @param data Training data to add keywords to
     */
    private void extractEntityPhrases(String text, TrainingData data) {
        // In a real implementation, use NLP for entity recognition
        // Here we'll use a simplistic approach with common patterns
        
        // Look for quoted text which often contains specific entities
        int startQuote = text.indexOf('"');
        while (startQuote >= 0) {
            int endQuote = text.indexOf('"', startQuote + 1);
            if (endQuote > startQuote) {
                String entity = text.substring(startQuote + 1, endQuote).trim();
                if (!entity.isEmpty() && entity.length() < 50) {
                    data.addKeyword(entity);
                }
                startQuote = text.indexOf('"', endQuote + 1);
            } else {
                break;
            }
        }
        
        // Look for text between "is" and punctuation
        int isIndex = text.indexOf(" is ");
        if (isIndex >= 0) {
            int endEntity = text.indexOf(".", isIndex + 4);
            if (endEntity < 0) endEntity = text.indexOf(",", isIndex + 4);
            if (endEntity < 0) endEntity = text.length();
            
            if (endEntity > isIndex + 4) {
                String entity = text.substring(isIndex + 4, endEntity).trim();
                if (!entity.isEmpty() && entity.length() < 50) {
                    data.addKeyword(entity);
                }
            }
        }
    }
} 