# CP2561 Aircraft Simulation Project

## Overview

This project extends the aircraft simulation codebase with three key features:
1. **Task 1**: Load flight maneuvers from a CSV file instead of hard-coded values
2. **Task 2**: Implement Observer pattern to eliminate polling overhead
3. **Task 3**: Add self-healing worker threads with exponential backoff

## Compilation

```bash
cd src
javac *.java
```

## Running the Simulation

### Task 1: Using a Custom Maneuver Script

Load the simulation with a specific maneuver script file:

```bash
java Main --script ../default_maneuvers.csv
```

If no `--script` flag is provided, the simulation looks for `default_maneuvers.csv` in the current directory:

```bash
java Main
```

To test error handling, try loading the error script:

```bash
java Main --script ../error_maneuvers.csv
```

The system will print validation errors like:
- `Script error on line 6: expected 4 fields but found 3`
- `Script error on line 8 field 2 ("roll"): "abc" is not a number`
- `Script error on line 11 field 2 ("roll"): 200.0 is out of range ±180`

### Task 2: Observer Pattern (Automatic)

Task 2 (Observer pattern) is automatically active. The GUI no longer polls direction controls:

```bash
java Main
```

Direction changes are now published via listeners, eliminating polling overhead and frame lag.

### Task 3: Self-Healing Threads

The simulation automatically wraps worker threads with `SupervisedRunner` for automatic recovery:

```bash
java Main
```

The turbulence, demo, and resource monitor threads will automatically restart with exponential backoff if they crash.

### Task 3: Testing Failure Recovery with `--inject-failures`

To test the self-healing mechanism, use the `--inject-failures` flag:

```bash
java Main --inject-failures
```

This will make the turbulence thread throw exceptions at 3, 6, and 9 seconds. The supervisor will catch these, apply exponential backoff (100ms → 200ms → 400ms), and restart the thread. The simulation will continue running for at least 60 seconds, demonstrating recovery.

You should see output like:
```
Exception in worker "turbulence": Injected failure at 3 seconds
Restarting worker "turbulence" after 100ms backoff...
Exception in worker "turbulence": Injected failure at 6 seconds
Restarting worker "turbulence" after 200ms backoff...
```

## CSV File Format (Task 1)

The maneuver script must be a CSV file with the following format:

```csv
seconds,roll,pitch,yaw
8,0,0,0
12,2,0,2
8,0,0,0
12,-2,0,-2
10,0,-5,0
10,0,3,0
10,0,0,0
```

**Constraints:**
- First line must be the header: `seconds,roll,pitch,yaw`
- `seconds`: Duration to hold the target values (in seconds)
- `roll`: Target bank angle, range ±180°
- `pitch`: Target pitch, range ±90°
- `yaw`: Target heading, range ±180°
- Blank lines and lines starting with `#` are treated as comments
- Non-numeric values and out-of-range values produce descriptive error messages with line numbers

## Submitting Your Code

Create a ZIP archive with:
- All `.java` source files
- `default_maneuvers.csv` (reproducing the original sequence)
- A second CSV file demonstrating error handling
- `PROMPTS.md` (log of all Copilot interactions)
- This `README.md`
- Git history: `git log --pretty=oneline > git_history.txt`

Do NOT include:
- `.class` files
- IDE folders (`.idea`, `.vscode`)
- Large data files

## Key Features Implemented

### Task 1 - Maneuver Script Loader
- Loads flight maneuvers from external CSV file
- Validates all input with clear error messages
- Supports comments and blank lines
- Default file support with clear error messages on missing files

### Task 2 - Observer Pattern
- `DirectionControlListener` interface for change notifications
- `CopyOnWriteArrayList` for thread-safe listener management
- Volatile fields in AircraftGUI for safe publication
- Eliminates polling and frame lag

**Thread Safety Comment:**
Safe publication across threads is guaranteed by:
1. Listeners write to volatile fields on the simulation thread
2. EDT reads these volatile fields without polling or locking
3. Volatile field semantics provide happens-before guarantees
4. Listeners never call Swing methods directly

### Task 3 - Self-Healing Worker Threads
- `SupervisedRunner` class wraps worker threads
- Exponential backoff: 100ms → 200ms → 400ms → ... → 5s
- Backoff reset after 10 seconds of successful execution
- Restart budget: max 5 restarts within 30 seconds
- Applied to: turbulence thread, demo thread, resource monitor

## Testing

All three features can be tested simultaneously:

```bash
# Test all features: script loading, observer pattern, and self-healing
java Main --script ../default_maneuvers.csv --inject-failures
```

The simulation will:
1. Load maneuvers from the CSV file
2. Display updates via observer pattern (no polling)
3. Recover from injected failures with increasing backoff
4. Continue running for 60+ seconds with recovery demonstrations
