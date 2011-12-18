package rt.util;


import java.util.logging.*;

public class Rate {
	private long last_tick_time;
	private long now, diff;
	double rest;
	private static  Logger log = Logger.getLogger("rt.util.Rate");
	
	public void throttle (float delay) {
		now = System.currentTimeMillis();
		diff = now - last_tick_time;
		rest = delay * 1000.0 - diff;
		
		// only sleep its been less then delay seconds since last tick
		if (rest > 0) {
			try {
				log.fine ( "sleeping for " + (long) rest + " milliseconds");
				Thread.sleep((long) rest);
		
			} catch (InterruptedException e) {
				log.warning("Thread.sleep interrupted?");
			}
		}
	}
	
	public void tick () {
		last_tick_time = System.currentTimeMillis();
	}
	
}
