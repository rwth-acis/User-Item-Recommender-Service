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
@Configuration("factors, lRateC, lRateCN, lRateCF, maxLRate, regC, regCN, regCF, iters, boldDriver")
public class ComNeighSVDPlusPlusFast extends BiasedMF {

	protected DenseMatrix Y, Ocu, Oci;
	protected DenseMatrix W, C; // weighting factors for neighborhood model
	
	protected int numUserCommunities;
	protected int numItemCommunities;
	
	protected DenseVector BCu, BCi;
	
	// Community membership matrices for users and items
	protected SparseMatrix userMembershipsMatrix, itemMembershipsMatrix;
	
	// User/item community membership map
	private DenseVector userMembershipsVector, itemMembershipsVector;
	
	public ComNeighSVDPlusPlusFast(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		setAlgoName("ComNeighSVD++Fast");
	}

	@Override
	protected void initModel() throws Exception {
		super.initModel();
		
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
//		case "dmid":
//			cd.setAlgorithm(CommunityDetectionAlgorithm.DMID);
//			cd.setDmidParameters(cf.getInt("cd.dmid.iter", 1000),
//								cf.getDouble("cd.dmid.prec", 0.001),
//								cf.getDouble("cd.dmid.proficioncy", 0.1));
//			break;
//		case "slpa":
//			cd.setAlgorithm(CommunityDetectionAlgorithm.SLPA);
//			cd.setSlpaParameters(cf.getDouble("cd.slpa.prob", 0.15),
//								cf.getInt("cd.slpa.memory", 100));
//			break;
		}
		
		cd.setGraph(userMatrix);
		cd.detectCommunities();
		userMembershipsMatrix = cd.getMemberships();
		userMembershipsVector = cd.getMembershipsVector();
		
		cd.setGraph(itemMatrix);
		cd.detectCommunities();
		itemMembershipsMatrix = cd.getMemberships();
		itemMembershipsVector = cd.getMembershipsVector();
		
		numUserCommunities = userMembershipsMatrix.numColumns();
		numItemCommunities = itemMembershipsMatrix.numColumns(); 
		
		logCommunityInfo();
		
		Y = new DenseMatrix(numItemCommunities, numFactors);
		Y.init(initMean, initStd);

		W = new DenseMatrix(numItems, numItemCommunities);
		W.init(initMean, initStd);

		C = new DenseMatrix(numItems, numItemCommunities);
		C.init(initMean, initStd);
		
		userItemsCache = trainMatrix.rowColumnsCache(cacheSpec);
		
		BCu = new DenseVector(numUserCommunities);
		BCu.init(initMean, initStd);
		
		BCi = new DenseVector(numItemCommunities);
		BCi.init(initMean, initStd);

		Ocu = new DenseMatrix(numUserCommunities, numFactors);
		Ocu.init(initMean, initStd);

		Oci = new DenseMatrix(numItemCommunities, numFactors);
		Oci.init(initMean, initStd);
	}

	@Override
	protected void buildModel() throws Exception {
		// iteratively learn the model parameters
		Logs.info("{}{} learn model parameters ...", new Object[] { algoName, foldInfo });
		for (int iter = 1; iter <= numIters; iter++) {
			loss = 0;
			for (MatrixEntry me : trainMatrix) {
				int u = me.row(); // user
				int i = me.column(); // item
				double rui = me.get();
				
				int cu = (int) userMembershipsVector.get(u);
				int ci = (int) itemMembershipsVector.get(i);

				double pred = predict(u, i);
				double eui = rui - pred;

				loss += eui * eui;

				List<Integer> Iu = userItemsCache.get(u);

				double wi = Math.sqrt(Iu.size());
				
				double sgd;
				
				// update baseline parameters
				double bcu = BCu.get(cu);
				sgd = eui - regC * bcu;
				BCu.add(cu, lRateC * sgd);
				loss += regC * bcu * bcu;

				double bci = BCi.get(ci);
				sgd = eui - regC * bci;
				BCi.add(ci, lRateC * sgd);
				loss += regC * bci * bci;

				// update neighborhood model parameters
				for (int j : Iu){
					double ruj = trainMatrix.get(u, j);
					double buj = bias(u, j);
					int cj = (int) itemMembershipsVector.get(j);

					double wic = W.get(i, cj);
					sgd = eui * (ruj - buj) / wi - regCN * wic;
					W.add(i, cj, lRateCN * sgd);
					loss += regCN * wic * wic;
					
					double cic = C.get(i, cj);
					sgd = eui / wi - regCN * cic;
					C.add(i, cj, lRateCN * sgd);
					loss += regCN * cic * cic;
				}
				
				// update factor model parameters
				double[] sum_ys = new double[numFactors];
				for (int k = 0; k < numFactors; k++) {
					for (int j : Iu){
						int cj = (int) itemMembershipsVector.get(j);
						double yck = Y.get(cj, k);
						sum_ys[k] += yck;
					}
				}
				
				for (int k = 0; k < numFactors; k++){
					double ocuk = Ocu.get(cu, k);
					double ocik = Oci.get(ci, k);
					
					sgd = eui * ocik - regCF * ocuk;
					Ocu.add(cu, k, lRateCF * sgd);
					loss += regCF * ocuk * ocuk;
					
					sgd = eui * (ocuk + sum_ys[k] / wi) - regCF * ocik;
					Oci.add(ci, k, lRateCF * sgd);
					loss += regCF * ocik * ocik;
					
					for (int j : Iu) {
						int cj = (int) itemMembershipsVector.get(j);
						double ycjk = Y.get(cj, k);
						sgd = eui * ocik / wi - regCF * ycjk;
						Y.add(cj, k, lRateCF * sgd);
						loss += regCF * ycjk * ycjk;
					}
				}
			}

			loss *= 0.5;
			
			if (isConverged(iter))
				break;
		}// end of training

	}

	@Override
	protected double predict(int u, int i) throws Exception {
		List<Integer> Iu = userItemsCache.get(u);
		
		int cu = (int) userMembershipsVector.get(u);
		int ci = (int) itemMembershipsVector.get(i);
		
		double wi = Math.sqrt(Iu.size());

		// baseline prediction
		double pred = bias(u, i);
		
		// neighborhood model prediction
		for (int j : Iu){
			double ruj = trainMatrix.get(u, j);
			double buj = bias(u, j);
			int cj = (int) itemMembershipsVector.get(j);
			double wic = W.get(i, cj);
			double cic = C.get(i, cj);
			pred += ((ruj - buj) * wic + cic) / wi;
		}
		
		// factor model prediction
		DenseVector itemFactor = Oci.row(ci);
		
		DenseVector userFactor = Ocu.row(cu);
		for (int j : Iu){
			int cj = (int) itemMembershipsVector.get(j);
			DenseVector ycj = Y.row(cj).scale(1.0 / wi);
			userFactor.add(ycj);
		}
		
		pred += itemFactor.inner(userFactor);

		return pred;
	}

	private double bias(int u, int i){
		int cu = (int) userMembershipsVector.get(u);
		int ci = (int) itemMembershipsVector.get(i);
		
		double bcu = BCu.get(cu);
		double bci = BCi.get(ci);
		double bias = globalMean + bcu + bci;
		
		return bias;
	}
	
	private void logCommunityInfo() {
		int userMemSize = userMembershipsMatrix.size();
		int itemMemSize = itemMembershipsMatrix.size();
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
