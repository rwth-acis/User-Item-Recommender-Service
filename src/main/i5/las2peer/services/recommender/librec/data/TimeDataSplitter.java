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

package i5.las2peer.services.recommender.librec.data;

import java.util.concurrent.TimeUnit;

import i5.las2peer.services.recommender.librec.util.Logs;

/**
 * Class to split/sample rating matrix, appropriate for time-aware recommenders
 * 
 * @author guoguibing, martin
 * 
 */
public class TimeDataSplitter {

	// [row-id, col-id, rate]
	private SparseMatrix rateMatrix;
	private SparseMatrix timeMatrix;

	// number of folds
	private int numFold;
	
	private long[] startTimestamp;
	private long[] endTimestamp;
	private long[] splitTimestamp;

	/**
	 * Construct a data splitter to split a given matrix into kfolds
	 * 
	 * @param rateMatrix
	 *            matrix containing the ratings 
	 * @param timeMatrix
	 *            matrix containing the timestamps of the ratings 
	 * @param kfold
	 *            number of folds to split the data into
	 * @param trainRatio
	 *            ratio of training data, e.g. ratio of 0.8 means a training/test split of 80/20 (time-based)
	 * @param foldSize
	 *            portion of the dataset used for each fold
	 */
	public TimeDataSplitter(SparseMatrix rateMatrix, SparseMatrix timeMatrix, int kfold, double trainRatio, double foldSize) {
		this.rateMatrix = rateMatrix;
		this.timeMatrix = timeMatrix;

		splitFolds(kfold, trainRatio, foldSize);
	}

	/**
	 * Construct a data splitter with data source of a given rate matrix
	 * 
	 * @param rateMatrix
	 *            matrix containing the ratings 
	 * @param timeMatrix
	 *            matrix containing the timestamps of the ratings 
	 */
	public TimeDataSplitter(SparseMatrix rateMatrix, SparseMatrix timeMatrix) {
		this.rateMatrix = rateMatrix;
		this.timeMatrix = timeMatrix;
	}

	/**
	 * Split ratings into k-fold.
	 * 
	 * @param kfold
	 *            number of folds
	 * @param trainRatio
	 *            ratio of training data, e.g. ratio of 0.8 means a training/test split of 80/20 (time-based)
	 * @param foldSize
	 *            portion of the dataset used for each fold
	 */
	private void splitFolds(int kfold, double trainRatio, double foldSize) {
		assert kfold > 0;
		
		numFold = kfold;
		startTimestamp = new long[kfold];
		endTimestamp = new long[kfold];
		splitTimestamp = new long[kfold];
		
		// Find min and max timestamps
		long minTimestamp = Long.MAX_VALUE;
		long maxTimestamp = Long.MIN_VALUE;
		for(MatrixEntry e : rateMatrix){
			long timestamp = (long) timeMatrix.get(e.row(), e.column());
			if (minTimestamp > timestamp)
				minTimestamp = timestamp;
			if (maxTimestamp < timestamp)
				maxTimestamp = timestamp;
		}
		
		long timeRange = maxTimestamp - minTimestamp;
		long foldLength = (long) (foldSize * timeRange);
		
		// earliest and latest possible fold start timestamps
		long earliestStart = minTimestamp;
		long latestStart = maxTimestamp - foldLength;
		
		if (kfold == 1){
			startTimestamp[0] = latestStart;
			endTimestamp[0] = maxTimestamp;
			splitTimestamp[0] = startTimestamp[0] + (long) (trainRatio * foldLength);
		}
		else{
			// time between fold start timestamps
			long foldStep = (latestStart - earliestStart) / (kfold - 1);
			
			// start, end and split timestamps for each fold
			for (int i = 0; i < kfold; i++){
				startTimestamp[i] = earliestStart + i * foldStep;
				endTimestamp[i] = startTimestamp[i] + foldLength;
				splitTimestamp[i] = startTimestamp[i] + (long) (trainRatio * foldLength);
			}
		}
		
	}

	/**
	 * Return the k-th fold as test set (testMatrix), making all the others as train set in rateMatrix.
	 * 
	 * @param k
	 *            The index for desired fold.
	 * @return Rating matrices {k-th train data, k-th test data}
	 */
	public SparseMatrix[] getKthFold(int k) {
		if (k > numFold || k < 1)
			return null;

		SparseMatrix trainMatrix = new SparseMatrix(rateMatrix);
		SparseMatrix testMatrix = new SparseMatrix(rateMatrix);
		
		for (int u = 0, um = rateMatrix.numRows(); u < um; u++) {

			SparseVector items = rateMatrix.row(u);

			for (int j : items.getIndex()) {
				long ratingTime = (long) timeMatrix.get(u,  j); 
				if (ratingTime < startTimestamp[k-1] || ratingTime > splitTimestamp[k-1])
					trainMatrix.set(u, j, 0.0); // not in train time range, remove train data
				if (ratingTime <= splitTimestamp[k-1] || ratingTime > endTimestamp[k-1])
					testMatrix.set(u, j, 0.0); // not in test time range, remove test data
			}
		}
		
		// remove zero entries
		SparseMatrix.reshape(trainMatrix);
		SparseMatrix.reshape(testMatrix);

		debugInfo(trainMatrix, testMatrix, k);

		return new SparseMatrix[] { trainMatrix, testMatrix };
	}

	/**
	 * print out debug information
	 */
	private void debugInfo(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		// TODO
		String foldInfo = fold > 0 ? "Fold [" + fold + "]: " : "";
		int trainDays =  (int) TimeUnit.MILLISECONDS.toDays(splitTimestamp[fold-1] - startTimestamp[fold-1]);
		int testDays =  (int) TimeUnit.MILLISECONDS.toDays(endTimestamp[fold-1] - splitTimestamp[fold-1]);
		int trainSamples = trainMatrix.size();
		int testSamples = testMatrix.size();
		int trainRatio = (int) Math.round((double) trainSamples / (trainSamples+testSamples) * 100);
		int testRatio = (int) Math.round((double) testSamples / (trainSamples+testSamples) * 100);
		Logs.info("{}train days: {}, train amount: {}, test days: {}, test amount: {}, train/test ratio: {}/{}",
				foldInfo, trainDays, trainSamples, testDays, testSamples, trainRatio, testRatio);
	}
}
