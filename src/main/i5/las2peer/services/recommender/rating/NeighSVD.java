package i5.las2peer.services.recommender.rating;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.MatrixEntry;
import no.uib.cipr.matrix.Vector;
import no.uib.cipr.matrix.Vector.Norm;
import no.uib.cipr.matrix.VectorEntry;
import no.uib.cipr.matrix.sparse.FlexCompRowMatrix;
import no.uib.cipr.matrix.sparse.SparseVector;

public class NeighSVD {
	
	// Ratings matrix containing the known user-item ratings
	private FlexCompRowMatrix ratingsMatrix;
	
	// Parameters used for learning the model parameters
	private double step = 0.0001;
	private double gamma1;
	private double gamma2;
	private double gamma3;
	private double gamma6;
	private double gamma7;
	private double gamma8;
	private double lambda;
	private double lambda6;
	private double lambda7;
	private double lambda8;
	private int factorSize = 20;
	private int maxIterations = 30;
	//...
	
	// Model parameters - baseline estimation
	private double meanRating;	// Mean rating
	private Vector userBias;	// User bias
	private Vector itemBias;	// Item bias
	
	// Model parameters - neighborhood model
	private DenseMatrix w;
	private DenseMatrix c;
	
	// Model parameters - factor model
//	private DenseMatrix q;	// Item characteristics vectors - each matrix row represents one vector
	private Vector[] itemFactors;	// Item characteristics vectors
//	private DenseMatrix p;	// User preferences vectors - each matrix row represents one vector
	private Vector[] userFactors;	// User preferences vectors
	private Vector[] y;
	
	
//	public NeighSVD(){
//		
//	}
	
	public void setRatingsMatrix(FlexCompRowMatrix m){
		ratingsMatrix = m;
	}
	
	public void setParameters(){
		
	}
	
	public void buildModel(){
		
		for(int iter = 0; iter < maxIterations; iter++){
			
			double error = 0;
			
			for(MatrixEntry me : ratingsMatrix){
				
				// Compute the regularized squared error for each rating
				
				int u = me.row();		// user index
				int i = me.column();	// item index
				
				SparseVector userRatings = ratingsMatrix.getRow(u);		// Sparse vector containing ratings by user u
				
				double eui = me.get() - predict(u,i);
				double bu = userBias.get(u);
				double bi = itemBias.get(i);
				Vector pu = userFactors[u];
				Vector qi = itemFactors[i];
				double punorm = pu.norm(Norm.Two);
				double qinorm = qi.norm(Norm.Two);
				double wijSum = 0;
				double cijSum = 0;
				double yjSum = 0;
				
				for(VectorEntry ve : userRatings){
					int j = ve.index();
					
					wijSum += w.get(i, j);
					cijSum += c.get(i, j);
					
					Vector yj = y[j];
					double yjnorm = yj.norm(Norm.Two); 
					yjSum += yjnorm * yjnorm;
				}
				
				double reg = lambda * (bu*bu + bi*bi + punorm*punorm + qinorm*qinorm + wijSum*wijSum + cijSum*cijSum + yjSum);
				
				error += eui * eui + reg;
				
				// Adjust the model parameters
				
				bu += gamma1 * (eui - lambda6 * bu);
				bi += gamma1 * (eui - lambda6 * bi);
				
				Vector puAdd;
				puAdd = qi.copy();
				puAdd.scale(eui);
				puAdd.add(pu.copy().scale(-1 * lambda7));
				puAdd.scale(gamma2);
				pu.add(puAdd);
				
				Vector qiAdd = new DenseVector(factorSize).zero();
				for(VectorEntry ve : userRatings){
//					qiAdd
				}
				qiAdd = pu.copy(); 
				
				
				userBias.set(u, bu);
				itemBias.set(i, bi);
				userFactors[u] = pu;
				itemFactors[i] = qi;
				
			}
			
			// break on convergence
			if(error < 0.0001){
				break;
			}
		}
		
	}
	
	public double predict(int u, int i){
		
		SparseVector userRating = ratingsMatrix.getRow(u);		// Vector containing ratings by user u
		double norm = 1 / Math.sqrt(userRating.getUsed());		// Normalization factor for neighborhood and factor model
		
		// Baseline b
		double b = meanRating + userBias.get(u) + itemBias.get(i);
		
		// Neighborhood model prediction n 
		double n = 0;
		
		DenseVector ySum = new DenseVector(factorSize).zero();

		for(VectorEntry e : userRating){
			int j = e.index();	// item index
			
			double ruj = ratingsMatrix.get(u, j);
			double mu = meanRating;
			double bu = userBias.get(u);
			double bj = itemBias.get(j);
			double wij = w.get(i, j);
			double cij = c.get(i, j);
			Vector yj = y[j];
			
			n += (ruj - (mu + bu + bj)) * wij + cij;
			
			ySum.add(yj);
		}
		n *= norm;
		ySum.scale(norm);
		
		// Factor model prediction f
		double f = itemFactors[i].dot(ySum.add(userFactors[u]));
		
		return b+n+f;
	}
	
}
