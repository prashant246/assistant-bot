package com.microservice.assistant.api;

import com.microservice.assistant.model.Context;
import com.microservice.assistant.model.DocumentSegment;
import com.microservice.assistant.service.ContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contexts")
public class ContextController {

    private final ContextService contextService;

    @Autowired
    public ContextController(ContextService contextService) {
        this.contextService = contextService;
    }

    @PostMapping
    public ResponseEntity<Context> createContext(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        if (name == null || name.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        Context context = contextService.createContext(name);
        return ResponseEntity.status(HttpStatus.CREATED).body(context);
    }

    @GetMapping
    public ResponseEntity<List<Context>> getAllContexts() {
        return ResponseEntity.ok(contextService.getAllContexts());
    }

    @GetMapping("/{contextId}")
    public ResponseEntity<Context> getContext(@PathVariable("contextId") String contextId) {
        return contextService.getContext(contextId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{contextId}/documents")
    public ResponseEntity<Context> addDocument(
            @PathVariable("contextId") String contextId,
            @RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            Context context = contextService.addDocument(
                    contextId, file.getOriginalFilename(), file.getInputStream());
            return ResponseEntity.ok(context);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/documents/bytes")
    public ResponseEntity<Context> addDocumentBytes(
            @RequestParam(value = "contextId", required = false) String contextId,
            @RequestParam("fileName") String fileName,
            @RequestParam(value = "contextName", required = false) String contextName,
            @RequestBody byte[] documentBytes) {
        
        try {
            // Create context if it doesn't exist
            if (contextId == null || contextId.isEmpty()) {
                if (contextName == null || contextName.isEmpty()) {
                    contextName = "Context for " + fileName;
                }
                Context newContext = contextService.createContext(contextName);
                contextId = newContext.getId();
            }
            
            // Add document to context
            Context context = contextService.addDocument(
                    contextId, fileName, new ByteArrayInputStream(documentBytes));
            return ResponseEntity.ok(context);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/documents/base64")
    public ResponseEntity<Context> addDocumentBase64(
            @RequestBody Map<String, String> request) {
        
        try {
            // Extract parameters from request
            String contextId = request.get("contextId");
            String fileName = request.get("fileName");
            String contextName = request.get("contextName");
            String base64Content = request.get("content");
            
            // Validate required fields
            if (fileName == null || fileName.isEmpty()) {
                return ResponseEntity.badRequest().body(null);
            }
            
            if (base64Content == null || base64Content.isEmpty()) {
                return ResponseEntity.badRequest().body(null);
            }
            
            // Decode base64 content
            byte[] documentBytes;
            try {
                documentBytes = Base64.getDecoder().decode(base64Content);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(null);
            }
            
            // Create context if it doesn't exist
            if (contextId == null || contextId.isEmpty()) {
                if (contextName == null || contextName.isEmpty()) {
                    contextName = "Context for " + fileName;
                }
                Context newContext = contextService.createContext(contextName);
                contextId = newContext.getId();
            }
            
            // Add document to context
            Context context = contextService.addDocument(
                    contextId, fileName, new ByteArrayInputStream(documentBytes));
            return ResponseEntity.ok(context);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{contextId}/search")
    public ResponseEntity<List<DocumentSegment>> searchContext(
            @PathVariable("contextId") String contextId,
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        
        try {
            List<DocumentSegment> segments = contextService.searchContext(contextId, query, limit);
            return ResponseEntity.ok(segments);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{contextId}")
    public ResponseEntity<Void> deleteContext(@PathVariable("contextId") String contextId) {
        boolean deleted = contextService.deleteContext(contextId);
        
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
} 