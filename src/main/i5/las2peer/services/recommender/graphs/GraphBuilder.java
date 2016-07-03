package i5.las2peer.services.recommender.graphs;


import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import librec.data.SparseMatrix;
import librec.data.VectorEntry;

public class GraphBuilder {
	private int k = 10;
	private int mu = 300;
	private SimilarityMeasure similarity = SimilarityMeasure.COSINE_SIMILARITY;
	private SparseMatrix ratingsMatrix;
	private SparseMatrix taggingsMatrix;
	int numUsers;
	int numItems;
	private SparseMatrix userAdjMatrix;
	private SparseMatrix itemAdjMatrix;
	private int graphTime;
	
	public enum SimilarityMeasure{
		PEARSON_CORRELATION, COSINE_SIMILARITY
	}
	
	public void setK(int k){
		this.k = k;
	}
	
	public void setSimilarityMeasure(SimilarityMeasure sim){
		similarity = sim;
	}
	
	public void setRatingData(SparseMatrix ratingsMatrix){
		this.ratingsMatrix = ratingsMatrix;
		numUsers = ratingsMatrix.numRows();
		numItems = ratingsMatrix.numColumns();
	}
	
	public void setTaggingData(SparseMatrix taggingsMatrix){
		this.taggingsMatrix = taggingsMatrix;
		numUsers = ratingsMatrix.numRows();
		numItems = ratingsMatrix.numColumns();
	}
	
	public void buildGraphs(){
		Stopwatch sw = Stopwatch.createStarted();
		if(taggingsMatrix != null){
			buildGraphsFromTaggings();
		}
		else if (ratingsMatrix != null){
			buildGraphsFromRatings();
		}
		sw.stop();
		graphTime = (int) sw.elapsed(TimeUnit.SECONDS);
	}
	
	private void buildGraphsFromRatings() {
		// Intermediate table to create the user graph (adjacency matrix) from
		Table<Integer, Integer, Double> adjacencyTable = HashBasedTable.create();
		// Column map to allow compressed row and column storage
		Multimap<Integer, Integer> adjacencyColMap = HashMultimap.create();
		
		// Construct GFSparseMatrix from ratings matrix and compute TF-IDF value for each element
		GFSparseMatrix userTfidfMatrix = getUserTfidfMatrix();
		
		GFSparseMatrix itemTfidfMatrix = getItemTfidfMatrix();
		
		
		// Run KNNGraphConstruction - kNNGraphConstruction(SparseMatrix inputMatrix, double[][] kNNGraph, int k, int mu)
		double[][] kNNGraph = new double[numUsers][];
		for (int i=0; i<numUsers; i++) {
			kNNGraph[i] = new double[k*2];
			for (int j=0; j<k*2; j++) {
				kNNGraph[i][j] = -1;
			}
		}
		GreedyFiltering.kNNGraphConstruction(inputMatrix, kNNGraph, k, mu);
		
		
		// Convert double[][] containing KNN graph into a sparse adjacency matrix
		// kNNGraph has dimensions [numUsers][k*2]
		// e.g. kNNGraph[5][8]=17, kNNGraph[5][9]=0.35 means that user 5 has neighbor 17 with similarity 0.35
		
		for (int u1 = 0; u1 < numUsers; u1++){
			for (int idx = 0; idx < k*2; idx += 2){
				int u2 = (int) kNNGraph[u1][idx];
				if (u2 != -1){
					double sim = kNNGraph[u1][idx+1];
					adjacencyTable.put(u1, u2, sim);
					adjacencyColMap.put(u2, u1);
				}
			}
		}
		
		userAdjMatrix = new SparseMatrix(numUsers, numUsers, adjacencyTable, adjacencyColMap);

		
	}
	
	private GFSparseMatrix getItemTfidfMatrix() {
		ratingsMatrix.transpose();
		
	}
	
	private GFSparseMatrix getUserTfidfMatrix() {
	}
	
	private SparseMatrix computeTfidf(SparseMatrix) {
		int numElements = ratingsMatrix.size();
		GFSparseMatrix inputMatrix = new GFSparseMatrix(numUsers, numElements, numItems);

		int vectorCounter = 0;
		int elementCounter = 0;
		
		// Get value frequencies (number of vectors containing each value)
		int[] frequencies = new int[numItems];
		for (int user = 0; user < numUsers; user++){
			for (VectorEntry element : ratingsMatrix.row(user)){
				int dimension = element.index();
				frequencies[dimension]++;
			}
		}

		for (int u = 0; u < numUsers; u++){
			inputMatrix.setVectorIndex(vectorCounter, elementCounter); 
			
			// Get max element value
			double maxValue = 0;
			for (VectorEntry element : ratingsMatrix.row(u)){
				double value = element.get();
				if (maxValue < value){
					maxValue = value;
				}
			}
			
			for (VectorEntry element : ratingsMatrix.row(u)){
				int dimension = element.index();
				double value = element.get();
				
				double tfidf = (0.5 + (0.5 * value / maxValue)) * Math.log((double) numItems / (double) frequencies[dimension]);
				
				inputMatrix.setDimension(elementCounter, dimension);
				inputMatrix.setValue(elementCounter, tfidf);
				
				elementCounter++;
			}

			vectorCounter++;
		}
		inputMatrix.setVectorIndex(vectorCounter, elementCounter);
		
		// Sort each vector by value
		GFSorting.sortByValue(inputMatrix);
		

		return inputMatrix;
	}

	private void buildGraphsFromTaggings() {
		// TODO Auto-generated method stub
	}

	public SparseMatrix getUserAdjacencyMatrix(){
		return userAdjMatrix;
	}
	
	public SparseMatrix getItemAdjacencyMatrix(){
		return itemAdjMatrix;
	}
	
	public int getComputationTime(){
		return graphTime;
	}
	
	
}
