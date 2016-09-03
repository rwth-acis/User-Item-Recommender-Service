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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import i5.las2peer.services.recommender.communities.CommunityDetector;
import i5.las2peer.services.recommender.communities.CommunityDetector.CommunityDetectionAlgorithm;
import i5.las2peer.services.recommender.graphs.GraphBuilder;
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
		+ " regCF, iters, boldDriver, beta, numBins, numCBins")
public class TimeComNeighSVD extends IterativeRecommender {

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

	// item's implicit influence
	private DenseMatrix Y;

	// {item, bin(t)} bias matrix
	private DenseMatrix Bit;

	// {user, day, bias} table
	private Table<Integer, Integer, Double> But;

	// user bias weight parameters
	private DenseVector Alpha;

	// {user, feature} alpha matrix
	private DenseMatrix Auk;

	// {user, {feature, day, value} } map
	private Map<Integer, Table<Integer, Integer, Double>> Pukt;

	// {user, user scaling stable part}
	private DenseVector Cu;

	// {user, day, day-specific scaling part}
	private DenseMatrix Cut;
	
	// item's explicit influence (neighborhood model)
	private DenseMatrix W;
	
	// item's implicit influence (neighborhood model)
	private DenseMatrix C;
	
	// decay parameter phi (beta in Koren paper)
	private DenseVector Phi;
	
	// ---Community-related algorithm parameters
	
	// k parameter for the k-nn graph construction
	private int knn;
	
	// Similarity measure for the k-nn graph construction
	private SimilarityMeasure sim;
	
	// Community detection algorithm
	private CommunityDetectionAlgorithm cdAlgo;
	
	// Steps parameter for the Walktrap algorithm
	private int wtSteps;
	
	// ---Community information---
	
	// number of community structures to compute (to capture community drift over time) 
	private int numCBins;
	
	// Number of user/item communities for each bin
	private int[] numUserCommunities;
	private int[] numItemCommunities;
	
	// User/item community membership matrices for each bin 
	private SparseMatrix[] userMemberships, itemMemberships;
	
	// User communities' mean rating times
	private DenseVector[] communityMeanDate;
	
	// Average ratings given by the members of each community (numUserCommunities x numItems) for each bin
	private SparseMatrix[] communityRatingsMatrix;
	
	// Average ratings given by each user's communities (numUsers x numItems) for each bin
	private SparseMatrix[] userCommunitiesRatingsMatrix;
	
	// Average ratings given by the members of each community (numUserCommunities x numItems) for each bin
	private SparseMatrix[] communityTimeMatrix;
	
	// Average ratings given by each user's communities (numUsers x numItems) for each bin
	private SparseMatrix[] userCommunitiesTimeMatrix;
	
	// User/item communities caches for each bin
	private List<LoadingCache<Integer, List<Integer>>> userCommunitiesCache, itemCommunitiesCache, userCommunitiesItemsCache;
	
	// ---Community-related model parameters---
	
	// time-independent user community bias
	private DenseVector[] BCu;
	
	// time-specific user community bias
	private List<Table<Integer, Integer, Double>> BCut;
	
	// User community bias linear drift
	private DenseVector AlphaC;
	
	// time-independent item community bias
	private DenseVector[] BCi;
	
	// time-specific item community bias
	private DenseMatrix[] BCit;
	
	// item community factor matrix
	private DenseMatrix[] OCi;

	// user community factor matrix
	private DenseMatrix[] OCu;

	// {user community, {feature, day, value} } map for time-dependent user community features
	private List<Map<Integer, Table<Integer, Integer, Double>>> OCut;
	
	// {user community, feature} alpha matrix  
	private DenseMatrix ACu;
	
	// user community item's implicit influence (SVD model)
	private DenseMatrix Z;

	// item's implicit community feedback (neighborhood model)
	private DenseMatrix D;
	
	// decay parameter psi
	private DenseVector Psi;
	
	
	public TimeComNeighSVD(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		setAlgoName("timeComNeighSVD++");
		
		beta = algoOptions.getFloat("-beta");
		numBins = algoOptions.getInt("-bins");
		numCBins = algoOptions.getInt("-cbins");
		knn = cf.getInt("graph.knn.k", 10);
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
		
		userBias = new DenseVector(numUsers);
		userBias.init(initMean, initStd);

		itemBias = new DenseVector(numItems);
		itemBias.init(initMean, initStd);

		Alpha = new DenseVector(numUsers);
		Alpha.init(initMean, initStd);

		Bit = new DenseMatrix(numItems, numBins);
		Bit.init(initMean, initStd);

		Y = new DenseMatrix(numItems, numFactors);
		Y.init(initMean, initStd);

		Auk = new DenseMatrix(numUsers, numFactors);
		Auk.init(initMean, initStd);

		But = HashBasedTable.create();
		
		Pukt = new HashMap<>();

		Cu = new DenseVector(numUsers);
		Cu.init(initMean, initStd);

		Cut = new DenseMatrix(numUsers, numDays);
		Cut.init(initMean, initStd);
		
		W = new DenseMatrix(numItems, numItems);
		W.init(initMean, initStd);
		
		C = new DenseMatrix(numItems, numItems);
		C.init(initMean, initStd);
		
		Phi = new DenseVector(numUsers);
		Phi.init(0.01);

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
		SparseMatrix[] userMatrix = new SparseMatrix[numCBins + 1];
		SparseMatrix[] itemMatrix = new SparseMatrix[numCBins + 1];
		GraphBuilder gb = new GraphBuilder();
		gb.setK(knn);
		gb.setSimilarityMeasure(sim);
		gb.setRatingData(trainMatrix);
		gb.buildGraphs();
		userMatrix[0] = gb.getUserAdjacencyMatrix();
		itemMatrix[0] = gb.getItemAdjacencyMatrix();
		if (numCBins > 1){
			SparseMatrix[] trainMatrixCBin = trainDataCBins();
			for (int cbin = 1; cbin <= numCBins; cbin++){
				gb.setRatingData(trainMatrixCBin[cbin - 1]);
				gb.buildGraphs();
				userMatrix[cbin] = gb.getUserAdjacencyMatrix();
				itemMatrix[cbin] = gb.getItemAdjacencyMatrix();
			}
		}
		gb = null;
		
		// detect communities
		Logs.info("{}{} detect communities ...", new Object[] { algoName, foldInfo });
		userMemberships = new SparseMatrix[numCBins + 1];
		itemMemberships = new SparseMatrix[numCBins + 1];
		userCommunitiesCache = new ArrayList<LoadingCache<Integer, List<Integer>>>(numCBins + 1);
		itemCommunitiesCache = new ArrayList<LoadingCache<Integer, List<Integer>>>(numCBins + 1);
		numUserCommunities = new int[numCBins + 1];
		numItemCommunities = new int[numCBins + 1];
		CommunityDetector cd = new CommunityDetector();
		cd.setAlgorithm(cdAlgo);
		if (cdAlgo == CommunityDetectionAlgorithm.WALKTRAP)
			cd.setWalktrapParameters(wtSteps);
		for (int cbin = 0; cbin <= numCBins; cbin++){
			if (numCBins == 1 && cbin == 1){
				// if we use only one bin no need to detect communities again
				userMemberships[cbin] = userMemberships[0];
				itemMemberships[cbin] = itemMemberships[0];
			}
			else{
				cd.setGraph(userMatrix[cbin]);
				cd.detectCommunities();
				userMemberships[cbin] = cd.getMemberships();
				cd.setGraph(itemMatrix[cbin]);
				cd.detectCommunities();
				itemMemberships[cbin] = cd.getMemberships();
			}
			userCommunitiesCache.add(cbin, userMemberships[cbin].rowColumnsCache(cacheSpec));
			numUserCommunities[cbin] = userMemberships[cbin].numColumns();
			itemCommunitiesCache.add(cbin, itemMemberships[cbin].rowColumnsCache(cacheSpec));
			numItemCommunities[cbin] = itemMemberships[cbin].numColumns(); 
		}
		userMatrix = null;
		itemMatrix = null;
		cd = null;
		
		logCommunityInfo();

		// compute user communities' average ratings for each item
		communityRatingsMatrix = new SparseMatrix[numCBins + 1];
		communityTimeMatrix = new SparseMatrix[numCBins + 1];
		communityMeanDate = new DenseVector[numCBins + 1];
		for (int cbin = 0; cbin <= numCBins; cbin++){
			communityMeanDate[cbin] = new DenseVector(numUserCommunities[cbin]);
			Table<Integer, Integer, Double> communityRatingsTable = HashBasedTable.create();
			Table<Integer, Integer, Double> communityTimeTable = HashBasedTable.create();
			for (int community = 0; community < numUserCommunities[cbin]; community++){
				// each user's membership level for the community
				SparseVector communityUsersVector = userMemberships[cbin].column(community);
				// build set of items that have been rated by members of the community
				HashSet<Integer> items = new HashSet<Integer> ();
				for (VectorEntry e : communityUsersVector){
					int user = e.index();
					List<Integer> userItems = userItemsCache.get(user);
					for (int item : userItems)
						items.add(item);
				}
				// to compute mean rating times for each community keep track of time and number of ratings given
				double communityTimeSum = 0;
				int ratingsCount = 0;
				for (int item : items){
					// Sum of ratings given by users of the community to the item, weighted by the users community membership levels
					double ratingsSum = 0;
					double communityItemTimeSum = 0;
					double membershipsSum = 0;
					// Each user's rating for the item
					SparseVector itemUsersVector = trainMatrix.column(item);
					for (VectorEntry e : communityUsersVector){
						int user = e.index();
						if (itemUsersVector.contains(user)){
							double muc = userMemberships[cbin].get(user, community);
							double rui = itemUsersVector.get(user);
							double tui = timeMatrix.get(user, item);
							ratingsSum += rui * muc;
							communityItemTimeSum += tui * muc;
							membershipsSum += muc;
							communityTimeSum += days((long) timeMatrix.get(user, item), minTrainTimestamp);
							ratingsCount++;
						}
					}
					if (membershipsSum > 0){
						double communityRating = ratingsSum / membershipsSum;
						double communityTime = communityItemTimeSum / membershipsSum;
						communityRatingsTable.put(community, item, communityRating);
						communityTimeTable.put(community, item, communityTime);
					}
					double meanTime = (ratingsCount > 0) ? (communityTimeSum) / ratingsCount : globalMeanDate;
					communityMeanDate[cbin].set(community, meanTime);
				}
			}
			communityRatingsMatrix[cbin] = new SparseMatrix(numUserCommunities[cbin], numItems, communityRatingsTable);
			communityTimeMatrix[cbin] = new SparseMatrix(numUserCommunities[cbin], numItems, communityTimeTable);
			int numRatingsPerCommunity = communityRatingsMatrix[cbin].size() / communityRatingsMatrix[cbin].numRows();
			Logs.info("{}{} Community Ratings: Number of communities: {}, Avg. number of ratings per community: {}",
					algoName, foldInfo, communityRatingsMatrix[cbin].numRows(), numRatingsPerCommunity);
		}
		
		// compute each user's communities' average rating for each item
		userCommunitiesRatingsMatrix = new SparseMatrix[numCBins + 1];
		userCommunitiesTimeMatrix = new SparseMatrix[numCBins + 1];
		userCommunitiesItemsCache = new ArrayList<LoadingCache<Integer, List<Integer>>>(numCBins + 1);
		for (int cbin = 0; cbin <= numCBins; cbin++){
		    Table<Integer, Integer, Double> userCommunitiesRatingsTable = HashBasedTable.create();
		    Table<Integer, Integer, Double> userCommunitiesTimeTable = HashBasedTable.create();
			for (int user = 0; user < numUsers; user++){
				List<Integer> userCommunities;
				userCommunities = userCommunitiesCache.get(cbin).get(user);
				for (int item = 0; item < numItems; item++){
					double ratingsSum = 0;
					double timeSum = 0;
					double membershipsSum = 0;
					for (int community : userCommunities){
						double communityRating = communityRatingsMatrix[cbin].get(community, item);
						double communityTime = communityTimeMatrix[cbin].get(community, item);
						double userMembership = userMemberships[cbin].get(user, community);
						ratingsSum += communityRating * userMembership;
						timeSum += communityTime * userMembership;
						membershipsSum += userMembership;
					}
					if (ratingsSum > 0){
						double userCommunitiesRating = ratingsSum / membershipsSum;
						double userCommunitiesTime = timeSum / membershipsSum;
						userCommunitiesRatingsTable.put(user, item, userCommunitiesRating);
						userCommunitiesTimeTable.put(user, item, userCommunitiesTime);
					}
				}
			}
			userCommunitiesRatingsMatrix[cbin] = new SparseMatrix(numUsers, numItems, userCommunitiesRatingsTable);
			userCommunitiesTimeMatrix[cbin] = new SparseMatrix(numUsers, numItems, userCommunitiesTimeTable);
			userCommunitiesItemsCache.add(cbin, userCommunitiesRatingsMatrix[cbin].rowColumnsCache(cacheSpec));
			int numRatingsPerUser = userCommunitiesRatingsMatrix[cbin].size() / userCommunitiesRatingsMatrix[cbin].numRows();
			Logs.info("{}{} User Communities Ratings: Number of users: {}, Avg. number of community ratings per user: {}",
					algoName, foldInfo, userCommunitiesRatingsMatrix[cbin].numRows(), numRatingsPerUser);
		}
		
		// initialize community-related model parameters
		AlphaC = new DenseVector(numUserCommunities[0]);
		AlphaC.init(initMean, initStd);
		D = new DenseMatrix(numItems, numItems);
		D.init(initMean, initStd);
		Psi = new DenseVector(numUsers);
		Psi.init(0.01);
		
		BCu = new DenseVector[numCBins + 1];
		BCut = new ArrayList<Table<Integer, Integer, Double>>(numCBins + 1);
		BCi = new DenseVector[numCBins + 1];
		BCit = new DenseMatrix[numCBins + 1];
		OCi = new DenseMatrix[numCBins + 1];
		OCu = new DenseMatrix[numCBins + 1];
		OCut = new ArrayList<Map<Integer, Table<Integer, Integer, Double>>>(numCBins + 1);
		ACu = new DenseMatrix(numUserCommunities[0], numFactors);
		ACu.init(initMean, initStd);
		Z = new DenseMatrix(numItems, numItems);
		Z.init(initMean, initStd);
		for (int cbin = 0; cbin <= numCBins; cbin++){
			BCu[cbin] = new DenseVector(numUserCommunities[cbin]);
			BCu[cbin].init(initMean, initStd);
			BCut.add(cbin, HashBasedTable.create());
			BCi[cbin] = new DenseVector(numItemCommunities[cbin]);
			BCi[cbin].init(initMean, initStd);
			BCit[cbin] = new DenseMatrix(numItemCommunities[cbin], numBins);
			BCit[cbin].init(initMean, initStd);
			OCi[cbin] = new DenseMatrix(numItemCommunities[cbin], numFactors);
			OCi[cbin].init(initMean, initStd);
			OCu[cbin] = new DenseMatrix(numUserCommunities[cbin], numFactors);
			OCu[cbin].init(initMean, initStd);
			OCut.add(cbin, new HashMap<>());
		}
	}
	
	@Override
	protected void buildModel() throws Exception {
		for (int iter = 1; iter <= numIters; iter++) {
			loss = 0;
			
			int cnt = 0;
			for (MatrixEntry me : trainMatrix) {
				if(cnt >= 500)
					break;
				cnt++;
				
				int u = me.row();
				int i = me.column();
				double rui = me.get();

				long timestamp = (long) timeMatrix.get(u, i);
				// day t
				int t = days(timestamp, minTrainTimestamp);
				int bin = bin(t);
				double dev_ut = dev(u, t);
				
				// set non community-related variables
				List<Integer> Iu = userItemsCache.get(u);
				double wi = Iu.size() > 0 ? Math.pow(Iu.size(), -0.5) : 0;
				
				// set community-related variables
				int cbin = cbin(t);
				List<Integer> itemCommunities = itemCommunitiesCache.get(cbin).get(i);
				List<Integer> userCommunities = userCommunitiesCache.get(cbin).get(u);
				List<Integer> userStaticCommunities = userCommunitiesCache.get(0).get(u);
				List<Integer> Icu = userCommunitiesItemsCache.get(cbin).get(u);
				double wc = Icu.size() > 0 ? Math.pow(Icu.size(), -0.5) : 0;
				
				// lazy initialization
				if (!But.contains(u, t))
					But.put(u, t, Randoms.gaussian(initMean, initStd));
				if (!Pukt.containsKey(u))
					Pukt.put(u, HashBasedTable.create());
				for (int k = 0; k < numFactors; k++)
					if (!Pukt.get(u).contains(k, t))
						Pukt.get(u).put(k, t, Randoms.gaussian(initMean, initStd));
				for (int c : userCommunities){
					if (!BCut.get(cbin).contains(c, t))
						BCut.get(cbin).put(c, t, Randoms.gaussian(initMean, initStd));
					if (!OCut.get(cbin).containsKey(c))
						OCut.get(cbin).put(c, HashBasedTable.create());
					for (int k = 0; k < numFactors; k++)
						if (!OCut.get(cbin).get(c).contains(k, t))
							OCut.get(cbin).get(c).put(k, t, Randoms.gaussian(initMean, initStd));
				}
				
				double pui = predict(u, i);
				
				double eui = pui - rui;
				loss += eui * eui;
				
				// Update baseline parameters
				// ==========================
				
				double bi = itemBias.get(i);
				double cu = Cu.get(u);
				double cut = Cut.get(u, t);
				double bit = Bit.get(i, bin);
				double bu = userBias.get(u);
				double but = But.get(u, t);
				double au = Alpha.get(u);

				// update bi
				double sgd = eui * (cu + cut) + regB * bi;
				itemBias.add(i, -lRate * sgd);
				loss += regB * bi * bi;

				// update bi,bin(t)
				sgd = eui * (cu + cut) + regB * bit;
				Bit.add(i, bin, -lRate * sgd);
				loss += regB * bit * bit;

				// update cu
				sgd = eui * (bi + bit) + regB * cu;
				Cu.add(u, -lRate * sgd);
				loss += regB * cu * cu;

				// update cut
				sgd = eui * (bi + bit) + regB * cut;
				Cut.add(u, t, -lRate * sgd);
				loss += regB * cut * cut;

				// update bu
				sgd = eui + regB * bu;
				userBias.add(u, -lRate * sgd);
				loss += regB * bu * bu;

				// update au
				sgd = eui * dev_ut + regB * au;
				Alpha.add(u, -lRate * sgd);
				loss += regB * au * au;

				// update but
				sgd = eui + regB * but;
				But.put(u, t, but - lRate * sgd);
				loss += regB * but * but;
				
				// update bcu, bcut
				for (int c : userCommunities){
					double bcu = BCu[cbin].get(c);
					double bcut = BCut.get(cbin).get(c, t);
					double muc = userMemberships[cbin].get(u, c);
					
					sgd = eui * muc + regC * bcu;
					BCu[cbin].add(c, -lRateC * sgd);
					loss += regC * bcu * bcu;
					
					sgd = eui * muc + regC * bcut;
					BCut.get(cbin).put(c, t, bcut - lRateC * sgd);
					loss += regC * bcut * bcut;
				}
				
				// update alpha_c
				for (int c : userStaticCommunities){
					double alphac = AlphaC.get(c);
					double devct = devc(c, t);
					double muc = userMemberships[0].get(u, c);
					
					sgd = eui * devct * muc - regC * alphac;
					AlphaC.add(c, -lRateC * sgd);
					loss += regC * alphac * alphac;
				}
				
				// update bci, bcit
				for (int c: itemCommunities){
					double bci = BCi[cbin].get(c);
					double bcit = BCit[cbin].get(c, bin);
					double mic = itemMemberships[cbin].get(i, c);
					
					sgd = eui * mic + regC * bci;
					BCi[cbin].add(c, -lRateC * sgd);
					loss += regC * bci * bci;
					
					sgd = eui * mic + regC * bcit;
					BCit[cbin].add(c, bin, -lRateC * sgd);
					loss += regC * bcit * bcit;
				}

				// Update SVD model parameters
				// ===========================
				
				Table<Integer, Integer, Double> Pkt = Pukt.get(u);

				for (int k = 0; k < numFactors; k++) {
					double qik = Q.get(i, k);
					double puk = P.get(u, k);
					double auk = Auk.get(u, k);
					double pkt = Pkt.get(k, t);
					double pukt = puk + auk * dev_ut + pkt;

					double sum_yk = 0;
					for (int j : Iu)
						sum_yk += Y.get(j, k);
					
					double sum_zk = 0;
					for (int j : Icu)
						sum_zk += Z.get(j, k);
					
					double sum_ocuk = 0;
					double sum_ocukt = 0;
					for (int c : userCommunities){
						double muc = userMemberships[cbin].get(u, c);
						sum_ocuk += OCu[cbin].get(c, k) * muc;
						sum_ocukt += OCut.get(cbin).get(c).get(k, t) * muc;
					}
					
					double sum_acuk = 0;
					for (int c : userStaticCommunities){
						double muc = userMemberships[0].get(u, c);
						sum_acuk += ACu.get(c, k) * devc(c, t) * muc;
					}
					
					double sum_ocik = 0;
					for (int c : itemCommunities){
						double mic = itemMemberships[cbin].get(i, c);
						sum_ocik += OCi[cbin].get(cbin, k) * mic;
					}
					
					// update qik
					sgd = eui * (pukt + sum_ocuk + sum_ocukt + sum_acuk + wi * sum_yk + wc * sum_zk) + regI * qik;
					Q.add(i, k, -lRateF * sgd);
					loss += regI * qik * qik;
					
					// update puk
					sgd = eui * (qik + sum_ocik) + regU * puk;
					P.add(u, k, -lRateF * sgd);
					loss += regU * puk * puk;

					// update auk
					sgd = eui * (qik + sum_ocik) * dev_ut + regU * auk;
					Auk.add(u, k, -lRateF * sgd);
					loss += regU * auk * auk;

					// update pkt
					sgd = eui * (qik + sum_ocik) + regU * pkt;
					Pkt.put(k, t, pkt - lRateF * sgd);
					loss += regU * pkt * pkt;
					
					// update yjk
					for (int j : Iu) {
						double yjk = Y.get(j, k);
						sgd = eui * wi * (qik + sum_ocik) + regI * yjk;
						Y.add(j, k, -lRateF * sgd);
						loss += regI * yjk * yjk;
					}
					
					// update oci
					for (int c : itemCommunities){
						double ocik = OCi[cbin].get(c, k);
						double mic = itemMemberships[cbin].get(i, c);
						sgd = eui * mic * (pukt + sum_ocuk + sum_ocukt + sum_acuk + wi * sum_yk + wc * sum_zk) + regCF * ocik;
						OCi[cbin].add(c, k, -lRateCF * sgd);
						loss += regCF * ocik * ocik;
					}
					
					// update ocu and ocut
					for (int c : userCommunities){
						double ocuk = OCu[cbin].get(c, k);
						double ocukt = OCut.get(cbin).get(c).get(k, t);
						double muc = userMemberships[cbin].get(u, c);
						
						sgd = eui * muc * (qik + sum_ocik) + regCF * ocuk;
						OCu[cbin].add(c, k, -lRateCF * sgd);
						loss += regCF * ocuk * ocuk;
						
						sgd = eui * muc * (qik + sum_ocik) + regCF * ocukt;
						OCut.get(cbin).get(c).put(k, t, ocukt - lRateCF * sgd);
						loss += regCF * ocukt * ocukt;
					}
					
					// update acu
					for (int c : userStaticCommunities){
						double acuk = ACu.get(c, k);
						double muc = userMemberships[0].get(u, c);
						double devcut = devc(c, t);
						
						sgd = eui * devcut * muc * (qik + sum_ocik) + regCF * acuk;
						ACu.add(c, k, -lRateCF * sgd);
						loss += regCF * acuk * acuk;
					}
					
					// update zjk
					for (int j : Icu) {
						double zjk = Z.get(j, k);
						sgd = eui * wc * (qik + sum_ocik) + regCF * zjk;
						Z.add(j, k, -lRateCF * sgd);
						loss += regCF * zjk * zjk;
					}
				}
				
				// Update neighborhood model parameters
				// ====================================
				
				// update w, c and phi
				double sgd_phi = 0;
				for (int j : Iu){
					double e = decay(u, j, t);
					double ruj = trainMatrix.get(u, j);
					double buj = (itemBias.get(i) + Bit.get(i, bin)) * (Cu.get(u) + Cut.get(u, t));
					buj += userBias.get(u) + Alpha.get(u) * dev_ut;
					buj += But.contains(u, t) ? But.get(u, t) : 0;
					
					// update w
					double wij = W.get(i, j);
					sgd = eui * wi * e * (ruj - buj) + regN * wij;
					W.add(i, j, -lRateN * sgd);
					loss += regI * wij * wij;
					
					// update c
					double cij = C.get(i, j);
					sgd = eui * wi * e + regN * cij;
					C.add(i, j, -lRateN * sgd);
					loss += regI * cij * cij;
					
					// update phi
					int diff = Math.abs(t - days((long) timeMatrix.get(u, j), minTrainTimestamp));
					sgd_phi = eui * wi * (-1 * diff) * e * ((ruj - buj) * wij + cij);
				}
				double phi = Phi.get(u);
				sgd_phi += regN * phi;
				Phi.add(u, -lRateMu * sgd_phi);
				loss += regI * phi * phi;
				
				// update d and psi
				double sgd_psi = 0;
				for (int j : Icu){
					double dij = D.get(i, j);
					double e = cdecay(u, j, t, cbin);
					double rcuj = userCommunitiesRatingsMatrix[cbin].get(u, j);
					double buj = bias(u, j, t, userStaticCommunities, userCommunities, itemCommunities);
					sgd = eui * wc + e * (rcuj - buj) + regCN * dij;
					D.add(i, j, -lRateCN * sgd);
					loss += regCN * dij * dij;
					
					int tj = days((long) timeMatrix.get(u, j), minTrainTimestamp);
					int diff = Math.abs(t - tj);
					sgd_psi += eui * wc * (-1 * diff) * e * ((rcuj - buj) * dij);
				}
				double psi = Psi.get(u);
				if(psi < 0) System.out.println(String.format("psi(%d) = %f", u, psi));
				sgd_psi += regCN * psi;
				// do not let psi become negative
				double delta_psi = (lRateMu * sgd_psi > psi) ? (psi / 2.0) : (lRateMu * sgd_psi);
				Psi.add(u, -delta_psi);
				loss += regCN * psi * psi;
				if(loss > 10000){
					System.out.println(String.format("loss: %f", loss));
				}
			}

			loss *= 0.5;

			if (isConverged(iter))
				break;
		}
	}

	@Override
	protected double predict(int u, int i) throws Exception {
		// retrieve the test rating timestamp
		long timestamp = (long) timeMatrix.get(u, i);
		int t = days(timestamp, minTrainTimestamp);
		double dev_ut = dev(u, t);
		
		List<Integer> Iu = userItemsCache.get(u);
		double wi = Iu.size() > 0 ? Math.pow(Iu.size(), -0.5) : 0;

		// set community-related variables
		int cbin = cbin(t);
		List<Integer> itemCommunities = itemCommunitiesCache.get(cbin).get(i);
		List<Integer> userCommunities = userCommunitiesCache.get(cbin).get(u);
		List<Integer> userStaticCommunities = userCommunitiesCache.get(0).get(u);
		List<Integer> Icu = userCommunitiesItemsCache.get(cbin).get(u);
		double wc = Icu.size() > 0 ? Math.pow(Icu.size(), -0.5) : 0;

		// baseline / bias
		double pred = bias(u, i, t, userStaticCommunities, userCommunities, itemCommunities);
		
		// qi * pu(t)
		for (int k = 0; k < numFactors; k++) {
			double itemFactor = Q.get(i, k);
			
			for (int c : itemCommunities)
				itemFactor += OCi[cbin].get(c, k) * itemMemberships[cbin].get(i, c);
			
			double userFactor = P.get(u, k) + Auk.get(u, k) * dev_ut;
			if (Pukt.containsKey(u)) {
				Table<Integer, Integer, Double> pkt = Pukt.get(u);
				if (pkt != null) {
					// eq. (13)
					userFactor += (pkt.contains(k, t) ? pkt.get(k, t) : 0);
				}
			}
			for (int c : userCommunities){
				double ocuk = OCu[cbin].get(c, k);
				double muc = userMemberships[cbin].get(u,c);
				
				userFactor += ocuk * muc;
				
				if (OCut.get(cbin).containsKey(c)){
					Table<Integer, Integer, Double> ocuTable = OCut.get(cbin).get(c);
					double ocukt = ocuTable.contains(k, t) ? ocuTable.get(k, t) : 0;
					userFactor += ocukt * muc;
				}
			}
			for (int c : userStaticCommunities){
				double acuk = AlphaC.get(c);
				double devcut = devc(c, t);
				double muc = userMemberships[0].get(u, c);
				
				userFactor += acuk * devcut * muc;
			}
			for (int j : Iu)
				userFactor += Y.get(j, k);
			for (int j : Icu)
				userFactor += Z.get(j,k);

			pred += userFactor * itemFactor;
		}
		
		// e^(-beta_u * |t-tj|)(ruj - buj) * wij + cij): eq. (16)
		// we use phi instead of beta since beta is already used for the time deviation in the baseline model
		for (int j : Iu){
			double e = decay(u, j, t);
			double ruj = rateMatrix.get(u, j);
			double buj = bias(u, j, t, userStaticCommunities, userCommunities, itemCommunities);

			pred += e * ((ruj - buj) * W.get(i, j) + C.get(i, j)) * wi;
		}
		
		// e^(-psi_u * |t-tj|)(rCuj - buj) * dij)
		for (int j : Icu){
			double e = cdecay(u, j, t, cbin);
			double rcuj = userCommunitiesRatingsMatrix[cbin].get(u, j);
			double buj = bias(u, j, t, userStaticCommunities, userCommunities, itemCommunities);
			double dij = D.get(i,j);
			
			pred += e * (rcuj - buj) * dij * wc;
		}

		return pred;
	}

	@Override
	public String toString() {
		return Strings.toString(new Object[] { numFactors, initLRate, initLRateN, initLRateF, initLRateC, initLRateCN, initLRateCF, 
				initLRateMu, maxLRate, regB, regN, regU, regI, regC, regCN, regCF, numIters, isBoldDriver, beta, numBins, numCBins});
	}

	/***************************************************************** Functional Methods *******************************************/
	/**
	 * @return the time deviation for a specific timestamp (number of days) t w.r.t the mean date tu
	 */
	protected double dev(int u, int t) {
		double tu = userMeanDate.get(u);

		// date difference in days
		double diff = t - tu;

		return Math.signum(diff) * Math.pow(Math.abs(diff), beta);
	}

	/**
	 * @return the rating decay for user u rating item j w.r.t. timestamp (number of days) t, i.e. e^(-beta_u * |t-tj|) from eq. (16)
	 */
	protected double decay(int u, int j, int t) {
		int tj = days((long) timeMatrix.get(u, j), minTrainTimestamp);

		// date difference in days
		int diff = Math.abs(t - tj);

		return Math.pow(Math.E, -1.0 * Phi.get(u) * diff);
	}

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
	private double bias(int u, int i, int t, List<Integer> userStaticCommunities, List<Integer> userCommunities, List<Integer> itemCommunities){
		double bias = globalMean;
		int bin = bin(t);
		double dev_ut = dev(u, t);
		int cbin = cbin(t);

		// bi(t): eq. (12)
		double bi = itemBias.get(i);
		double bit = Bit.get(i, bin);
		double cu = Cu.get(u);
		double cut = (t >= 0 && t < numDays) ? Cut.get(u, t) : 0;
		bias = (bi + bit) * (cu + cut);
		
		// bu(t): eq. (9)
		double but = But.contains(u, t) ? But.get(u, t) : 0;
		bias += userBias.get(u) + Alpha.get(u) * dev_ut + but;
		
		// bci(t)
		for (int c : itemCommunities){
			double mic = itemMemberships[cbin].get(i, c);
			double bci = BCi[cbin].get(c);
			double bcit = BCit[cbin].get(c, bin);
			bias += (bci + bcit) * mic;
		}
		
		// bcu(t)
		for (int c : userCommunities){
			double muc = userMemberships[cbin].get(u, c);
			double bcu = BCu[cbin].get(c);
			double bcut = BCut.get(cbin).contains(c, t) ? BCut.get(cbin).get(c, t) : 0;
			bias += (bcu + bcut) * muc;
		}
		for (int c : userStaticCommunities){
			double muc = userMemberships[0].get(u, c);
			double alpha = Alpha.get(c);
			double dev = devc(c, t);
			bias += alpha * dev * muc;
		}
		return bias;
	}

	/******************************************************* Community-related Methods **********************************************/
	
	/**
	 * @return an array of size numCBins containing the rating matrices for each cbin (bins are numbered from 0 to numCBins-1)
	 */
	private SparseMatrix[] trainDataCBins() {
		Logs.info("{}{} split training data into bins for dynamic community structure detection ...", algoName, foldInfo);
		Logs.info("{}{} number of days: {}, global mean rating day: {}", algoName, foldInfo, numDays, (int) globalMeanDate);
		
		List<Table<Integer, Integer, Double>> table = new ArrayList<Table<Integer, Integer, Double>>(numCBins);
		SparseMatrix[] matrix = new SparseMatrix[numCBins];
		
		for (int cbin = 0; cbin < numCBins; cbin++){
			table.add(cbin, HashBasedTable.create());
			int fromDay = numDays / numCBins * cbin;
			int toDay = numDays / numCBins * (cbin + 1) - 1;
			Logs.info("{}{} community bin {}: from day {} to day {}", algoName, foldInfo, cbin, fromDay, toDay);
		}
		
		for (MatrixEntry e : trainMatrix){
			int user = e.row();
			int item = e.column();
			double rating = e.get();
			long time = (long) timeMatrix.get(user, item);
			int days = days(time, minTrainTimestamp);
			
			int cbin = cbin(days);
			table.get(cbin-1).put(user, item, rating);
		}
		
		for (int cbin = 0; cbin < numCBins; cbin++){
			matrix[cbin] = new SparseMatrix(numUsers, numItems, table.get(cbin));
			Logs.info("{}{} trainMatrix cbin {}: {} ratings", algoName, foldInfo, cbin, matrix[cbin].size());
		}
		
		return matrix;
	}

	/**
	 * @return the time deviation for a specific timestamp (number of days) t w.r.t the community mean date tc
	 */
	protected double devc(int c, int t) {
		double tc = communityMeanDate[0].get(c);

		// date difference in days
		double diff = t - tc;

		return Math.signum(diff) * Math.pow(Math.abs(diff), beta);
	}

	/**
	 * @return the community bin number (starting from 1..numCBins) for a specific day t (number of days after trainMinTimestamp);
	 */
	protected int cbin(int day) {
		int cbin = (int) (day / (numDays + 0.0) * numCBins) + 1;
		
		if (cbin < 1)
			return 1;
		if (cbin > numCBins)
			return numCBins;
		
		return cbin;
	}

	/**
	 * @param u user
	 * @param j item
	 * @param t time (day) of the rating that is to be predicted
	 * @return the rating decay for user u rating item j w.r.t. timestamp (number of days) t, i.e. e^(-beta_u * |t-tj|) from eq. (16)
	 */
	protected double cdecay(int u, int j, int t, int cbin) {
		int tj = days((long) userCommunitiesTimeMatrix[cbin].get(u, j), minTrainTimestamp);
		
		// date difference in days
		int diff = Math.abs(t - tj);

		return Math.pow(Math.E, -1.0 * Psi.get(u) * diff);
	}
	
	private void logCommunityInfo() {
		for (int cbin = 0; cbin <= numCBins; cbin++){
			int userMemSize = userMemberships[cbin].size();
			int itemMemSize = itemMemberships[cbin].size();
			
			// users per community
			double avgupc = (double) userMemSize / numUserCommunities[cbin];
			int minupc = Integer.MAX_VALUE;
			int maxupc = Integer.MIN_VALUE;
			for (int c = 0; c < numUserCommunities[cbin]; c++){
				int upc = userMemberships[cbin].columnSize(c);
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
				int cpu = userMemberships[cbin].rowSize(u);
				if (cpu < mincpu)
					mincpu = cpu;
				if (cpu > maxcpu)
					maxcpu = cpu;
			}
			
			
			// items per community
			double avgipc = (double) itemMemSize / numItemCommunities[cbin];
			int minipc = Integer.MAX_VALUE;
			int maxipc = Integer.MIN_VALUE;
			for (int c = 0; c < numItemCommunities[cbin]; c++){
				int ipc = itemMemberships[cbin].columnSize(c);
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
				int cpi = itemMemberships[cbin].rowSize(i);
				if (cpi < mincpi)
					mincpi = cpi;
				if (cpi > maxcpi)
					maxcpi = cpi;
			}

			String cbinInfo = "";
			if (cbin == 0)
				cbinInfo = "static community structure";
			else
				cbinInfo = "dynamic community structure bin " + cbin;
			
			Logs.info("{}{} {} user communites: {}, [min, max, avg] users per community: [{}, {}, {}], [min, max, avg] communities per user: [{}, {}, {}]",
					new Object[] { algoName, foldInfo, cbinInfo, numUserCommunities[cbin], minupc, maxupc, avgupc, mincpu, maxcpu, avgcpu });
			Logs.info("{}{} {} item communites: {}, [min, max, avg] items per community: [{}, {}, {}], [min, max, avg] communities per item: [{}, {}, {}]",
					new Object[] { algoName, foldInfo, cbinInfo, numItemCommunities[cbin], minipc, maxipc, avgipc, mincpi, maxcpi, avgcpi });
		}
	}

}
