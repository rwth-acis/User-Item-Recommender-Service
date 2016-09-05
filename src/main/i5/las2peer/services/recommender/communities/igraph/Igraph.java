package i5.las2peer.services.recommender.communities.igraph;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import i5.las2peer.services.recommender.communities.igraph.IgraphLibrary.igraph_matrix_t;
import i5.las2peer.services.recommender.communities.igraph.IgraphLibrary.igraph_t;
import i5.las2peer.services.recommender.communities.igraph.IgraphLibrary.igraph_vector_t;
import i5.las2peer.services.recommender.librec.data.DenseVector;
import i5.las2peer.services.recommender.librec.data.MatrixEntry;
import i5.las2peer.services.recommender.librec.data.SparseMatrix;

public class Igraph {
	
	private igraph_t graph;
	private igraph_vector_t weights;
	
	private int numUsers;
	
	private igraph_vector_t membershipsVector;
	
	private SparseMatrix membershipsMatrix;
	
	private IgraphLibrary igraph = IgraphLibrary.INSTANCE;
	
	
	public Igraph(){
		igraph = IgraphLibrary.INSTANCE;
		
		membershipsVector = new igraph_vector_t();
		igraph.igraph_vector_init(membershipsVector, 0);
	}
	
	public void setGraph(SparseMatrix inputMatrix){
		// get the number of users in the user adjacency matrix
		numUsers = inputMatrix.numRows();
		
		// create an undirected graph with numUsers nodes
		graph = new igraph_t();
		int isDirected = 0;	// 0=undirected
		igraph.igraph_empty(graph, numUsers, isDirected);
		
		// write edges into a map to avoid duplicates and self edges
		Multimap<Integer, Integer> edgeMap = HashMultimap.create();
		for (MatrixEntry e : inputMatrix){
			int user1 = e.row();
			int user2 = e.column();
			if(!edgeMap.containsEntry(user2, user1)){
				edgeMap.put(user1, user2);
			}
		}
		
		// create an edge vector, initialize to 0 elements and reserve memory for the number of edges contained in the edge map
		igraph_vector_t edges = new igraph_vector_t();
		igraph.igraph_vector_init(edges, 0);
		int numEdges = edgeMap.size();
		igraph.igraph_vector_reserve(edges, numEdges*2);
		
		// create a weight vector
		weights = new igraph_vector_t();
		igraph.igraph_vector_init(weights, 0);
		igraph.igraph_vector_reserve(edges, numEdges);
		
		// append each edge to the vector
		for (int user1 : edgeMap.keySet()){
			for (int user2 : edgeMap.get(user1)){
				double weight = inputMatrix.get(user1, user2);
				igraph.igraph_vector_push_back(edges, user1);
				igraph.igraph_vector_push_back(edges, user2);
				igraph.igraph_vector_push_back(weights, weight);
			}
		}
		
		// add edges to the graph
		igraph.igraph_add_edges(graph, edges, null);
	}
	
	public SparseMatrix getMembershipsMatrix() {
		// fill membership information into the memberships matrix
		Table<Integer, Integer, Double> membershipsTable = HashBasedTable.create();
		Multimap<Integer, Integer> membershipsColMap = HashMultimap.create();

		int maxCommunity = 0;
		
		for (int user = 0; user < numUsers; user++){
			int community = (int) igraph.igraph_vector_e(membershipsVector, user);
			membershipsTable.put(user,community,1.0);
			membershipsColMap.put(community, user);
			if(community > maxCommunity){
				maxCommunity = community;
			}
		}
		membershipsMatrix = new SparseMatrix(numUsers, maxCommunity+1, membershipsTable, membershipsColMap);
		
		return membershipsMatrix;
	}
	
	public DenseVector getMembershipsVector() {
		// fill membership information into the memberships matrix
		DenseVector memberships = new DenseVector(numUsers);
		
		for (int user = 0; user < numUsers; user++){
			int community = (int) igraph.igraph_vector_e(membershipsVector, user);
			memberships.set(user, community);
		}
		
		return memberships;
	}
	
	public void detectCommunitiesWalktrap(int steps){
		/*
		int igraph_community_walktrap(
				  const igraph_t *graph, 
			      const igraph_vector_t *weights,
			      int steps,						// Integer constant, the length of the random walks. Paper uses t=2 and t=5.
			      igraph_matrix_t *merges,
			      igraph_vector_t *modularity, 
			      igraph_vector_t *membership);
		*
		* Complexity: O(m*n)
	 	*/
		
		igraph_matrix_t mergesMatrix = new igraph_matrix_t();
		igraph.igraph_matrix_init(mergesMatrix, 0, 0);
		
		igraph_vector_t modularityVector = new igraph_vector_t();
		igraph.igraph_vector_init(modularityVector, 0);

		igraph.igraph_community_walktrap(graph, weights, steps, mergesMatrix, modularityVector, membershipsVector);
	}
	
}
