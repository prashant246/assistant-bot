package com.microservice.assistant.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a segment of text from a microservice document
 */


public class DocumentSegment {
    private String id;
    private String content;
    private String sourceFile;
    private int startLine;
    private int endLine;
    private SegmentType type;
    private String parentSegmentId;

    public DocumentSegment(String id, String content, String sourceFile, int startLine, int endLine, SegmentType type, String parentSegmentId) {
        this.id = id;
        this.content = content;
        this.sourceFile = sourceFile;
        this.startLine = startLine;
        this.endLine = endLine;
        this.type = type;
        this.parentSegmentId = parentSegmentId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public SegmentType getType() {
        return type;
    }

    public void setType(SegmentType type) {
        this.type = type;
    }

    public String getParentSegmentId() {
        return parentSegmentId;
    }

    public void setParentSegmentId(String parentSegmentId) {
        this.parentSegmentId = parentSegmentId;
    }

    public enum SegmentType {
        CONFIGURATION,
        CODE,
        DOCUMENTATION,
        API_DEFINITION,
        DEPENDENCY,
        OTHER
    }
} 