# HttpCommunicationMod Web Server Migration Plan

## Executive Summary

This document outlines a comprehensive plan to completely replace the current CommunicationMod's stdio-based subprocess communication with a web server architecture using REST endpoints. The migration will eliminate process-launching and stdin/stdout communication entirely, replacing it with an embedded HTTP server that provides `GET /state` and `POST /command` endpoints. The result is HttpCommunicationMod - a complete rewrite with HTTP API.

## Current Architecture Analysis

### Current Implementation Overview

The legacy CommunicationMod operated through the following architecture:

1. **Process Management**: Launches external subprocess specified in configuration
2. **Communication Threads**:
   - `DataReader`: Reads commands from subprocess stdout
   - `DataWriter`: Writes game state to subprocess stdin
3. **State Management**: `GameStateListener` monitors game state changes
4. **Command Processing**: `CommandExecutor` parses and executes incoming commands
5. **State Serialization**: `GameStateConverter` converts game state to JSON

### Current Communication Flow

```
Legacy CommunicationMod → ProcessBuilder → External Process
       ↑                               ↓
   DataWriter ←―――――――――――――――――――――― DataReader
       ↑                               ↓
   JSON State                      Commands
```

### Key Files and Their Roles

- **`CommunicationMod.java`**: Main coordination, process lifecycle management (replaced by HttpCommunicationMod.java)
- **`DataReader.java`**: Reads command messages from subprocess stdout
- **`DataWriter.java`**: Writes game state JSON to subprocess stdin
- **`GameStateConverter.java`**: Serializes current game state to JSON format
- **`CommandExecutor.java`**: Parses command strings and executes game actions
- **`GameStateListener.java`**: Detects game state changes and triggers updates

## Proposed Web Server Architecture

### New Architecture Overview

Complete replacement of subprocess communication with an embedded HTTP server:

```
External Client → HTTP → Web Server → HttpCommunicationMod
                           ↑              ↓
                    GET /state      POST /command
                           ↑              ↓
                   GameStateConverter  CommandExecutor
```

**Key Changes:**
- Remove all subprocess management code
- Remove DataReader and DataWriter threads
- Remove process lifecycle management
- Replace with embedded HTTP server
- Direct HTTP request/response handling

### Required Endpoints

#### `GET /state`
- **Purpose**: Retrieve current game state
- **Response**: JSON representation of current game state
- **Behavior**:
  - Returns immediately with current state
  - Same JSON format as currently sent via stdin
  - No side effects on game state

#### `POST /command`
- **Purpose**: Execute a command in the game
- **Request Body**: Command string (same format as current stdin commands)
- **Response**: JSON with execution result and updated game state
- **Behavior**:
  - Parse and validate command
  - Execute command through existing `CommandExecutor`
  - Return result status and updated game state

### HTTP Server Technology Choice

**Recommended**: Use Java's built-in `com.sun.net.httpserver.HttpServer`
- **Pros**:
  - No additional dependencies
  - Lightweight
  - Simple to integrate
  - Already available in Java 8+
- **Cons**:
  - Basic feature set
  - Manual request/response handling

**Alternative**: Add lightweight HTTP framework dependency
- Options: Spark Java, NanoHTTPD, Jetty
- Trade-off: Additional dependency vs. richer features

## Detailed Implementation Plan

### Phase 1: Core Web Server Implementation

#### 1.1 Create Web Server Components

**New Files to Create:**

1. **`WebServer.java`**
   ```java
   public class WebServer {
       private HttpServer server;
       private int port;

       public void start(int port) { ... }
       public void stop() { ... }
       private void setupEndpoints() { ... }
   }
   ```

2. **`StateHandler.java`** - Handle GET /state requests
   ```java
   public class StateHandler implements HttpHandler {
       public void handle(HttpExchange exchange) { ... }
   }
   ```

3. **`CommandHandler.java`** - Handle POST /command requests
   ```java
   public class CommandHandler implements HttpHandler {
       public void handle(HttpExchange exchange) { ... }
   }
   ```

#### 1.2 Configuration Changes

**Replace Configuration Options:**
- Remove `command` option (no longer needed)
- Remove `runAtGameStart` option (no longer needed)
- Add `webServerPort` (int): Port for web server (default: 8080)
- Add `webServerHost` (string): Host binding (default: "localhost")
- Keep `verbose` option for HTTP request logging

### Phase 2: Integration with Existing Systems

#### 2.1 Create HttpCommunicationMod.java (replacing CommunicationMod.java)

**Changes Required:**

1. **Startup Logic**:
   ```java
   // Replace subprocess startup completely
   public HttpCommunicationMod() {
       BaseMod.subscribe(this);
       // Remove all subprocess-related initialization
       // Remove readQueue, writeQueue, readThread, writeThread
       startWebServer();
   }
   ```

2. **Remove Subprocess Code**:
   - Delete `startExternalProcess()` method
   - Delete `startCommunicationThreads()` method
   - Remove `ProcessBuilder` related code
   - Remove thread management for DataReader/DataWriter
   - Remove all process lifecycle management

3. **State Change Handling**:
   - Remove automatic state sending to subprocess
   - State will be pulled via GET /state instead
   - Keep state change detection for internal consistency

4. **Command Processing**:
   - Remove `receivePreUpdate()` subprocess message reading
   - Commands will arrive via POST /command HTTP requests
   - Reuse existing `CommandExecutor` logic

#### 2.2 Update GameStateListener.java

**Modifications:**
- Remove automatic state broadcasting
- Add method to get current state on demand: `getCurrentState()`
- Maintain state change detection for caching/optimization
- Add state caching mechanism for performance

#### 2.3 Enhance Error Handling

**New Error Response Format:**
```json
{
  "success": false,
  "error": "Error message",
  "error_type": "INVALID_COMMAND|OUT_OF_BOUNDS|GAME_STATE",
  "game_state": { ... } // Current state even on error
}
```

### Phase 3: Advanced Features

#### 3.1 Authentication & Security

**Optional Security Features:**
- API key authentication
- Rate limiting
- CORS configuration for browser clients
- Request logging

#### 3.2 Enhanced API Features

**Additional Endpoints:**
- `GET /health` - Server health check
- `GET /version` - Mod version information
- `POST /reset` - Reset game state
- `GET /commands` - List available commands

#### 3.3 WebSocket Support (Future Enhancement)

**For Real-time Updates:**
- WebSocket endpoint for state change notifications
- Eliminate need for polling GET /state
- Push state changes immediately to connected clients

### Phase 4: Complete Replacement Strategy

#### 4.1 Code Removal

**Files to Delete:**
- `DataReader.java` - No longer needed
- `DataWriter.java` - No longer needed

**Code to Remove from legacy CommunicationMod.java (replaced by HttpCommunicationMod.java):**
- All subprocess management methods
- Thread management for reading/writing
- Process lifecycle code
- Configuration options for subprocess commands

#### 4.2 Documentation Updates

**Update README.md:**
- Complete rewrite focusing on web server architecture
- Document new HTTP endpoints
- Remove all subprocess-related documentation
- Include HTTP client examples instead of subprocess examples

**Create API Documentation:**
- OpenAPI/Swagger specification
- Request/response examples
- Error code documentation

### Phase 5: Testing & Validation

#### 5.1 Testing Strategy

**Unit Tests:**
- Web server endpoint functionality
- Command parsing and execution
- State serialization
- Error handling

**Integration Tests:**
- Full request/response cycle
- Concurrent request handling
- State consistency across requests

**Performance Tests:**
- Response time benchmarks
- Memory usage comparison
- Concurrent client handling

#### 5.2 Example Client Implementation

**Create Reference Clients:**
- Python client using requests library
- Node.js client
- Curl command examples

## Technical Considerations

### Performance Implications

**Current vs. Proposed:**
- **Current**: Persistent connection, streaming communication
- **Proposed**: Request/response, stateless communication
- **Impact**: Slight latency increase per command, but more flexible architecture

**Optimization Strategies:**
- HTTP keep-alive connections
- State caching to avoid regenerating JSON
- Async request handling
- Connection pooling for clients

### Error Handling

**Improved Error Management:**
- Structured error responses
- HTTP status codes for different error types
- Detailed error messages with context
- Graceful degradation

### Configuration Management

**New Configuration:**
```properties
# Web Server Configuration
webServerPort=8080
webServerHost=localhost
webServerAuth=false
webServerApiKey=
verbose=true

# Remove these deprecated options:
# command (no longer used)
# runAtGameStart (no longer used)
# maxInitializationTimeout (no longer used)
```

### Security Considerations

**Potential Risks:**
- Network exposure of game control
- Unauthorized command execution
- Information disclosure

**Mitigation Strategies:**
- Bind to localhost by default
- Optional API key authentication
- Request validation and sanitization
- Rate limiting to prevent abuse

## Dependencies and Requirements

### Required Dependencies

**No New Dependencies** (using built-in HttpServer):
- Java 8+ (already required)
- Existing gson dependency for JSON handling

**Dependencies to Remove:**
- No subprocess management dependencies to remove (all were built-in Java)

**Optional Dependencies** (for enhanced features):
- HTTP framework (Spark, NanoHTTPD) for richer features
- Authentication library if advanced auth needed

### System Requirements

**Runtime Requirements:**
- Same as current: Java 8+, Slay the Spire, ModTheSpire
- Network port availability (configurable)
- No additional system dependencies

## Implementation Timeline

### Phase 1: Foundation (Week 1-2)
- Implement basic web server
- Create core endpoint handlers
- Replace CommunicationMod.java with HttpCommunicationMod.java

### Phase 2: Integration (Week 3-4)
- Complete HttpCommunicationMod integration with web server
- Delete DataReader.java and DataWriter.java
- Update configuration system
- Implement error handling

### Phase 3: Polish (Week 5-6)
- Complete documentation rewrite
- Create HTTP client examples
- Testing and validation

### Phase 4: Release (Week 7-8)
- Final testing
- Bug fixes and improvements
- Release with updated documentation

## Success Criteria

### Functional Requirements
- [ ] GET /state returns accurate game state JSON
- [ ] POST /command executes commands correctly
- [ ] Error handling matches current behavior
- [ ] Performance comparable to current implementation
- [ ] All subprocess functionality completely replaced

### Non-Functional Requirements
- [ ] Response time < 100ms for simple commands
- [ ] Memory usage increase < 10%
- [ ] Support for concurrent clients
- [ ] Reliable error reporting
- [ ] Clear HTTP API documentation

## Risk Assessment

### High Risk
- **Complete Architecture Change**: Replacing entire communication system
- **Performance Degradation**: Monitor response times vs subprocess
- **Security Vulnerabilities**: Implement proper validation for HTTP endpoints

### Medium Risk
- **Configuration Migration**: Users need to update config files
- **Client Code Rewrite**: All existing automation scripts need HTTP updates
- **Thread Safety**: Ensure concurrent request handling

### Low Risk
- **Dependency Management**: Using built-in libraries
- **Platform Compatibility**: Java standard libraries

## Future Enhancements

### Potential Improvements
1. **WebSocket Support**: Real-time state updates
2. **GraphQL API**: More flexible querying
3. **Admin Interface**: Web-based control panel
4. **Metrics Collection**: Performance monitoring
5. **Plugin Architecture**: Extensible command system

### Community Benefits
- **Easier Integration**: Standard HTTP instead of subprocess
- **Language Agnostic**: Any language can communicate via HTTP
- **Better Debugging**: Standard HTTP tools and logging
- **Scalability**: Multiple clients can connect simultaneously
- **Cloud Deployment**: Easier to deploy in containerized environments

## Conclusion

The complete replacement of subprocess-based communication with a web server architecture will modernize the mod as HttpCommunicationMod and provide a cleaner, more maintainable codebase. This is a breaking change that requires all users to update their integration code, but provides significant long-term benefits.

The web server approach offers significant advantages:
- **Simplified Client Development**: Standard HTTP requests instead of subprocess management
- **Better Error Handling**: Structured responses with HTTP status codes
- **Enhanced Debugging**: Standard HTTP tooling and logging
- **Improved Scalability**: Multiple concurrent clients
- **Reduced Complexity**: Eliminates process lifecycle management
- **Modern Architecture**: Aligns with current API development practices

This complete replacement will position HttpCommunicationMod as a modern, maintainable tool that's easier to integrate with and debug. While it requires users to update their automation scripts, the improved developer experience and reliability make this a worthwhile breaking change.