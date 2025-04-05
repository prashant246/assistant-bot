package com.microservice.assistant.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a response to a query from the assistant
 */

public class QueryResponse {
    private String id;
    private String answer;
    private String queryId;
    private List<DocumentSegment> relevantSegments;
    private Map<String, Object> metadata;
    private long timestamp;
    private double confidence;

    public QueryResponse(String id, String answer, String queryId, List<DocumentSegment> relevantSegments, Map<String, Object> metadata, long timestamp, double confidence) {
        this.id = id;
        this.answer = answer;
        this.queryId = queryId;
        this.relevantSegments = relevantSegments;
        this.metadata = metadata;
        this.timestamp = timestamp;
        this.confidence = confidence;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getQueryId() {
        return queryId;
    }

    public void setQueryId(String queryId) {
        this.queryId = queryId;
    }

    public List<DocumentSegment> getRelevantSegments() {
        return relevantSegments;
    }

    public void setRelevantSegments(List<DocumentSegment> relevantSegments) {
        this.relevantSegments = relevantSegments;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}