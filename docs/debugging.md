# Debugging Guide

## ⚠️ IMPORTANT

**Log files can be extremely large (hundreds of MB). NEVER read them directly with `cat`, `less`, or editors. Always use `tail` and `grep`.**

---

## Quick Reference

### Monitor in Real-Time
```bash
# Follow logs as they're written
tail -f logs/spire_collector.log

# Follow with filtering
tail -f logs/spire_collector.log | grep "Phase transition"
```

### View Recent Entries
```bash
# Last 100 lines
tail -n 100 logs/spire_collector.log

# Last 500 lines (useful for full game run)
tail -n 500 logs/spire_collector.log
```

### Filter by Log Level
```bash
# INFO and higher (standard operation)
tail -n 200 logs/spire_collector.log | grep -E '\[INFO\]|\[WARN\]|\[ERROR\]'

# Errors only
tail -n 500 logs/spire_collector.log | grep '\[ERROR\]'

# TRACE level (very verbose)
tail -n 100 logs/spire_collector.log | grep '\[TRACE\]'
```

---

## Debugging Endless Runs

### Check Configuration
```bash
tail -n 1000 logs/spire_collector.log | grep "Endless runs enabled"
```
**Expected:** `GameLifecycleController initialized. Endless runs enabled: true`

### View Main Menu Decisions
```bash
tail -n 500 logs/spire_collector.log | grep "Reached main menu"
```

**✅ Working:**
```
Reached main menu. Previous phase: DEATH_SCREEN (game end: true), First game started: true, Endless runs: true
Endless runs enabled - restarting game
```

**❌ Bug:**
```
Reached main menu. Previous phase: GAMEPLAY_MODE (game end: false), First game started: true, Endless runs: true
Not starting game (endless runs disabled or didn't just finish game)
```

### Check Actual Game State
```bash
tail -n 500 logs/spire_collector.log | grep "Actual game state"
```
Shows what the game state ACTUALLY is at decision time vs what phases were detected.

---

## Debugging Phase Transitions

### View All Phase Transitions
```bash
tail -n 500 logs/spire_collector.log | grep "Phase transition"
```

**Expected sequence:**
```
Phase transition: UNKNOWN -> MAIN_MENU
Phase transition: MAIN_MENU -> GAMEPLAY_MODE
Phase transition: GAMEPLAY_MODE -> IN_DUNGEON
Phase transition: IN_DUNGEON -> IN_COMBAT
Phase transition: IN_COMBAT -> ROOM_COMPLETE
Phase transition: ROOM_COMPLETE -> DEATH_SCREEN
Phase transition: DEATH_SCREEN -> MAIN_MENU
```

### Check for DEATH/VICTORY Screen Detection
```bash
# Look for screen detection
tail -n 500 logs/spire_collector.log | grep -E "DEATH screen detected|VICTORY screen detected"

# Look for phase transitions
tail -n 500 logs/spire_collector.log | grep -E "DEATH_SCREEN|VICTORY_SCREEN"
```

**✅ If detected:**
```
DEATH screen detected - notifying DEATH_SCREEN phase
Phase transition: IN_COMBAT -> DEATH_SCREEN
```

**❌ If missing:** The patches aren't detecting the end screen.

### Check Idempotent Rejections
```bash
tail -n 200 logs/spire_collector.log | grep "idempotent rejection"
```
Shows when patches fire but phase hasn't changed (normal for persistent phases).

---

## Common Patterns

### Phase Timeline (Clean View)
```bash
tail -n 1000 logs/spire_collector.log | grep "Phase transition" | cut -d' ' -f1,2,6-
```

### All Death/Victory Related Logs
```bash
tail -n 500 logs/spire_collector.log | grep -iE "death|victory"
```

### Game Lifecycle Events
```bash
tail -n 500 logs/spire_collector.log | grep -E "Phase transition|Reached main menu|Starting new game"
```

### Context Around Matches
```bash
# 3 lines before and after
tail -n 500 logs/spire_collector.log | grep -A 3 -B 3 "DEATH_SCREEN"

# 5 lines after "Reached main menu"
tail -n 500 logs/spire_collector.log | grep -A 5 "Reached main menu"
```

### Count Occurrences
```bash
# Count phase transitions
tail -n 1000 logs/spire_collector.log | grep -c "Phase transition"

# Count errors
tail -n 1000 logs/spire_collector.log | grep -c "\[ERROR\]"

# Count by phase
tail -n 1000 logs/spire_collector.log | grep "Phase transition" | grep -o "-> [A-Z_]*" | sort | uniq -c
```

---

## Key Commands Summary

```bash
# Real-time monitoring
tail -f logs/spire_collector.log

# Phase transition timeline
tail -n 1000 logs/spire_collector.log | grep "Phase transition"

# Endless runs decision
tail -n 500 logs/spire_collector.log | grep "Reached main menu" -A 2

# Death/Victory detection
tail -n 500 logs/spire_collector.log | grep -iE "death|victory"

# Actual game state
tail -n 500 logs/spire_collector.log | grep "Actual game state"

# Errors
tail -n 1000 logs/spire_collector.log | grep "\[ERROR\]"
```
