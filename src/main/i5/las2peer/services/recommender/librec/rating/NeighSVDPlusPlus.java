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

import i5.las2peer.services.recommender.librec.data.Configuration;
import i5.las2peer.services.recommender.librec.data.DenseMatrix;
import i5.las2peer.services.recommender.librec.data.MatrixEntry;
import i5.las2peer.services.recommender.librec.data.SparseMatrix;
import i5.las2peer.services.recommender.librec.util.Strings;

/**
 * Yehuda Koren, <strong>Factorization Meets the Neighborhood: a Multifaceted Collaborative Filtering Model.</strong>,
 * KDD 2008.
 * 
 * @author guoguibing, martin
 * 
 */
@Configuration("factors, lRate, lRateN, lRateF, maxLRate, regB, regN, regU, regI, iters, boldDriver")
public class NeighSVDPlusPlus extends BiasedMF {

	protected DenseMatrix Y;
	protected DenseMatrix W,C; // weighting factors for neighborhood model

	public NeighSVDPlusPlus(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		setAlgoName("NeighSVD++");
	}

	@Override
	protected void initModel() throws Exception {
		super.initModel();

		Y = new DenseMatrix(numItems, numFactors);
		Y.init(initMean, initStd);

		W = new DenseMatrix(numItems, numItems);
		W.init(initMean, initStd);

		C = new DenseMatrix(numItems, numItems);
		C.init(initMean, initStd);

		userItemsCache = trainMatrix.rowColumnsCache(cacheSpec);
	}

	@Override
	protected void buildModel() throws Exception {

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
				double w = Math.sqrt(items.size());

				// update baseline parameters
				double bu = userBias.get(u);
				double sgd = euj - regB * bu;
				userBias.add(u, lRate * sgd);

				loss += regB * bu * bu;

				double bj = itemBias.get(j);
				sgd = euj - regB * bj;
				itemBias.add(j, lRate * sgd);

				loss += regB * bj * bj;

				// update neighborhood model parameters
				for (int k : items){	// to reduce complexity we can reduce the list of items to the nearest neighbors of item k
					double ruk = trainMatrix.get(u, k);
					double buk = globalMean + userBias.get(u) + itemBias.get(k);
					
					double wjk = W.get(j, k);
					sgd = euj * (ruk - buk) / w - regN * wjk;
					W.add(j, k, lRateN * sgd);
					loss += regN * wjk * wjk;
					
					double cjk = C.get(j, k);
					sgd = euj / w - regN * cjk;
					C.add(j, k, lRateN * sgd);
					loss += regN * cjk * cjk;
				}
				
				// update factor model parameters
				double[] sum_ys = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					double sum_f = 0;
					for (int k : items)
						sum_f += Y.get(k, f);

					sum_ys[f] = w > 0 ? sum_f / w : sum_f;
				}

				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double qjf = Q.get(j, f);

					double sgd_u = euj * qjf - regU * puf;
					double sgd_j = euj * (puf + sum_ys[f]) - regI * qjf;

					P.add(u, f, lRateF * sgd_u);
					Q.add(j, f, lRateF * sgd_j);

					loss += regU * puf * puf + regI * qjf * qjf;

					for (int k : items) {
						double ykf = Y.get(k, f);
						double delta_y = euj * qjf / w - regU * ykf;
						Y.add(k, f, lRateF * delta_y);

						loss += regU * ykf * ykf;
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
		double w = Math.sqrt(items.size());
		double buj = globalMean + userBias.get(u) + itemBias.get(j);
		
		// baseline prediction
		double pred = buj + DenseMatrix.rowMult(P, u, Q, j);
		
		// neighborhood model prediction
		for (int k : items){
			double buk = globalMean + userBias.get(u) + itemBias.get(k);
			double ruk = trainMatrix.get(u, k);
			double wjk = W.get(j, k);
			double cjk = C.get(j, k);
			pred += ((ruk - buk) * wjk + cjk) / w;
		}
		
		// factor model prediction
		for (int k : items)
			pred += DenseMatrix.rowMult(Y, k, Q, j) / w;

		return pred;
	}
	
	@Override
	public String toString() {
		return Strings.toString(new Object[] { numFactors, initLRate, initLRateN, initLRateF, maxLRate, regB, regN, regU, regI, numIters,
				isBoldDriver});
	}

}
