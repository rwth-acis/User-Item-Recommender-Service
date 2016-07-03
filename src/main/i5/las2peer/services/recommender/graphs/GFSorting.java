package i5.las2peer.services.recommender.graphs;

public class GFSorting {
	public static void sortByDimNumber(GFSparseMatrix inputMatrix) {
		// For each vector, we sort the elements of the vector in the ascending order of dimension numbers,
		// because we want to speed up the similarity calculations
		
		// It is not mandatory to use a fast sorting algorithm, 
		// because the sorting process takes only a minor portion in terms of time
					
		for (int i=0; i<inputMatrix.numVector; i++) {
			for (int j=inputMatrix.vectorIndex[i]+1; j<inputMatrix.vectorIndex[i+1]; j++) {
				for (int k=j; k>=inputMatrix.vectorIndex[i]+1; k--) {
					if (inputMatrix.dimension[k] < inputMatrix.dimension[k-1])
						swap(inputMatrix, k-1, k);					
					else
						break;
				}								
			}
		}		
	}	
	
	public static void sortByValue(GFSparseMatrix inputMatrix) {
		// For each vector, we sort the elements of the vector in the ascending order of dimension numbers,
		// because we want to speed up the similarity calculations
		
		// It is not mandatory to use a fast sorting algorithm, 
		// because the sorting process takes only a minor portion in terms of time
					
		for (int i=0; i<inputMatrix.numVector; i++) {
			for (int j=inputMatrix.vectorIndex[i]+1; j<inputMatrix.vectorIndex[i+1]; j++) {
				for (int k=j; k>=inputMatrix.vectorIndex[i]+1; k--) {
					if (inputMatrix.value[k] > inputMatrix.value[k-1])
						swap(inputMatrix, k-1, k);					
					else
						break;
				}								
			}
		}		
	}	
	
	private static void swap(GFSparseMatrix inputMatrix, int x, int y) {
		int tempDimension = inputMatrix.dimension[x];
		double tempValue = inputMatrix.value[x];
		
		inputMatrix.dimension[x] = inputMatrix.dimension[y];
		inputMatrix.value[x] = inputMatrix.value[y];
		
		inputMatrix.dimension[y] = tempDimension;
		inputMatrix.value[y] = tempValue;
	}
}
