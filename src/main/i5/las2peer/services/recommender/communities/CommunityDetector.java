package i5.las2peer.services.recommender.communities;

import i5.las2peer.services.recommender.communities.webocd.Cover;
import i5.las2peer.services.recommender.communities.webocd.CustomGraph;
import i5.las2peer.services.recommender.communities.webocd.OcdAlgorithmException;
import i5.las2peer.services.recommender.communities.webocd.RandomWalkLabelPropagationAlgorithm;
import librec.data.SparseMatrix;

public class CommunityDetector {
	
	private CommunityDetectionAlgorithm algorithm;
	
	private SparseMatrix graph;
	
	private SparseMatrix membershipsMatrix;
	
	// DMID parameters
	private int dmidLeadershipIterationBound = 1000;
	private double dmidLeadershipPrecisionFactor = 0.001;
	private double dmidProfitabilityDelta = 0.1;
	
	// Walktrap parameters
	private int walktrapSteps = 2;
	
	
	public enum CommunityDetectionAlgorithm{
		WALKTRAP, DMID
	}
	
	
	public void setAlgorithm(CommunityDetectionAlgorithm algorithm){
		this.algorithm = algorithm;
	}
	
	public void setGraph(SparseMatrix graph){
		this.graph = graph;
	}
	
	public void setDmidParameters(int iterationBound, double precisionFactor, double profitabilityDelta){
		dmidLeadershipIterationBound = iterationBound;
		dmidLeadershipPrecisionFactor = precisionFactor;
		dmidProfitabilityDelta = profitabilityDelta;
	}
	
	public void setWalktrapParameters(int steps){
		walktrapSteps = steps;
	}
	
	public void detectCommunities(){
		switch(algorithm){
		case WALKTRAP:
			detectWalktrap();
			break;
		case DMID:
			detectDmid();
			break;
		default:
			break;
		}
	}
	
	public SparseMatrix getCommunityStructure(){
		return membershipsMatrix;
	}
	
	
	private void detectDmid() {
		// TODO 
		
//		RandomWalkLabelPropagationAlgorithm dmidAlgo = new RandomWalkLabelPropagationAlgorithm();
//		
//		CustomGraph customGraph = getCustomGraph();
//		
//		try {
//			Cover cover = dmidAlgo.detectOverlappingCommunities(customGraph);
//		} catch (OcdAlgorithmException e) {
//			e.printStackTrace();
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}

	}

	private void detectWalktrap() {
		// TODO Auto-generated method stub
		
	}
	
	private CustomGraph getCustomGraph() {
		
		// TODO Construct CustomGraph from graph
		// ...
		
		return null;
	}

}
