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

package i5.las2peer.services.recommender.librec.rating;

import java.util.List;

import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import i5.las2peer.services.recommender.communities.CommunityDetector;
import i5.las2peer.services.recommender.communities.CommunityDetector.CommunityDetectionAlgorithm;
import i5.las2peer.services.recommender.graphs.GraphBuilder;
import i5.las2peer.services.recommender.graphs.GraphBuilder.SimilarityMeasure;
import i5.las2peer.services.recommender.librec.data.DenseMatrix;
import i5.las2peer.services.recommender.librec.data.DenseVector;
import i5.las2peer.services.recommender.librec.data.MatrixEntry;
import i5.las2peer.services.recommender.librec.data.SparseMatrix;
import i5.las2peer.services.recommender.librec.data.SparseVector;
import i5.las2peer.services.recommender.librec.data.VectorEntry;
import i5.las2peer.services.recommender.librec.util.Logs;

/**
 * Community-aware model based on Yehuda Koren, Factorization Meets the Neighborhood: a Multifaceted Collaborative Filtering Model., KDD 2008.
 * No separate user and item factors for the SVD model. 
 * 
 * @author guoguibing, martin
 * 
 */
public class ComNeighSVDPlusPlus2 extends BiasedMF {

	protected DenseMatrix Y, Z;
	protected DenseMatrix W, C, D; // weighting factors for neighborhood model
	
	protected int numUserCommunities;
	protected int numItemCommunities;
	
	protected DenseVector userComBias, itemComBias;
	
	protected SparseMatrix userMemberships, itemMemberships; // Community membership matrices for users and items
	protected SparseMatrix communityRatingsMatrix;  // Average ratings given by the members of each community (numUserCommunities x numItems)
	protected SparseMatrix userCommunitiesRatingsMatrix;  // Average ratings given by each user's communities (numUsers x numItems)
	
	protected LoadingCache<Integer, List<Integer>> userCommunitiesCache, itemCommunitiesCache, userCommunitiesItemsCache;
	
	public ComNeighSVDPlusPlus2(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		setAlgoName("ComNeighSVD++");
	}

	@Override
	protected void initModel() throws Exception {
		super.initModel();
	}

	@Override
	protected void buildModel() throws Exception {
		Y = new DenseMatrix(numItems, numFactors);
		Y.init(initMean, initStd);

		Z = new DenseMatrix(numItems, numFactors);
		Z.init(initMean, initStd);

		W = new DenseMatrix(numItems, numItems);
		W.init(initMean, initStd);

		C = new DenseMatrix(numItems, numItems);
		C.init(initMean, initStd);
		
		D = new DenseMatrix(numItems, numItems);
		D.init(initMean, initStd);
		
		userItemsCache = trainMatrix.rowColumnsCache(cacheSpec);

		// build the user and item graphs
		GraphBuilder gb = new GraphBuilder();
		gb.setRatingData(trainMatrix);
		gb.setK(10);
		gb.setSimilarityMeasure(SimilarityMeasure.COSINE_SIMILARITY);
		gb.buildGraphs();
		SparseMatrix userMatrix = gb.getUserAdjacencyMatrix();
		SparseMatrix itemMatrix = gb.getItemAdjacencyMatrix();
		
		// detect communities
		CommunityDetector cd = new CommunityDetector();
		cd.setAlgorithm(CommunityDetectionAlgorithm.WALKTRAP);
		cd.setWalktrapParameters(2);
		
		cd.setGraph(userMatrix);
		cd.detectCommunities();
		userMemberships = cd.getMemberships();
		userCommunitiesCache = userMemberships.rowColumnsCache(cacheSpec);
		
		cd.setGraph(itemMatrix);
		cd.detectCommunities();
		itemMemberships = cd.getMemberships();
		itemCommunitiesCache = itemMemberships.rowColumnsCache(cacheSpec);
		
		numUserCommunities = userMemberships.numColumns(); 
		userComBias = new DenseVector(numUserCommunities);
		userComBias.init(initMean, initStd);
		
		numItemCommunities = itemMemberships.numColumns(); 
		itemComBias = new DenseVector(numItemCommunities);
		itemComBias.init(initMean, initStd);

		communityRatingsMatrix = getCommunityRatings();
		
		userCommunitiesRatingsMatrix = getUserCommunitiesRatings();
		userCommunitiesItemsCache = userCommunitiesRatingsMatrix.rowColumnsCache(cacheSpec);
		
		// iteratively learn the model parameters
		for (int iter = 1; iter <= numIters; iter++) {

			loss = 0;
			for (MatrixEntry me : trainMatrix) {

				int u = me.row(); // user
				int j = me.column(); // item
				double ruj = me.get();

				double pred = predict(u, j);
				double euj = ruj - pred;

				loss += euj * euj;

				List<Integer> items = userItemsCache.get(u);
				List<Integer> userCommunities = userCommunitiesCache.get(u);
				List<Integer> itemCommunities = itemCommunitiesCache.get(j);
				List<Integer> userCommunitiesItems = userCommunitiesItemsCache.get(u);  // items that have been rated by u's community co-members

				double w = Math.sqrt(items.size());
				double cw = Math.sqrt(userCommunitiesItems.size());

				// update baseline parameters
				double bu = userBias.get(u);
				double sgd = euj - regB * bu;
				userBias.add(u, lRate * sgd);

				loss += regB * bu * bu;

				double bj = itemBias.get(j);
				sgd = euj - regB * bj;
				itemBias.add(j, lRate * sgd);

				loss += regB * bj * bj;
				
				for (int cu : userCommunities){
					double bc = userComBias.get(cu);
					sgd = euj * userMemberships.get(u, cu) - regC * bc;
					userComBias.add(cu, lRateC * sgd);
					loss += regC * bc * bc;
				}
				for (int ci : itemCommunities){
					double bc = itemComBias.get(ci);
					sgd = euj * itemMemberships.get(j, ci) - regC * bc;
					itemComBias.add(ci, lRateC * sgd);
					loss += regC * bc * bc;
				}

				// update neighborhood model parameters
				for (int k : items){	// to reduce complexity we can reduce the list of items to the nearest neighbors of item k
					double ruk = trainMatrix.get(u, k);
					double buk = getBias(u,k, userCommunities, itemCommunitiesCache.get(k));
					double wjk = W.get(j, k);
					sgd = euj * (ruk - buk) / w - regN * wjk;
					W.add(j, k, lRateN * sgd);
					loss += regN * wjk * wjk;
					
					double cjk = C.get(j, k);
					sgd = euj / w - regN * cjk;
					C.add(j, k, lRateN * sgd);
					loss += regN * cjk * cjk;
				}
				for (int k : userCommunitiesItems){
					double djk = D.get(j, k);
					double rcuk = userCommunitiesRatingsMatrix.get(u, k);
					double buk = getBias(u,k, userCommunities, itemCommunitiesCache.get(k));
					sgd = euj / cw * (rcuk - buk) - regCN * djk;
					D.add(j, k , lRateCN * sgd);
					loss += regCN * djk * djk;
				}
				
				// update factor model parameters
				double[] sum_ys = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					double sum_f = 0;
					for (int k : items)
						sum_f += Y.get(k, f);
					sum_ys[f] = w > 0 ? sum_f / w : sum_f;
				}

				double[] sum_zs = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					double sum_f = 0;
					for (int k : userCommunitiesItems)
						sum_f += Z.get(k, f);
					sum_zs[f] = cw > 0 ? sum_f / cw : sum_f;
				}
				
				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double qjf = Q.get(j, f);

					double sgd_puf = euj * qjf - regU * puf;
					double sgd_qjf = euj * (puf + sum_ys[f] + sum_zs[f]) - regI * qjf;

					P.add(u, f, lRate * sgd_puf);
					Q.add(j, f, lRate * sgd_qjf);

					loss += regU * puf * puf + regI * qjf * qjf;

					for (int k : items) {
						double ykf = Y.get(k, f);
						double delta_y = euj * qjf / w - regU * ykf;
						Y.add(k, f, lRate * delta_y);
						loss += regU * ykf * ykf;
					}
					
					for (int k : userCommunitiesItems){
						double zkf = Z.get(k, f);
						double delta_z = euj * qjf / cw - regCF * zkf;
						Z.add(k, f, lRateCF * delta_z);
						loss += regCF * zkf * zkf;
					}
				}
			}

			loss *= 0.5;

			if (isConverged(iter))
				break;

		}// end of training

	}

	@Override
	protected double predict(int u, int j) throws Exception {
		List<Integer> items = userItemsCache.get(u);
		List<Integer> userCommunities = userCommunitiesCache.get(u);
		List<Integer> itemCommunities = itemCommunitiesCache.get(j);
		List<Integer> userCommunitiesItems = userCommunitiesItemsCache.get(u);  // items that have been rated by u's community co-members
		
		double w = Math.sqrt(items.size());
		double cw = Math.sqrt(userCommunitiesItems.size());  // used for normalizing over the user's communities

		// baseline prediction
		double pred = getBias(u, j, userCommunities, itemCommunities);
		
		// neighborhood model prediction
		for (int k : items){
			double buk = getBias(u,k, userCommunities, itemCommunitiesCache.get(k));
			double ruk = trainMatrix.get(u, k);
			double wjk = W.get(j, k);
			double cjk = C.get(j, k);
			pred += ((ruk - buk) * wjk + cjk) / w;
		}
		for (int k : userCommunitiesItems){
			double rcuk = userCommunitiesRatingsMatrix.get(u, k);
			double buk = getBias(u,k, userCommunities, itemCommunitiesCache.get(k));
			double djk = D.get(j, k);
			pred += (rcuk - buk) * djk / cw;
		}
		
		// factor model prediction
		pred += DenseMatrix.rowMult(P, u, Q, j);
		for (int k : items)
			pred += DenseMatrix.rowMult(Y, k, Q, j) / w;
		for (int c : userCommunitiesItems)
			pred += DenseMatrix.rowMult(Z, c, Q, j) / cw;

		return pred;
	}

	private SparseMatrix getCommunityRatings() {
		// Get the average community ratings for each item
		Table<Integer, Integer, Double> communityRatingsTable = HashBasedTable.create();
		for (int community = 0; community < numUserCommunities; community++){
			SparseVector communityUsersVector = userMemberships.column(community);  // Contains each user's membership level for the community
			for (int item = 0; item < numItems; item++){
				double ratingsSum = 0;  // Sum of ratings given by users of the community to
										// the item, weighted by the users community membership levels
				double membershipsSum = 0;  // For normalization
				SparseVector itemUsersVector = trainMatrix.column(item);  // Contains each users rating for the item
				for (VectorEntry e : communityUsersVector){
					int user = e.index();
					if (itemUsersVector.contains(user)){  // Only consider users that are part of the community and have rated the item
						double userMembership = communityUsersVector.get(user);
						double userRating = itemUsersVector.get(user);
						ratingsSum += userRating * userMembership;
						membershipsSum += userMembership;
					}
				}
				if (membershipsSum != 0){
					double communityRating = ratingsSum / membershipsSum;
					communityRatingsTable.put(community, item, communityRating);
				}
			}
		}
		SparseMatrix matrix = new SparseMatrix(numUserCommunities, numItems, communityRatingsTable);
		return matrix;
	}
		
	private SparseMatrix getUserCommunitiesRatings() throws Exception {
		// Get each user's community ratings, i.e. the weighted average rating of the user's communities for each item
		// The resulting matrix has dimensions numUsers x numItems
		Table<Integer, Integer, Double> userCommunitiesRatingsTable = HashBasedTable.create();
		for (int user = 0; user < numUsers; user++){
			for (int item = 0; item < numItems; item++){
				List<Integer> userCommunities = userCommunitiesCache.get(user);
				double ratingsSum = 0;
				double membershipsSum = 0;
				for (int community : userCommunities){
					double communityRating = communityRatingsMatrix.get(community, item);
					double userMembership = userMemberships.get(user, community);
					ratingsSum += communityRating * userMembership;
					membershipsSum += userMembership;
				}
				if (membershipsSum != 0){
					double userCommunitiesRating = ratingsSum / membershipsSum;
					userCommunitiesRatingsTable.put(user, item, userCommunitiesRating);
				}
			}
		}
		SparseMatrix matrix = new SparseMatrix(numUsers, numItems, userCommunitiesRatingsTable);
		return matrix;
	}
	
	private double getBias(int u, int j, List<Integer> userCommunities, List<Integer> itemCommunities){
		double bias = globalMean + userBias.get(u) + itemBias.get(j);
		for (int cu : userCommunities){
			double bc = userComBias.get(cu);
			double muc = userMemberships.get(u, cu);  // community membership weight
			bias += bc * muc;
		}
		for (int ci : itemCommunities){
			double bc = itemComBias.get(ci);
			double mic = itemMemberships.get(j, ci);  // community membership weight
			bias += bc * mic;
		}
		return bias;
	}
}
