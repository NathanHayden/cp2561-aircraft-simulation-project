/*
 * Copyright (C) 2025 Shivaji Patil, College of the North Atlantic
 * All rights reserved.
 *
 * Aircraft Simulation Project
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ManeuverScript loads and validates aircraft maneuvers from a CSV file.
 * The CSV format is:
 *   seconds,roll,pitch,yaw
 *   8,0,0,0
 *   12,2,0,2
 *   ...
 * 
 * Validates ranges: roll/yaw ±180°, pitch ±90°
 * Comments (lines starting with #) and blank lines are skipped.
 */
public class ManeuverScript {
    
    /**
     * Inner class representing a single maneuver: how long to hold target values.
     */
    public static class Maneuver {
        public final long durationMs;
        public final double rollTarget;
        public final double pitchTarget;
        public final double yawTarget;

        public Maneuver(long durationMs, double rollTarget, double pitchTarget, double yawTarget) {
            this.durationMs = durationMs;
            this.rollTarget = rollTarget;
            this.pitchTarget = pitchTarget;
            this.yawTarget = yawTarget;
        }
    }

    private final List<Maneuver> maneuvers = new ArrayList<>();

    /**
     * Loads and parses a maneuver script from the given file path.
     * Validates all entries and raises descriptive errors for malformed data.
     * 
     * @param filePath Path to the CSV file
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if validation fails
     */
    public static ManeuverScript load(String filePath) throws IOException, IllegalArgumentException {
        ManeuverScript script = new ManeuverScript();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Skip blank lines and comments
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                // Skip header line
                if (lineNumber == 1 && trimmed.equalsIgnoreCase("seconds,roll,pitch,yaw")) {
                    continue;
                }

                // Parse the line
                String[] fields = line.split(",");

                if (fields.length != 4) {
                    throw new IllegalArgumentException(
                        String.format("Script error on line %d: expected 4 fields but found %d",
                            lineNumber, fields.length));
                }

                double duration, roll, pitch, yaw;

                try {
                    duration = Double.parseDouble(fields[0].trim());
                    roll = Double.parseDouble(fields[1].trim());
                    pitch = Double.parseDouble(fields[2].trim());
                    yaw = Double.parseDouble(fields[3].trim());
                } catch (NumberFormatException e) {
                    // Determine which field failed
                    String[] fieldNames = {"seconds", "roll", "pitch", "yaw"};
                    for (int i = 0; i < fields.length; i++) {
                        try {
                            Double.parseDouble(fields[i].trim());
                        } catch (NumberFormatException nfe) {
                            throw new IllegalArgumentException(
                                String.format("Script error on line %d field %d (\"%s\"): \"%s\" is not a number",
                                    lineNumber, i + 1, fieldNames[i], fields[i].trim()));
                        }
                    }
                    // Should not reach here
                    throw new IllegalArgumentException("Script error on line " + lineNumber + ": parse error");
                }

                // Validate ranges
                if (roll < -180 || roll > 180) {
                    throw new IllegalArgumentException(
                        String.format("Script error on line %d field 2 (\"roll\"): %.1f is out of range ±180",
                            lineNumber, roll));
                }
                if (pitch < -90 || pitch > 90) {
                    throw new IllegalArgumentException(
                        String.format("Script error on line %d field 3 (\"pitch\"): %.1f is out of range ±90",
                            lineNumber, pitch));
                }
                if (yaw < -180 || yaw > 180) {
                    throw new IllegalArgumentException(
                        String.format("Script error on line %d field 4 (\"yaw\"): %.1f is out of range ±180",
                            lineNumber, yaw));
                }

                // Convert seconds to milliseconds
                long durationMs = (long) (duration * 1000);
                script.maneuvers.add(new Maneuver(durationMs, roll, pitch, yaw));
            }
        }

        if (script.maneuvers.isEmpty()) {
            throw new IllegalArgumentException("Script contains no valid maneuvers");
        }

        return script;
    }

    /**
     * Gets the list of loaded maneuvers.
     */
    public List<Maneuver> getManeuvers() {
        return maneuvers;
    }

    /**
     * Gets a maneuver by index, wrapping around at the end.
     */
    public Maneuver getManeuver(int index) {
        return maneuvers.get(index % maneuvers.size());
    }

    /**
     * Gets the total number of maneuvers.
     */
    public int size() {
        return maneuvers.size();
    }
}
