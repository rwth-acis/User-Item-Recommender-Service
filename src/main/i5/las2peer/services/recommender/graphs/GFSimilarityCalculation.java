package i5.las2peer.services.recommender.graphs;

import java.io.*;
import java.util.*;

public class GFSimilarityCalculation {
	private static int temp = 0;	
	
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
