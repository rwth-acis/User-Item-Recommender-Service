package i5.las2peer.services.recommender.rating;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import java.util.Properties;

import i5.las2peer.services.recommender.librec.data.DataDAO;
import i5.las2peer.services.recommender.librec.data.DataSplitter;
import i5.las2peer.services.recommender.librec.data.SparseMatrix;
import i5.las2peer.services.recommender.librec.intf.Recommender;
import i5.las2peer.services.recommender.librec.intf.Recommender.Measure;
import i5.las2peer.services.recommender.librec.ranking.WRMF;
import i5.las2peer.services.recommender.librec.rating.ComNeighSVDPlusPlus;
import i5.las2peer.services.recommender.librec.rating.ComNeighSVDPlusPlus2;
import i5.las2peer.services.recommender.librec.rating.ItemKNN;
import i5.las2peer.services.recommender.librec.rating.NeighSVDPlusPlus;
import i5.las2peer.services.recommender.librec.rating.SVDPlusPlus;
import i5.las2peer.services.recommender.librec.util.Dates;
import i5.las2peer.services.recommender.librec.util.FileConfiger;
import i5.las2peer.services.recommender.librec.util.FileIO;
import i5.las2peer.services.recommender.librec.util.Logs;

public class LibRec {
	
	private Algorithm algorithm;
	
	private Properties configuration;
	
	private SparseMatrix ratingsMatrix, timeMatrix;
	
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
			configuration.setProperty("learn.rate", "0.01 -max -1 -bold-driver");
			configuration.setProperty("reg.lambda", "0.1 -u 0.1 -i 0.1 -b 0.1");
			break;
		case "nsvd":
			this.algorithm = Algorithm.NeighSVDPlusPlus;
			configuration.setProperty("learn.rate", "0.007 -n 0.007 -max -1 -bold-driver");
			configuration.setProperty("reg.lambda", "0.1 -u 0.1 -i 0.1 -b 0.1 -n 0.15");
			break;
		case "tsvd":
			this.algorithm = Algorithm.TimeSVDPlusPlus;
			configuration.setProperty("learn.rate", "0.005 -max -1 -bold-driver");
			configuration.setProperty("reg.lambda", "0.05 -u 0.05 -i 0.05 -b 0.15");
			configuration.setProperty("timeSVD++", "-beta 0.04 -bins 30");
			break;
		case "tnsvd":
			this.algorithm = Algorithm.TimeNeighSVDPlusPlus;
			break;
		case "cnsvd":
			this.algorithm = Algorithm.ComNeighSVDPlusPlus;
			configuration.setProperty("learn.rate", "0.007 -n 0.007 -max -1 -bold-driver");
			configuration.setProperty("reg.lambda", "0.05 -u 0.05 -i 0.05 -b 0.05 -n 0.15");
			break;
		case "cnsvd2":
			this.algorithm = Algorithm.ComNeighSVDPlusPlus2;
			configuration.setProperty("learn.rate", "0.007 -n 0.007 -max -1 -bold-driver");
			configuration.setProperty("reg.lambda", "0.05 -u 0.05 -i 0.05 -b 0.05 -n 0.15");
			break;
		case "tcnsvd":
			this.algorithm = Algorithm.TimeComNeighSVDPlusPlus;
			break;
		case "cnsvd-fast":
			this.algorithm = Algorithm.ComNeighSVDPlusPlusFast;
			break;
		case "tcnsvd-fast":
			this.algorithm = Algorithm.TimeComNeighSVDPlusPlusFast;
			break;
		}
	}
	
	public void setParameter(String parameter, String value){
		configuration.setProperty(parameter, value);
	}
	
	public void setRatingsMatrix(SparseMatrix ratingsMatrix){
		this.ratingsMatrix = ratingsMatrix;
	}
	
	public void setTimeMatrix(SparseMatrix timeMatrix){
		this.timeMatrix = timeMatrix;
	}
	
	public void readRatingsFromFile(String filePath, DatasetType type) throws Exception {
		// DAO object
		DataDAO rateDao = new DataDAO(filePath);
		
		// data columns to use
		int[] columns;
//		if(type == DatasetType.FilmTrust){
		columns = new int[] {0, 1, 2};
//		}
//		else if(type == DatasetType.MovieLens){
//			columns = new int[] {0, 1, 2, 3};
//		}
		
		// is first line: headline
		rateDao.setHeadline(false);
		
		// rating threshold
		float binThold = -1;
		
		// time unit of ratings' timestamps
		rateDao.setTimeUnit(TimeUnit.SECONDS);
		
		SparseMatrix[] data = rateDao.readData(columns, binThold);
		ratingsMatrix = data[0];
		timeMatrix = data[1];
		
		Recommender.rateMatrix = ratingsMatrix;
		Recommender.timeMatrix = timeMatrix;
		Recommender.rateDao = rateDao;
		Recommender.binThold = binThold;
	}
	
	public void buildModel() throws Exception{
		Recommender.rateMatrix = ratingsMatrix;
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
		// TODO: Follow evaluation protocol, e.g. sort by date, ensure time-dependency of training/test data
		
		// Split data into training and testing data
		Recommender.rateMatrix = ratingsMatrix;
		Recommender.isEvaluate = true;
		
		// Add configuration to recommender
		Recommender.cf = new FileConfiger(configuration);
		
		int cvFolds = 5;

		DataSplitter ds = new DataSplitter(ratingsMatrix, cvFolds);

		Thread[] ts = new Thread[cvFolds];
		Recommender[] models = new Recommender[cvFolds];

		for (int i = 0; i < cvFolds; i++) {
			SparseMatrix[] kthFoldMatrices = ds.getKthFold(i + 1);
			Recommender algo = getRecommender(kthFoldMatrices[0], kthFoldMatrices[1], i + 1);

			models[i] = algo;
			ts[i] = new Thread(algo);
			ts[i].start();
		}

		for (Thread t : ts)
			t.join();

		// average performance of k-fold
		evalMeasures = new HashMap<>();
		for (Recommender model : models) {
			for (Entry<Measure, Double> en : model.measures.entrySet()) {
				Measure m = en.getKey();
				double val = evalMeasures.containsKey(m) ? evalMeasures.get(m) : 0.0;
				evalMeasures.put(m, val + en.getValue() / cvFolds);
				// TODO: Maybe not only compute average, but also mean and variance. Should do that if we use SLPA.
			}
		}

		String result = Recommender.getEvalInfo(evalMeasures);
		// we add quota symbol to indicate the textual format of time 
		String time = String.format("'%s','%s'", Dates.parse(evalMeasures.get(Measure.TrainTime).longValue()),
				Dates.parse(evalMeasures.get(Measure.TestTime).longValue()));

		// double commas as the separation of results and configuration
		StringBuilder sb = new StringBuilder();
		String config = models[0].toString();
		sb.append(models[0].algoName).append(",").append(result).append(",,");
		if (!config.isEmpty())
			sb.append(config).append(",");
		sb.append(time).append("\n");

		String evalInfo = sb.toString();
		Logs.info(evalInfo);

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
			break;
		case TimeNeighSVDPlusPlus:
			break;
		case ComNeighSVDPlusPlus:
			return new ComNeighSVDPlusPlus(trainMatrix, testMatrix, fold);
		case ComNeighSVDPlusPlus2:
			return new ComNeighSVDPlusPlus2(trainMatrix, testMatrix, fold);
		case TimeComNeighSVDPlusPlus:
			break;
		default:
			break;
		}
		return null;
	}
	
}
