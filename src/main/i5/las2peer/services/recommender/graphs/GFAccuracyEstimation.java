package i5.las2peer.services.recommender.graphs;

import java.io.*;
import java.util.*;

public class GFAccuracyEstimation {
	public static void printAccuracy(GFSparseMatrix inputMatrix, double[][] kNNGraph, int k, int numVerification) {
		// There are many duplicate calculations
		// Thus we need to refine this source code if "numVerification" is a high value
		System.out.println("Estimating the accuracy of the constructed k-NN graph... "
						+ "(# Verification Nodes: " + numVerification + ")");

		double[][] exactkNNGraph = new double[inputMatrix.numVector][];
		for (int i=0; i<inputMatrix.numVector; i++) {
			exactkNNGraph[i] = new double[k*2];			
			for (int j=0; j<k*2; j++) {
				exactkNNGraph[i][j] = -1;
			}
		}		

		// 1) Select the "numVerification" number of nodes randomly
		int[] randomVector = new int[inputMatrix.numVector];
		Random rand = new Random();
		for (int i=0; i<numVerification; i++)
			randomVector[i] = rand.nextInt(inputMatrix.numVector);
						
		// 2) For each selected node, calculate the exact k-NN		
		for (int i=0; i<numVerification; i++)
			for (int j = 0; j < inputMatrix.numVector; j++)
				GFSimilarityCalculation.calculateCosineSim(inputMatrix, exactkNNGraph, k, randomVector[i], j);

		// 3) Compare "exactkNNGraph" with "kNNGraph"
		int totalCount = numVerification * k;
		int overlapCount = 0;
				
		for (int i=0; i<numVerification; i++) {
			int testVector = randomVector[i];
			
			for (int x=0; x<k*2; x+=2)
				for (int y=0; y<k*2; y+=2)
					if (exactkNNGraph[testVector][x] == kNNGraph[testVector][y])
						overlapCount++;
		}
				
		System.out.println("The total count: " + totalCount);
		System.out.println("The overlap count: " + overlapCount);
		System.out.println("Estimated Accuracy: " + ((double)overlapCount / (double)totalCount));
	}
}
