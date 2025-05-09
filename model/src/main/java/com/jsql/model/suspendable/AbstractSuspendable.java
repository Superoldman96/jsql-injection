package com.jsql.model.suspendable;

import com.jsql.model.InjectionModel;
import com.jsql.model.exception.JSqlException;
import com.jsql.util.LogLevelUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A thread used to inject database ; stoppable and pausable.
 */
public abstract class AbstractSuspendable {
    
    private static final Logger LOGGER = LogManager.getRootLogger();

    /**
     * Make the action to stop if true.
     */
    private boolean isStopped = false;
    
    /**
     * Make the action to pause if true, else make it unpause.
     */
    private boolean isPaused = false;

    protected final InjectionModel injectionModel;
    
    protected AbstractSuspendable(InjectionModel injectionModel) {
        this.injectionModel = injectionModel;
    }
    
    /**
     * The pausable/stoppable action.
     */
    public abstract String run(Object... args) throws JSqlException;

    /**
     * Thread's states Pause and Stop are processed by this method.<br>
     * - Pause action in infinite loop if invoked while shouldPauseThread is set to true,<br>
     * - Return stop state.
     * @return Stop state
     */
    public synchronized boolean isSuspended() {
        // Make application loop until shouldPauseThread is set to false by another user action
        while (this.isPaused) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                LOGGER.log(LogLevelUtil.IGNORE, e, e);
                Thread.currentThread().interrupt();
            }
        }
        return this.isStopped || this.injectionModel.isStoppedByUser();  // Return true if stop requested, else return false
    }
    
    /**
     * Mark as stopped.
     */
    public void stop() {
        this.unpause();
        this.isStopped = true;
    }
    
    /**
     * Mark as paused.
     */
    public void pause() {
        this.isPaused = true;
    }
    
    /**
     * Mark as unpaused.
     */
    public void unpause() {
        this.isPaused = false;
        this.resume();  // Restart the action after unpause
    }
    
    /**
     * Return true if thread is paused, false otherwise.
     * @return Pause state
     */
    public boolean isPaused() {
        return this.isPaused;
    }
    
    /**
     * Wake threads.
     */
    public synchronized void resume() {
        this.notifyAll();
    }
}
