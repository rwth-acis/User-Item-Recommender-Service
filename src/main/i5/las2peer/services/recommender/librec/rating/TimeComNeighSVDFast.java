// Copyright (C) 2014-2015 Guibing Guo
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
package i5.las2peer.services.recommender.librec.rating;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import i5.las2peer.services.recommender.communities.CommunityDetector;
import i5.las2peer.services.recommender.communities.CommunityDetector.CommunityDetectionAlgorithm;
import i5.las2peer.services.recommender.graphs.GraphBuilder;
import i5.las2peer.services.recommender.graphs.GraphBuilder.GraphConstructionMethod;
import i5.las2peer.services.recommender.graphs.GraphBuilder.SimilarityMeasure;
import i5.las2peer.services.recommender.librec.data.Configuration;
import i5.las2peer.services.recommender.librec.data.DenseMatrix;
import i5.las2peer.services.recommender.librec.data.DenseVector;
import i5.las2peer.services.recommender.librec.data.MatrixEntry;
import i5.las2peer.services.recommender.librec.data.SparseMatrix;
import i5.las2peer.services.recommender.librec.data.SparseVector;
import i5.las2peer.services.recommender.librec.data.VectorEntry;
import i5.las2peer.services.recommender.librec.intf.IterativeRecommender;
import i5.las2peer.services.recommender.librec.util.Logs;
import i5.las2peer.services.recommender.librec.util.Randoms;
import i5.las2peer.services.recommender.librec.util.Strings;

/**
 * Koren, <strong>Collaborative Filtering with Temporal Dynamics</strong>, KDD 2009.
 * 
 * @author guoguibing
 * 
 */
@Configuration("factors, lRate, lRateN, lRateF, lRateC, lRateCN, lRateCF, lRateMu, maxLRate, regB, regN, regU, regI, regC, regCN,"
		+ " regCF, iters, boldDriver, beta, numBins, graphMethod, graphKNN, similarity, cdAlgo, wtSteps")
public class TimeComNeighSVDFast extends IterativeRecommender {

	// the span of days of training timestamps
	private int numDays;
	
	// the minimum/maximum training timestamp
	private long minTrainTimestamp;
	private long maxTrainTimestamp;

	// global mean rating date
	private double globalMeanDate;
	
	// {user, mean date}
	private DenseVector userMeanDate;

	// time decay factor
	private float beta;

	// number of bins over all the items
	private int numBins;

	// ---Community-related algorithm parameters
	
	// graph construction method
	private GraphConstructionMethod graphMethod;

	// k parameter for the k-nn graph construction
	private int knn;
	
	// Similarity measure for the k-nn graph construction
	private SimilarityMeasure sim;
	
	// Community detection algorithm
	private CommunityDetectionAlgorithm cdAlgo;
	
	// Steps parameter for the Walktrap algorithm
	private int wtSteps;
	
	// ---Community information---
	
	// Number of user/item communities
	private int numUserCommunities;
	private int numItemCommunities;
	
	// User/item community membership matrices
	private SparseMatrix userMembershipsMatrix, itemMembershipsMatrix;
	
	// User/item community membership map
	private DenseVector userMembershipsVector, itemMembershipsVector;
	
	// User communities' mean rating times
	private DenseVector communityMeanDate;
	
	// Average ratings given by the members of each community (numUserCommunities x numItems)
	private SparseMatrix communityTimeMatrix;
	
	// ---Community-related model parameters---
	
	// time-independent user community bias
	private DenseVector BCu;
	
	// time-specific user community bias
	private Table<Integer, Integer, Double> BCut;
	
	// User community bias linear drift
	private DenseVector AlphaC;
	
	// time-independent item community bias
	private DenseVector BCi;
	
	// time-specific item community bias
	private DenseMatrix BCit;
	
	// item community factor matrix
	private DenseMatrix OCi;

	// user community factor matrix
	private DenseMatrix OCu;

	// {user community, {feature, day, value} } map for time-dependent user community features
	private Map<Integer, Table<Integer, Integer, Double>> OCut;
	
	// {user community, feature} alpha matrix  
	private DenseMatrix ACu;
	
	// item's implicit influence
	private DenseMatrix Y;

	// item's explicit influence (neighborhood model)
	private DenseMatrix W;
	
	// item's implicit influence (neighborhood model)
	private DenseMatrix C;
	
	// decay parameter phi (beta in Koren paper)
	private DenseVector Phi;
	

	public TimeComNeighSVDFast(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		setAlgoName("timeComNeighSVD++Fast");
		
		beta = algoOptions.getFloat("-beta");
		numBins = algoOptions.getInt("-bins");
		knn = cf.getInt("graph.knn.k", 10);
		switch (cf.getString("graph.method", "ratings").toLowerCase()){
		case "tags":
			graphMethod = GraphConstructionMethod.TAGS;
			break;
		default:
		case "ratings":
			graphMethod = GraphConstructionMethod.RATINGS;
			break;
		}
		switch (cf.getString("graph.knn.sim", "cosine").toLowerCase()){
		case "pearson":
			sim = SimilarityMeasure.PEARSON_CORRELATION;
			break;
		default:
		case "cosine":
			sim = SimilarityMeasure.COSINE_SIMILARITY;
			break;
		}
		switch (cf.getString("cd.algo", "wt").toLowerCase()){
		case "dmid":
			cdAlgo = CommunityDetectionAlgorithm.DMID;
			break;
		case "slpa":
			cdAlgo = CommunityDetectionAlgorithm.SLPA;
			break;
		default:
		case "wt":
			cdAlgo = CommunityDetectionAlgorithm.WALKTRAP;
			break;
		}
		wtSteps = cf.getInt("cd.walktrap.steps", 2);
	}

	@Override
	protected void initModel() throws Exception {
		super.initModel();

		minTrainTimestamp = Long.MAX_VALUE;
		maxTrainTimestamp = Long.MIN_VALUE;
		for (MatrixEntry e : trainMatrix){
			long t = (long) timeMatrix.get(e.row(), e.column());
			if (t < minTrainTimestamp)
				minTrainTimestamp = t;
			if (t > maxTrainTimestamp)
				maxTrainTimestamp = t;
		}
		numDays = days(maxTrainTimestamp, minTrainTimestamp) + 1;
		
		// cache
		userItemsCache = trainMatrix.rowColumnsCache(cacheSpec);

		// global average date
		double sum = 0;
		int cnt = 0;
		for (MatrixEntry me : trainMatrix) {
			int u = me.row();
			int i = me.column();
			double rui = me.get();

			if (rui <= 0)
				continue;

			sum += days((long) timeMatrix.get(u, i), minTrainTimestamp);
			cnt++;
		}
		globalMeanDate = sum / cnt;

		// compute users' mean rating timestamps
		userMeanDate = new DenseVector(numUsers);
		List<Integer> Ru = null;
		for (int u = 0; u < numUsers; u++) {

			sum = 0;
			Ru = userItemsCache.get(u);
			for (int i : Ru) {
				sum += days((long) timeMatrix.get(u, i), minTrainTimestamp);
			}

			double mean = (Ru.size() > 0) ? (sum + 0.0) / Ru.size() : globalMeanDate;
			userMeanDate.set(u, mean);
		}
		
		// build user and item graphs
		Logs.info("{}{} build user and item graphs ...", new Object[] { algoName, foldInfo });
		GraphBuilder gb = new GraphBuilder();
		gb.setMethod(graphMethod);
		gb.setK(knn);
		gb.setSimilarityMeasure(sim);
		gb.setRatingData(trainMatrix);
		gb.setTaggingData(userTagTable, itemTagTable);
		gb.buildGraphs();
		SparseMatrix userMatrix = gb.getUserAdjacencyMatrix();
		SparseMatrix itemMatrix = gb.getItemAdjacencyMatrix();
		gb.buildGraphs();
		userMatrix = gb.getUserAdjacencyMatrix();
		itemMatrix = gb.getItemAdjacencyMatrix();
		gb = null;
		
		// detect communities
		Logs.info("{}{} detect communities ...", new Object[] { algoName, foldInfo });
		CommunityDetector cd = new CommunityDetector();
		cd.setAlgorithm(cdAlgo);
		cd.setOverlapping(false);
		if (cdAlgo == CommunityDetectionAlgorithm.WALKTRAP)
			cd.setWalktrapParameters(wtSteps);
		
		cd.setGraph(userMatrix);
		cd.detectCommunities();
		userMembershipsMatrix = cd.getMemberships();
		userMembershipsVector = cd.getMembershipsVector();
		numUserCommunities = cd.getNumCommunities();
		
		cd.setGraph(itemMatrix);
		cd.detectCommunities();
		itemMembershipsMatrix = cd.getMemberships();
        itemMembershipsVector = cd.getMembershipsVector();
		numItemCommunities = cd.getNumCommunities();
		
		userMatrix = null;
		itemMatrix = null;
		cd = null;
		
		logCommunityInfo();

		// compute user communities' mean rating times overall and per item
		communityMeanDate = new DenseVector(numUserCommunities);
		Table<Integer, Integer, Double> communityTimeTable = HashBasedTable.create();
		
		for (int community = 0; community < numUserCommunities; community++){
			// vector of users that belong to the community
			SparseVector communityUsersVector = userMembershipsMatrix.column(community);
			// build set of items that have been rated by members of the community
			HashSet<Integer> items = new HashSet<Integer> ();
			for (VectorEntry e : communityUsersVector){
				int user = e.index();
				List<Integer> userItems = userItemsCache.get(user);
				for (int item : userItems)
					items.add(item);
			}
			// to compute communities' overall mean rating times keep track of time and number of ratings given
			double communityTimeSum = 0;
			int ratingsCount = 0;
			for (int item : items){
				// to compute communities' per item mean rating times keep track of time and number of ratings given to each item
				double communityItemTimeSum = 0;
				int itemRatingsCount = 0;
				// vector containing all user's ratings for the item
				SparseVector itemUsersVector = trainMatrix.column(item);
				for (VectorEntry e : communityUsersVector){
					int user = e.index();
					if (itemUsersVector.contains(user)){
						double tui = timeMatrix.get(user, item);
						communityItemTimeSum += tui;
						communityTimeSum += days((long) timeMatrix.get(user, item), minTrainTimestamp);
						ratingsCount++;
						itemRatingsCount++;
					}
				}
				if (itemRatingsCount > 0){
					double communityTime = communityItemTimeSum / itemRatingsCount;
					communityTimeTable.put(community, item, communityTime);
				}
			}
			double meanDate = (ratingsCount > 0) ? (communityTimeSum) / ratingsCount : globalMeanDate;
			communityMeanDate.set(community, meanDate);
		}
		communityTimeMatrix = new SparseMatrix(numUserCommunities, numItems, communityTimeTable);
		
		// initialize community-related model parameters
		BCu = new DenseVector(numUserCommunities);
		BCu.init(initMean, initStd);
		AlphaC = new DenseVector(numUserCommunities);
		AlphaC.init(initMean, initStd);
		BCut = HashBasedTable.create();
		BCi = new DenseVector(numItemCommunities);
		BCi.init(initMean, initStd);
		BCit = new DenseMatrix(numItemCommunities, numBins);
		BCit.init(initMean, initStd);
		
		OCi = new DenseMatrix(numItemCommunities, numFactors);
		OCi.init(initMean, initStd);
		OCu = new DenseMatrix(numUserCommunities, numFactors);
		OCu.init(initMean, initStd);
		OCut = new HashMap<>();
		ACu = new DenseMatrix(numUserCommunities, numFactors);
		ACu.init(initMean, initStd);
		Y = new DenseMatrix(numItemCommunities, numFactors);
		Y.init(initMean, initStd);
		
		Phi = new DenseVector(numUserCommunities);
		Phi.init(0.01);
		W = new DenseMatrix(numItems, numItemCommunities);
		W.init(initMean, initStd);
		C = new DenseMatrix(numItems, numItemCommunities);
		C.init(initMean, initStd);
	}
	
	@Override
	protected void buildModel() throws Exception {
		Logs.info("{}{} learn model parameters ...", new Object[] { algoName, foldInfo });
		for (int iter = 1; iter <= numIters; iter++) {
			loss = 0;
			for (MatrixEntry me : trainMatrix) {
				int u = me.row();
				int i = me.column();
				double rui = me.get();

				int cu = (int) userMembershipsVector.get(u);
				int ci = (int) itemMembershipsVector.get(i);
				long timestamp = (long) timeMatrix.get(u, i);
				// day t
				int t = days(timestamp, minTrainTimestamp);
				int bin = bin(t);
				
				// set non community-related variables
				List<Integer> Iu = userItemsCache.get(u);
				double wi = Iu.size() > 0 ? Math.pow(Iu.size(), -0.5) : 0;
				
				// lazy initialization
				if (!BCut.contains(cu, t))
					BCut.put(cu, t, Randoms.gaussian(initMean, initStd));
				if (!OCut.containsKey(cu))
					OCut.put(cu, HashBasedTable.create());
				for (int k = 0; k < numFactors; k++)
					if (!OCut.get(cu).contains(k, t))
						OCut.get(cu).put(k, t, Randoms.gaussian(initMean, initStd));
				
				double pui = predict(u, i);
				
				double eui = pui - rui;
				loss += eui * eui;
				
				// Update baseline parameters
				// ==========================
				
				double sgd;
				
				// update bcu, bcut
				double bcu = BCu.get(cu);
				double bcut = BCut.get(cu, t);
				
				sgd = eui + regC * bcu;
				BCu.add(cu, -lRateC * sgd);
				loss += regC * bcu * bcu;
				
				sgd = eui + regC * bcut;
				BCut.put(cu, t, bcut - lRateC * sgd);
				loss += regC * bcut * bcut;
				
				// update alpha_c
				double alphac = AlphaC.get(cu);
				double devct = devc(cu, t);
				
				sgd = eui * devct - regC * alphac;
				AlphaC.add(cu, -lRateC * sgd);
				loss += regC * alphac * alphac;
				
				// update bci, bcit
				double bci = BCi.get(ci);
				double bcit = BCit.get(ci, bin);
				
				sgd = eui + regC * bci;
				BCi.add(ci, -lRateC * sgd);
				loss += regC * bci * bci;
				
				sgd = eui + regC * bcit;
				BCit.add(ci, bin, -lRateC * sgd);
				loss += regC * bcit * bcit;

				// Update SVD model parameters
				// ===========================
				
				for (int k = 0; k < numFactors; k++) {
					double sum_yck = 0;
					for (int j : Iu){
						int cj = (int) itemMembershipsVector.get(j);
						double yck = Y.get(cj, k);
						sum_yck += yck;
					}
					
					double ocuk = OCu.get(cu, k);
					double ocukt = OCut.get(cu).get(k, t);
					double acuk = ACu.get(cu, k);
					double devcut = devc(cu, t);
					double ocik = OCi.get(ci, k);
					
					// update oci
					sgd = eui * (ocuk + acuk * devcut + ocukt + wi * sum_yck) + regCF * ocik;
					OCi.add(ci, k, -lRateCF * sgd);
					loss += regCF * ocik * ocik;
					
					// update ocu, acu and ocut
					sgd = eui * ocik + regCF * ocuk;
					OCu.add(cu, k, -lRateCF * sgd);
					loss += regCF * ocuk * ocuk;
					
					sgd = eui * devcut * ocik + regCF * acuk;
					ACu.add(cu, k, -lRateCF * sgd);
					loss += regCF * acuk * acuk;
					
					sgd = eui * ocik + regCF * ocukt;
					OCut.get(cu).put(k, t, ocukt - lRateCF * sgd);
					loss += regCF * ocukt * ocukt;
					
					for (int j : Iu) {
						int cj = (int) itemMembershipsVector.get(j);
						double ycjk = Y.get(cj, k);
						sgd = eui * wi * ocik + regCF * ycjk;
						Y.add(cj, k, -lRateCF * sgd);
						loss += regCF * ycjk * ycjk;
					}
				}
				
				// Update neighborhood model parameters
				// ====================================
				
				// update w, c and phi
				double sgd_phi = 0;
				for (int j : Iu){
					double ruj = trainMatrix.get(u, j);
					double buj = bias(u, j, t);
					int cj = (int) itemMembershipsVector.get(j);
					double e = cdecay(cu, j, t);
					
					// update w
					double wic = W.get(i, cj);
					sgd = eui * wi * e * (ruj - buj) + regCN * wic;
					W.add(i, cj, -lRateCN * sgd);
					loss += regCN * wic * wic;
					
					// update c
					double cic = C.get(i, cj);
					sgd = eui * wi * e + regCN * cic;
					C.add(i, cj, -lRateCN * sgd);
					loss += regCN * cic * cic;
					
					// update phi
					int tj = days((long) communityTimeMatrix.get(cu, j), minTrainTimestamp);
					int diff = Math.abs(t - tj);
					sgd_phi = eui * wi * (-1 * diff) * e * ((ruj - buj) * wic + cic);
				}
				double phi = Phi.get(cu);
				sgd_phi += regCN * phi;
				// do not let phi become negative
				double delta_phi = (lRateMu * sgd_phi > phi) ? (phi / 2.0) : (lRateMu * sgd_phi);
				Phi.add(cu, -delta_phi);
				loss += regCN * phi * phi;
			}

			loss *= 0.5;

			if (isConverged(iter))
				break;
		}
	}

	@Override
	protected double predict(int u, int i) throws Exception {
		int cu = (int) userMembershipsVector.get(u);
		int ci = (int) itemMembershipsVector.get(i);
		// retrieve the test rating timestamp
		long timestamp = (long) timeMatrix.get(u, i);
		int t = days(timestamp, minTrainTimestamp);
		
		List<Integer> Iu = userItemsCache.get(u);
		double wi = Iu.size() > 0 ? Math.pow(Iu.size(), -0.5) : 0;

		// baseline / bias
		double pred = bias(u, i, t);
		
		// qi * pu(t)
		for (int k = 0; k < numFactors; k++) {
			double ocik = OCi.get(ci, k);
			
			double ocuk = OCu.get(cu, k);
			double acuk = AlphaC.get(cu);
			double devcut = devc(cu, t);
			double ocukt = (OCut.containsKey(cu) && OCut.get(cu).contains(k, t)) ? OCut.get(cu).get(k, t) : 0;
			
			double sum_yck = 0;
			for (int j : Iu){
				int cj = (int) itemMembershipsVector.get(j);
				double yck = Y.get(cj, k);
				sum_yck += yck;
			}

			pred += ocik * (ocuk + acuk * devcut + ocukt + wi * sum_yck);
		}
		
		// e^(-beta_u * |t-tj|)(ruj - buj) * wij + cij): eq. (16)
		// we use phi instead of beta since beta is already used for the time deviation in the baseline model
		for (int j : Iu){
			double ruj = rateMatrix.get(u, j);
			double buj = bias(u, j, t);
			int cj = (int) itemMembershipsVector.get(j);
			double e = cdecay(cu, j, t);
			double wic = W.get(i, cj);
			double cic = C.get(i, cj);
			pred += wi * e * ((ruj - buj) * wic + cic);
		}
		
		return pred;
	}

	@Override
	public String toString() {
		return Strings.toString(new Object[] { numFactors, initLRate, initLRateN, initLRateF, initLRateC, initLRateCN, initLRateCF, 
				initLRateMu, maxLRate, regB, regN, regU, regI, regC, regCN, regCF, numIters, isBoldDriver, beta, numBins,
				graphMethod, knn, sim, cdAlgo, wtSteps});
	}

	/***************************************************************** Functional Methods *******************************************/
	/**
	 * @return the bin number (starting from 0..numBins-1) for a specific day;
	 */
	protected int bin(int day) {
		int bin = (int) (day / (numDays + 0.0) * numBins);
		
		if (bin < 0)
			return 1;
		if (bin >= numBins)
			return numBins - 1;

		return bin;
	}

	/**
	 * @return number of days for a given time difference
	 */
	protected static int days(long diff) {
		return (int) TimeUnit.MILLISECONDS.toDays(diff);
	}

	/**
	 * @return number of days between two timestamps
	 */
	protected static int days(long t1, long t2) {
		return days(Math.abs(t1 - t2));
	}
	
	/******************************************************* Prediction Methods *****************************************************/
	
	/**
	 * @return bias for given user and item
	 */
	private double bias(int u, int i, int t){
		int cu = (int) userMembershipsVector.get(u);
		int ci = (int) itemMembershipsVector.get(i);
		
		double bias = globalMean;
		int bin = bin(t);

		// bci(t)
		double bci = BCi.get(ci);
		double bcit = BCit.get(ci, bin);
		bias += bci + bcit;
		
		// bcu(t)
		double bcu = BCu.get(cu);
		double bcut = BCut.contains(cu, t) ? BCut.get(cu, t) : 0;
		double alpha = AlphaC.get(cu);
		double dev = devc(cu, t);
		bias += bcu + alpha * dev + bcut;
		
		return bias;
	}

	/******************************************************* Community-related Methods **********************************************/
	
	/**
	 * @return the time deviation for a specific timestamp (number of days) t w.r.t the community mean date tc
	 */
	protected double devc(int c, int t) {
		double tc = communityMeanDate.get(c);

		// date difference in days
		double diff = t - tc;

		return Math.signum(diff) * Math.pow(Math.abs(diff), beta);
	}

	/**
	 * @param c user community
	 * @param j item
	 * @param t time (day) of the rating that is to be predicted
	 * @return the rating decay for user u rating item j w.r.t. timestamp (number of days) t, i.e. e^(-beta_u * |t-tj|) from eq. (16)
	 */
	protected double cdecay(int cu, int j, int t) {
		int tj = days((long) communityTimeMatrix.get(cu, j), minTrainTimestamp);
		
		// date difference in days
		int diff = Math.abs(t - tj);

		return Math.pow(Math.E, -1.0 * Phi.get(cu) * diff);
	}
	
	private void logCommunityInfo() {
		int userMemSize = userMembershipsMatrix.size();
		int itemMemSize = itemMembershipsMatrix.size();
		
		// users per community
		double avgupc = (double) userMemSize / numUserCommunities;
		int minupc = Integer.MAX_VALUE;
		int maxupc = Integer.MIN_VALUE;
		for (int c = 0; c < numUserCommunities; c++){
			int upc = userMembershipsMatrix.columnSize(c);
			if (upc < minupc)
				minupc = upc;
			if (upc > maxupc)
				maxupc = upc;
		}
		
		// communities per user
		double avgcpu = (double) userMemSize / numUsers;
		int mincpu = Integer.MAX_VALUE;
		int maxcpu = Integer.MIN_VALUE;
		for (int u = 0; u < numUsers; u++){
			int cpu = userMembershipsMatrix.rowSize(u);
			if (cpu < mincpu)
				mincpu = cpu;
			if (cpu > maxcpu)
				maxcpu = cpu;
		}
		
		// items per community
		double avgipc = (double) itemMemSize / numItemCommunities;
		int minipc = Integer.MAX_VALUE;
		int maxipc = Integer.MIN_VALUE;
		for (int c = 0; c < numItemCommunities; c++){
			int ipc = itemMembershipsMatrix.columnSize(c);
			if (ipc < minipc)
				minipc = ipc;
			if (ipc > maxipc)
				maxipc = ipc;
		}
		
		// communities per item
		double avgcpi = (double) itemMemSize / numItems;
		int mincpi = Integer.MAX_VALUE;
		int maxcpi = Integer.MIN_VALUE;
		for (int i = 0; i < numItems; i++){
			int cpi = itemMembershipsMatrix.rowSize(i);
			if (cpi < mincpi)
				mincpi = cpi;
			if (cpi > maxcpi)
				maxcpi = cpi;
		}
		
		Logs.info("{}{} user communites: {}, [min, max, avg] users per community: [{}, {}, {}], [min, max, avg] communities per user: [{}, {}, {}]",
				new Object[] { algoName, foldInfo, numUserCommunities, minupc, maxupc, avgupc, mincpu, maxcpu, avgcpu });
		Logs.info("{}{} item communites: {}, [min, max, avg] items per community: [{}, {}, {}], [min, max, avg] communities per item: [{}, {}, {}]",
				new Object[] { algoName, foldInfo, numItemCommunities, minipc, maxipc, avgipc, mincpi, maxcpi, avgcpi });
	}
	
}
