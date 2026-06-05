/*
 * Copyright (C) 2025 Shivaji Patil, College of the North Atlantic
 * All rights reserved.
 *
 * Aircraft Simulation Project
 */

import java.util.function.BooleanSupplier;

/**
 * SupervisedRunner implements self-healing worker thread supervision.
 * 
 * When a worker thread crashes, this supervisor:
 * 1. Catches the exception and logs it with worker name and stack trace
 * 2. Implements exponential backoff: starts at 100ms, doubles after each failure, caps at 5s
 * 3. Resets backoff to 100ms after 10 consecutive seconds of success
 * 4. Limits restart attempts to 5 within 30 seconds
 * 
 * If the restart budget is exceeded, the supervisor logs a permanent failure and stops.
 */
public class SupervisedRunner implements Runnable {
    
    private final String workerName;
    private final Runnable work;
    private final BooleanSupplier isSimulationRunning;
    
    // Backoff state
    private long backoffMs = 100;  // Start at 100ms
    private final long MAX_BACKOFF_MS = 5000;  // Cap at 5 seconds
    
    // Restart tracking
    private long lastSuccessTime = System.currentTimeMillis();
    private final long SUCCESS_THRESHOLD_MS = 10000;  // 10 seconds of success resets backoff
    
    // Restart budget: max 5 restarts within 30 seconds
    private final int MAX_RESTARTS = 5;
    private final long BUDGET_WINDOW_MS = 30000;  // 30 seconds
    private long[] restartTimes = new long[MAX_RESTARTS];
    private int restartCount = 0;

    public SupervisedRunner(String workerName, Runnable work, BooleanSupplier isSimulationRunning) {
        this.workerName = workerName;
        this.work = work;
        this.isSimulationRunning = isSimulationRunning;
    }

    @Override
    public void run() {
        while (isSimulationRunning.getAsBoolean()) {
            try {
                // Run the worker
                lastSuccessTime = System.currentTimeMillis();
                work.run();
                
                // If the work completes normally, that's also a success
                long successDuration = System.currentTimeMillis() - lastSuccessTime;
                if (successDuration >= SUCCESS_THRESHOLD_MS) {
                    // Reset backoff after 10 seconds of continuous success
                    backoffMs = 100;
                    System.out.println("Worker \"" + workerName + "\" ran successfully for " + 
                        successDuration + "ms; backoff reset to 100ms");
                }
                
            } catch (Exception e) {
                handleWorkerFailure(e);
            }
        }
    }

    private void handleWorkerFailure(Exception exception) {
        // Check if we've exceeded the restart budget
        if (!canRestart()) {
            System.err.println("worker \"" + workerName + "\" exceeded restart budget; will not be restarted");
            System.err.println("Final exception: " + exception.getMessage());
            exception.printStackTrace();
            return;
        }
        
        // Log the failure
        System.err.println("Exception in worker \"" + workerName + "\": " + exception.getMessage());
        exception.printStackTrace();
        
        // Record this restart attempt
        recordRestartAttempt();
        
        // Sleep for the current backoff duration
        try {
            System.out.println("Restarting worker \"" + workerName + "\" after " + 
                backoffMs + "ms backoff...");
            Thread.sleep(backoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
        }
        
        // Double the backoff, capped at 5 seconds
        backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
        
        // Check if enough time has passed to reset backoff
        long timeSinceLastSuccess = System.currentTimeMillis() - lastSuccessTime;
        if (timeSinceLastSuccess >= SUCCESS_THRESHOLD_MS) {
            backoffMs = 100;
        }
    }

    private boolean canRestart() {
        long now = System.currentTimeMillis();
        
        // Count how many restarts occurred within the last 30 seconds
        int recentRestarts = 0;
        for (long restartTime : restartTimes) {
            if (now - restartTime < BUDGET_WINDOW_MS) {
                recentRestarts++;
            }
        }
        
        return recentRestarts < MAX_RESTARTS;
    }

    private void recordRestartAttempt() {
        long now = System.currentTimeMillis();
        restartTimes[restartCount % MAX_RESTARTS] = now;
        restartCount++;
    }
}
