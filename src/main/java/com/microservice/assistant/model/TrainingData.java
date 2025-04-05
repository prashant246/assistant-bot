package com.microservice.assistant.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents training data for the assistant
 */
public class TrainingData {
    private String contextId;
    private String content;
    private String sourceFile;
    private Map<String, String> metadata;
    private List<String> tags;
    private TrainingType type;

    @JsonCreator
    public TrainingData(@JsonProperty("contextId") String contextId, @JsonProperty("content") String content,
                        @JsonProperty("sourceFile") String sourceFile, @JsonProperty("metadata") Map<String, String> metadata,
                        @JsonProperty("tags") List<String> tags, @JsonProperty("type") TrainingType type) {
        this.contextId = contextId;
        this.content = content;
        this.sourceFile = sourceFile;
        this.metadata = metadata;
        this.tags = tags;
        this.type = type;
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
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public TrainingType getType() {
        return type;
    }

    public void setType(TrainingType type) {
        this.type = type;
    }

    public enum TrainingType {
        DOCUMENT,
        QUERY_RESPONSE_PAIR,
        FEEDBACK,
        METADATA
    }
} 