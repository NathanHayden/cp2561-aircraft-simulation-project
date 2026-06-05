/*
 * Copyright (C) 2025 Shivaji Patil, College of the North Atlantic
 * All rights reserved.
 * 
 * Aircraft Simulation Project
 */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    /** Writer for logging output to file */
    private static PrintWriter logWriter;

    /** Writer for CSV data logging */
    static PrintWriter csvLogWriter;

    /** Statistics object to track simulation performance */
    private static Map<String, Map<String, Double>> statisticsData = new HashMap<>();

    // Direction control instances
    private static DirectionControl rollControl;
    private static DirectionControl pitchControl;
    private static DirectionControl yawControl;

    /**
     * Helper method to parse command line arguments
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> argMap = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    String value = args[i + 1];
                    argMap.put(key, value);
                    i++;  // Skip the value in next iteration
                } else {
                    // Flag with no value (like --inject-failures)
                    argMap.put(key, "true");
                }
            }
        }
        return argMap;
    }

    /**
     * Logs a message to the log file.
     */
    public static void logToFile(String message) {
        if (logWriter != null) {
            logWriter.println(message);
            logWriter.flush();
        }
    }

    /**
     * Logs data to the CSV file for later analysis.
     */
    public static void logToCSV(String axis, double expected, double actual, double velocity) {
        if (csvLogWriter != null) {
            double deviation = expected - actual;
            String timestamp = java.time.LocalDateTime.now().toString();
            csvLogWriter.println(String.format("%s,%s,%.2f,%.2f,%.2f,%.2f",
                    timestamp, axis, expected, actual, deviation, velocity));
            csvLogWriter.flush();
        }
    }

    /**
     * Displays the collected statistics for all direction controls
     */
    public static void displayStatistics() {
        System.out.println("\n===== SIMULATION STATISTICS =====");
        for (String axis : statisticsData.keySet()) {
            Map<String, Double> stats = statisticsData.get(axis);
            System.out.println(axis + " Statistics:");
            System.out.println("  Samples: " + stats.get("sampleCount").intValue());
            System.out.println("  Average Deviation: " + String.format("%.2f", stats.get("averageDeviation")));
            System.out.println("  Maximum Deviation: " + String.format("%.2f", stats.get("maxDeviation")));
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // Parse command line arguments
        Map<String, String> params = parseArgs(args);

        // Apply the native Swing look-and-feel and announce the host OS.
        PlatformSupport.applySystemLookAndFeel();
        System.out.println("Detected OS: " + PlatformSupport.osLabel()
                + " (ANSI on stdout: " + PlatformSupport.supportsAnsi() + ")");

        // Load the maneuver script (Task 1)
        ManeuverScript maneuverScript = null;
        String scriptPath = params.getOrDefault("script", "default_maneuvers.csv");
        
        try {
            maneuverScript = ManeuverScript.load(scriptPath);
            System.out.println("Loaded maneuver script: " + scriptPath + 
                " (" + maneuverScript.size() + " maneuvers)");
        } catch (IllegalArgumentException iae) {
            System.err.println(iae.getMessage());
            System.exit(1);
        } catch (java.io.IOException ioe) {
            System.err.println("Script error: could not read file " + scriptPath);
            System.exit(1);
        }

        // Check for --inject-failures flag (Task 3)
        boolean injectFailures = params.containsKey("inject-failures");
        if (injectFailures) {
            System.out.println("Running with --inject-failures: will throw exceptions at 3s, 6s, and 9s");
        }

        // Set up configuration
        String configDir = System.getProperty("user.home") + File.separator + ".aircraft_sim";
        new File(configDir).mkdirs(); // Create directory if it doesn't exist
        
        String configFile = configDir + File.separator + "config.properties";
        ConfigLoader config = new ConfigLoader(configFile);
        
        // Set up logging
        boolean enableLogging = config.getBoolean("logging.enabled", false);
        if (enableLogging) {
            try {
                String logFile = configDir + File.separator + "simulation_" +
                    LocalDateTime.now().toString().replace(':', '_') + ".log";
                logWriter = new PrintWriter(new FileWriter(logFile));

                String csvFile = configDir + File.separator + "data_" +
                    LocalDateTime.now().toString().replace(':', '_') + ".csv";
                csvLogWriter = new PrintWriter(new BufferedWriter(new FileWriter(csvFile)));
                csvLogWriter.println("timestamp,axis,expected,actual,deviation,velocity");

                logToFile("Simulation started");
            } catch (IOException e) {
                System.err.println("Error setting up logging: " + e.getMessage());
            }
        }

        // Create direction controls with DirectionControlStable for ultra stability
        rollControl = new DirectionControlStable("Roll", -180, 180, config);
        pitchControl = new DirectionControlStable("Pitch", -90, 90, config);
        yawControl = new DirectionControlStable("Yaw", -180, 180, config);

        // Clear screen and hide cursor for clean visualization (only when the
        // terminal supports ANSI escape sequences).
        if (PlatformSupport.supportsAnsi()) {
            System.out.print("\033[H\033[2J");  // Clear screen
            System.out.print("\033[?25l");       // Hide cursor
        }

        // Set up turbulence flag - enable by default for autonomous mode
        AtomicBoolean turbulenceEnabled = new AtomicBoolean(true);

        // Set up running flag
        AtomicBoolean running = new AtomicBoolean(true);

        // Display welcome message for autonomous mode
        System.out.println("\n\nAUTONOMOUS AIRCRAFT SIMULATION");
        System.out.println("-----------------------------");
        System.out.println("This simulation runs completely autonomously.");
        System.out.println("All flight maneuvers are performed automatically.");
        System.out.println("Press 'q' and Enter at any time to quit the simulation.");
        System.out.println("\nStarting simulation in 3 seconds...");
        Thread.sleep(3000);

        // Create and start threads
        Thread userInputThread = createInputThread(rollControl, pitchControl, yawControl, turbulenceEnabled, running);
        Thread turbulenceThread = createTurbulenceThread(rollControl, pitchControl, yawControl, turbulenceEnabled, running, injectFailures);
        Thread automatedDemoThread = createAutomatedDemoThread(rollControl, pitchControl, yawControl, maneuverScript);

        // Wrap long-lived worker threads with SupervisedRunner (Task 3)
        Thread supervisedTurbulenceThread = new Thread(
            new SupervisedRunner("turbulence", 
                () -> {
                    try {
                        turbulenceThread.run();
                    } catch (Exception e) {
                        throw e;
                    }
                }, 
                running::get),
            "SupervisedTurbulence"
        );

        Thread supervisedDemoThread = new Thread(
            new SupervisedRunner("automated-demo",
                () -> {
                    try {
                        automatedDemoThread.run();
                    } catch (Exception e) {
                        throw e;
                    }
                },
                running::get),
            "SupervisedDemo"
        );

        userInputThread.start();
        supervisedTurbulenceThread.start();
        supervisedDemoThread.start();

        // Create and start the Swing GUI. The GUI reads orientation directly
        // from the DirectionControl instances passed in - no JSON intermediary.
        System.out.println("Launching Swing GUI for flight parameters display...");
        AircraftGUI gui = new AircraftGUI(rollControl, pitchControl, yawControl);

        // Live OS resource monitor: samples CPU / memory once a second and
        // tells the GUI to throttle its frame rate when the host is under load.
        ResourceMonitor resourceMonitor = new ResourceMonitor(1000, gui::setPerformanceLevel);
        gui.setResourceMonitor(resourceMonitor);
        
        Thread resourceMonitorThread = new Thread(
            new SupervisedRunner("resource-monitor",
                () -> resourceMonitor.run(),
                running::get),
            "SupervisedResourceMonitor"
        );
        resourceMonitorThread.start();

        gui.show();
        
        // Create and start thread to update the GUI
        Thread guiUpdateThread = AircraftGUI.createGUIUpdateThread(gui, running);
        guiUpdateThread.start();
        
        // Set up quit action to stop the simulation
        try {
            gui.setQuitAction(() -> {
                running.set(false);
            });
            System.out.println("Quit action registered successfully.");
        } catch (Exception e) {
            System.err.println("Warning: Could not register quit action: " + e.getMessage());
            System.err.println("Simulation will continue, but you may need to use Ctrl+C to exit.");
        }
        
        System.out.println("Swing GUI is reading orientation directly from the DirectionControl simulation.");
        
        // Initial startup message that won't get cleared
        System.out.println("\nSTARTING AIRCRAFT SIMULATION WITH DEDICATED FLIGHT ANALYTICS DISPLAY\n");
        System.out.println("Loading configuration...");
        System.out.println("Initializing flight dynamics...");
        System.out.println("Starting analytics display thread...");
        System.out.println("\nPress 'q' to quit the simulation when you want to stop.\n");
        
        // Main simulation loop
        try {
            System.out.println("Simulation active - check analytics display...");
            
            // Wait for quit command
            while (running.get()) {
                // Update direction controls with physics
                rollControl.update();
                pitchControl.update();
                yawControl.update();
                
                // Small sleep to reduce CPU usage
                Thread.sleep(50);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Stop the resource monitor first - it's a daemon thread but we
            // ask it to exit cleanly before tearing down everything else.
            resourceMonitor.stop();
            resourceMonitorThread.interrupt();

            // Interrupt the GUI update thread
            guiUpdateThread.interrupt();

            // Show cursor again when exiting
            if (PlatformSupport.supportsAnsi()) {
                System.out.print("\033[?25h"); // Show cursor
            }

            // Close log writers if open
            if (logWriter != null) {
                logWriter.close();
            }
            if (csvLogWriter != null) {
                csvLogWriter.close();
            }

            // Interrupt other threads
            userInputThread.interrupt();
            turbulenceThread.interrupt();
            automatedDemoThread.interrupt();

            System.out.println("\nSimulation terminated. Thank you for flying with us!");
        }


        // Collect and display statistics
        statisticsData.put("Roll", rollControl.getStatistics());
        statisticsData.put("Pitch", pitchControl.getStatistics());
        statisticsData.put("Yaw", yawControl.getStatistics());
        displayStatistics();

        // Close log files
        closeLogFiles();
    }

    private static void closeLogFiles() {
        if (logWriter != null) {
            logWriter.close();
        }
        if (csvLogWriter != null) {
            csvLogWriter.close();
        }
    }

    /**
     * Creates a thread that only accepts input to stop the execution
     * Simulation runs completely autonomously
     */
    private static Thread createInputThread(DirectionControl roll, DirectionControl pitch, DirectionControl yaw,
                                     AtomicBoolean turbulenceEnabled, AtomicBoolean running) {
        return new Thread(() -> {
            try (Scanner scanner = new Scanner(System.in)) {
                System.out.println("\nAUTONOMOUS SIMULATION MODE");
                System.out.println("Press 'q' to quit at any time");
                
                while (running.get()) {
                    if (scanner.hasNextLine()) {
                        String input = scanner.nextLine().trim().toLowerCase();
                        if (input.equals("q")) {
                            System.out.println("Stopping simulation...");
                            running.set(false);
                            break;
                        } else {
                            System.out.println("Press 'q' to quit the simulation");
                        }
                    }
                    
                    // Small sleep to prevent tight loop
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }
    
    /**
     * Creates a thread that applies turbulence to the aircraft.
     * If injectFailures is true, throws exceptions at 3, 6, and 9 seconds for testing.
     */
    private static Thread createTurbulenceThread(DirectionControl roll, DirectionControl pitch, DirectionControl yaw,
                                         AtomicBoolean turbulenceEnabled, AtomicBoolean running, boolean injectFailures) {
        return new Thread(() -> {
            Random random = new Random();
            long startTime = System.currentTimeMillis();

            while (running.get()) {
                try {
                    // Only apply turbulence if enabled
                    if (turbulenceEnabled.get()) {
                        // Create random jitter values to simulate turbulence
                        double rollJitter = (random.nextDouble() - 0.5) * 2.0;
                        double pitchJitter = (random.nextDouble() - 0.5) * 1.5;
                        double yawJitter = (random.nextDouble() - 0.5) * 1.0;

                        // Apply jitter
                        roll.setCurrentValue(roll.getCurrentValue() + rollJitter);
                        pitch.setCurrentValue(pitch.getCurrentValue() + pitchJitter);
                        yaw.setCurrentValue(yaw.getCurrentValue() + yawJitter);
                    }

                    // Task 3: Inject failures at 3, 6, and 9 seconds for testing recovery
                    if (injectFailures) {
                        long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                        if (elapsedSeconds == 3 || elapsedSeconds == 6 || elapsedSeconds == 9) {
                            throw new RuntimeException("Injected failure at " + elapsedSeconds + " seconds");
                        }
                    }

                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * Creates a thread that automatically demonstrates various flight maneuvers
     * loaded from a ManeuverScript file (Task 1)
     */
    private static Thread createAutomatedDemoThread(DirectionControl roll, DirectionControl pitch, DirectionControl yaw,
                                                     ManeuverScript script) {
        return new Thread(() -> {
            try {
                // Allow time for the simulation to start
                Thread.sleep(3000);
                System.out.println("\nStarting automated flight demonstration from script...");
                
                java.util.List<ManeuverScript.Maneuver> maneuvers = script.getManeuvers();
                int maneuverIndex = 0;

                while (true) {
                    ManeuverScript.Maneuver current = maneuvers.get(maneuverIndex % maneuvers.size());
                    
                    // Set targets
                    roll.setTargetValue(current.rollTarget);
                    pitch.setTargetValue(current.pitchTarget);
                    yaw.setTargetValue(current.yawTarget);
                    
                    System.out.println("\nManeuver " + (maneuverIndex + 1) + ": " +
                        "roll=" + String.format("%.1f", current.rollTarget) + "°, " +
                        "pitch=" + String.format("%.1f", current.pitchTarget) + "°, " +
                        "yaw=" + String.format("%.1f", current.yawTarget) + "°, " +
                        "duration=" + (current.durationMs / 1000) + "s");
                    
                    Thread.sleep(current.durationMs);
                    maneuverIndex++;
                }
            } catch (InterruptedException e) {
                System.out.println("Demo thread interrupted.");
            }
        });
    }
}