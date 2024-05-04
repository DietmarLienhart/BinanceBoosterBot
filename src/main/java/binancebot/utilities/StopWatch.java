package binancebot.utilities;

import java.util.Hashtable;

public class StopWatch {

	private static final Hashtable<String, Double> startTime = new Hashtable<String, Double>();

	/** start a process by giving a task name */
	public static void start(String taskname) {
		startTime.put(taskname, Double.valueOf(System.currentTimeMillis()));
	}

	/** returns the time needed for a task so far and removes it */
	public static double stop(String taskname) {
		return (System.currentTimeMillis() - ((double) startTime.remove(taskname))) / 1000;
	}

	/**
	 * print time that was needed for a given identifier since start and removes it afterwards!
	 */
	public static double end(String taskname) {
		double seconds = stop(taskname);
		Log.log("Time needed for " + taskname + ": " + seconds + " seconds");
		startTime.remove(taskname);
		return seconds;
	}

	/** returns the time needed for a task so far */
	public static double getTime(String taskname) {
		return (System.currentTimeMillis() - ((double) startTime.get(taskname))) / 1000;
	}

}