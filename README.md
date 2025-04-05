# Microservice Assistant

An AI-powered assistant that helps developers understand and work with microservices by processing microservice information and providing contextual responses.

## Features

- **Context Building**: Upload and process files containing microservice information
- **Query Mode**: Ask questions about your microservices and get informed answers
- **Enhanced Training Mode**: Continuously improve context quality through multiple training methods
- **Document Segmentation**: Automatically breaks down documents into relevant segments
- **Semantic Search**: Find relevant information using natural language queries
- **ALM Integration**: Connects to external AI models for enhanced responses

## Technical Stack

- Java 17
- Spring Boot 3.2
- Apache Lucene for text indexing and search
- Apache PDFBox and POI for document parsing
- External AI integration via Gemini 2.0 Flash model

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.6+
- ALM API access token (provided by default)

### Building the Application

```bash
mvn clean install
```

### Running the Application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080/microservice-assistant`

### Configuration

Key configuration parameters in `application.properties`:

```properties
# ALM API configuration
alm.api.url=https://api.rabbithole.cred.club/v1/chat/completions
alm.api.key=sk-t27d_0VNByGL8lon6JDxtw
alm.model=gemini-2-0-flash
```

## API Endpoints

### Context Management

- `POST /api/contexts` - Create a new context
- `GET /api/contexts` - List all contexts
- `GET /api/contexts/{contextId}` - Get context details
- `POST /api/contexts/{contextId}/documents` - Upload document to context
- `GET /api/contexts/{contextId}/search` - Search within a context
- `DELETE /api/contexts/{contextId}` - Delete a context

### Queries

- `POST /api/queries` - Submit a query
- `GET /api/queries/{responseId}` - Get a previous response
- `POST /api/queries/{responseId}/feedback` - Rate a response

### Training

- `POST /api/training` - Submit training data
- `GET /api/training/examples` - Get example training data formats
- `GET /api/training/status/{trainingId}` - Check training status
- `GET /api/training/metrics` - Get training metrics

## Enhanced Training Capabilities

The assistant supports several training methods to continuously improve context quality:

### 1. Document Training

Add new documents to existing contexts with optional tagging for better classification.

```json
{
  "contextId": "your-context-id",
  "type": "DOCUMENT",
  "content": "# User Service API\n\nThe user service exposes endpoints...",
  "sourceFile": "UserService.md",
  "tags": ["api", "documentation", "user-service"]
}
```

### 2. Query-Response Pair Training

Teach the assistant with known good question/answer pairs:

```json
{
  "contextId": "your-context-id",
  "type": "QUERY_RESPONSE_PAIR",
  "content": "What authentication method does the user service use?\n---\nThe user service uses JWT tokens for authentication with a 24-hour expiration."
}
```

### 3. Feedback-Based Training

Improve context based on user feedback:

```json
{
  "contextId": "your-context-id",
  "type": "FEEDBACK",
  "content": "4:The answer about authentication was very helpful",
  "tags": ["authentication", "jwt"]
}
```

### 4. Metadata Enhancement

Add structured metadata to improve context organization:

```json
{
  "contextId": "your-context-id",
  "type": "METADATA",
  "metadata": {
    "service_name": "User Service",
    "version": "2.3.1",
    "team": "Authentication Team",
    "dependencies": "Postgres, Redis, Auth Service"
  }
}
```

## How It Works

### Context Building

When you upload documents about your microservices, the system:
1. Parses and segments the documents
2. Classifies segments by type (code, configuration, API, etc.)
3. Indexes the segments for efficient retrieval 

### Training Process

1. **Document Training**: Adds new content segments to existing contexts
2. **Query-Response Training**: Stores exemplar QA pairs to improve response quality
3. **Feedback Training**: Uses feedback to enhance metadata and adjust segment priority
4. **Metadata Training**: Adds structured information to improve context relevance

### Query Processing

When you ask a question, the system:
1. Searches for relevant segments in the specified context
2. Formats these segments as context
3. Sends the context along with your question to the ALM model (Gemini 2.0)
4. Returns the AI-generated response

## Future Improvements

- Integration with multiple AI models
- Enhanced document parsing and segmentation
- Knowledge graph visualization
- Integration with CI/CD pipelines
- Support for more document formats
- Automated context optimization based on usage patterns
