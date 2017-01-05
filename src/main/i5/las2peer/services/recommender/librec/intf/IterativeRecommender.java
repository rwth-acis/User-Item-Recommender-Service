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

package i5.las2peer.services.recommender.librec.intf;

import i5.las2peer.services.recommender.librec.data.Configuration;
import i5.las2peer.services.recommender.librec.data.DenseMatrix;
import i5.las2peer.services.recommender.librec.data.DenseVector;
import i5.las2peer.services.recommender.librec.data.SparseMatrix;
import i5.las2peer.services.recommender.librec.util.FileIO;
import i5.las2peer.services.recommender.librec.util.LineConfiger;
import i5.las2peer.services.recommender.librec.util.Logs;
import i5.las2peer.services.recommender.librec.util.Strings;

/**
 * Recommenders using iterative learning techniques
 * 
 * @author guoguibing
 * 
 */
@Configuration("factors, lRate, maxLRate, regB, regU, regI, iters, boldDriver")
public abstract class IterativeRecommender extends Recommender {

	/************************************ Static parameters for all recommenders ***********************************/
	// init, maximum learning rate, momentum
	protected static float initLRate, initLRateN, initLRateF, initLRateMu, initLRateC, initLRateCN, initLRateCF, maxLRate, momentum;
	// line configer for regularization parameters
	protected static LineConfiger regOptions;
	// user, item and bias regularization
	protected static float reg, regB, regU, regI, regN, regC, regCN, regCF;
	// number of factors
	protected static int numFactors;
	// number of iterations
	protected static int numIters;

	// whether to adjust learning rate automatically
	protected static boolean isBoldDriver;
	// decay of learning rate
	protected static float decay;

	// indicator of static field initialization
	public static boolean resetStatics = true;
	
	// perform evaluation on the test set after each learning iteration
	protected static boolean isEvalIter = false;

	/************************************ Recommender-specific parameters ****************************************/
	// factorized user-factor matrix
	protected DenseMatrix P;

	// factorized item-factor matrix
	protected DenseMatrix Q;

	// user biases
	protected DenseVector userBias;
	// item biases
	protected DenseVector itemBias;

	// adaptive learn rate
	protected double lRate, lRateN, lRateF, lRateMu, lRateC, lRateCN, lRateCF;
	// objective loss
	protected double loss, last_loss = 0;
	// predictive measure
	protected double measure, last_measure = 0;

	// initial models using normal distribution
	protected boolean initByNorm;

	public IterativeRecommender(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		// initialization 
		if (resetStatics) {
			resetStatics = false;

			LineConfiger lc = cf.getParamOptions("learn.rate");
			if (lc != null) {
				initLRate = Float.parseFloat(lc.getMainParam());
				initLRateN = lc.getFloat("-n", initLRate);
				initLRateF = lc.getFloat("-f", initLRate);
				initLRateMu = lc.getFloat("-mu", initLRate);
				initLRateC = lc.getFloat("-c", initLRate);
				initLRateCN = lc.getFloat("-cn", initLRate);
				initLRateCF = lc.getFloat("-cf", initLRate);
				maxLRate = lc.getFloat("-max", -1);
				isBoldDriver = lc.contains("-bold-driver");
				decay = lc.getFloat("-decay", -1);
				momentum = lc.getFloat("-momentum", 50);
			}

			regOptions = cf.getParamOptions("reg.lambda");
			if (regOptions != null) {
				reg = Float.parseFloat(regOptions.getMainParam());
				regU = regOptions.getFloat("-u", reg);
				regI = regOptions.getFloat("-i", reg);
				regB = regOptions.getFloat("-b", reg);
				regN = regOptions.getFloat("-n", reg);
				regC = regOptions.getFloat("-c", reg);
				regCN = regOptions.getFloat("-cn", reg);
				regCF = regOptions.getFloat("-cf", reg);
			}

			numFactors = cf.getInt("num.factors", 10);
			numIters = cf.getInt("num.max.iter", 100);
		}

		// method-specific settings
		lRate = initLRate;
		lRateN = initLRateN;
		lRateF = initLRateF;
		lRateMu = initLRateMu;
		lRateC = initLRateC;
		lRateCN = initLRateCN;
		lRateCF = initLRateCF;
		initByNorm = true;
		
		// evaluation after each learning iteration
		isEvalIter = cf.isOn("eval.iter", false);
	}

	/**
	 * default prediction method
	 */
	@Override
	protected double predict(int u, int j) throws Exception {
		return DenseMatrix.rowMult(P, u, Q, j);
	}

	/**
	 * Post each iteration, we do things:
	 * 
	 * <ol>
	 * <li>print debug information</li>
	 * <li>check if converged</li>
	 * <li>if not, adjust learning rate</li>
	 * </ol>
	 * 
	 * @param iter
	 *            current iteration
	 * 
	 * @return boolean: true if it is converged; false otherwise
	 * @throws Exception on file I/O errors
	 * 
	 */
	protected boolean isConverged(int iter) throws Exception {

		float delta_loss = (float) (last_loss - loss);

		if (earlyStopMeasure != null) {
			if (isRankingPred){
				earlyStopMeasure = Measure.Loss;
			}

			switch (earlyStopMeasure) {
			case Loss:
				measure = loss;
				last_measure = last_loss;
				break;

			default:
				boolean flag = isResultsOut;
				isResultsOut = false; // to stop outputs
				measure = evalRatings().get(earlyStopMeasure);
				isResultsOut = flag; // recover the flag
				break;
			}
		}

		float delta_measure = (float) (last_measure - measure);

		// print out debug info
		if (verbose) {
			String learnRate = lRate > 0 ? ", learn_rate = " + (float) lRate : "";

			String earlyStop = "";
			if (earlyStopMeasure != null && earlyStopMeasure != Measure.Loss) {
				earlyStop = String.format(", %s = %.6f, delta_%s = %.6f", new Object[] { earlyStopMeasure,
						(float) measure, earlyStopMeasure, delta_measure });
			}
			
			Logs.info("{}{} iter {}: loss = {}, delta_loss = {}{}{}", new Object[] { algoName, foldInfo, iter,
						(float) loss, delta_loss, earlyStop, learnRate });
		}

		if (Double.isNaN(loss) || Double.isInfinite(loss)) {
			Logs.error("Loss = {}: current settings does not fit the recommender! Change the settings and try again!", loss);
			System.exit(-1);
		}

		// check if converged
		boolean cond1 = Math.abs(loss) < 1e-5;
		boolean cond2 = (delta_measure > 0) && (delta_measure < 1e-5);
		boolean converged = cond1 || cond2;

		// if not converged, update learning rate
		if (!converged)
			updateLRate(iter);

		last_loss = loss;
		last_measure = measure;

		return converged;
	}

	/**
	 * Update current learning rate after each epoch <br>
	 * 
	 * <ol>
	 * <li>bold driver: Gemulla et al., Large-scale matrix factorization with distributed stochastic gradient descent,
	 * KDD 2011.</li>
	 * <li>constant decay: Niu et al, Hogwild!: A lock-free approach to parallelizing stochastic gradient descent, NIPS
	 * 2011.</li>
	 * <li>Leon Bottou, Stochastic Gradient Descent Tricks</li>
	 * <li>more ways to adapt learning rate can refer to: http://www.willamette.edu/~gorr/classes/cs449/momrate.html</li>
	 * </ol>
	 * 
	 * @param iter
	 *            the current iteration
	 */
	protected void updateLRate(int iter) {

		if (lRate <= 0 || lRateN <= 0 || lRateF <= 0 || lRateC <= 0 || lRateCN <= 0 || lRateCF <= 0)
			return;

		if (isBoldDriver && iter > 1){
			lRate = Math.abs(last_loss) > Math.abs(loss) ? lRate * 1.05 : lRate * 0.5;
			lRateN = Math.abs(last_loss) > Math.abs(loss) ? lRateN * 1.05 : lRateN * 0.5;
			lRateF = Math.abs(last_loss) > Math.abs(loss) ? lRateF * 1.05 : lRateF * 0.5;
			lRateMu = Math.abs(last_loss) > Math.abs(loss) ? lRateMu * 1.05 : lRateMu * 0.5;
			lRateC = Math.abs(last_loss) > Math.abs(loss) ? lRateC * 1.05 : lRateC * 0.5;
			lRateCN = Math.abs(last_loss) > Math.abs(loss) ? lRateCN * 1.05 : lRateCN * 0.5;
			lRateCF = Math.abs(last_loss) > Math.abs(loss) ? lRateCF * 1.05 : lRateCF * 0.5;
		}
		else if (decay > 0 && decay < 1){
			lRate *= decay;
			lRateN *= decay;
			lRateF *= decay;
			lRateMu *= decay;
			lRateC *= decay;
			lRateCN *= decay;
			lRateCF *= decay;
		}

		// limit to max-learn-rate after update
		if (maxLRate > 0 && lRate > maxLRate)
			lRate = maxLRate;
		if (maxLRate > 0 && lRateN > maxLRate)
			lRateN = maxLRate;
		if (maxLRate > 0 && lRateF > maxLRate)
			lRateF = maxLRate;
		if (maxLRate > 0 && lRateMu > maxLRate)
			lRateMu = maxLRate;
		if (maxLRate > 0 && lRateC > maxLRate)
			lRateC = maxLRate;
		if (maxLRate > 0 && lRateCN > maxLRate)
			lRateCN = maxLRate;
		if (maxLRate > 0 && lRateCF > maxLRate)
			lRateCF = maxLRate;
	}

	@Override
	protected void initModel() throws Exception {

		P = new DenseMatrix(numUsers, numFactors);
		Q = new DenseMatrix(numItems, numFactors);

		// initialize model
		if (initByNorm) {
			P.init(initMean, initStd);
			Q.init(initMean, initStd);
		} else {
			P.init(); // P.init(smallValue);
			Q.init(); // Q.init(smallValue);
		}

	}

	protected void saveModel() throws Exception {
		// make a folder
		String dirPath = FileIO.makeDirectory(tempDirPath, algoName);

		// suffix info
		String suffix = foldInfo + ".bin";

		// writing training, test data
		FileIO.serialize(trainMatrix, dirPath + "trainMatrix" + suffix);
		FileIO.serialize(testMatrix, dirPath + "testMatrix" + suffix);

		// write matrices P, Q
		FileIO.serialize(P, dirPath + "userFactors" + suffix);
		FileIO.serialize(Q, dirPath + "itemFactors" + suffix);

		// write vectors
		if (userBias != null)
			FileIO.serialize(userBias, dirPath + "userBiases" + suffix);
		if (itemBias != null)
			FileIO.serialize(itemBias, dirPath + "itemBiases" + suffix);

		Logs.debug("Learned models are saved to folder \"{}\"", dirPath);
	}

	protected void loadModel() throws Exception {
		// make a folder
		String dirPath = FileIO.makeDirectory(tempDirPath, algoName);

		Logs.debug("A recommender model is loaded from {}", dirPath);

		// suffix info
		String suffix = foldInfo + ".bin";

		trainMatrix = (SparseMatrix) FileIO.deserialize(dirPath + "trainMatrix" + suffix);
		testMatrix = (SparseMatrix) FileIO.deserialize(dirPath + "testMatrix" + suffix);

		// write matrices P, Q
		P = (DenseMatrix) FileIO.deserialize(dirPath + "userFactors" + suffix);
		Q = (DenseMatrix) FileIO.deserialize(dirPath + "itemFactors" + suffix);

		// write vectors
		userBias = (DenseVector) FileIO.deserialize(dirPath + "userBiases" + suffix);
		itemBias = (DenseVector) FileIO.deserialize(dirPath + "itemBiases" + suffix);
	}

	@Override
	public String toString() {
		return Strings.toString(new Object[] { numFactors, initLRate, maxLRate, regB, regU, regI, numIters,
				isBoldDriver });
	}

}
