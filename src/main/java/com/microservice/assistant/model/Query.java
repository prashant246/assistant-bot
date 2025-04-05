package com.microservice.assistant.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a query to be processed by the assistant
 */

public class Query {
    private String question;
    private String contextId;
    private Map<String, String> parameters;
    private boolean useAllContexts;

    public Query(String question, String contextId, Map<String, String> parameters, boolean useAllContexts) {
        this.question = question;
        this.contextId = contextId;
        this.parameters = parameters;
        this.useAllContexts = useAllContexts;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getContextId() {
        return contextId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public boolean isUseAllContexts() {
        return useAllContexts;
    }

    public void setUseAllContexts(boolean useAllContexts) {
        this.useAllContexts = useAllContexts;
    }
}