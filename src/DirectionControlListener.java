/*
 * Copyright (C) 2025 Shivaji Patil, College of the North Atlantic
 * All rights reserved.
 *
 * Aircraft Simulation Project
 */

/**
 * Listener interface for DirectionControl value changes.
 * Implemented by classes that need to react when an aircraft axis changes value.
 * 
 * Thread safety note: onDirectionChanged() is called from the simulation thread.
 * Listeners must ensure thread-safe publication of values to the Swing EDT.
 */
public interface DirectionControlListener {
    
    /**
     * Called when a DirectionControl's current value changes.
     * 
     * @param control The DirectionControl instance that changed
     */
    void onDirectionChanged(DirectionControl control);
}
