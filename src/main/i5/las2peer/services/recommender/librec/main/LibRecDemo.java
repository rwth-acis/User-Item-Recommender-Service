// Copyright (C) 2014 Guibing Guo
//
// This file is part of LibRec.
//
// LibRec is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// LibRec is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with LibRec. If not, see <http://www.gnu.org/licenses/>.
//

package i5.las2peer.services.recommender.librec.main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import i5.las2peer.services.recommender.librec.baseline.ConstantGuess;
import i5.las2peer.services.recommender.librec.baseline.GlobalAverage;
import i5.las2peer.services.recommender.librec.baseline.ItemAverage;
import i5.las2peer.services.recommender.librec.baseline.ItemCluster;
import i5.las2peer.services.recommender.librec.baseline.MostPopular;
import i5.las2peer.services.recommender.librec.baseline.RandomGuess;
import i5.las2peer.services.recommender.librec.baseline.UserAverage;
import i5.las2peer.services.recommender.librec.baseline.UserCluster;
import i5.las2peer.services.recommender.librec.data.DataDAO;
import i5.las2peer.services.recommender.librec.data.DataSplitter;
import i5.las2peer.services.recommender.librec.data.MatrixEntry;
import i5.las2peer.services.recommender.librec.data.SparseMatrix;
import i5.las2peer.services.recommender.librec.ext.AR;
import i5.las2peer.services.recommender.librec.ext.External;
import i5.las2peer.services.recommender.librec.ext.Hybrid;
import i5.las2peer.services.recommender.librec.ext.NMF;
import i5.las2peer.services.recommender.librec.ext.PD;
import i5.las2peer.services.recommender.librec.ext.PRankD;
import i5.las2peer.services.recommender.librec.ext.SlopeOne;
import i5.las2peer.services.recommender.librec.intf.GraphicRecommender;
import i5.las2peer.services.recommender.librec.intf.IterativeRecommender;
import i5.las2peer.services.recommender.librec.intf.Recommender;
import i5.las2peer.services.recommender.librec.intf.Recommender.Measure;
import i5.las2peer.services.recommender.librec.ranking.BHfree;
import i5.las2peer.services.recommender.librec.ranking.BPR;
import i5.las2peer.services.recommender.librec.ranking.BUCM;
import i5.las2peer.services.recommender.librec.ranking.CLiMF;
import i5.las2peer.services.recommender.librec.ranking.FISMauc;
import i5.las2peer.services.recommender.librec.ranking.FISMrmse;
import i5.las2peer.services.recommender.librec.ranking.GBPR;
import i5.las2peer.services.recommender.librec.ranking.ItemBigram;
import i5.las2peer.services.recommender.librec.ranking.LDA;
import i5.las2peer.services.recommender.librec.ranking.LRMF;
import i5.las2peer.services.recommender.librec.ranking.RankALS;
import i5.las2peer.services.recommender.librec.ranking.RankSGD;
import i5.las2peer.services.recommender.librec.ranking.SBPR;
import i5.las2peer.services.recommender.librec.ranking.SLIM;
import i5.las2peer.services.recommender.librec.ranking.WBPR;
import i5.las2peer.services.recommender.librec.ranking.WRMF;
import i5.las2peer.services.recommender.librec.rating.BPMF;
import i5.las2peer.services.recommender.librec.rating.BiasedMF;
import i5.las2peer.services.recommender.librec.rating.CPTF;
import i5.las2peer.services.recommender.librec.rating.GPLSA;
import i5.las2peer.services.recommender.librec.rating.ItemKNN;
import i5.las2peer.services.recommender.librec.rating.LDCC;
import i5.las2peer.services.recommender.librec.rating.PMF;
import i5.las2peer.services.recommender.librec.rating.RSTE;
import i5.las2peer.services.recommender.librec.rating.RfRec;
import i5.las2peer.services.recommender.librec.rating.SVDPlusPlus;
import i5.las2peer.services.recommender.librec.rating.SoRec;
import i5.las2peer.services.recommender.librec.rating.SoReg;
import i5.las2peer.services.recommender.librec.rating.SocialMF;
import i5.las2peer.services.recommender.librec.rating.TimeSVD;
import i5.las2peer.services.recommender.librec.rating.TrustMF;
import i5.las2peer.services.recommender.librec.rating.TrustSVD;
import i5.las2peer.services.recommender.librec.rating.URP;
import i5.las2peer.services.recommender.librec.rating.UserKNN;
import i5.las2peer.services.recommender.librec.util.Dates;
import i5.las2peer.services.recommender.librec.util.EMailer;
import i5.las2peer.services.recommender.librec.util.FileConfiger;
import i5.las2peer.services.recommender.librec.util.FileIO;
import i5.las2peer.services.recommender.librec.util.LineConfiger;
import i5.las2peer.services.recommender.librec.util.Logs;
import i5.las2peer.services.recommender.librec.util.Randoms;
import i5.las2peer.services.recommender.librec.util.Strings;
import i5.las2peer.services.recommender.librec.util.Systems;

/**
 * Main Class of the LibRec Library
 * 
 * @author guoguibing
 * 
 */
public class LibRecDemo {
	// version: MAJOR version (significant changes), followed by MINOR version (small changes, bug fixes)
	protected static String version = "1.4";
	// output directory path
	protected static String tempDirPath = "./Results/";

	// configuration
	protected FileConfiger cf;
	protected List<String> configFiles;
	protected String algorithm;

	protected float binThold;
	protected int[] columns;
	protected TimeUnit timeUnit;

	// rate DAO object
	protected DataDAO rateDao;
	// line configer for rating data, LibRec outputs
	protected LineConfiger ratingOptions, outputOptions;

	// rating, timestamp matrix
	protected SparseMatrix rateMatrix, timeMatrix;

	/**
	 * run the LibRec library
	 * @param args command line arguments
	 * @throws Exception on various errors processing arguments, file I/O, running the algorithm
	 */
	protected void execute(String[] args) throws Exception {
		// process librec arguments
		cmdLine(args);

		// multiple runs at one time
		for (String config : configFiles) {

			// reset general settings
			preset(config);

			// prepare data
			readData();

			// run a specific algorithm
			run();
		}

		// collect results
		String filename = (configFiles.size() > 1 ? "multiAlgorithms" : algorithm) + "@" + Dates.now() + ".txt";
		String results = tempDirPath + filename;
		FileIO.copyFile("results.txt", results);

		// send notification
		notifyMe(results);
	}

	/**
	 * read input data
	 * @throws Exception on file I/O and number formatting errors
	 */
	protected void readData() throws Exception {
		// DAO object
		rateDao = new DataDAO(cf.getPath("dataset.ratings"));

		// data configuration
		ratingOptions = cf.getParamOptions("ratings.setup");

		// data columns to use
		List<String> cols = ratingOptions.getOptions("-columns");
		columns = new int[cols.size()];
		for (int i = 0; i < cols.size(); i++)
			columns[i] = Integer.parseInt(cols.get(i));

		// is first line: headline
		rateDao.setHeadline(ratingOptions.contains("-headline"));

		// rating threshold
		binThold = ratingOptions.getFloat("-threshold");

		// time unit of ratings' timestamps
		timeUnit = TimeUnit.valueOf(ratingOptions.getString("--time-unit", "seconds").toUpperCase());
		rateDao.setTimeUnit(timeUnit);

//		SparseMatrix[] data = ratingOptions.contains("--as-tensor") ? rateDao.readTensor(columns, binThold) : rateDao
//				.readData(columns, binThold);
		SparseMatrix[] data = rateDao.readData(columns, binThold);
		rateMatrix = data[0];
		timeMatrix = data[1];

		Recommender.rateMatrix = rateMatrix;
		Recommender.timeMatrix = timeMatrix;
		Recommender.rateDao = rateDao;
		Recommender.binThold = binThold;
	}

	/**
	 * reset general (and static) settings
	 * @param configFile string representing the file to read the configuration from
	 * @throws Exception on file I/O errors
	 */
	protected void preset(String configFile) throws Exception {

		// a new configer
		cf = new FileConfiger(configFile);

		// seeding the general recommender
		Recommender.cf = cf;

		// reset recommenders' static properties 
		Recommender.resetStatics = true;
		IterativeRecommender.resetStatics = true;
		GraphicRecommender.resetStatics = true;

		// LibRec outputs
		outputOptions = cf.getParamOptions("output.setup");
		if (outputOptions != null) {
			tempDirPath = outputOptions.getString("-dir", "./Results/");
		}

		// make output directory
		Recommender.tempDirPath = FileIO.makeDirectory(tempDirPath);

		// initialize random seed 
		LineConfiger evalOptions = cf.getParamOptions("evaluation.setup");
		Randoms.seed(evalOptions.getLong("--rand-seed", System.currentTimeMillis())); // initial random seed
	}

	/**
	 * entry of the LibRec library
	 * @param args command line arguments
	 */
	public static void main(String[] args) {

		try {
			new LibRecDemo().execute(args);

		} catch (Exception e) {
			// capture exception to log file
			Logs.error(e.getMessage());

			e.printStackTrace();
		}
	}

	/**
	 * process arguments specified at the command line
	 * 
	 * @param args
	 *            command line arguments
	 * @throws Exception on file I/O errors
	 */
	protected void cmdLine(String[] args) throws Exception {

		if (args == null || args.length < 1) {
			if (configFiles == null)
				configFiles = Arrays.asList("librec.conf");
			return;
		}

		LineConfiger paramOptions = new LineConfiger(args);

		configFiles = paramOptions.contains("-c") ? paramOptions.getOptions("-c") : Arrays.asList("librec.conf");

		if (paramOptions.contains("-v")) {
			// print out short version information
			System.out.println("LibRec version " + version);
			System.exit(0);
		}

		if (paramOptions.contains("--version")) {
			// print out full version information
			about();
			System.exit(0);
		}

		if (paramOptions.contains("--dataset-spec")) {
			for (String configFile : configFiles) {
				// print out data set specification
				cf = new FileConfiger(configFile);

				readData();

				rateDao.printSpecs();

				String socialSet = cf.getPath("dataset.social");
				if (!socialSet.equals("-1")) {
					DataDAO socDao = new DataDAO(socialSet, rateDao.getUserIds());
					socDao.printSpecs();
				}
			}
			System.exit(0);
		}

		if (paramOptions.contains("--dataset-split")) {

			for (String configFile : configFiles) {
				// split the training data set into "train-test" or "train-validation-test" subsets
				cf = new FileConfiger(configFile);

				readData();

				double trainRatio = paramOptions.getDouble("-r", 0.8);
				boolean isValidationUsed = paramOptions.contains("-v");
				double validRatio = isValidationUsed ? paramOptions.getDouble("-v") : 0;

				if (trainRatio <= 0 || validRatio < 0 || (trainRatio + validRatio) >= 1) {
					throw new Exception(
							"Wrong format! Accepted formats are either '-dataset-split ratio' or '-dataset-split trainRatio validRatio'");
				}

				// split data
				DataSplitter ds = new DataSplitter(rateMatrix);

				SparseMatrix[] data = null;
				if (isValidationUsed) {
					data = ds.getRatio(trainRatio, validRatio);
				} else {

					switch (paramOptions.getString("-target")) {
					case "u":
						data = paramOptions.contains("--by-date") ? ds.getRatioByUserDate(trainRatio, timeMatrix) : ds
								.getRatioByUser(trainRatio);
						break;
					case "i":
						data = paramOptions.contains("--by-date") ? ds.getRatioByItemDate(trainRatio, timeMatrix) : ds
								.getRatioByItem(trainRatio);
						break;
					case "r":
					default:
						data = paramOptions.contains("--by-date") ? ds.getRatioByRatingDate(trainRatio, timeMatrix)
								: ds.getRatioByRating(trainRatio);
						break;
					}
				}

				// write out
				String dirPath = FileIO.makeDirectory(rateDao.getDataDirectory(), "split");
				writeMatrix(data[0], dirPath + "training.txt");

				if (isValidationUsed) {
					writeMatrix(data[1], dirPath + "validation.txt");
					writeMatrix(data[2], dirPath + "test.txt");
				} else {
					writeMatrix(data[1], dirPath + "test.txt");
				}
			}
			System.exit(0);
		}

	}

	/**
	 * write a matrix data into a file
	 * @param data data matrix
	 * @param filePath file to write
	 * @throws Exception on file I/O errors
	 */
	private void writeMatrix(SparseMatrix data, String filePath) throws Exception {
		// delete old file first
		FileIO.deleteFile(filePath);

		List<String> lines = new ArrayList<>(1500);
		for (MatrixEntry me : data) {
			int u = me.row();
			int j = me.column();
			double ruj = me.get();

			if (ruj <= 0)
				continue;

			String user = rateDao.getUserId(u);
			String item = rateDao.getItemId(j);
			String timestamp = timeMatrix != null ? " " + timeMatrix.get(u, j) : "";

			lines.add(user + " " + item + " " + (float) ruj + timestamp);

			if (lines.size() >= 1000) {
				FileIO.writeList(filePath, lines, true);
				lines.clear();
			}
		}

		if (lines.size() > 0)
			FileIO.writeList(filePath, lines, true);

		Logs.debug("Matrix data is written to: {}", filePath);
	}

	/**
	 * run a specific algorithm one or multiple times
	 * @throws Exception on file I/O and algorithm errors
	 */
	protected void run() throws Exception {

		// evaluation setup
		String setup = cf.getString("evaluation.setup");
		LineConfiger evalOptions = new LineConfiger(setup);

		Logs.info("With Setup: {}", setup);

		// repeat many times with the same settings
		int numRepeats = evalOptions.getInt("--repeat", 1);
		for (int i = 0; i < numRepeats; i++) {
			runAlgorithm(evalOptions);
		}
	}

	/**
	 * prepare training and test data, and then run a specified recommender
	 * @param evalOptions evaluation options
	 * @throws Exception on errors splitting the data or running the algorithm
	 */
	private void runAlgorithm(LineConfiger evalOptions) throws Exception {

		DataSplitter ds = new DataSplitter(rateMatrix);
		SparseMatrix[] data = null;

		int N;
		double ratio;
		Recommender algo = null;

		switch (evalOptions.getMainParam().toLowerCase()) {
		case "cv":
			runCrossValidation(evalOptions);
			return; // make it close
		case "leave-one-out":
			boolean isByDate = evalOptions.contains("--by-date");
			switch (evalOptions.getString("-target", "r")) {
			case "u":
				data = ds.getLOOByUser(isByDate, timeMatrix);
				break;
			case "i":
				data = ds.getLOOByItem(isByDate, timeMatrix);
				break;
			case "r":
			default:
				runLeaveOneOut(evalOptions);
				return; //
			}
			break;
		case "test-set":
			DataDAO testDao = new DataDAO(evalOptions.getString("-f"), rateDao.getUserIds(), rateDao.getItemIds());
			testDao.setTimeUnit(timeUnit);

			SparseMatrix[] testData = testDao.readData(columns, binThold);
			data = new SparseMatrix[] { rateMatrix, testData[0] };
			Recommender.testTimeMatrix = testData[1];
			break;
		case "given-n":
			N = evalOptions.getInt("-N", 20);

			switch (evalOptions.getString("-target")) {
			case "i":
				data = evalOptions.contains("--by-date") ? ds.getGivenNByItemDate(N, timeMatrix) : ds
						.getGivenNByItem(N);
				break;
			case "u":
			default:
				data = evalOptions.contains("--by-date") ? ds.getGivenNByUserDate(N, timeMatrix) : ds
						.getGivenNByUser(N);
				break;
			}
			break;
		case "given-ratio":
			ratio = evalOptions.getDouble("-r", 0.8);

			switch (evalOptions.getString("-target")) {
			case "u":
				data = evalOptions.contains("--by-date") ? ds.getRatioByUserDate(ratio, timeMatrix) : ds
						.getRatioByUser(ratio);
				break;
			case "i":
				data = evalOptions.contains("--by-date") ? ds.getRatioByItemDate(ratio, timeMatrix) : ds
						.getRatioByItem(ratio);
				break;
			case "r":
			default:
				data = evalOptions.contains("--by-date") ? ds.getRatioByRatingDate(ratio, timeMatrix) : ds
						.getRatioByRating(ratio);
				break;
			}

			break;
		default:
			ratio = evalOptions.getDouble("-r", 0.8);
			data = ds.getRatioByRating(ratio);
			break;
		}

		algo = getRecommender(data, -1);
		algo.execute();

		printEvalInfo(algo, algo.measures);
	}

	private void runCrossValidation(LineConfiger params) throws Exception {

		int kFold = params.getInt("-k", 5);
		boolean isParallelFold = params.isOn("-p", true);

		DataSplitter ds = new DataSplitter(rateMatrix, kFold);

		Thread[] ts = new Thread[kFold];
		Recommender[] algos = new Recommender[kFold];

		for (int i = 0; i < kFold; i++) {
			Recommender algo = getRecommender(ds.getKthFold(i + 1), i + 1);

			algos[i] = algo;
			ts[i] = new Thread(algo);
			ts[i].start();

			if (!isParallelFold)
				ts[i].join();
		}

		if (isParallelFold)
			for (Thread t : ts)
				t.join();

		// average performance of k-fold
		Map<Measure, Double> avgMeasure = new HashMap<>();
		for (Recommender algo : algos) {
			for (Entry<Measure, Double> en : algo.measures.entrySet()) {
				Measure m = en.getKey();
				double val = avgMeasure.containsKey(m) ? avgMeasure.get(m) : 0.0;
				avgMeasure.put(m, val + en.getValue() / kFold);
			}
		}

		printEvalInfo(algos[0], avgMeasure);
	}

	/**
	 * interface to run Leave-one-out approach
	 * @param evalOptions evaluation options
	 * @throws Exception on errors running the algorithm
	 */
	private void runLeaveOneOut(LineConfiger params) throws Exception {

		int numThreads = params.getInt("-t", Runtime.getRuntime().availableProcessors()); // default by number of processors

		Thread[] ts = new Thread[numThreads];
		Recommender[] algos = new Recommender[numThreads];

		// average performance of k-fold
		Map<Measure, Double> avgMeasure = new HashMap<>();

		int rows = rateMatrix.numRows();
		int cols = rateMatrix.numColumns();

		int count = 0;
		for (MatrixEntry me : rateMatrix) {
			double rui = me.get();
			if (rui <= 0)
				continue;

			int u = me.row();
			int i = me.column();

			// leave the current rating out
			SparseMatrix trainMatrix = new SparseMatrix(rateMatrix);
			trainMatrix.set(u, i, 0);
			SparseMatrix.reshape(trainMatrix);

			// build test matrix
			Table<Integer, Integer, Double> dataTable = HashBasedTable.create();
			Multimap<Integer, Integer> colMap = HashMultimap.create();
			dataTable.put(u, i, rui);
			colMap.put(i, u);
			SparseMatrix testMatrix = new SparseMatrix(rows, cols, dataTable, colMap);

			// get a recommender
			Recommender algo = getRecommender(new SparseMatrix[] { trainMatrix, testMatrix }, count + 1);

			algos[count] = algo;
			ts[count] = new Thread(algo);
			ts[count].start();

			if (numThreads == 1) {
				ts[count].join(); // fold by fold

				for (Entry<Measure, Double> en : algo.measures.entrySet()) {
					Measure m = en.getKey();
					double val = avgMeasure.containsKey(m) ? avgMeasure.get(m) : 0.0;
					avgMeasure.put(m, val + en.getValue());
				}
			} else if (count < numThreads) {
				count++;
			}

			if (count == numThreads) {
				// parallel fold
				for (Thread t : ts)
					t.join();
				count = 0;

				// record performance
				for (Recommender algo2 : algos) {
					for (Entry<Measure, Double> en : algo2.measures.entrySet()) {
						Measure m = en.getKey();
						double val = avgMeasure.containsKey(m) ? avgMeasure.get(m) : 0.0;
						avgMeasure.put(m, val + en.getValue());
					}
				}
			}
		}

		// normalization
		int size = rateMatrix.size();
		for (Entry<Measure, Double> en : avgMeasure.entrySet()) {
			Measure m = en.getKey();
			double val = en.getValue();
			avgMeasure.put(m, val / size);
		}

		printEvalInfo(algos[0], avgMeasure);
	}

	/**
	 * print out the evaluation information for a specific algorithm
	 * @param algo recommender algorithm
	 * @param ms measures
	 * @throws Exception on file I/O errors
	 */
	private void printEvalInfo(Recommender algo, Map<Measure, Double> ms) throws Exception {

		String result = Recommender.getEvalInfo(ms);
		// we add quota symbol to indicate the textual format of time 
		String time = String.format("'%s','%s'", Dates.parse(ms.get(Measure.TrainTime).longValue()),
				Dates.parse(ms.get(Measure.TestTime).longValue()));

		// double commas as the separation of results and configuration
		StringBuilder sb = new StringBuilder();
		String config = algo.toString();
		sb.append(algo.algoName).append(",").append(result).append(",,");
		if (!config.isEmpty())
			sb.append(config).append(",");
		sb.append(time).append("\n");

		String evalInfo = sb.toString();
		Logs.info(evalInfo);

		// copy to clipboard for convenience, useful for a single run
		if (outputOptions.contains("--to-clipboard")) {
			Strings.toClipboard(evalInfo);
			Logs.debug("Results have been copied to clipboard!");
		}

		// append to a specific file, useful for multiple runs
		if (outputOptions.contains("--to-file")) {
			String filePath = outputOptions.getString("--to-file", tempDirPath + algorithm + ".txt");
			FileIO.writeString(filePath, evalInfo, true);
			Logs.debug("Results have been collected to file: {}", filePath);
		}
	}

	/**
	 * Send a notification of completeness
	 * 
	 * @param attachment
	 *            email attachment
	 * @throws Exception on file I/O and notifier errors
	 */
	protected void notifyMe(String attachment) throws Exception {

		String hostInfo = FileIO.getCurrentFolder() + "." + algorithm + " [" + Systems.getIP() + "]";

		LineConfiger emailOptions = cf.getParamOptions("email.setup");

		if (emailOptions == null || !emailOptions.isMainOn()) {
			System.out.println("Program " + hostInfo + " has completed!");
			return;
		}

		EMailer notifier = new EMailer();
		Properties props = notifier.getProps();

		props.setProperty("mail.debug", "false");

		String port = emailOptions.getString("-port");
		props.setProperty("mail.smtp.host", emailOptions.getString("-host"));
		props.setProperty("mail.smtp.port", port);
		props.setProperty("mail.smtp.auth", emailOptions.getString("-auth"));

		props.put("mail.smtp.socketFactory.port", port);
		props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

		final String user = emailOptions.getString("-user");
		props.setProperty("mail.smtp.user", user);
		props.setProperty("mail.smtp.password", emailOptions.getString("-password"));

		props.setProperty("mail.from", user);
		props.setProperty("mail.to", emailOptions.getString("-to"));

		props.setProperty("mail.subject", hostInfo);
		props.setProperty("mail.text", "Program was completed @" + Dates.now());

		String msg = "Program [" + algorithm + "] has completed !";
		notifier.send(msg, attachment);
	}

	/**
	 * @return a recommender to be run
	 * @param data array containing training [0] and testing [1] data
	 * @param fold fold number
	 * @throws Exception on recommender construction errors
	 */
	protected Recommender getRecommender(SparseMatrix[] data, int fold) throws Exception {

		algorithm = cf.getString("recommender");

		SparseMatrix trainMatrix = data[0], testMatrix = data[1];

		// output data
		writeData(trainMatrix, testMatrix, fold);

		switch (algorithm.toLowerCase()) {

		/* baselines */
		case "globalavg":
			return new GlobalAverage(trainMatrix, testMatrix, fold);
		case "useravg":
			return new UserAverage(trainMatrix, testMatrix, fold);
		case "itemavg":
			return new ItemAverage(trainMatrix, testMatrix, fold);
		case "usercluster":
			return new UserCluster(trainMatrix, testMatrix, fold);
		case "itemcluster":
			return new ItemCluster(trainMatrix, testMatrix, fold);
		case "random":
			return new RandomGuess(trainMatrix, testMatrix, fold);
		case "constant":
			return new ConstantGuess(trainMatrix, testMatrix, fold);
		case "mostpop":
			return new MostPopular(trainMatrix, testMatrix, fold);

			/* rating prediction */
		case "userknn":
			return new UserKNN(trainMatrix, testMatrix, fold);
		case "itemknn":
			return new ItemKNN(trainMatrix, testMatrix, fold);
		case "itembigram":
			return new ItemBigram(trainMatrix, testMatrix, fold);
		case "regsvd":
			return new PMF(trainMatrix, testMatrix, fold);
		case "rfrec":
			return new RfRec(trainMatrix, testMatrix, fold);
		case "biasedmf":
			return new BiasedMF(trainMatrix, testMatrix, fold);
		case "gplsa":
			return new GPLSA(trainMatrix, testMatrix, fold);
		case "svd++":
			return new SVDPlusPlus(trainMatrix, testMatrix, fold);
		case "timesvd++":
			return new TimeSVD(trainMatrix, testMatrix, fold);
		case "pmf":
			return new PMF(trainMatrix, testMatrix, fold);
		case "bpmf":
			return new BPMF(trainMatrix, testMatrix, fold);
		case "socialmf":
			return new SocialMF(trainMatrix, testMatrix, fold);
		case "trustmf":
			return new TrustMF(trainMatrix, testMatrix, fold);
		case "sorec":
			return new SoRec(trainMatrix, testMatrix, fold);
		case "soreg":
			return new SoReg(trainMatrix, testMatrix, fold);
		case "rste":
			return new RSTE(trainMatrix, testMatrix, fold);
		case "trustsvd":
			return new TrustSVD(trainMatrix, testMatrix, fold);
		case "urp":
			return new URP(trainMatrix, testMatrix, fold);
		case "ldcc":
			return new LDCC(trainMatrix, testMatrix, fold);
		case "cptf":
			return new CPTF(trainMatrix, testMatrix, fold);

			/* item ranking */
		case "climf":
			return new CLiMF(trainMatrix, testMatrix, fold);
		case "fismrmse":
			return new FISMrmse(trainMatrix, testMatrix, fold);
		case "fism":
		case "fismauc":
			return new FISMauc(trainMatrix, testMatrix, fold);
		case "lrmf":
			return new LRMF(trainMatrix, testMatrix, fold);
		case "rankals":
			return new RankALS(trainMatrix, testMatrix, fold);
		case "ranksgd":
			return new RankSGD(trainMatrix, testMatrix, fold);
		case "wrmf":
			return new WRMF(trainMatrix, testMatrix, fold);
		case "bpr":
			return new BPR(trainMatrix, testMatrix, fold);
		case "wbpr":
			return new WBPR(trainMatrix, testMatrix, fold);
		case "gbpr":
			return new GBPR(trainMatrix, testMatrix, fold);
		case "sbpr":
			return new SBPR(trainMatrix, testMatrix, fold);
		case "slim":
			return new SLIM(trainMatrix, testMatrix, fold);
		case "lda":
			return new LDA(trainMatrix, testMatrix, fold);

			/* extension */
		case "nmf":
			return new NMF(trainMatrix, testMatrix, fold);
		case "hybrid":
			return new Hybrid(trainMatrix, testMatrix, fold);
		case "slopeone":
			return new SlopeOne(trainMatrix, testMatrix, fold);
		case "pd":
			return new PD(trainMatrix, testMatrix, fold);
		case "ar":
			return new AR(trainMatrix, testMatrix, fold);
		case "prankd":
			return new PRankD(trainMatrix, testMatrix, fold);
		case "external":
			return new External(trainMatrix, testMatrix, fold);

			/* both tasks */
		case "bucm":
			return new BUCM(trainMatrix, testMatrix, fold);
		case "bhfree":
			return new BHfree(trainMatrix, testMatrix, fold);

		default:
			throw new Exception("No recommender is specified!");
		}
	}

	protected void writeData(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		if (outputOptions != null && outputOptions.contains("--fold-data")) {

			String prefix = rateDao.getDataDirectory() + rateDao.getDataName();
			String suffix = ((fold >= 0) ? "-" + fold : "") + ".txt";

			try {
				writeMatrix(trainMatrix, prefix + "-train" + suffix);
				writeMatrix(testMatrix, prefix + "-test" + suffix);
			} catch (Exception e) {
				Logs.error(e.getMessage());
				e.printStackTrace();
			}
		}

	}

	/**
	 * set the configuration file to be used
	 * @param configurations configuration files
	 */
	public void setConfigFiles(String... configurations) {
		configFiles = Arrays.asList(configurations);
	}

	/**
	 * Print out software information
	 */
	private void about() {
		String about = "\nLibRec version " + version + ", copyright (C) 2014-2015 Guibing Guo \n\n"

		/* Description */
		+ "LibRec is free software: you can redistribute it and/or modify \n"
				+ "it under the terms of the GNU General Public License as published by \n"
				+ "the Free Software Foundation, either version 3 of the License, \n"
				+ "or (at your option) any later version. \n\n"

				/* Usage */
				+ "LibRec is distributed in the hope that it will be useful, \n"
				+ "but WITHOUT ANY WARRANTY; without even the implied warranty of \n"
				+ "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the \n"
				+ "GNU General Public License for more details. \n\n"

				/* licence */
				+ "You should have received a copy of the GNU General Public License \n"
				+ "along with LibRec. If not, see <http://www.gnu.org/licenses/>.";

		System.out.println(about);
	}

}
