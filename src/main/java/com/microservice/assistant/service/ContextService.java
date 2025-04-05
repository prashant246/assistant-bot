package com.microservice.assistant.service;

import com.microservice.assistant.model.Context;
import com.microservice.assistant.model.DocumentSegment;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing contexts built from microservice documents
 */
public interface ContextService {
    
    /**
     * Create a new context
     * @param name Context name
     * @return The created context
     */
    Context createContext(String name);
    
    /**
     * Get a context by its ID
     * @param contextId The context ID
     * @return The context if found
     */
    Optional<Context> getContext(String contextId);
    
    /**
     * Get all available contexts
     * @return List of all contexts
     */
    List<Context> getAllContexts();
    
    /**
     * Add document to a context by processing its content
     * @param contextId The context ID
     * @param fileName Original file name
     * @param inputStream Document content stream
     * @return Updated context
     */
    Context addDocument(String contextId, String fileName, InputStream inputStream);
    
    /**
     * Search for relevant segments in a context
     * @param contextId The context ID
     * @param query The search query
     * @param limit Maximum number of results
     * @return List of relevant document segments
     */
    List<DocumentSegment> searchContext(String contextId, String query, int limit);
    
    /**
     * Delete a context
     * @param contextId The context ID
     * @return true if deleted successfully
     */
    boolean deleteContext(String contextId);
} 