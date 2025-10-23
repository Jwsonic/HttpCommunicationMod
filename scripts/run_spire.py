#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# ///
"""
GameProcess - Manages a single Java game process with async operations.
"""

import asyncio
import logging
import sys
import uuid
from pathlib import Path
from typing import Optional, Dict, List
from logging.handlers import RotatingFileHandler


class GameConfig:
    """Simple configuration class for game settings."""

    def __init__(self, data: Dict):
        """
        Initialize GameConfig from dict.

        Args:
            data: Dictionary containing configuration values
        """
        self.spire_resource_dir = Path(data["spire_resource_dir"])
        self.mts_jar = Path(data["mts_jar"])
        self.log_dir = Path(data.get("log_dir", "./logs"))
        self.seed = data.get("seed", "A0")
        self.mod_list = data.get("mod_list", [])
        self.http_mod_host = data.get("http_mod_host", "localhost")
        self.http_mod_port = int(data.get("http_mod_port", 8080))
        self.agent_log_dir = data.get("agent_log_dir")
        self.skip_launcher = data.get("skip_launcher", False)
        self.skip_intro = data.get("skip_intro", False)

    @classmethod
    def from_toml(cls, toml_path: Path) -> "GameConfig":
        """
        Load configuration from TOML file.

        Args:
            toml_path: Path to TOML configuration file

        Returns:
            GameConfig instance

        Raises:
            FileNotFoundError: If config file doesn't exist
            ImportError: If tomllib not available (Python < 3.11)
        """
        if not toml_path.exists():
            raise FileNotFoundError(f"Config file not found: {toml_path}")

        try:
            import tomllib

            with open(toml_path, "rb") as f:
                config_data = tomllib.load(f)
            return cls(config_data)
        except ImportError:
            raise ImportError("tomllib not available. Requires Python 3.11+")


class GameProcess:
    """Manages a single Java game process with async operations."""

    def __init__(self, session_id: str, config: GameConfig):
        """
        Initialize a GameProcess.

        Args:
            session_id: Unique identifier for this game session
            config: GameConfig object with all game settings
        """
        self.session_id = session_id
        self.config = config
        self.process: Optional[asyncio.subprocess.Process] = None
        self.stdout_task: Optional[asyncio.Task] = None
        self.stderr_task: Optional[asyncio.Task] = None

        # Set up logger for this game session
        self.logger = self._setup_logger()

    async def start(self) -> None:
        """Start the Java process with proper configuration."""
        command = self._build_command()
        env = self._build_env()

        # Validate Java executable exists
        java_path = Path(command[0])
        if not java_path.exists():
            raise FileNotFoundError(f"Java executable not found at {java_path}")

        # Start the process
        self.process = await asyncio.create_subprocess_exec(
            *command,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            env=env,
            cwd=str(self.config.spire_resource_dir),
        )

        # Start async tasks to capture output
        self.stdout_task = asyncio.create_task(self._capture_stdout())
        self.stderr_task = asyncio.create_task(self._capture_stderr())

    async def stop(self) -> None:
        """Gracefully stop the process (SIGTERM)."""
        if self.process:
            self.process.terminate()
            try:
                await asyncio.wait_for(self.process.wait(), timeout=10.0)
            except asyncio.TimeoutError:
                # Fallback to kill if graceful stop times out
                await self.kill()

    async def kill(self) -> None:
        """Force kill the process (SIGKILL)."""
        if self.process:
            self.process.kill()
            await self.process.wait()

    async def is_running(self) -> bool:
        """Check if process is still running."""
        if not self.process:
            return False
        return self.process.returncode is None

    async def wait(self) -> int:
        """Wait for process to complete and return exit code."""
        if self.process:
            return await self.process.wait()
        return -1

    def _build_command(self) -> List[str]:
        """
        Build Java command with arguments.

        Returns:
            List of command arguments ready for subprocess execution
        """
        java_path = self.config.spire_resource_dir / "jre" / "bin" / "java"
        command = [str(java_path), "-jar", str(self.config.mts_jar)]

        # Add mods if specified
        if self.config.mod_list:
            command.extend(["--mods", ",".join(self.config.mod_list)])

        # Add launcher flags
        if self.config.skip_launcher:
            command.append("--skip-launcher")
        if self.config.skip_intro:
            command.append("--skip-intro")

        return command

    def _build_env(self) -> Dict[str, str]:
        """
        Build environment variables for HTTP mod.

        Returns:
            Dictionary of environment variables
        """
        env = {
            "HTTP_MOD_PORT": str(self.config.http_mod_port),
            "HTTP_MOD_HOST": self.config.http_mod_host,
            "GAME_SESSION_ID": self.session_id,
        }

        if self.config.agent_log_dir:
            env["AGENT_LOG_DIR"] = str(self.config.agent_log_dir)

        return env

    def _setup_logger(self) -> logging.Logger:
        """
        Set up logger for this game session.

        Returns:
            Configured logger instance
        """
        logger = logging.getLogger(f"game.{self.session_id}")
        logger.setLevel(logging.INFO)
        logger.propagate = False  # Don't propagate to root logger

        # Create log file path
        log_path = self.config.log_dir / f"spire_game_{self.session_id}.log"
        log_path.parent.mkdir(parents=True, exist_ok=True)

        # Set up rotating file handler (10MB per file, keep 5 backups)
        handler = RotatingFileHandler(
            log_path, maxBytes=10 * 1024 * 1024, backupCount=5
        )
        handler.setFormatter(
            logging.Formatter("%(asctime)s [%(levelname)s] %(message)s")
        )
        logger.addHandler(handler)

        return logger

    async def _capture_stdout(self) -> None:
        """Capture stdout to log file using logger."""
        if not self.process or not self.process.stdout:
            return

        while True:
            line = await self.process.stdout.readline()
            if not line:
                break
            self.logger.info(line.decode().rstrip())

    async def _capture_stderr(self) -> None:
        """Capture stderr to log file using logger."""
        if not self.process or not self.process.stderr:
            return

        while True:
            line = await self.process.stderr.readline()
            if not line:
                break
            self.logger.error(line.decode().rstrip())


async def main():
    """Main function for testing GameProcess standalone."""
    if len(sys.argv) < 2:
        print("Usage: python game_process.py <config_file.toml>")
        print("Example config file (TOML):")
        print("""
spire_resource_dir = "/path/to/SlayTheSpire"
mts_jar = "/path/to/ModTheSpire.jar"
log_dir = "./logs"
seed = "A0"
mod_list = ["basemod", "HttpCommunicationMod", "superfastmode"]
http_mod_host = "localhost"
http_mod_port = 8080
agent_log_dir = "./agent_logs"
skip_launcher = true
skip_intro = true
        """)
        sys.exit(1)

    config_file = Path(sys.argv[1])

    # Load config
    try:
        config = GameConfig.from_toml(config_file)
    except FileNotFoundError as e:
        print(f"Error: {e}")
        sys.exit(1)
    except ImportError as e:
        print(f"Error: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"Error loading config: {e}")
        sys.exit(1)

    # Generate session ID
    session_id = str(uuid.uuid4())

    print(f"Starting game session: {session_id}")
    print(f"Log file: {config.log_dir / f'spire_game_{session_id}.log'}")

    # Create and start the game process
    game = GameProcess(session_id, config)

    try:
        await game.start()
        print(f"Game process started (PID: {game.process.pid})")  # pyright: ignore[reportOptionalMemberAccess]
        print("Press Ctrl+C to stop the game...")

        # Wait for the process to complete
        exit_code = await game.wait()
        print(f"Game process exited with code: {exit_code}")

    except KeyboardInterrupt:
        print("\nStopping game gracefully...")
        await game.stop()
        print("Game stopped.")
    except Exception as e:
        print(f"Error: {e}")
        await game.kill()


if __name__ == "__main__":
    asyncio.run(main())
