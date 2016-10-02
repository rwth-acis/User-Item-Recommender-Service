package i5.las2peer.services.recommender.rating;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Table;

import java.util.Properties;
import java.util.Set;

import i5.las2peer.services.recommender.librec.data.CSVDataDAO;
import i5.las2peer.services.recommender.librec.data.DataDAO;
import i5.las2peer.services.recommender.librec.data.DataSplitter;
import i5.las2peer.services.recommender.librec.data.NetflixDataDAO;
import i5.las2peer.services.recommender.librec.data.SparseMatrix;
import i5.las2peer.services.recommender.librec.data.TagDataSplitter;
import i5.las2peer.services.recommender.librec.data.TimeDataSplitter;
import i5.las2peer.services.recommender.librec.intf.Recommender;
import i5.las2peer.services.recommender.librec.intf.Recommender.Measure;
import i5.las2peer.services.recommender.librec.ranking.WRMF;
import i5.las2peer.services.recommender.librec.rating.ComNeighSVDPlusPlus;
import i5.las2peer.services.recommender.librec.rating.ComNeighSVDPlusPlusFast;
import i5.las2peer.services.recommender.librec.rating.ItemKNN;
import i5.las2peer.services.recommender.librec.rating.NeighSVDPlusPlus;
import i5.las2peer.services.recommender.librec.rating.SVDPlusPlus;
import i5.las2peer.services.recommender.librec.rating.TimeComNeighSVD;
import i5.las2peer.services.recommender.librec.rating.TimeComNeighSVDFast;
import i5.las2peer.services.recommender.librec.rating.TimeNeighSVD;
import i5.las2peer.services.recommender.librec.rating.TimeSVD;
import i5.las2peer.services.recommender.librec.util.Dates;
import i5.las2peer.services.recommender.librec.util.FileConfiger;
import i5.las2peer.services.recommender.librec.util.FileIO;
import i5.las2peer.services.recommender.librec.util.Logs;

public class LibRec {
	
	private Algorithm algorithm;
	
	private Properties configuration;
	
	private SparseMatrix ratingsMatrix, timeMatrix;
	public static Table<Integer, Integer, Set<Long>> userTagTable, itemTagTable;

//	private int numUsers, numItems;
	
	private Recommender model;
	
	private Map<Measure,Double> evalMeasures;
	
	public enum DatasetType { FilmTrust, MovieLens, Netflix }
	
	public enum Algorithm {
		ItemKNN, WRMF, SVDPlusPlus, TimeSVDPlusPlus,
		NeighSVDPlusPlus, TimeNeighSVDPlusPlus,
		ComNeighSVDPlusPlus, ComNeighSVDPlusPlus2, ComNeighSVDPlusPlusFast,
		TimeComNeighSVDPlusPlus, TimeComNeighSVDPlusPlusFast
	}
	
	public LibRec(){
		this("itemknn");
	}
	
	public LibRec(String algorithm){
		configuration = new Properties();
		
		// Common options
		configuration.setProperty("item.ranking", "off -topN -1 -ignore -1");
		configuration.setProperty("evaluation.setup", "--test-view all");
		configuration.setProperty("output.setup", "off");
		configuration.setProperty("num.factors", "10");
		configuration.setProperty("num.max.iter", "30");
		
		switch(algorithm.toLowerCase()){
		case "itemknn":
			this.algorithm = Algorithm.ItemKNN;
			configuration.setProperty("similarity", "PCC");
			configuration.setProperty("num.shrinkage", "30");
			configuration.setProperty("num.neighbors", "50");
			break;
		case "wrmf":
			this.algorithm = Algorithm.WRMF;
			configuration.setProperty("learn.rate", "0.001 -max -1 -bold-driver");
			configuration.setProperty("reg.lambda", "0.1 -u 0.001 -i 0.001 -b 0.001");
			configuration.setProperty("WRMF", "-alpha 1");
			break;
		case "svd":
			this.algorithm = Algorithm.SVDPlusPlus;
			configuration.setProperty("learn.rate", "0.01 -max -1 -decay 0.95");
			configuration.setProperty("reg.lambda", "0.1");
			break;
		case "nsvd":
			this.algorithm = Algorithm.NeighSVDPlusPlus;
			configuration.setProperty("learn.rate", "0.01 -n 0.01 -f 0.01 -max -1 -bold-driver");
			configuration.setProperty("reg.lambda", "0.1 -b 0.1 -n 0.1 -u 0.1 -i 0.1");
			break;
		case "tsvd":
			this.algorithm = Algorithm.TimeSVDPlusPlus;
			configuration.setProperty("learn.rate", "0.001 -max -1 -bold-driver");
			configuration.setProperty("reg.lambda", "0.002 -u 0.002 -i 0.002 -b 0.002");
			configuration.setProperty("timeSVD++", "-beta 0.04 -bins 30");
			break;
		case "tnsvd":
			this.algorithm = Algorithm.TimeNeighSVDPlusPlus;
			configuration.setProperty("learn.rate", "0.005 -n 0.005 -f 0.005 -mu 0.0000001 -max -1 -decay 0.95");
			configuration.setProperty("reg.lambda", "0.002 -b 0.002 -n 0.002 -u 0.002 -i 0.002");
			configuration.setProperty("timeNeighSVD++", "-beta 0.04 -bins 30");
			break;
		case "cnsvd":
			this.algorithm = Algorithm.ComNeighSVDPlusPlus;
			configuration.setProperty("learn.rate", "0.007 -n 0.007 -f 0.007 -c 0.007 -cn 0.007 -cf 0.007 -max -1 -bold-driver");
			configuration.setProperty("reg.lambda", "0.05 -b 0.05 -n 0.15 -u 0.05 -i 0.05 -c 0.05 -cn 0.05 -cf 0.05");
			configuration.setProperty("ComNeighSVD++", "-k 200");
			break;
		case "cnsvd2":
			this.algorithm = Algorithm.ComNeighSVDPlusPlus2;
			configuration.setProperty("learn.rate", "0.007 -n 0.007 -f 0.007 -c 0.007 -cn 0.007 -cf 0.007 -max -1 -bold-driver");
			configuration.setProperty("reg.lambda", "0.05 -b 0.05 -n 0.15 -u 0.05 -i 0.05 -c 0.05 -cn 0.05 -cf 0.05");
			break;
		case "cnsvdfast":
			this.algorithm = Algorithm.ComNeighSVDPlusPlusFast;
			configuration.setProperty("learn.rate", "0.0001 -max -1");
			configuration.setProperty("reg.lambda", "0.05");
			break;
		case "tcnsvd":
			this.algorithm = Algorithm.TimeComNeighSVDPlusPlus;
			configuration.setProperty("learn.rate", "0.05 -mu 0.0000001 -max -1 -bold-driver");
			configuration.setProperty("reg.lambda", "0.002 -b 0.002 -n 0.002 -u 0.002 -i 0.002");
			configuration.setProperty("timeComNeighSVD++", "-beta 0.04 -bins 30 -cbins 2 -k 200");
			break;
		case "tcnsvdfast":
			this.algorithm = Algorithm.TimeComNeighSVDPlusPlusFast;
			configuration.setProperty("learn.rate", "0.0001 -mu 0.0000001 -max -1 -decay 0.95");
			configuration.setProperty("reg.lambda", "0.002");
			configuration.setProperty("timeComNeighSVD++Fast", "-beta 0.04 -bins 30");
			break;
		}
	}
	
	public void setParameter(String parameter, String value){
		configuration.setProperty(parameter, value);
	}
	
	public void readRatingsFromFile(String filePath, String type) throws Exception {
		// DAO object
		DataDAO rateDao;
		
		// rating threshold
		float binThold = -1;

		SparseMatrix[] data;
		
		if(type.toLowerCase().equals("filmtrust")){
			rateDao = new CSVDataDAO(filePath);
			int[] columns = new int[] {0, 1, 2};	// contains three columns: userId, movieId, rating
			rateDao.setHeadline(false); 			// does not contain headline
			rateDao.setTimeUnit(TimeUnit.SECONDS);	// time unit of ratings' timestamps, not used since FilmTrust does not contain timestamps
			data = rateDao.readData(columns, binThold);
		}
		else if(type.toLowerCase().equals("movielens")){
			rateDao = new CSVDataDAO(filePath);
			int[] columns = new int[] {0, 1, 2, 3};	// contains three columns: userId, movieId, rating, timestamp
			rateDao.setHeadline(true);	 			// first line is headline
			rateDao.setTimeUnit(TimeUnit.SECONDS);	// time unit of ratings' timestamps
			data = rateDao.readData(columns, binThold);
		}
		else if(type.toLowerCase().equals("netflix")){
			rateDao = new CSVDataDAO(filePath);
			int[] columns = new int[] {0, 1, 2, 3};	// contains three columns: userId, movieId, rating, timestamp
			rateDao.setHeadline(true);	 			// first line is headline
			rateDao.setTimeUnit(TimeUnit.MILLISECONDS);	// time unit of ratings' timestamps
			data = rateDao.readData(columns, binThold);
		}
		else if(type.toLowerCase().equals("netflix_orig")){
			rateDao = new NetflixDataDAO(filePath);
			data = rateDao.readData(binThold);
		}
		else{
			return;
		}
		
		ratingsMatrix = data[0];
		timeMatrix = data[1];
		
		Recommender.rateDao = rateDao;
		Recommender.binThold = binThold;
	}
	
	public void readTaggingsFromFile(String filePath) throws Exception {
		DataDAO rateDao = Recommender.rateDao;
		TimeUnit timeUnit = TimeUnit.SECONDS;
		
		BiMap<String, Integer> tagIds = HashBiMap.create();
		userTagTable = HashBasedTable.create();
		itemTagTable = HashBasedTable.create();
		
		// for statistics
		int numTaggings = 0;
		
		BufferedReader br = FileIO.getReader(filePath);
		
		boolean skipFirstLine = true;
		
		String line = null;
		while ((line = br.readLine()) != null) {
			if (skipFirstLine){
				skipFirstLine = false;
				continue;
			}
			
			String[] data = line.trim().split(",");
			
			// skip line if it has the wrong format
			if(data.length != 4){
				continue;
			}
			
			String userString = data[0];
			String itemString = data[1];
			String tagString = data[2].trim().toLowerCase().replaceAll("[\\p{Punct}\\p{Space}]", "");
			String timeString = data[3];
			
			int user;
			int item;
			
			// skip users and items that are not part of the ratings matrix
			try{
				user = rateDao.getUserId(userString);
				item = rateDao.getItemId(itemString);
			}
			catch (Exception e){
				continue;
			}
			
			int tag = tagIds.containsKey(tagString) ? tagIds.get(tagString) : tagIds.size();
			tagIds.put(tagString, tag);
			
			long mms = 0L;
			try {
				mms = Long.parseLong(timeString);
			} catch (NumberFormatException e) {
				mms = (long) Double.parseDouble(timeString);
			}
			long timestamp = timeUnit.toMillis(mms);
			
			if (!userTagTable.contains(user, tag)){
				userTagTable.put(user, tag, new HashSet<Long>());
			}
			if (!itemTagTable.contains(item, tag)){
				itemTagTable.put(item, tag, new HashSet<Long>());
			}
			
			userTagTable.get(user, tag).add(timestamp);
			itemTagTable.get(item, tag).add(timestamp);
			
			numTaggings++;
		}
		
		int numUserTags = userTagTable.columnKeySet().size();
		int numItemTags = itemTagTable.columnKeySet().size();
		Logs.info("Tagging data: number of tagging instances: {}, unique user tags: {}, unique item tags: {}",
				numTaggings, numUserTags, numItemTags);
	}
	
	public void printDatasetSpecifications() throws Exception{
		if(Recommender.rateDao != null){
			Recommender.rateDao.printSpecs();
		}
	}
	
	public void buildModel() throws Exception{
		Recommender.rateMatrix = ratingsMatrix;
		Recommender.timeMatrix = timeMatrix;
		Recommender.isEvaluate = false;
		
		// Add configuration to recommender
		Recommender.cf = new FileConfiger(configuration);
		
		model = getRecommender(ratingsMatrix, null, -1);
		model.execute();
	}
	
	public double getPrediction(int u, int i) throws Exception{
		return model.getPrediction(u,i);
	}
	
	public void evaluate() throws InterruptedException{
		// Split data into training and testing data
		Recommender.rateMatrix = ratingsMatrix;
		Recommender.timeMatrix = timeMatrix;
		Recommender.isEvaluate = true;
		
		// Add configuration to recommender
		FileConfiger cf = new FileConfiger(configuration);
		Recommender.cf = cf;
		
		String evalType = cf.getString("eval.type", "TimeCV");
		int folds = cf.getInt("eval.folds", 5);
		boolean isParallel = cf.getString("eval.parallel", "true").toLowerCase().equals("false") ? false : true;
		
		Thread[] ts = new Thread[folds];
		Recommender[] models = new Recommender[folds];
		
		switch (evalType.toLowerCase()){
		case "cv":
			DataSplitter ds = new DataSplitter(ratingsMatrix, folds);
			for (int i = 0; i < folds; i++) {
				SparseMatrix[] kthFoldMatrices = ds.getKthFold(i + 1);
				models[i] = getRecommender(kthFoldMatrices[0], kthFoldMatrices[1], i + 1);
			}
			break;
		default:
		case "timecv":
			double trainRatio = cf.getDouble("eval.train.ratio", 0.8);
			double foldSize = cf.getDouble("eval.fold.size", 0.2);
			TimeDataSplitter timeds = new TimeDataSplitter(ratingsMatrix, timeMatrix, folds, trainRatio, foldSize);
			TagDataSplitter tagds = null;
			if (userTagTable != null && itemTagTable != null){
				tagds = new TagDataSplitter(userTagTable, itemTagTable, timeMatrix, folds, trainRatio, foldSize);
			}
			for (int i = 0; i < folds; i++) {
				SparseMatrix[] kthFoldMatrices = timeds.getKthFold(i + 1);
				models[i] = getRecommender(kthFoldMatrices[0], kthFoldMatrices[1], i + 1);
				if (tagds != null){
					models[i].userTagTable = tagds.getKthFoldUserTagTable(i + 1);
					models[i].itemTagTable = tagds.getKthFoldItemTagTable(i + 1);
				}
			}
			break;
		}
		
		for (int i = 0; i < folds; i++) {
			ts[i] = new Thread(models[i]);
			ts[i].start();
			if (!isParallel)
				ts[i].join();
		}
		
		if (isParallel){
			for (Thread t : ts)
				t.join();
		}
		
		// average performance of k-fold
		evalMeasures = new HashMap<>();
		for (Recommender model : models) {
			for (Entry<Measure, Double> en : model.measures.entrySet()) {
				Measure m = en.getKey();
				double val = evalMeasures.containsKey(m) ? evalMeasures.get(m) : 0.0;
				evalMeasures.put(m, val + en.getValue() / folds);
				// TODO: Maybe not only compute average, but also mean and variance. Should do that if we use SLPA.
			}
		}
		
		String ratingResult = Recommender.getRatingEvalInfo(evalMeasures);
		String rankingResult = Recommender.getRankingEvalInfo(evalMeasures);
		
		String algoName = models[0].algoName;
		
		String algoConfigInfo = algoName + " configuration: " + models[0].toString();
		String evalTimeInfo = algoName + " time measurements: [TrainTime,TestTime] = ["
				+ Dates.parse(evalMeasures.get(Measure.TrainTime).longValue()) + ","
				+ Dates.parse(evalMeasures.get(Measure.TestTime).longValue()) + "]";
		String evalRatingInfo = algoName + " rating evaluation measurements: " + ratingResult;
		String evalRankingInfo = algoName + " ranking evaluation measurements: " + rankingResult;

		Logs.info(algoConfigInfo);
		Logs.info(evalTimeInfo);
		Logs.info(evalRatingInfo);
		Logs.info(evalRankingInfo);
	}
	
	public double getEvalResult(Measure measure){
		return evalMeasures.get(measure);
	}
	
	private Recommender getRecommender(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold){
		switch(algorithm){
		case ItemKNN:
			return new ItemKNN(trainMatrix, testMatrix, fold);
		case WRMF:
			return new WRMF(trainMatrix, testMatrix, fold);
		case SVDPlusPlus:
			return new SVDPlusPlus(trainMatrix, testMatrix, fold);
		case NeighSVDPlusPlus:
			return new NeighSVDPlusPlus(trainMatrix, testMatrix, fold);
		case TimeSVDPlusPlus:
			return new TimeSVD(trainMatrix, testMatrix, fold);
		case TimeNeighSVDPlusPlus:
			return new TimeNeighSVD(trainMatrix, testMatrix, fold);
		case ComNeighSVDPlusPlus:
			return new ComNeighSVDPlusPlus(trainMatrix, testMatrix, fold);
		case ComNeighSVDPlusPlusFast:
			return new ComNeighSVDPlusPlusFast(trainMatrix, testMatrix, fold);
		case TimeComNeighSVDPlusPlus:
			return new TimeComNeighSVD(trainMatrix, testMatrix, fold);
		case TimeComNeighSVDPlusPlusFast:
			return new TimeComNeighSVDFast(trainMatrix, testMatrix, fold);
		default:
			break;
		}
		return null;
	}
	
}
