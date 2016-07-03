package i5.las2peer.services.recommender.graphs;

import java.io.*;
import java.util.*;

public class GreedyFiltering {
	private static int numCandidates;

	public static void bucketJoin(GFSparseMatrix inputMatrix, Vector<Integer> bucket, double[][] kNNGraph, int k) {
		for (int i=1; i<bucket.size(); i++) {
			int m = bucket.get(i);
			
			for (int j=0; j<i; j++) {				
				int n = bucket.get(j);
				
				SimilarityCalculation.calculateCosineSim(inputMatrix, kNNGraph, k, m, n);				
				numCandidates++;			
			}
		}
	}

	public static void kNNGraphConstruction(GFSparseMatrix inputMatrix, double[][] kNNGraph, int k, int mu) {
		double startTime = System.currentTimeMillis();
		numCandidates = 0;
		
		// 1) Find candidate pairs
		System.out.println("Finding candidate pairs...");
				
		Vector<Integer>[] bucket = new Vector[inputMatrix.numDimension];
		for (int i=0; i<inputMatrix.numDimension; i++)
			bucket[i] = new Vector<Integer>();
		Vector<Integer> todoVector = new Vector<Integer>();
				
		// For each vector, set the prefix of the vector to 1 
		// (the prefix part contains the first element)		
		for (int i=0; i<inputMatrix.numVector; i++) {
			int vectorIndex = inputMatrix.vectorIndex[i];						
			int dim = (int)Math.round(inputMatrix.dimension[vectorIndex]);
			bucket[dim].add(i);
		}
	
		// "todoVector" contains vectors that do not meet the stop condition
		for (int i=0; i<inputMatrix.numVector; i++)
			todoVector.add(i);				
		int currentPrefix = 1;
		int size;
		do {
			// Determine which prefixes will be extended
			size = todoVector.size();
			for (int i=0; i<size; i++) {
				int v = todoVector.get(i);
				int vectorIndex = inputMatrix.vectorIndex[v];
				int vectorSize = inputMatrix.vectorIndex[v+1]-inputMatrix.vectorIndex[v];
				int comparisonCount = 0;
				
				for (int j=0; j<currentPrefix; j++) {
					int dim = (int)Math.round(inputMatrix.dimension[vectorIndex+j]);
					comparisonCount += bucket[dim].size()-1;					
				}
				
				if (comparisonCount >= mu || currentPrefix >= vectorSize) {
					todoVector.remove(i);
					i--;		
					size--;
				}
			}
						
			// Put the vectors into a bucket
			size = todoVector.size();
			for (int i=0; i<size; i++) {
				int v = todoVector.get(i);
				int vectorIndex = inputMatrix.vectorIndex[v];
				int dim = (int)Math.round(inputMatrix.dimension[vectorIndex+currentPrefix]);
				bucket[dim].add(v);
			}
			// Extend the prefixes			
			currentPrefix++;
		} while (size > 0);

		// Before calculating similarities, sort each element into ascending order of its dimension number
		System.out.println("Sorting in an ascending order of dimension numbers...");
		GFSorting.sortByDimNumber(inputMatrix);		
		
		// 2) Calculate the similarity for each candidate pair
		System.out.println("Calculating similarities...");
				
		for (int i=0; i<inputMatrix.numDimension; i++)
			if (bucket[i].size() >= 2)
				bucketJoin(inputMatrix, bucket[i], kNNGraph, k);
		
		double endTime = System.currentTimeMillis();
		System.out.println("Elapsed time: " + (endTime-startTime));
		System.out.println("Scan rate: " + ((double)numCandidates / ((double)inputMatrix.numVector * (inputMatrix.numVector-1) / 2)));
	}
}
