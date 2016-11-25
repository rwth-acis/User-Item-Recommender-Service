package i5.las2peer.services.recommender;

import java.util.HashMap;
import java.util.Map;

import i5.las2peer.services.recommender.librec.main.LibRec;
import i5.las2peer.services.recommender.librec.util.Logs;

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
		
		// Read tag data from file
		if (options.containsKey("--tags-file")){
			String taggingsFile = options.get("--tags-file"); 
			try {
				librec.readTaggingsFromFile(taggingsFile);
			} catch (Exception e) {
				Logs.error("Error reading from tag file " + taggingsFile);
				Logs.error(e.getMessage());
				e.printStackTrace();
				return;
			}
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
		} catch (Exception e) {
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
		
		if(options.containsKey("--rec-tcnsvd-cbins")){
			librec.setParameter("timeComNeighSVD++", "-beta 0.04 -bins 30 -cbins " + options.get("--rec-tcnsvd-cbins") + " -k 200");
		}
		
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
		
		if(options.containsKey("--cd-max-oc")){
			librec.setParameter("cd.max.oc", options.get("--cd-max-oc"));
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
		
		String lRateStr="";
		boolean setLRate = false;
		if(options.containsKey("--rec-learn-rate")){
			lRateStr += options.get("--rec-learn-rate");
			setLRate = true;
		}
		for (String l : new String[] {"n", "f", "c", "cn", "cf", "mu"}){
			String optStr = String.format("--rec-learn-rate-%s", l);
			if(options.containsKey(optStr)){
				if (!setLRate)
					lRateStr += options.get(optStr);
				lRateStr += String.format(" -%s %s", l, options.get(optStr));
				setLRate = true;
			}
		}
		if (setLRate){
			lRateStr += " -decay 0.95";
			librec.setParameter("learn.rate", lRateStr);
		}

		String lambdaStr="";
		boolean setLambda = false;
		if(options.containsKey("--rec-lambda")){
			lambdaStr += options.get("--rec-lambda");
			setLambda = true;
		}
		for (String l : new String[] {"b", "n", "f", "c", "cn", "cf"}){
			String optStr = String.format("--rec-lambda-%s", l);
			if(options.containsKey(optStr)){
				if (!setLambda)
					lambdaStr += options.get(optStr);
				lambdaStr += String.format(" -%s %s", l, options.get(optStr));
				setLambda = true;
			}
		}
		if (setLambda){
			librec.setParameter("reg.lambda", lambdaStr);
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
		
		if(options.containsKey("--eval-type")){
			librec.setParameter("eval.type", options.get("--eval-type"));
		}
		
		if(options.containsKey("--eval-folds")){
			librec.setParameter("eval.folds", options.get("--eval-folds"));
		}
		
		if(options.containsKey("--eval-train-ratio")){
			librec.setParameter("eval.train.ratio", options.get("--eval-train-ratio"));
		}
		
		if(options.containsKey("--eval-fold-size")){
			librec.setParameter("eval.fold.size", options.get("--eval-fold-size"));
		}
		
		if(options.containsKey("--eval-parallel")){
			librec.setParameter("eval.parallel", options.get("--eval-parallel"));
		}
		
		if(options.containsKey("--eval-iter")){
			librec.setParameter("eval.iter", options.get("--eval-iter"));
		}
	}
	
}
