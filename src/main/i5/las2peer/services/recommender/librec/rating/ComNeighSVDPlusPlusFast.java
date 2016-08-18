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

import i5.las2peer.services.recommender.communities.CommunityDetector;
import i5.las2peer.services.recommender.communities.CommunityDetector.CommunityDetectionAlgorithm;
import i5.las2peer.services.recommender.graphs.GraphBuilder;
import i5.las2peer.services.recommender.graphs.GraphBuilder.SimilarityMeasure;
import i5.las2peer.services.recommender.librec.data.Configuration;
import i5.las2peer.services.recommender.librec.data.DenseMatrix;
import i5.las2peer.services.recommender.librec.data.DenseVector;
import i5.las2peer.services.recommender.librec.data.MatrixEntry;
import i5.las2peer.services.recommender.librec.data.SparseMatrix;
import i5.las2peer.services.recommender.librec.util.Logs;
import i5.las2peer.services.recommender.librec.util.Strings;

/**
 * Community-aware model based on Yehuda Koren, Factorization Meets the Neighborhood: a Multifaceted Collaborative Filtering Model., KDD 2008.
 * 
 * @author guoguibing, martin
 * 
 */
@Configuration("factors, lRate, lRateN, lRateF, lRateC, lRateCN, lRateCF, maxLRate,"
		+ " regB, regN, regU, regI, regC, regCN, regCF, iters, boldDriver")
public class ComNeighSVDPlusPlusFast extends BiasedMF {

	protected DenseMatrix Y, Ocu, Oci;
	protected DenseMatrix W, C; // weighting factors for neighborhood model
	
	protected int numUserCommunities;
	protected int numItemCommunities;
	
	protected DenseVector userComBias, itemComBias;
	
	protected SparseMatrix userMemberships, itemMemberships; // Community membership matrices for users and items
	
	protected LoadingCache<Integer, List<Integer>> userCommunitiesCache, itemCommunitiesCache, userCommunitiesItemsCache;
	
	public ComNeighSVDPlusPlusFast(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		setAlgoName("ComNeighSVD++Fast");
	}

	@Override
	protected void initModel() throws Exception {
		super.initModel();
	}

	@Override
	protected void buildModel() throws Exception {
		// build the user and item graphs
		Logs.info("{}{} build user and item graphs ...", new Object[] { algoName, foldInfo });
		GraphBuilder gb = new GraphBuilder();
		gb.setRatingData(trainMatrix);
		gb.setK(10);
		gb.setSimilarityMeasure(SimilarityMeasure.COSINE_SIMILARITY);
		gb.buildGraphs();
		SparseMatrix userMatrix = gb.getUserAdjacencyMatrix();
		SparseMatrix itemMatrix = gb.getItemAdjacencyMatrix();
		
		// detect communities
		Logs.info("{}{} detect communities ...", new Object[] { algoName, foldInfo });
		CommunityDetector cd = new CommunityDetector();
		switch(cf.getString("cd.algo", "dmid").toLowerCase()){
		default:
		case "walktrap":
			cd.setAlgorithm(CommunityDetectionAlgorithm.WALKTRAP);
			cd.setWalktrapParameters(cf.getInt("cd.walktrap.steps", 2));
			break;
		case "dmid":
			cd.setAlgorithm(CommunityDetectionAlgorithm.DMID);
			cd.setDmidParameters(cf.getInt("cd.dmid.iter", 1000),
								cf.getDouble("cd.dmid.prec", 0.001),
								cf.getDouble("cd.dmid.proficioncy", 0.1));
			break;
		case "slpa":
			cd.setAlgorithm(CommunityDetectionAlgorithm.SLPA);
			cd.setSlpaParameters(cf.getDouble("cd.slpa.prob", 0.15),
								cf.getInt("cd.slpa.memory", 100));
			break;
		}
		
		cd.setGraph(userMatrix);
		cd.detectCommunities();
		userMemberships = cd.getMemberships();
		userCommunitiesCache = userMemberships.rowColumnsCache(cacheSpec);
		
		cd.setGraph(itemMatrix);
		cd.detectCommunities();
		itemMemberships = cd.getMemberships();
		itemCommunitiesCache = itemMemberships.rowColumnsCache(cacheSpec);
		
		numUserCommunities = userMemberships.numColumns();
		numItemCommunities = itemMemberships.numColumns(); 
		
		debugCommunityInfo();
		
		Y = new DenseMatrix(numItemCommunities, numFactors);
		Y.init(initMean, initStd);

		W = new DenseMatrix(numItems, numItemCommunities);
		W.init(initMean, initStd);

		C = new DenseMatrix(numItems, numItemCommunities);
		C.init(initMean, initStd);
		
		userItemsCache = trainMatrix.rowColumnsCache(cacheSpec);
		
		userComBias = new DenseVector(numUserCommunities);
		userComBias.init(initMean, initStd);
		
		itemComBias = new DenseVector(numItemCommunities);
		itemComBias.init(initMean, initStd);

		Ocu = new DenseMatrix(numUserCommunities, numFactors);
		Ocu.init(initMean, initStd);

		Oci = new DenseMatrix(numItemCommunities, numFactors);
		Oci.init(initMean, initStd);

		// iteratively learn the model parameters
		Logs.info("{}{} learn model parameters ...", new Object[] { algoName, foldInfo });
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

				double w = Math.sqrt(items.size());

				// update baseline parameters
				for (int cu : userCommunities){
					double bc = userComBias.get(cu);
					double sgd = euj * userMemberships.get(u, cu) - regC * bc;
					userComBias.add(cu, lRateC * sgd);
					loss += regC * bc * bc;
				}
				for (int ci : itemCommunities){
					double bc = itemComBias.get(ci);
					double sgd = euj * itemMemberships.get(j, ci) - regC * bc;
					itemComBias.add(ci, lRateC * sgd);
					loss += regC * bc * bc;
				}

				// update neighborhood model parameters
				for (int k : items){
					double ruk = trainMatrix.get(u, k);
					double buk = getBias(u, k, userCommunities, itemCommunitiesCache.get(k));
					List<Integer> kItemCommunities = itemCommunitiesCache.get(k);
					for (int c : kItemCommunities){
						double wjc = W.get(j, c);
						double mjc = itemMemberships.get(j, c);
						double sgd_w = euj * (ruk - buk) * mjc / w - regCN * wjc;
						W.add(j, c, lRateCN * sgd_w);
						loss += wjc * wjc;
						
						double cjc = C.get(j, c);
						double sgd_c = euj * mjc / w - regCN * cjc;
						C.add(j, c, lRateCN * sgd_c);
						loss += cjc * cjc;
					}
				}
				
				// update factor model parameters
				double[] sum_ys = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					double sum_f = 0;
					for (int k : items){
						List<Integer> kItemCommunities = itemCommunitiesCache.get(k);
						for (int c : kItemCommunities){
							double ycf = Y.get(c, f);
							double mjc = itemMemberships.get(j, c);
							sum_ys[f] += ycf * mjc;
						}
					}
				}

				double[] sum_ocus = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					for (int c : userCommunities)
						sum_ocus[f] += Ocu.get(c, f) * userMemberships.get(u, c);
				}
				
				double[] sum_ocis = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					for (int c : itemCommunities)
						sum_ocis[f] += Oci.get(c, f) * itemMemberships.get(j, c);
				}
				
				for (int f = 0; f < numFactors; f++) {
					for (int c : userCommunities){
						double ocuf = Ocu.get(c, f);
						double muc = userMemberships.get(u, c);
						double delta_ocu = euj * muc * sum_ocis[f] - regCF + ocuf;
						Ocu.add(c, f, lRateCF * delta_ocu);
						loss += regCF * ocuf * ocuf;
					}
					
					for (int c : itemCommunities){
						double ocif = Oci.get(c, f);
						double mic = itemMemberships.get(j, c);
						double delta_oci = euj * mic * (sum_ocus[f] + sum_ys[f]) - regCF + ocif;
						Oci.add(c, f, lRateCF * delta_oci);
						loss += regCF * ocif * ocif;
					}
					
					for (int k : items) {
						List<Integer> kItemCommunities = itemCommunitiesCache.get(k);
						for (int c : kItemCommunities){
							double ycf = Y.get(c, f);
							double delta_y = euj * sum_ocis[f] / w - regCF * ycf;
							Y.add(c, f, lRateCF * delta_y);
							loss += regCF * ycf * ycf;
						}
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
		
		double w = Math.sqrt(items.size());

		// baseline prediction
		double pred = getBias(u, j, userCommunities, itemCommunities);
		
		// neighborhood model prediction
		for (int k : items){
			double ruk = trainMatrix.get(u, k);
			double buk = getBias(u,k, userCommunities, itemCommunitiesCache.get(k));
			List<Integer> kItemCommunities = itemCommunitiesCache.get(k);
			for (int c : kItemCommunities){
				double wjc = W.get(j, c);
				double cjc = C.get(j, c);
				double mjc = itemMemberships.get(j, c);
				pred += ((ruk - buk) * wjc + cjc) * mjc / w;
			}
		}
		
		// factor model prediction
		DenseVector userFactor = new DenseVector(numFactors);
		DenseVector itemFactor = new DenseVector(numFactors);
		for (int c : userCommunities){
			userFactor.add(Ocu.row(c).scale(userMemberships.get(u, c)));
		}
		for (int k : items){
			List<Integer> kItemCommunities = itemCommunitiesCache.get(k);
			for (int c : kItemCommunities){
				userFactor.add(Y.row(c).scale(itemMemberships.get(k, c)));
			}
		}
		for (int c : itemCommunities){
			itemFactor.add(Oci.row(c).scale(itemMemberships.get(j, c)));
		}
		pred += itemFactor.inner(userFactor);

		return pred;
	}

	private double getBias(int u, int j, List<Integer> userCommunities, List<Integer> itemCommunities){
		double bias = globalMean;
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
	
	private void debugCommunityInfo() {
		int userMemSize = userMemberships.size();
		int itemMemSize = itemMemberships.size();
		double upc = (double) userMemSize / numUserCommunities;
		double cpu = (double) userMemSize / numUsers;
		double ipc = (double) itemMemSize / numItemCommunities;
		double cpi = (double) itemMemSize / numItems;
		
		Logs.info("{}{} user communites: {}, users per community: {}, communities per user: {}",
				new Object[] { algoName, foldInfo, numUserCommunities, upc, cpu });
		Logs.info("{}{} item communites: {}, items per community: {}, communities per item: {}",
				new Object[] { algoName, foldInfo, numItemCommunities, ipc, cpi });
	}
	
	@Override
	public String toString() {
		return Strings.toString(new Object[] { numFactors,
				initLRateC, initLRateCN, initLRateCF, maxLRate,
				regC, regCN, regCF, numIters, isBoldDriver});
	}
	
}
