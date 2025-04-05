package com.microservice.assistant.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents training data for the assistant
 */
public class TrainingData {
    private String id;
    private String contextId;
    private String content;
    private String sourceFile;
    private TrainingType type;
    private List<String> tags;
    private Map<String, String> metadata;
    private List<String> keywords;
    private long createdAt;
    private long updatedAt;
    
    public enum TrainingType {
        DOCUMENT,
        QUERY_RESPONSE_PAIR,
        FEEDBACK,
        METADATA,
        KEYWORDS
    }
    
    public TrainingData() {
        this.id = UUID.randomUUID().toString();
        this.tags = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.keywords = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getContextId() {
        return contextId;
    }
    
    public void setContextId(String contextId) {
        this.contextId = contextId;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
        // Extract keywords from content when it's set
        extractKeywords();
        this.updatedAt = System.currentTimeMillis();
    }
    
    public String getSourceFile() {
        return sourceFile;
    }
    
    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }
    
    public TrainingType getType() {
        return type;
    }
    
    public void setType(TrainingType type) {
        this.type = type;
    }
    
    public List<String> getTags() {
        return tags;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public List<String> getKeywords() {
        return keywords;
    }
    
    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }
    
    public void addKeyword(String keyword) {
        if (this.keywords == null) {
            this.keywords = new ArrayList<>();
        }
        if (!this.keywords.contains(keyword)) {
            this.keywords.add(keyword);
        }
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    /**
     * Adds a structured metadata entry
     * @param key The metadata key
     * @param value The metadata value
     */
    public void addMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
        this.updatedAt = System.currentTimeMillis();
    }
    
    /**
     * Extract important keywords from content
     */
    private void extractKeywords() {
        if (content == null || content.isEmpty()) {
            return;
        }
        
        // Simple keyword extraction - in a real implementation, 
        // use NLP for entity extraction, TF-IDF, etc.
        List<String> stopWords = List.of("the", "and", "is", "of", "to", "in", "a", "for", "with");
        String[] words = content.toLowerCase().split("\\W+");
        
        Map<String, Integer> wordFreq = new HashMap<>();
        for (String word : words) {
            if (word.length() > 3 && !stopWords.contains(word)) {
                wordFreq.put(word, wordFreq.getOrDefault(word, 0) + 1);
            }
        }
        
        // Add words that appear multiple times or are in tags
        for (Map.Entry<String, Integer> entry : wordFreq.entrySet()) {
            if (entry.getValue() > 1 || (tags != null && tags.contains(entry.getKey()))) {
                addKeyword(entry.getKey());
            }
        }
        
        // If we're a QA pair, add key terms from the question part
        if (type == TrainingType.QUERY_RESPONSE_PAIR && content.contains("---")) {
            String[] parts = content.split("\\n---\\n", 2);
            if (parts.length == 2) {
                String question = parts[0].trim().toLowerCase();
                for (String word : question.split("\\W+")) {
                    if (word.length() > 3 && !stopWords.contains(word)) {
                        addKeyword(word);
                    }
                }
            }
        }
    }
    
    /**
     * Check if this training data is relevant to a query based on keywords and content
     * @param query The search query
     * @return A relevance score (higher is more relevant)
     */
    public double getRelevanceScore(String query) {
        if (query == null || query.isEmpty() || content == null) {
            return 0.0;
        }
        
        double score = 0.0;
        String lowerQuery = query.toLowerCase();
        String[] queryTerms = lowerQuery.split("\\W+");
        
        // Check keywords match
        for (String keyword : keywords) {
            if (lowerQuery.contains(keyword)) {
                score += 2.0;
            }
        }
        
        // Check content match
        if (content.toLowerCase().contains(lowerQuery)) {
            score += 5.0;
        }
        
        // Check for individual query terms in content
        for (String term : queryTerms) {
            if (term.length() > 3 && content.toLowerCase().contains(term)) {
                score += 1.0;
            }
        }
        
        // Boost score for QA pairs matching the query
        if (type == TrainingType.QUERY_RESPONSE_PAIR) {
            String[] parts = content.split("\\n---\\n", 2);
            if (parts.length == 2) {
                String question = parts[0].trim().toLowerCase();
                if (question.equals(lowerQuery)) {
                    score += 10.0; // Exact match
                } else if (question.contains(lowerQuery) || lowerQuery.contains(question)) {
                    score += 5.0; // Partial match
                }
            }
        }
        
        // Boost score for metadata that might match the query
        if (metadata != null && !metadata.isEmpty()) {
            for (Object value : metadata.values()) {
                if (value instanceof String && ((String) value).toLowerCase().contains(lowerQuery)) {
                    score += 3.0;
                }
            }
        }
        
        return score;
    }
} 