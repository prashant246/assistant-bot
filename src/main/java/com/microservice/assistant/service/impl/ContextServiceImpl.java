package com.microservice.assistant.service.impl;

import com.microservice.assistant.model.Context;
import com.microservice.assistant.model.DocumentSegment;
import com.microservice.assistant.service.ContextService;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ContextServiceImpl implements ContextService {
    
    private final Map<String, Context> contexts = new ConcurrentHashMap<>();
    private final Map<String, Directory> indexes = new ConcurrentHashMap<>();
    
    @Override
    public Context createContext(String name) {
        String id = UUID.randomUUID().toString();
        
        Context context = new Context(
                id,
                name,
                new HashMap<>(),
                new ArrayList<>(),
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );

        contexts.put(id, context);
        indexes.put(id, new ByteBuffersDirectory());
        
        return context;
    }
    
    @Override
    public Optional<Context> getContext(String contextId) {
        return Optional.ofNullable(contexts.get(contextId));
    }
    
    @Override
    public List<Context> getAllContexts() {
        return new ArrayList<>(contexts.values());
    }
    
    @Override
    public Context addDocument(String contextId, String fileName, InputStream inputStream) {
        Context context = contexts.get(contextId);
        if (context == null) {
            throw new IllegalArgumentException("Context not found: " + contextId);
        }
        
        try {
            // Read document content
            String content = new BufferedReader(new InputStreamReader(inputStream))
                    .lines().collect(Collectors.joining("\n"));
            
            // Create segments (in a real implementation, we would do more sophisticated parsing)
            List<DocumentSegment> segments = parseDocumentIntoSegments(content, fileName);
            
            // Add segments to context
            context.getSegments().addAll(segments);
            context.setUpdatedAt(System.currentTimeMillis());
            
            // Index the segments
            indexSegments(contextId, segments);
            
            return context;
        } catch (IOException e) {
            throw new RuntimeException("Error processing document", e);
        }
    }
    
    @Override
    public List<DocumentSegment> searchContext(String contextId, String query, int limit) {
        Context context = contexts.get(contextId);
        if (context == null) {
            throw new IllegalArgumentException("Context not found: " + contextId);
        }
        
        Directory directory = indexes.get(contextId);
        try (IndexReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            QueryParser parser = new QueryParser("content", new StandardAnalyzer());
            
            TopDocs docs = searcher.search(parser.parse(query), limit);
            List<DocumentSegment> results = new ArrayList<>();
            
            for (ScoreDoc scoreDoc : docs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                String segmentId = doc.get("id");
                
                // Find the segment by ID
                context.getSegments().stream()
                        .filter(segment -> segment.getId().equals(segmentId))
                        .findFirst()
                        .ifPresent(results::add);
            }
            
            return results;
        } catch (IOException | ParseException e) {
            throw new RuntimeException("Error searching context", e);
        }
    }
    
    @Override
    public boolean deleteContext(String contextId) {
        contexts.remove(contextId);
        Directory directory = indexes.remove(contextId);
        
        try {
            if (directory != null) {
                directory.close();
            }
            return true;
        } catch (IOException e) {
            throw new RuntimeException("Error closing index", e);
        }
    }
    
    private List<DocumentSegment> parseDocumentIntoSegments(String content, String fileName) {
        List<DocumentSegment> segments = new ArrayList<>();
        
        // Simple implementation - split by lines with some buffer
        String[] lines = content.split("\\n");
        int segmentSize = 20; // Lines per segment
        
        for (int i = 0; i < lines.length; i += segmentSize) {
            int endLine = Math.min(i + segmentSize, lines.length);
            String segmentContent = String.join("\n", 
                    Arrays.copyOfRange(lines, i, endLine));
            
            DocumentSegment segment = new DocumentSegment(
                    UUID.randomUUID().toString(),
                    segmentContent,
                    fileName,
                    i + 1,
                    endLine,
                    determineSegmentType(fileName, segmentContent),
                    null
            );


            segments.add(segment);
        }
        
        return segments;
    }
    
    private DocumentSegment.SegmentType determineSegmentType(String fileName, String content) {
        // Simple heuristic based on file extension and content
        if (fileName.endsWith(".yaml") || fileName.endsWith(".yml") || 
                fileName.endsWith(".properties") || fileName.endsWith(".json")) {
            return DocumentSegment.SegmentType.CONFIGURATION;
        } else if (fileName.endsWith(".md") || fileName.endsWith(".txt")) {
            return DocumentSegment.SegmentType.DOCUMENTATION;
        } else if (fileName.contains("pom.xml") || fileName.contains("build.gradle")) {
            return DocumentSegment.SegmentType.DEPENDENCY;
        } else if (fileName.contains("api") || fileName.contains("controller") ||
                content.contains("@RestController") || content.contains("@Controller")) {
            return DocumentSegment.SegmentType.API_DEFINITION;
        } else if (fileName.endsWith(".java") || fileName.endsWith(".js") || 
                fileName.endsWith(".py") || fileName.endsWith(".go")) {
            return DocumentSegment.SegmentType.CODE;
        } else {
            return DocumentSegment.SegmentType.OTHER;
        }
    }
    
    private void indexSegments(String contextId, List<DocumentSegment> segments) throws IOException {
        Directory directory = indexes.get(contextId);
        StandardAnalyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        
        // Append to existing index
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            for (DocumentSegment segment : segments) {
                Document doc = new Document();
                
                doc.add(new StringField("id", segment.getId(), Field.Store.YES));
                doc.add(new TextField("content", segment.getContent(), Field.Store.NO));
                doc.add(new StringField("sourceFile", segment.getSourceFile(), Field.Store.YES));
                doc.add(new StringField("type", segment.getType().name(), Field.Store.YES));
                
                writer.addDocument(doc);
            }
            
            writer.commit();
        }
    }
} 