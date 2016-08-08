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

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collections;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import i5.las2peer.services.recommender.librec.util.Dates;
import i5.las2peer.services.recommender.librec.util.FileIO;
import i5.las2peer.services.recommender.librec.util.Logs;
import i5.las2peer.services.recommender.librec.util.Strings;

/**
 * A data access object (DAO) to a data file
 * 
 * @author guoguibing
 * 
 */
public class CSVDataDAO extends DataDAO{

	public CSVDataDAO(String path, BiMap<String, Integer> userIds, BiMap<String, Integer> itemIds) {
		super(path, userIds, itemIds);
	}

	public CSVDataDAO(String path) {
		super(path);
	}

	public CSVDataDAO(String path, BiMap<String, Integer> userIds) {
		super(path, userIds);
	}

	/**
	 * Default relevant columns {0: user column, 1: item column, 2: rate column}; default recommendation task is rating
	 * prediction;
	 * 
	 * 
	 * @return a sparse matrix storing all the relevant data
	 */
	public SparseMatrix[] readData() throws Exception {
		return readData(new int[] { 0, 1, 2 }, -1);
	}

	/**
	 * Default relevant columns {0: user column, 1: item column, 2: rate column}, default recommendation task is rating prediction;
	 * 
	 * @param binThold
	 *            the threshold to binarize a rating. If a rating is greater than the threshold, the value will be 1;
	 *            otherwise 0. To disable this feature, i.e., keep the original rating value, set the threshold a
	 *            negative value
	 * @return a sparse matrix storing all the relevant data
	 */
	public SparseMatrix[] readData(double binThold) throws Exception {
		return readData(new int[] { 0, 1, 2 }, binThold);
	}

	/**
	 * Read data from the data file. Note that we didn't take care of the duplicated lines.
	 * 
	 * @param cols
	 *            the indexes of the relevant columns in the data file: {user, item, [rating, timestamp] (optional)}
	 * @param binThold
	 *            the threshold to binarize a rating. If a rating is greater than the threshold, the value will be 1;
	 *            otherwise 0. To disable this feature, i.e., keep the original rating value, set the threshold a
	 *            negative value
	 * @return a sparse matrix storing all the relevant data
	 */
	public SparseMatrix[] readData(int[] cols, double binThold) throws Exception {

		Logs.info(String.format("Dataset: %s", Strings.last(dataPath, 38)));

		// Table {row-id, col-id, rate}
		Table<Integer, Integer, Double> dataTable = HashBasedTable.create();
		// Table {row-id, col-id, timestamp}
		Table<Integer, Integer, Long> timeTable = null;
		// Map {col-id, multiple row-id}: used to fast build a rating matrix
		Multimap<Integer, Integer> colMap = HashMultimap.create();

		BufferedReader br = FileIO.getReader(dataPath);
		String line = null;
		minTimestamp = Long.MAX_VALUE;
		maxTimestamp = Long.MIN_VALUE;
		while ((line = br.readLine()) != null) {
			if (isHeadline()) {
				setHeadline(false);
				continue;
			}

			String[] data = line.trim().split("[ \t,]+");
			
			if(data.length < 2){
				Logs.error(String.format("Dataset: Cannot read line \"%s\"", line));
				continue;
			}
			
			String user = data[cols[0]];
			String item = data[cols[1]];
			Double rate = (cols.length >= 3 && data.length >= 3) ? Double.valueOf(data[cols[2]]) : 1.0;

			// binarize the rating for item recommendation task
			if (binThold >= 0)
				rate = rate > binThold ? 1.0 : 0.0;

			scaleDist.add(rate);

			// inner id starting from 0
			int row = userIds.containsKey(user) ? userIds.get(user) : userIds.size();
			userIds.put(user, row);

			int col = itemIds.containsKey(item) ? itemIds.get(item) : itemIds.size();
			itemIds.put(item, col);

			dataTable.put(row, col, rate);
			colMap.put(col, row);

			// record rating's issuing time
			if (cols.length >= 4 && data.length >= 4) {
				if (timeTable == null)
					timeTable = HashBasedTable.create();

				// convert to million-seconds
				long mms = 0L;
				try {
					mms = Long.parseLong(data[cols[3]]); // cannot format "9.7323480e+008"
				} catch (NumberFormatException e) {
					mms = (long) Double.parseDouble(data[cols[3]]);
				}
				long timestamp = timeUnit.toMillis(mms);

				if (minTimestamp > timestamp)
					minTimestamp = timestamp;

				if (maxTimestamp < timestamp)
					maxTimestamp = timestamp;

				timeTable.put(row, col, timestamp);
			}

		}
		br.close();

		numRatings = scaleDist.size();
		ratingScale = new ArrayList<>(scaleDist.elementSet());
		Collections.sort(ratingScale);

		int numRows = numUsers(), numCols = numItems();

		// if min-rate = 0.0, shift upper a scale
		double minRate = ratingScale.get(0).doubleValue();
		double epsilon = minRate == 0.0 ? ratingScale.get(1).doubleValue() - minRate : 0;
		if (epsilon > 0) {
			// shift upper a scale
			for (int i = 0, im = ratingScale.size(); i < im; i++) {
				double val = ratingScale.get(i);
				ratingScale.set(i, val + epsilon);
			}
			// update data table
			for (int row = 0; row < numRows; row++) {
				for (int col = 0; col < numCols; col++) {
					if (dataTable.contains(row, col))
						dataTable.put(row, col, dataTable.get(row, col) + epsilon);
				}
			}
		}

		String dateRange = "";
		if (cols.length >= 4)
			dateRange = String.format(", Timestamps = {%s, %s}", Dates.toString(minTimestamp),
					Dates.toString(maxTimestamp));

		Logs.debug("With Specs: {Users, {}} = {{}, {}, {}}, Scale = {{}}{}", (isItemAsUser ? "Users, Links"
				: "Items, Ratings"), numRows, numCols, numRatings, Strings.toString(ratingScale), dateRange);

		// build rating matrix
		rateMatrix = new SparseMatrix(numRows, numCols, dataTable, colMap);

		if (timeTable != null)
			timeMatrix = new SparseMatrix(numRows, numCols, timeTable, colMap);

		// release memory of data table
		dataTable = null;
		timeTable = null;

		return new SparseMatrix[] { rateMatrix, timeMatrix };
	}

}
