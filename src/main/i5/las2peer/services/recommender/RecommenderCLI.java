package i5.las2peer.services.recommender;

import java.util.HashMap;
import java.util.Map;

import i5.las2peer.services.recommender.librec.util.Logs;
import i5.las2peer.services.recommender.rating.LibRec;

public class RecommenderCLI {
	
	private static Map<String,String> options;
	private static LibRec librec;
	
	public static void main(String[] args) {
		
		// Read options from command line arguments
		readOptions(args);
		
		// Create LibRec object
		if(options.containsKey("--rec-algo")){
			librec = new LibRec(options.get("--rec-algo"));
		}
		else {
			librec = new LibRec();
		}
		
		// Set parameters
		setParameters();
		
		// Read rating data from file
		String ratingsFile = options.containsKey("--ratings-file") ? options.get("--ratings-file") : "datasets/ratings.txt";
		try {
			librec.readRatingsFromFile(ratingsFile, LibRec.DatasetType.FilmTrust);
		} catch (Exception e) {
			Logs.error("Error reading from dataset file " + ratingsFile);
			Logs.error(e.getMessage());
			return;
		}
		
		try {
			librec.evaluate();
		} catch (InterruptedException e) {
			Logs.error("Error during evaluation");
			Logs.error(e.getMessage());
			return;
		}
		
	}
	
	private static void readOptions(String[] args){
		options = new HashMap<String,String>();
		String par = null;
		for(String arg : args){
			if(arg.startsWith("-")){
				par = arg;
			}
			else if(par != null){
				options.put(par, arg);
				par = null;
			}
		}
	}
	
	private static void setParameters() {
		
		if(options.containsKey("--cd-algo")){
			librec.setParameter("cd.algo", options.get("--cd-algo"));
		}
		
		if(options.containsKey("--cd-wt-steps")){
			librec.setParameter("cd.walktrap.steps", options.get("--cd-wt-steps"));
		}
		
		if(options.containsKey("--cd-dmid-iter-bound")){
			librec.setParameter("cd.dmid.iter", options.get("--cd-dmid-iter-bound"));
		}
		
		if(options.containsKey("--cd-dmid-prec-fact")){
			librec.setParameter("cd.dmid.prec", options.get("--cd-dmid-prec-fact"));
		}
		
		if(options.containsKey("--cd-dmid-prof-delta")){
			librec.setParameter("cd.dmid.proficiency", options.get("--cd-dmid-prof-delta"));
		}
		
		if(options.containsKey("--graph-method")){
			librec.setParameter("graph.method", options.get("--graph-method"));
		}
		
		if(options.containsKey("--graph-knn-k")){
			librec.setParameter("graph.knn.k", options.get("--graph-knn-k"));
		}
		
		if(options.containsKey("--graph-knn-sim")){
			librec.setParameter("graph.knn.sim", options.get("--graph-knn-sim"));
		}
		
		if(options.containsKey("--rec-factors")){
			librec.setParameter("num.factors", options.get("--rec-factors"));
		}
		
		if(options.containsKey("--rec-iters")){
			librec.setParameter("num.max.iter", options.get("--rec-iters"));
		}
		
		String lRate = "0.01";
		String lRateN = "0.01";
		boolean setLRate = false;
		if(options.containsKey("--rec-learn-rate")){
			lRate = options.get("--rec-learn-rate");
			setLRate = true;
		}
		if(options.containsKey("--rec-learn-rate-n")){
			lRateN = options.get("--rec-learn-rate-n");
			setLRate = true;
		}
		if (setLRate)
			librec.setParameter("learn.rate", lRate + " -n " + lRateN + " -max -1 -bold-driver");
		
		String lambda="0.1";
		String lambdau="0.001";
		String lambdai="0.001";
		String lambdab="0.001";
		boolean setLambda = false;
		if(options.containsKey("--rec-lambda")){
			lambda = options.get("--rec-lambda");
			setLambda = true;
		}
		if(options.containsKey("--rec-lambda-u")){
			lambdau = options.get("--rec-lambda-u");
			setLambda = true;
		}
		
		if(options.containsKey("--rec-lambda-i")){
			lambdai = options.get("--rec-lambda-i");
			setLambda = true;
		}
		
		if(options.containsKey("--rec-lambda-b")){
			lambdab = options.get("--rec-lambda-b");
			setLambda = true;
		}
		if(setLambda){
			librec.setParameter("reg.lambda", lambda + " -u " + lambdau + " -i " + lambdai + " -b " + lambdab);
		}
		
		String beta="0.04";
		String bins="30";
		boolean setTimeSVD = false;
		if(options.containsKey("--rec-beta")){
			beta = options.get("--rec-beta");
		}
		if(options.containsKey("--rec-bins")){
			bins = options.get("--rec-bins");
		}
		if(setTimeSVD){
			librec.setParameter("timeSVD++", "-beta " + beta + " -bins " + bins);
		}
		
		if(options.containsKey("--rec-wrmf-alpha")){
			librec.setParameter("WRMF", "-alpha " + options.get("--rec-wrmf-alpha"));
		}
		
		if(options.containsKey("--rec-knn-sim")){
			librec.setParameter("similarity", options.get("--rec-knn-sim"));
		}
		
		if(options.containsKey("--rec-knn-shrink")){
			librec.setParameter("num.shrinkage", options.get("--rec-knn-shrink"));
		}
		
		if(options.containsKey("--rec-knn-k")){
			librec.setParameter("num.neighbors", options.get("--rec-knn-k"));
		}
	}
	
}
