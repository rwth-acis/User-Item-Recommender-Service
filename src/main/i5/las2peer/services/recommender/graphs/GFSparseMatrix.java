package i5.las2peer.services.recommender.graphs;

public class GFSparseMatrix {			
	int numVector;
	int numDimension;
	
	// For example, vectorIndex[4] is the "start index" of the 5th vector
	// vectorIndex[numVector] is the "end index+1" of the final vector	
	int[] vectorIndex; 	
	
	// For example, dim[4] is the dimension number of the 5th vector
	// value[4] is the value of the 5th vector
	int[] dimension; 
	double[] value;
	
	public GFSparseMatrix() {
	}

	public GFSparseMatrix(int numVector, int numElement, int numDimension){
		this.numVector = numVector;
		this.vectorIndex = new int[numVector+1];
		this.dimension = new int[numElement];
		this.value = new double[numElement];
		this.numDimension = numDimension;
	}
	
	public int getNumVector() {
		return numVector;
	}
	public void setNumVector(int numVector) {
		this.numVector = numVector;
	}
	public int getNumDimension() {
		return numDimension;
	}
	public void setNumDimension(int numDimension) {
		this.numDimension = numDimension;
	}
	public int[] getVectorIndex() {
		return vectorIndex;
	}
	public int getVectorIndex(int a) {
		return vectorIndex[a];
	}
	public void setVectorIndex(int[] index) {
		this.vectorIndex = index;
	}
	public void setVectorIndex(int a, int index) {
		this.vectorIndex[a] = index;
	}
	public int[] getDimension() {
		return dimension;
	}
	public int getDimension(int a) {
		return dimension[a];
	}
	public void setDimension(int[] dimension) {
		this.dimension = dimension;
	}
	public void setDimension(int a, int dimension) {
		this.dimension[a] = dimension;
	}
	public double[] getValue() {
		return value;
	}
	public double getValue(int a) {
		return value[a];
	}
	public void setValue(double[] value) {
		this.value = value;
	} 		
	public void setValue(int a, double value) {
		this.value[a] = value;
	} 		
}
