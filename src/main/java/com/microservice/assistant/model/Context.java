package com.microservice.assistant.model;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Represents a context built from microservice documents
 */

public class Context {
    private String id;
    private String name;
    private Map<String, String> metadata;
    private List<DocumentSegment> segments;
    private long createdAt;
    private long updatedAt;

    public Context(String id, String name, Map<String, String> metadata, List<DocumentSegment> segments, long createdAt, long updatedAt) {
        this.id = id;
        this.name = name;
        this.metadata = metadata;
        this.segments = segments;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public List<DocumentSegment> getSegments() {
        return segments;
    }

    public void setSegments(List<DocumentSegment> segments) {
        this.segments = segments;
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
}