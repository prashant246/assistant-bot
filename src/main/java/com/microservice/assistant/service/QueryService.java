package com.microservice.assistant.service;

import com.microservice.assistant.model.Query;
import com.microservice.assistant.model.QueryResponse;

/**
 * Service for handling user queries and generating responses
 */
public interface QueryService {
    
    /**
     * Process a query and generate a response
     * @param query The query to process
     * @return The generated response
     */
    QueryResponse processQuery(Query query);
    
    /**
     * Get a previous query response by ID
     * @param responseId The response ID
     * @return The query response if found
     */
    QueryResponse getResponse(String responseId);
    
    /**
     * Rate a response for feedback
     * @param responseId The response ID
     * @param rating Rating value (typically 1-5)
     * @param feedback Optional feedback text
     * @return true if rating was recorded successfully
     */
    boolean rateResponse(String responseId, int rating, String feedback);
} 