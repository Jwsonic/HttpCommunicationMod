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

## API Reference

HttpCommunicationMod provides a REST API with four main endpoints for interacting with Slay the Spire.

### `GET /state`

Returns the current game state as JSON. This endpoint automatically logs the game state to the configured log file.

**HTTP Method:** `GET`

**Request:** No request body required

**Response:** JSON object containing:
- `available_commands`: Array of specific available commands (e.g., `["play 1", "play 2 0", "end"]`)
- `ready_for_command`: Boolean indicating if the game is ready to accept commands
- `in_game`: Boolean indicating if currently in a game
- `game_state`: Object containing detailed game state information

**Example Request:**
```bash
curl http://localhost:8080/state
```

**Success Response (200 OK):**
```json
{
  "available_commands": ["play 1", "play 2 0", "end"],
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

**Error Responses:**
- `405 Method Not Allowed`: Wrong HTTP method used
- `500 Internal Server Error`: Server error occurred

---

### `POST /command`

Executes a game command and returns the result along with updated game state. Commands are automatically logged to the configured log file.

**HTTP Method:** `POST`

**Request:** Plain text command in request body (e.g., `"play 1"`, `"end"`, `"choose 0"`)

**Response:** JSON object containing:
- `success`: Boolean indicating if command succeeded
- `command`: Echo of the command that was executed
- `state_changed`: Boolean indicating if the command changed game state (only on success)
- `game_state`: Current game state after command execution
- `error`: Error message (only on failure)

**Example Request:**
```bash
curl -X POST http://localhost:8080/command -d "play 1"
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "command": "play 1",
  "state_changed": true,
  "game_state": { ... }
}
```

**Error Response (200 OK with error details):**
```json
{
  "success": false,
  "error": "Invalid command: play 99. Card index out of bounds.",
  "command": "play 99",
  "game_state": { ... }
}
```

**Error Responses:**
- `400 Bad Request`: Empty command body
- `405 Method Not Allowed`: Wrong HTTP method used
- `500 Internal Server Error`: Server error occurred

---

### `POST /start`

Starts a new game with specified character, ascension level, and optional seed. This endpoint replaces the old text-based `START` command with a dedicated HTTP endpoint.

**HTTP Method:** `POST`

**Request:** JSON object with the following fields:
- `character` (required): Character name - valid values: `"IRONCLAD"`, `"THE_SILENT"`, `"SILENT"`, `"DEFECT"`, `"WATCHER"`
- `ascension_level` (optional): Integer from 0-20, defaults to 0
- `seed` (optional): Alphanumeric seed string (letters and numbers only)

**Response:** JSON object containing:
- `success`: Boolean indicating if game start succeeded
- `character`: Character class name that was selected
- `ascension_level`: Ascension level of the game
- `seed`: Numeric seed value (long)
- `seed_string`: Human-readable seed string
- `error`: Error message (only on failure)

**Example Request:**
```bash
curl -X POST http://localhost:8080/start \
  -H "Content-Type: application/json" \
  -d '{"character": "IRONCLAD", "ascension_level": 15, "seed": "TESTRUN"}'
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "character": "IRONCLAD",
  "ascension_level": 15,
  "seed": 123456789,
  "seed_string": "TESTRUN"
}
```

**Error Response (400 Bad Request):**
```json
{
  "success": false,
  "error": "Missing required field: character"
}
```

**Error Responses:**
- `400 Bad Request`: Invalid JSON, missing character field, invalid character name, ascension level out of bounds (0-20), or invalid seed format
- `405 Method Not Allowed`: Wrong HTTP method used
- `500 Internal Server Error`: Server error occurred

---

### `POST /reset`

Resets the game and returns to the main menu. This endpoint replaces the old text-based `RESET` command with a dedicated HTTP endpoint.

**HTTP Method:** `POST`

**Request:** No request body required

**Response:** `204 No Content` on success (no response body)

**Example Request:**
```bash
curl -X POST http://localhost:8080/reset
```

**Success Response (204 No Content):**
No response body

**Error Responses:**
- `405 Method Not Allowed`: Wrong HTTP method used
- `500 Internal Server Error`: Server error occurred

**Behavior:**
- Clears any pending unlocks from the current run
- Resets all game mode flags (Trial, Daily Run, Endless)
- Returns to the character selection/main menu screen
- Equivalent to finishing a run and returning to the main menu

---

### `GET /health`

Returns server status and metadata for monitoring and health checks.

**HTTP Method:** `GET`

**Request:** No request body required

**Response:** JSON object containing:
- `status`: Health status string (e.g., `"healthy"`)
- `mod_name`: Name of the mod
- `version`: Version string
- `endpoints`: Array of available endpoint paths

**Example Request:**
```bash
curl http://localhost:8080/health
```

**Success Response (200 OK):**
```json
{
  "status": "healthy",
  "mod_name": "HTTP Communication Mod",
  "version": "3.0.0",
  "endpoints": ["/state", "/command", "/start", "/reset", "/health"]
}
```

**Error Responses:**
- `405 Method Not Allowed`: Wrong HTTP method used
- `500 Internal Server Error`: Server error occurred

## Available Commands

The `/command` endpoint accepts text-based commands to control the game. The `available_commands` field in the `/state` response now returns **enumerated specific commands** for the current game state, rather than command categories.

**Example:** Instead of returning `["play", "end"]`, the API now returns specific commands like `["play 1", "play 2 0", "play 3", "end"]` - showing exactly which cards can be played and against which targets.

**Note:** The following commands are intentionally excluded from `available_commands`:
- `start` - Starting games is now done via the dedicated `POST /start` endpoint (see API Reference above)
- `reset` - Resetting the game is now done via the dedicated `POST /reset` endpoint (see API Reference above)
- `key`, `click`, `wait` - Low-level input commands that are still available via `/command` but not enumerated in state responses

### Game Control Commands
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

## Configuration

### Environment Variables

The mod uses environment variables for configuration (no config files are created or used):

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

# Start a new game
start_response = requests.post('http://localhost:8080/start',
    json={
        'character': 'IRONCLAD',
        'ascension_level': 10,
        'seed': 'MYRUN123'
    })
start_result = start_response.json()
print(f"Game started: {start_result['success']}, Seed: {start_result['seed_string']}")

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

async function startGame(character, ascensionLevel = 0, seed = null) {
    try {
        const response = await axios.post('http://localhost:8080/start', {
            character: character,
            ascension_level: ascensionLevel,
            seed: seed
        });
        return response.data;
    } catch (error) {
        console.error('Error starting game:', error);
    }
}

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
    // Start a new game
    const startResult = await startGame('DEFECT', 15, 'TESTRUN');
    console.log('Game started:', startResult.success);

    const state = await getGameState();
    console.log('Current HP:', state.game_state.current_hp);

    const result = await executeCommand('end');
    console.log('End turn result:', result.success);
})();
```

### cURL Examples
```bash
# Start new game as Silent on Ascension 10
curl -X POST http://localhost:8080/start \
  -H "Content-Type: application/json" \
  -d '{"character": "SILENT", "ascension_level": 10, "seed": "TESTRUN"}'

# Get game state
curl http://localhost:8080/state

# Play first card
curl -X POST http://localhost:8080/command -d "play 1"

# End turn
curl -X POST http://localhost:8080/command -d "end"

# Reset game and return to main menu
curl -X POST http://localhost:8080/reset

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

### v3.1.0 (Upcoming)
- **NEW**: Added dedicated `POST /reset` endpoint for resetting the game and returning to main menu
- **BREAKING CHANGE**: Removed `reset` command from `/command` endpoint - reset is now only available via `POST /reset` endpoint
- Updated `/health` endpoint to include `/reset` in endpoints list

### v3.0.0 (Fork)
- **COMPLETE REWRITE/FORK**: Forked from [original CommunicationMod](https://github.com/ForgottenArbiter/CommunicationMod) with entirely new HTTP API
- **BREAKING CHANGE**: No backwards compatibility with original subprocess-based API
- Added REST endpoints: `/state`, `/command`, `/start`, `/health`
- **NEW**: Dedicated `POST /start` endpoint for starting games with JSON request/response format
- **CHANGED**: `available_commands` now returns enumerated specific commands (e.g., `"play 1"`, `"play 2 0"`) instead of command categories
- Moved START command from text-based `/command` interface to dedicated `/start` HTTP endpoint
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