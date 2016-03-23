package i5.las2peer.services.recommender.utils;

import java.util.logging.Level;

import i5.las2peer.logging.L2pLogger;
import i5.las2peer.logging.NodeObserver.Event;
import i5.las2peer.services.recommender.storage.StorageService;

public class Logger {

	private static final L2pLogger logger = L2pLogger.getInstance(StorageService.class.getName());
	
	public static void logError(Object from, Exception e){
		logger.log(Level.SEVERE, e.toString(), e);
		L2pLogger.logEvent(from, Event.SERVICE_ERROR, e.toString());
	}
	
	public static void logError(Object from, String msg){
		logger.log(Level.SEVERE, msg);
		L2pLogger.logEvent(from, Event.SERVICE_ERROR, msg);
	}
	
}
