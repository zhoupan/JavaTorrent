package org.johnnei.javatorrent.async;

import org.johnnei.javatorrent.utils.Argument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Runnable} wrapper which handles the stopping of a runnable which should be looping.
 */
public class LoopingRunnable implements Runnable {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoopingRunnable.class);

	private final Runnable runnable;

	private boolean keepRunning;

	public LoopingRunnable(Runnable runnable) {
		this.runnable = Argument.requireNonNull(runnable, "Runnable not cannot be null");
		keepRunning = true;
	}

	/**
	 * Stops the runnable once it has completed its cycle.
	 */
	public void stop() {
		keepRunning = false;
	}

	@Override
	public void run() {
		while (keepRunning) {
			runnable.run();

			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				LOGGER.trace("Ignoring interrupted exception for endless looping task.", e);
				Thread.currentThread().interrupt();
			}
		}
	}

}
