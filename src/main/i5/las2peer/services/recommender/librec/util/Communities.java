package i5.las2peer.services.recommender.librec.util;

import java.util.HashSet;
import java.util.List;

import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import i5.las2peer.services.recommender.librec.data.SparseMatrix;
import i5.las2peer.services.recommender.librec.data.SparseVector;
import i5.las2peer.services.recommender.librec.data.VectorEntry;

public class Communities {
	private static String cacheSpec = "maximumSize=200,expireAfterAccess=2m";

	public static SparseMatrix limitOverlappingCommunities(SparseMatrix membershipsMatrix, int k){
		int numNodes = membershipsMatrix.numRows();
		int numCommunities = membershipsMatrix.numColumns();
		
		Table<Integer, Integer, Double> membershipsTable = HashBasedTable.create();
		Multimap<Integer, Integer> membershipsColMap = HashMultimap.create();
		
		for (int node = 0; node < numNodes; node++){
			// get vector containing the node's community membership levels (vector size: numCommunities)
			SparseVector communitiesVector = membershipsMatrix.row(node);
			
			// store the node's top n communities with community ID and membership level
			int[] topNCommunities = new int[k];
			for (int i = 0; i < k; i++){
				topNCommunities[i] = -1;
			}
			
			// position in the topNCommunities array that holds the community with the node's lowest membership level
			int minLevelPos = 0;
			// node's lowest membership level
			double minLevel = -1;
			
			for (VectorEntry entry : communitiesVector){
				double level = entry.get();
				
				if (level > minLevel){
					// add this community into the top n
					int community = entry.index();
					topNCommunities[minLevelPos] = community;
					
					// find new minLevelPos
					minLevel = level;
					for (int i = 0; i < k; i++){
						if (topNCommunities[i] < minLevel){
							minLevel = topNCommunities[i];
							minLevelPos = i;
						}
					}
				}
			}
			
			// fill the memberships table with only the node's top n communities
			for (int i = 0; i < k; i++){
				if (topNCommunities[i] >= 0){
					int community = topNCommunities[i];
					double level = membershipsMatrix.get(node, community);
					membershipsTable.put(node, community, level);
					membershipsColMap.put(community, node);
				}
			}
		}
		
		// build new memberships matrix from the memberships table
		return new SparseMatrix(numNodes, numCommunities, membershipsTable, membershipsColMap);
	}
	
	public static SparseMatrix userCommunitiesRatings(SparseMatrix userMemberships, SparseMatrix trainMatrix, int k) throws Exception {
		int numUsers = trainMatrix.numRows();
		int numItems = trainMatrix.numColumns();
		int numUserCommunities = userMemberships.numColumns();
		
		LoadingCache<Integer, List<Integer>> userItemsCache = trainMatrix.rowColumnsCache(cacheSpec);
		LoadingCache<Integer, List<Integer>> userCommunitiesCache = userMemberships.rowColumnsCache(cacheSpec);
		
		// Get the average community ratings for each item
		Table<Integer, Integer, Double> communityRatingsTable = HashBasedTable.create();
		for (int community = 0; community < numUserCommunities; community++){
			// each user's membership level for the community
			SparseVector communityUsersVector = userMemberships.column(community);
			// build set of items that have been rated by members of the community
			HashSet<Integer> items = new HashSet<Integer> ();
			for (VectorEntry e : communityUsersVector){
				int user = e.index();
				List<Integer> userItems = userItemsCache.get(user);
				for (int item : userItems)
					items.add(item);
			}
			for (int item : items){
				// Sum of ratings given by users of the community to item, weighted by the users community membership levels
				double ratingsSum = 0;
				// sum of membership levels of the users that have rated the item, used for normalization
				double membershipsSum = 0;
				// each user's rating for the item
				SparseVector itemUsersVector = trainMatrix.column(item);
				for (VectorEntry e : communityUsersVector){
					int user = e.index();
					if (itemUsersVector.contains(user)){
						double userMembership = communityUsersVector.get(user);
						double userRating = itemUsersVector.get(user);
						ratingsSum += userRating * userMembership;
						membershipsSum += userMembership;
					}
				}
				if (membershipsSum > 0){
					double communityRating = ratingsSum / membershipsSum;
					communityRatingsTable.put(community, item, communityRating);
				}
			}
		}
		SparseMatrix communityRatingsMatrix = new SparseMatrix(numUserCommunities, numItems, communityRatingsTable);
		
		// Get each user's community ratings, i.e. the weighted average rating of the user's communities for each item
		// The resulting matrix has dimensions numUsers x numItems
		
	    Table<Integer, Integer, Double> userCommunitiesRatingsTable = HashBasedTable.create();
		
		for (int user = 0; user < numUsers; user++){
			List<Integer> userCommunities = userCommunitiesCache.get(user);
			int[] topKItems = new int[k];
			double[] topKItemsMemberships = new double[k];
			double[] topKItemsRatings = new double[k];
			for (int i = 0; i < k; i++){
				topKItems[i] = -1;
			}
			// position of the item with the lowest membership level in the top-k array
			int minItemPos = 0;
			// membership level of that item
			double minMembership = 0;
			for (int item = 0; item < numItems; item++){
				double ratingsSum = 0;
				double membershipsSum = 0;
				for (int community : userCommunities){
					double communityRating = communityRatingsMatrix.get(community, item);
					if (communityRating > 0){
						double userMembership = userMemberships.get(user, community);
						ratingsSum += communityRating * userMembership;
						membershipsSum += userMembership;
					}
				}
				if (ratingsSum > 0 && membershipsSum > minMembership){
					topKItems[minItemPos] = item;
					topKItemsMemberships[minItemPos] = membershipsSum;
					topKItemsRatings[minItemPos] = ratingsSum;
					// find item with lowest membership level in the array
					minMembership = membershipsSum;
					for (int i = 0; i < k; i++){
						if (topKItemsMemberships[i] < minMembership){
							minItemPos = i;
							minMembership = topKItemsMemberships[i];
						}
					}
				}
			}
			// fill top-k items into table
			for (int i = 0; i < k; i++){
				if (topKItems[i] >= 0){
					int item = topKItems[i];
					double userCommunitiesRating = topKItemsRatings[i] / topKItemsMemberships[i];
					userCommunitiesRatingsTable.put(user, item, userCommunitiesRating);
				}
			}
		}
		
		return new SparseMatrix(numUsers, numItems, userCommunitiesRatingsTable);
	}
}
