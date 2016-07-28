package i5.las2peer.services.recommender.graphs;

import i5.las2peer.services.recommender.librec.data.SparseMatrix;
import i5.las2peer.services.recommender.librec.data.SparseVector;
import i5.las2peer.services.recommender.librec.data.VectorEntry;

public class GraphUtils {

	public static GFSparseMatrix getTfidfMatrix(SparseMatrix ratingsMatrix, boolean getUserMatrix) {
		// In this method we regard the ratingsMatrix as a matrix of feature vectors.
		// If we want to build the user TF-IDF matrix, then we consider each row of the ratings matrix as the
		// respective user's feature vector and each item represents one dimension of the feature vectors.
		// If we want to build the item TF-IDF matrix, then we consider each column of the ratings matrix as the
		// respective item's feature vector and each user represents one dimension of the feature vectors.
		
		int numUsers = ratingsMatrix.numRows();
		int numItems = ratingsMatrix.numColumns();
		
		int numVectors = getUserMatrix ? numUsers : numItems;
		int numDimensions = getUserMatrix ? numItems : numUsers;
		int numElements = ratingsMatrix.size();
		
		GFSparseMatrix tfidfMatrix = new GFSparseMatrix(numVectors, numElements, numDimensions);

		// Get value frequencies (number of vectors containing each value)
		int[] frequencies = new int[numDimensions];
		for (int vectorIdx = 0; vectorIdx < numVectors; vectorIdx++){
			SparseVector vector = getUserMatrix ? ratingsMatrix.row(vectorIdx) : ratingsMatrix.column(vectorIdx);
			for (VectorEntry element : vector){
				int dimension = element.index();
				frequencies[dimension]++;
			}
		}
		
		// Compute TF-IDF values and store in tfidfMatrix
		int elementIdx = 0;
		
		for (int vectorIdx = 0; vectorIdx < numVectors; vectorIdx++){
			tfidfMatrix.setVectorIndex(vectorIdx, elementIdx);
			
			SparseVector vector = getUserMatrix ? ratingsMatrix.row(vectorIdx) : ratingsMatrix.column(vectorIdx);
			
			// Get max element value
			double maxValue = 0;
			for (VectorEntry element : vector){
				double value = element.get();
				if (maxValue < value){
					maxValue = value;
				}
			}
			
			for (VectorEntry element : vector){
				int dimension = element.index();
				double value = element.get();
				
				double tfidfValue = (0.5 + (0.5 * value / maxValue)) * Math.log((double) numDimensions / (double) frequencies[dimension]);
				
				tfidfMatrix.setDimension(elementIdx, dimension);
				tfidfMatrix.setValue(elementIdx, tfidfValue);
				
				elementIdx++;
			}

		}
		
		tfidfMatrix.setVectorIndex(numVectors, elementIdx);

		// Sort each vector by value as required by the knn graph construction algorithm
		GFSorting.sortByValue(tfidfMatrix);

		return tfidfMatrix;
	}

//	public static void printGraphInfo(SparseMatrix matrix) {
//		System.out.println("Graph information");
//		System.out.println("Number of user vectors: " + matrix.numRows());
//		System.out.println("Number of item vectors: " + matrix.numColumns());
//		System.out.println("Number of elements: " + matrix.size());
//		System.out.println("Last ten items:");
//		for (int i = matrix.numColumns()-10; i < matrix.numColumns(); i++){
//			System.out.print("Item num " + i + ":");
//			for (VectorEntry e : matrix.column(i)){
//				System.out.print(" " + e.index() + ":" + e.get());
//			}
//			System.out.println();
//		}
//	}

}
