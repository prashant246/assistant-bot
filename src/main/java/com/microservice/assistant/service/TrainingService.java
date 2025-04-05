package com.microservice.assistant.service;

import com.microservice.assistant.model.TrainingData;

import java.util.List;

/**
 * Service for training the assistant with new information
 */
public interface TrainingService {
    
    /**
     * Train the assistant with new data
     * @param trainingData The training data
     * @return true if training was successful
     */
    boolean train(TrainingData trainingData);
    
    /**
     * Train the assistant with base64-encoded document content
     * @param contextId Optional context ID (will create new if not provided)
     * @param contextName Optional name for new context (used if contextId not provided)
     * @param fileName Name of the document file
     * @param base64Content Base64-encoded document content
     * @param tags Optional tags for categorizing the document
     * @return true if training was successful
     */
    boolean trainWithBase64Document(String contextId, String contextName, String fileName, 
                                   String base64Content, List<String> tags);
    
    /**
     * Search for relevant training data matching a query
     * @param contextId The context ID to search within
     * @param query The search query
     * @param limit Maximum number of results to return
     * @return List of training data entries relevant to the query
     */
    List<TrainingData> searchTrainingData(String contextId, String query, int limit);
    
    /**
     * Check training status
     * @param trainingId The training job ID
     * @return A string representing the current status
     */
    String getTrainingStatus(String trainingId);
    
    /**
     * Get metrics about the training process
     * @param contextId Optional context ID to filter metrics
     * @return JSON string with metrics data
     */
    String getTrainingMetrics(String contextId);
} 