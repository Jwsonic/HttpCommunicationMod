# HttpCommunicationMod

> **This is a fork of the [original CommunicationMod](https://github.com/ForgottenArbiter/CommunicationMod) that has been completely rewritten to use HTTP instead of subprocess communication.**

Slay the Spire mod that provides an HTTP API for allowing external programs to control the game

## Requirements

- Slay the Spire
- ModTheSpire (https://github.com/kiooeht/ModTheSpire)
- BaseMod (https://github.com/daviscook477/BaseMod)

## Setup

1. Copy HttpCommunicationMod.jar to your ModTheSpire mods directory
2. Run ModTheSpire with HttpCommunicationMod enabled
3. The mod will automatically start an HTTP server on `localhost:8080` (configurable via environment variables)
4. Use HTTP requests to interact with the game

## What does this mod do?

HttpCommunicationMod starts an embedded HTTP server and provides REST API endpoints for external programs to monitor and control Slay the Spire. The mod replaces the previous subprocess-based communication with a modern HTTP API.

## API Endpoints


### `GET /state`
Returns the current game state as JSON. This endpoint also automatically logs the game state to the configured log file.

**Example Request:**
```bash
curl http://localhost:8080/state
```

**Example Response:**
```json
{
  "available_commands": ["play", "end", "key", "click", "wait", "state"],
  "ready_for_command": true,
  "in_game": true,
  "game_state": {
    "screen_type": "COMBAT",
    "combat_state": {
      "player": {
        "current_hp": 68,
        "max_hp": 75,
        "energy": 3,
        "block": 0
      },
      "hand": [...],
      "monsters": [...],
      "draw_pile": [...]
    },
    "floor": 1,
    "act": 1,
    "gold": 99,
    "class": "IRONCLAD"
  }
}
```

### `POST /command`
Executes a game command and returns the result along with updated game state. Commands are automatically logged to the configured log file.

**Request:** Plain text command in request body

**Example Request:**
```bash
curl -X POST http://localhost:8080/command -d "play 1"
```

**Success Response:**
```json
{
  "success": true,
  "command": "play 1",
  "state_changed": true,
  "game_state": { ... }
}
```

**Error Response:**
```json
{
  "success": false,
  "error": "Invalid command: play 99. Card index out of bounds.",
  "command": "play 99",
  "game_state": { ... }
}
```

### `GET /health`
Returns server status and metadata for monitoring.

**Example Response:**
```json
{
  "status": "healthy",
  "mod_name": "HTTP Communication Mod",
  "version": "3.0.0",
  "endpoints": ["/state", "/command", "/health"]
}
```

## Available Commands

The HTTP API supports all the same commands as the previous subprocess version:

### Game Control Commands
- **START PlayerClass [AscensionLevel] [Seed]** - Starts a new game
  - Example: `START IRONCLAD 15 ABC123`
- **PLAY CardIndex [TargetIndex]** - Plays a card from hand
  - Example: `PLAY 1 0` (play first card targeting first monster)
- **END** - Ends your turn
- **CHOOSE ChoiceIndex|ChoiceName** - Makes a choice on decision screens
  - Example: `CHOOSE 0` or `CHOOSE Skip`

### Navigation Commands
- **PROCEED/CONFIRM** - Clicks proceed/confirm button
- **RETURN/CANCEL/SKIP/LEAVE** - Clicks return/cancel button

### Potion Commands
- **POTION Use|Discard PotionSlot [TargetIndex]** - Use or discard potions
  - Example: `POTION USE 0 1` (use potion in slot 0 on monster 1)

### Input Commands
- **KEY Keyname [Timeout]** - Simulates key presses
  - Available keys: CONFIRM, CANCEL, MAP, DECK, DRAW_PILE, DISCARD_PILE, etc.
- **CLICK Left|Right X Y [Timeout]** - Simulates mouse clicks
  - Coordinates are in 1920x1080 resolution regardless of actual screen size
- **WAIT Timeout** - Waits for specified frames or state change

### Utility Commands
- **STATE** - Forces immediate state response
- **RESET** - Restarts the game

## Configuration

### Environment Variables

The mod uses environment variables for configuration (no config files are created or used):

- **`HTTP_MOD_PORT`**: Optional. Specifies the port for the web server. If not set, defaults to 8080.
  - Example: `HTTP_MOD_PORT=9000`
  - Must be a valid integer port number

- **`HTTP_MOD_HOST`**: Optional. Specifies the host/IP address for the web server. If not set, defaults to localhost.
  - Example: `HTTP_MOD_HOST=0.0.0.0` (bind to all interfaces)
  - Example: `HTTP_MOD_HOST=127.0.0.1` (localhost only)

- **`HTTP_MOD_LOG_PATH`**: Optional. Specifies the path for logging game state and commands. If not set, defaults to `http_mod.log` in the current directory.
  - Example: `HTTP_MOD_LOG_PATH=/path/to/custom/logfile.log`
  - Supports absolute paths and will create necessary directories
  - Logs are appended with timestamps in format: `[YYYY-MM-DD HH:mm:ss] TYPE: content`

### Configuration System

The mod uses a simple two-tier configuration system:

1. **Environment Variables** (highest priority) - Checked first if set
2. **Built-in Defaults** (fallback) - Used when environment variables are not set

**Built-in Defaults:**
- Port: `8080`
- Host: `localhost`
- Log Path: `http_mod.log`

**No Config Files:** The mod does not use or create any configuration files. All configuration is done through environment variables or defaults, making it perfect for containerized environments, CI/CD systems, and deployments where file system access may be restricted.

### Automatic Logging

The mod automatically logs:
- **Game State**: Every time `/state` is requested (like the old DataWriter)
- **Commands**: Every command received via `/command` (like the old DataReader)

Example log entries:
```
[2025-01-15 14:30:25] GAME_STATE: {"available_commands":["play","end"],"ready_for_command":true,...}
[2025-01-15 14:30:26] COMMAND: play 1
[2025-01-15 14:30:27] GAME_STATE: {"available_commands":["end"],"ready_for_command":true,...}
```

## Client Examples

### Python Example
```python
import requests
import json

# Get current game state
response = requests.get('http://localhost:8080/state')
game_state = response.json()
print(f"Current floor: {game_state['game_state']['floor']}")

# Execute a command
command_response = requests.post('http://localhost:8080/command', data='play 1')
result = command_response.json()
if result['success']:
    print("Command executed successfully!")
else:
    print(f"Command failed: {result['error']}")
```

### JavaScript/Node.js Example
```javascript
const axios = require('axios');

async function getGameState() {
    try {
        const response = await axios.get('http://localhost:8080/state');
        return response.data;
    } catch (error) {
        console.error('Error getting game state:', error);
    }
}

async function executeCommand(command) {
    try {
        const response = await axios.post('http://localhost:8080/command', command);
        return response.data;
    } catch (error) {
        console.error('Error executing command:', error);
    }
}

// Usage
(async () => {
    const state = await getGameState();
    console.log('Current HP:', state.game_state.current_hp);

    const result = await executeCommand('end');
    console.log('End turn result:', result.success);
})();
```

### cURL Examples
```bash
# Get game state
curl http://localhost:8080/state

# Play first card
curl -X POST http://localhost:8080/command -d "play 1"

# End turn
curl -X POST http://localhost:8080/command -d "end"

# Start new game as Silent on Ascension 10
curl -X POST http://localhost:8080/command -d "start silent 10"

# Check server health
curl http://localhost:8080/health
```


## Known Limitations

- The full state of the Match and Keep event is not transmitted
- There is no feedback or state change if you attempt to take or buy a potion while your potion inventory is full
- Unselecting cards in hand select screens is not supported
- Several actions do not currently register a state change if they are performed manually in game
- HttpCommunicationMod has not been tested without fast mode on

## Debugging

### Server Logs
- All HTTP requests and responses are logged to the ModTheSpire log window
- Error details are included in both the log and HTTP error responses
- Set `verbose=false` in config to reduce log output

### Common Issues
- **Connection refused**: Check that the mod is loaded and the server started successfully
- **Port in use**: Set the `HTTP_MOD_PORT` environment variable to a different port
- **Command errors**: Check the HTTP response JSON for detailed error messages
- **State not updating**: Ensure you're polling `/state` after commands that change game state
- **Configuration not working**: Remember this mod uses environment variables, not config files

### Testing the API
Use the `/health` endpoint to verify the server is running:
```bash
curl http://localhost:8080/health
```

## What are some of the potential applications of this mod?

- **Twitch plays Slay the Spire** - Web-based voting interfaces
- **Slay the Spire AIs** - Machine learning and bot development
- **Stream overlays** - Real-time game state display for streamers
- **Analytics tools** - Game state tracking and analysis
- **Custom interfaces** - Alternative UIs and control methods
- **Multi-client scenarios** - Multiple tools monitoring the same game

## Version History

### v3.0.0 (Fork)
- **COMPLETE REWRITE/FORK**: Forked from [original CommunicationMod](https://github.com/ForgottenArbiter/CommunicationMod) with entirely new HTTP API
- **BREAKING CHANGE**: No backwards compatibility with original subprocess-based API
- Added REST endpoints: `/state`, `/command`, `/health`
- Replaced subprocess communication with embedded HTTP server
- Environment variable configuration system (no config files)
- Automatic logging to configurable log files
- Support for multiple concurrent clients
- Structured JSON error responses with HTTP status codes
- Health check endpoint for monitoring and deployment automation

## Contributing

This mod is open source. Contributions, bug reports, and feature requests are welcome!

## Credits

- **Original CommunicationMod**: [Forgotten Arbiter](https://github.com/ForgottenArbiter/CommunicationMod)

For more information about Slay the Spire modding, visit the [ModTheSpire wiki](https://github.com/kiooeht/ModTheSpire/wiki).