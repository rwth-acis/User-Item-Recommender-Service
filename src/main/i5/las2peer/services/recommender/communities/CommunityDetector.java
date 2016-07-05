package i5.las2peer.services.recommender.communities;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import i5.las2peer.services.recommender.communities.webocd.Cover;
import i5.las2peer.services.recommender.communities.webocd.CustomGraph;
import i5.las2peer.services.recommender.communities.webocd.OcdAlgorithmException;
import i5.las2peer.services.recommender.communities.webocd.RandomWalkLabelPropagationAlgorithm;
import i5.las2peer.services.recommender.librec.data.MatrixEntry;
import i5.las2peer.services.recommender.librec.data.SparseMatrix;
import y.base.Edge;
import y.base.Node;

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
	
	public void detectCommunities() throws OcdAlgorithmException, InterruptedException{
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
	
	private void detectDmid() throws OcdAlgorithmException, InterruptedException {
		RandomWalkLabelPropagationAlgorithm dmidAlgo = new RandomWalkLabelPropagationAlgorithm();
		CustomGraph customGraph = getWebOCDCustomGraph();
		
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("leadershipIterationBound", Integer.toString(dmidLeadershipIterationBound));
		parameters.put("leadershipPrecisionFactor", Double.toString(dmidLeadershipPrecisionFactor));
		parameters.put("profitabilityDelta", Double.toString(dmidProfitabilityDelta));
		dmidAlgo.setParameters(parameters);
		
		Cover cover = dmidAlgo.detectOverlappingCommunities(customGraph);
		
		membershipsMatrix = cover.getMemberships();
	}
	
	private void detectWalktrap() {
		// TODO Auto-generated method stub
		
	}
	
	private CustomGraph getWebOCDCustomGraph() {
		// Construct a CustomGraph to be used by WebOCD from the SparseMatrix graph
		
		CustomGraph customGraph = new CustomGraph();
		
		BiMap<Integer, Node> userNodeMap = HashBiMap.create();
		
		int numUsers = graph.numRows();
		
		for (int user = 0; user < numUsers; user++){
			Node node = customGraph.createNode();
			userNodeMap.put(user, node);
		}
		
		for (MatrixEntry e : graph){
			int user1 = e.row();
			int user2 = e.column();
			double weight = e.get();
			Node node1 = userNodeMap.get(user1);
			Node node2 = userNodeMap.get(user2);
			
			Edge edge = customGraph.createEdge(node1, node2);
			customGraph.setEdgeWeight(edge, weight);
		}
		
		return customGraph;
	}
	
}
