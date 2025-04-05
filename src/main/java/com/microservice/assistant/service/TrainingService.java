package com.microservice.assistant.service;

import com.microservice.assistant.model.TrainingData;

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