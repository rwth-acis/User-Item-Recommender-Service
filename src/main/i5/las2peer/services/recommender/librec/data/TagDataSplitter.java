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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import i5.las2peer.services.recommender.librec.util.Logs;

/**
 * Class to split/sample rating matrix, appropriate for time-aware recommenders
 * 
 * @author guoguibing, martin
 * 
 */
public class TagDataSplitter {

	// [row-id, col-id, rate]
	private SparseMatrix timeMatrix;
	
	// tables of user and item taggings including timestamps
	private Table<Integer, Integer, Set<Long>> userTagTable, itemTagTable;

	// number of folds
	private int numFold;
	
	private long[] startTimestamp;
	private long[] endTimestamp;
	private long[] splitTimestamp;
	
	private List<Table<Integer,Integer,Set<Long>>> trainUserTagTable;
	private List<Table<Integer, Integer, Set<Long>>> trainItemTagTable;


	/**
	 * Construct a data splitter to split given tagging tables into kfolds
	 * 
	 * @param userTagTable
	 *            table containing tags given by each user
	 * @param itemTagTable
	 *            table containing tags given to each item
	 * @param timeMatrix
	 *            time matrix
	 * @param kfold
	 *            number of folds to split the data into
	 * @param trainRatio
	 *            ratio of training data, e.g. ratio of 0.8 means a training/test split of 80/20 (time-based)
	 * @param foldSize
	 *            portion of the dataset used for each fold
	 */
	public TagDataSplitter(Table<Integer, Integer, Set<Long>> userTagTable, Table<Integer,
			Integer, Set<Long>> itemTagTable, SparseMatrix timeMatrix, int kfold, double trainRatio, double foldSize) {
		this.userTagTable = userTagTable;
		this.itemTagTable = itemTagTable;
		this.timeMatrix = timeMatrix;

		splitFolds(kfold, trainRatio, foldSize);
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

		// Find min and max timestamps
		startTimestamp = new long[kfold];
		endTimestamp = new long[kfold];
		splitTimestamp = new long[kfold];
		
		long minTimestamp = Long.MAX_VALUE;
		long maxTimestamp = Long.MIN_VALUE;
		for(MatrixEntry e : timeMatrix){
			long timestamp = (long) e.get();
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
		
		// initialize training tables for each fold
		trainUserTagTable = new ArrayList<Table<Integer, Integer, Set<Long>>>(numFold);
		trainItemTagTable = new ArrayList<Table<Integer, Integer, Set<Long>>>(numFold);
		for (int i = 0; i < kfold; i++){
			trainUserTagTable.add(i, HashBasedTable.create());
			trainItemTagTable.add(i, HashBasedTable.create());
		}
		
		// split user tagging data
		Set<Cell<Integer, Integer, Set<Long>>> userTaggingCells = userTagTable.cellSet();
		for (Cell<Integer, Integer, Set<Long>> c: userTaggingCells){
			int user = c.getRowKey();
			int tag = c.getColumnKey();
			Set<Long> times = c.getValue();
			for (long time : times){
				for (int fold = 0; fold < numFold; fold++){
					if (time >= startTimestamp[fold] && time <= splitTimestamp[fold]){
						if (!trainUserTagTable.get(fold).contains(user, tag)){
							trainUserTagTable.get(fold).put(user, tag, new HashSet<Long>());
						}
						trainUserTagTable.get(fold).get(user, tag).add(time);
					}
				}
			}
		}

		// split item tagging data
		Set<Cell<Integer, Integer, Set<Long>>> itemTaggingCells = itemTagTable.cellSet();
		for (Cell<Integer, Integer, Set<Long>> c: itemTaggingCells){
			int item = c.getRowKey();
			int tag = c.getColumnKey();
			Set<Long> times = c.getValue();
			for (long time : times){
				for (int fold = 0; fold < numFold; fold++){
					if (time >= startTimestamp[fold] && time <= splitTimestamp[fold]){
						if (!trainItemTagTable.get(fold).contains(item, tag)){
							trainItemTagTable.get(fold).put(item, tag, new HashSet<Long>());
						}
						trainItemTagTable.get(fold).get(item, tag).add(time);
					}
				}
			}
		}
		debugInfo();
	}
	
	/**
	 * Return the user tagging data that falls into the k-th fold training time range.
	 * 
	 * @param k
	 * 		The index for desired fold, numbered 1..numFold.
	 * @return table containing the user tagging data that falls into the k-th fold training time range.
	 */
	public Table<Integer, Integer, Set<Long>> getKthFoldUserTagTable(int k) {
		if (k > numFold || k < 1)
			return null;
		return trainUserTagTable.get(k-1);
	}

	/**
	 * Return the item tagging data that falls into the k-th fold training time range.
	 * 
	 * @param k
	 * 		The index for desired fold, numbered 1..numFold.
	 * @return table containing the item tagging data that falls into the k-th fold training time range.
	 */
	public Table<Integer, Integer, Set<Long>> getKthFoldItemTagTable(int k) {
		if (k > numFold || k < 1)
			return null;
		return trainItemTagTable.get(k-1);
	}

	/**
	 * print out debug information
	 */
	private void debugInfo() {
		for (int fold = 0; fold < numFold; fold++){
			String foldInfo = "Tagging data for training fold [" + (fold+1) + "]:";
			int userTags = trainUserTagTable.get(fold).size();
			int itemTags = trainItemTagTable.get(fold).size();
			int trainDays =  (int) TimeUnit.MILLISECONDS.toDays(splitTimestamp[fold] - startTimestamp[fold]);
			Logs.info("{} train days: {}, number of user tags: {}, number of item tags: {}", foldInfo, trainDays, userTags, itemTags);
		}
	}
}
