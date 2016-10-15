package i5.las2peer.services.recommender.graphs;

public class GFSimilarityCalculation {
	
	public static void calculateCosineSim(GFSparseMatrix inputMatrix, double[][] kNNGraph, int k, int m, int n) {
		// Note: We can calculate cosine similarity faster than this version
		// However, for the sake of simplicity, we implement the simplest version
		double sim = 0.0;
				
		int i = inputMatrix.vectorIndex[n];
		int j = inputMatrix.vectorIndex[m];		
		
		while (true) {
			if (inputMatrix.dimension[i] == inputMatrix.dimension[j]) {
				sim += inputMatrix.value[i] * inputMatrix.value[j];
				i++;
				j++;
			}
			else if (inputMatrix.dimension[i] < inputMatrix.dimension[j]) i++;
			else j++;
			
			if (i >= inputMatrix.vectorIndex[n+1] || j >= inputMatrix.vectorIndex[m+1]) {
				break;
			}
		}

		updatekNNGraph(kNNGraph, m, n, sim, k);
		updatekNNGraph(kNNGraph, n, m, sim, k);
	}
	
	public static void calculateMSDSim(GFSparseMatrix inputMatrix, double[][] kNNGraph, int k, int m, int n) {
		// Calculate Mean Squared Distance
		// Ricci, Rokach, Shapira, Kantor - Recommender Systems Handbook, eq. (4.22)
		
		int i = inputMatrix.vectorIndex[n];
		int j = inputMatrix.vectorIndex[m];		
		
		int numEntries = 0;
		double msd = 0;
		
		while (true) {
			if (inputMatrix.dimension[i] == inputMatrix.dimension[j]) {
				msd += (inputMatrix.value[i] - inputMatrix.value[j]) * (inputMatrix.value[i] - inputMatrix.value[j]);
				numEntries++;
				i++;
				j++;
			}
			else if (inputMatrix.dimension[i] < inputMatrix.dimension[j]) i++;
			else j++;
			
			if (i >= inputMatrix.vectorIndex[n+1] || j >= inputMatrix.vectorIndex[m+1]) {
				break;
			}
		}
		
		double sim = (msd > 0) ? numEntries / msd : 0;
		
		updatekNNGraph(kNNGraph, m, n, sim, k);
		updatekNNGraph(kNNGraph, n, m, sim, k);
	}
	
	public static void calculateJMSDSim(GFSparseMatrix inputMatrix, double[][] kNNGraph, int k, int m, int n) {
		// Calculate Jaccards Mean Squared Distance
		// Bobadilla, Serradilla, Bernal - A new collaborative filtering metric that improves the behavior of recommender systems
		
		int i = inputMatrix.vectorIndex[n];
		int j = inputMatrix.vectorIndex[m];		
		
		int numCommonEntries = 0;
		int numNonCommonEntries = 0;
		double msd = 0;
		
		while (true) {
			if (i >= inputMatrix.vectorIndex[n+1] && j >= inputMatrix.vectorIndex[m+1]) {
				break;
			}
			
			if (i >= inputMatrix.vectorIndex[n+1]){
				numNonCommonEntries++;
				j++;
			}
			else if (j >= inputMatrix.vectorIndex[m+1]){
				numNonCommonEntries++;
				i++;
			}
			else if (inputMatrix.dimension[i] == inputMatrix.dimension[j]) {
				msd += (inputMatrix.value[i] - inputMatrix.value[j]) * (inputMatrix.value[i] - inputMatrix.value[j]);
				numCommonEntries++;
				i++;
				j++;
			}
			else if (inputMatrix.dimension[i] < inputMatrix.dimension[j]){
				numNonCommonEntries++;
				i++;
			}
			else{
				numNonCommonEntries++;
				j++;
			}
		}
		
		msd = msd / numCommonEntries;
		
		double jacc = numCommonEntries > 0 ? (numCommonEntries + numNonCommonEntries) / numCommonEntries : 0;
		double sim = jacc * (1-msd);
		
		updatekNNGraph(kNNGraph, m, n, sim, k);
		updatekNNGraph(kNNGraph, n, m, sim, k);
	}
	
	public static void calculatePearsonSim(GFSparseMatrix inputMatrix, double[][] kNNGraph, int k, int m, int n) {
		// Calculate Pearson correlation coefficient
		// Ricci, Rokach, Shapira, Kantor - Recommender Systems Handbook, eq. (4.20), (4.21)
		double sim = 0.0;
		
		// calculate means on vector entries that are part of both vectors
		int i = inputMatrix.vectorIndex[n];
		int j = inputMatrix.vectorIndex[m];		
		
		double nMean = 0;
		double mMean = 0;
		int numEntries = 0;
		
		while (true) {
			if (inputMatrix.dimension[i] == inputMatrix.dimension[j]) {
				nMean += (double) inputMatrix.value[i];
				mMean += (double) inputMatrix.value[j];
				numEntries++;
				i++;
				j++;
			}
			else if (inputMatrix.dimension[i] < inputMatrix.dimension[j]) i++;
			else j++;
			
			if (i >= inputMatrix.vectorIndex[n+1] || j >= inputMatrix.vectorIndex[m+1]) {
				break;
			}
		}
		nMean = (numEntries > 0) ? nMean / numEntries : 0;
		mMean = (numEntries > 0) ? mMean / numEntries : 0;
		
		// calculate variances and Pearson correlation coefficient
		i = inputMatrix.vectorIndex[n];
		j = inputMatrix.vectorIndex[m];		

		double nVar = 0;
		double mVar = 0;
		
		while (true) {
			if (inputMatrix.dimension[i] == inputMatrix.dimension[j]) {
				sim += (inputMatrix.value[i] - nMean) * (inputMatrix.value[j] - mMean);
				nVar += (inputMatrix.value[i] - nMean) * (inputMatrix.value[i] - nMean);
				mVar += (inputMatrix.value[j] - mMean) * (inputMatrix.value[j] - mMean);
				i++;
				j++;
			}
			else if (inputMatrix.dimension[i] < inputMatrix.dimension[j]) i++;
			else j++;
			
			if (i >= inputMatrix.vectorIndex[n+1] || j >= inputMatrix.vectorIndex[m+1]) {
				break;
			}
		}
		sim = (mVar > 0 && nVar > 0) ? sim / Math.sqrt(mVar * nVar) : 0;

		updatekNNGraph(kNNGraph, m, n, sim, k);
		updatekNNGraph(kNNGraph, n, m, sim, k);
	}
	
	private static void updatekNNGraph(double[][] kNNGraph, int m, int n, double sim, int k) {
		// This method updates the k-NN list of m
		// First, check whether m and n are equal number
		// Second, check whether n is already existed in the list
		// If there is no n, then find the minimum similarity value in the list
		// If (the minimum similarity value) < (sim), then update the list		
		double min = Double.MAX_VALUE;
		int min_index = -1;
		
		if (m == n) return;
		
		for (int i=0; i<k*2; i+=2) {
			if (kNNGraph[m][i] == n) 
				return;
			if (kNNGraph[m][i+1] < min) {
				min_index = i;
				min = kNNGraph[m][i+1];
			}				
		}
		
		if (min < sim) {
			kNNGraph[m][min_index] = n;
			kNNGraph[m][min_index+1] = sim;
		}
	}	
}
