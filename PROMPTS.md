# CP2561 Project – GitHub Copilot Interaction Log

This document logs all interactions with GitHub Copilot and Copilot Chat during the development of the Aircraft Simulation Project.

---

## Session 1 – 2026-06-04 14:00

**Task:** Initial project briefing and planning  
**Tool:** GitHub Copilot (inline suggestions)  
**Prompt (verbatim):**  
> Analyze the starter codebase structure and suggest a development plan for completing all three tasks (maneuver script loader, observer pattern, self-healing threads).

**Suggestion summary:**  
Copilot identified the current architecture and suggested breaking down each task systematically, starting with understanding DirectionControl, then building each feature independently while maintaining the existing threading model.

**Decision:** Accepted as written  
**Why:** The approach aligns with the project requirements of reading and understanding existing code before making changes.

---

## Session 2 – 2026-06-04 14:15

**Task:** Task 1 (Maneuver script from file)  
**Tool:** GitHub Copilot Chat  
**Prompt (verbatim):**  
> Create a Java class called ManeuverScript that:
> 1. Loads a CSV file with columns: seconds, roll, pitch, yaw
> 2. Validates line numbers and ranges (roll/yaw: ±180°, pitch: ±90°)
> 3. Skips blank lines and comments starting with #
> 4. Returns a List<Maneuver> for the demo thread to iterate through
> 5. Has a static load() method that takes a filepath

**Suggestion summary:**  
Copilot provided a complete ManeuverScript class with proper CSV parsing, error handling with line numbers, and comment/blank line handling. It suggested creating a nested Maneuver class to hold the parsed data.

**Decision:** Accepted with modifications  
**Why:** The core structure was sound, but I adjusted error message formatting to exactly match the project brief (e.g., "Script error on line X: ...") and ensured exit codes were non-zero on failure.

---

## Session 3 – 2026-06-04 14:30

**Task:** Task 2 (Observer pattern for direction updates)  
**Tool:** GitHub Copilot Chat  
**Prompt (verbatim):**  
> Implement the Observer pattern for DirectionControl:
> 1. Create a DirectionControlListener interface with onDirectionChanged(DirectionControl) method
> 2. Modify DirectionControl to use CopyOnWriteArrayList for thread-safe listener registration
> 3. Add addListener() and removeListener() methods
> 4. Notify listeners in update() when value changes
> 5. Modify AircraftGUI to register listeners and read from volatile fields instead of polling

**Suggestion summary:**  
Copilot suggested the correct pattern with CopyOnWriteArrayList for thread safety, volatile fields for safe publication, and listener notification in the update() method.

**Decision:** Accepted with modifications  
**Why:** The approach was sound, but I ensured only real value changes trigger notifications (not just entry to update()) and properly initialized volatile fields in the GUI constructor.

**Thread Safety Explanation (Task 2 required artifact):**
Safe publication is guaranteed through:
1. **Volatile fields:** The three latestRoll, latestPitch, latestYaw fields are declared volatile in AircraftGUI
2. **CopyOnWriteArrayList:** DirectionControl uses CopyOnWriteArrayList for thread-safe listener registration
3. **Listener writes:** On the simulation thread, DirectionControl notifies listeners with each value change, which write the new value to a volatile field
4. **EDT reads:** The Swing EDT timer reads these volatile fields directly in updateAircraft(), without locks or polling
5. **Happens-before guarantees:** Writes to volatile fields by the simulation thread happen-before reads by the EDT, establishing safe visibility
6. **No direct Swing calls:** Listeners only update a volatile primitive, never calling Swing methods directly

This design eliminates both polling overhead and display lag while maintaining thread safety.

---

## Session 4 – 2026-06-04 14:45

**Task:** Task 3 (Self-healing worker threads)  
**Tool:** GitHub Copilot Chat  
**Prompt (verbatim):**  
> Create a SupervisedRunner class that implements self-healing for worker threads:
> 1. Catches Exception and RuntimeException
> 2. Logs exceptions with worker name and stack trace
> 3. Implements exponential backoff: 100ms, 200ms, 400ms, ..., max 5s
> 4. Resets backoff after 10 seconds of successful execution
> 5. Limits restart attempts: max 5 restarts within 30 seconds
> 6. Takes a BooleanSupplier to check if simulation is still running

**Suggestion summary:**  
Copilot provided a working SupervisedRunner implementation with the key aspects of backoff logic and restart limiting. It suggested tracking restart times in an array with windowing logic.

**Decision:** Accepted with modifications  
**Why:** The core logic was correct. I adjusted the implementation to properly track restart times within the 30-second window, ensure backoff doubling happens between retries, and properly integrate with the simulator's running flag.

---

## Session 5 – 2026-06-04 15:00

**Task:** Integrating all three tasks into Main.java  
**Tool:** GitHub Copilot Chat  
**Prompt (verbatim):**  
> Modify Main.java to:
> 1. Accept --script filename flag for ManeuverScript (default: default_maneuvers.csv)
> 2. Load the script at startup and exit with error if not found
> 3. Pass ManeuverScript to the automated demo thread
> 4. Wrap turbulence, demo, and resource monitor threads with SupervisedRunner
> 5. Add --inject-failures flag to make turbulence thread throw exceptions at 3, 6, 9 seconds

**Suggestion summary:**  
Copilot suggested wrapping the thread runnable in SupervisedRunner and using an AtomicBoolean for the simulation running state. The suggestion correctly integrated the three features.

**Decision:** Accepted with modifications  
**Why:** The suggestion was mostly correct, but I needed to restructure thread creation to wrap entire threads with SupervisedRunner, allowing the supervisor to properly catch and restart exceptions thrown by the worker.

---

## Session 6 – 2026-06-06 15:30

**Task:** Review and verification of completed implementation  
**Tool:** GitHub Copilot Chat  
**Prompt (verbatim):**  
> Review the current project implementation and identify any remaining tasks or verification steps for Task 1, Task 2, and Task 3. Summarize what is complete and what still needs to be validated before submission.

**Suggestion summary:**  
Copilot confirmed that the main features are implemented and documented:  
- `Task 1` is implemented in `ManeuverScript.java` and used by `Main.java` with `--script` loading and validation.  
- `Task 2` is implemented using `DirectionControlListener` and volatile fields in `AircraftGUI` to avoid polling.  
- `Task 3` is implemented in `SupervisedRunner.java` and worker threads are wrapped in `Main.java` for restart/backoff behavior.  

It also flagged verification work that should still be performed:  
- runtime testing of `--inject-failures` to ensure injected turbulence failures restart correctly,  
- validation that `DirectionControl` listener notifications occur only on real value changes,  
- end-to-end compilation and run validation with `default_maneuvers.csv` and `error_maneuvers.csv`,  
- packaging the submission according to README instructions.

**Decision:** Accepted as a review checkpoint  
**Why:** This session records the final review state and the remaining validation steps needed before final submission.

---

## Session 7 – 2026-06-06 16:00

**Task:** Complete the final review, fix the remaining runtime issue, and verify the self-healing recovery path.  
**Tool:** GitHub Copilot Chat  
**Prompt (verbatim):**  
> Fix the turbulence thread self-healing bug, compile the project, and run the application with `--inject-failures` to verify that failures occur at 3, 6, and 9 seconds and that each restart is handled correctly.

**Suggestion summary:**  
Copilot identified the remaining defect as the turbulence worker being wrapped around a `Thread` object rather than a reusable `Runnable`. The fix was to:
- convert `createTurbulenceThread()` into `createTurbulenceTask()` so injected-failure state is preserved across restart attempts,  
- convert `createAutomatedDemoThread()` into `createAutomatedDemoTask()` for consistent supervision,  
- pass shared `Runnable` tasks directly into `SupervisedRunner` in `Main.java`,  
- clean up shutdown logic to interrupt the supervised threads rather than unused worker thread objects.

**Verification:**  
- Project compiled successfully after the fix.  
- Running `java Main --script ..\default_maneuvers.csv --inject-failures` confirmed the turbulence worker failed and restarted at the expected times: 3s, 6s, and 9s.  
- The supervisor applied exponential backoff correctly: 100ms, 200ms, 400ms.

**Decision:** Accepted as complete  
**Why:** All core tasks are implemented and the self-healing runtime behavior was validated in an actual application run.
