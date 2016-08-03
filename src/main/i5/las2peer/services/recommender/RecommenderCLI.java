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
		String ratingsFile = options.containsKey("--ratings-file") ? options.get("--ratings-file") : "datasets/filmtrust/ratings.txt";
		String datasetType = options.containsKey("--dataset-type") ? options.get("--dataset-type") : "filmtrust";
		try {
			librec.readRatingsFromFile(ratingsFile, datasetType);
		} catch (Exception e) {
			Logs.error("Error reading from dataset file " + ratingsFile);
			Logs.error(e.getMessage());
			e.printStackTrace();
			return;
		}
		
		try {
			librec.printDatasetSpecifications();
		} catch (Exception e) {
			Logs.error("Error printing the dataset specifications");
			Logs.error(e.getMessage());
			e.printStackTrace();
			return;
		}
		
		try {
			librec.evaluate();
		} catch (InterruptedException e) {
			Logs.error("Error during evaluation");
			Logs.error(e.getMessage());
			e.printStackTrace();
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
		String lRateF = "0.01";
		String lRateC = "0.01";
		String lRateCN = "0.01";
		String lRateCF = "0.01";
		boolean setLRate = false;
		if(options.containsKey("--rec-learn-rate")){
			lRate = options.get("--rec-learn-rate");
			setLRate = true;
		}
		if(options.containsKey("--rec-learn-rate-n")){
			lRateN = options.get("--rec-learn-rate-n");
			setLRate = true;
		}
		if(options.containsKey("--rec-learn-rate-f")){
			lRateF = options.get("--rec-learn-rate-f");
			setLRate = true;
		}
		if(options.containsKey("--rec-learn-rate-c")){
			lRateC = options.get("--rec-learn-rate-c");
			setLRate = true;
		}
		if(options.containsKey("--rec-learn-rate-cn")){
			lRateCN = options.get("--rec-learn-rate-cn");
			setLRate = true;
		}
		if(options.containsKey("--rec-learn-rate-cf")){
			lRateCF = options.get("--rec-learn-rate-cf");
			setLRate = true;
		}
		if (setLRate)
			librec.setParameter("learn.rate", lRate
					+ " -n " + lRateN
					+ " -f " + lRateF
					+ " -c " + lRateC
					+ " -cn " + lRateCN
					+ " -cf " + lRateCF
					+ " -max -1 -bold-driver");
		
		String lambda="0.1";
		String lambdaB="0.001";
		String lambdaN="0.001";
		String lambdaU="0.001";
		String lambdaI="0.001";
		String lambdaC="0.001";
		String lambdaCN="0.001";
		String lambdaCF="0.001";
		boolean setLambda = false;
		if(options.containsKey("--rec-lambda")){
			lambda = options.get("--rec-lambda");
			setLambda = true;
		}
		if(options.containsKey("--rec-lambda-b")){
			lambdaB = options.get("--rec-lambda-b");
			setLambda = true;
		}
		if(options.containsKey("--rec-lambda-n")){
			lambdaN = options.get("--rec-lambda-n");
			setLambda = true;
		}
		if(options.containsKey("--rec-lambda-u")){
			lambdaU = options.get("--rec-lambda-u");
			setLambda = true;
		}
		if(options.containsKey("--rec-lambda-i")){
			lambdaI = options.get("--rec-lambda-i");
			setLambda = true;
		}
		if(options.containsKey("--rec-lambda-c")){
			lambdaC = options.get("--rec-lambda-c");
			setLambda = true;
		}
		if(options.containsKey("--rec-lambda-cn")){
			lambdaCN = options.get("--rec-lambda-cn");
			setLambda = true;
		}
		if(options.containsKey("--rec-lambda-cf")){
			lambdaCF = options.get("--rec-lambda-cf");
			setLambda = true;
		}
		if(setLambda){
			librec.setParameter("reg.lambda", lambda
					+ " -b " + lambdaB
					+ " -n " + lambdaN
					+ " -u " + lambdaU
					+ " -i " + lambdaI
					+ " -c " + lambdaC
					+ " -cn " + lambdaCN
					+ " -cf " + lambdaCF);
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
