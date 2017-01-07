package i5.las2peer.services.recommender.communities;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import i5.las2peer.services.recommender.communities.igraph.Igraph;
import i5.las2peer.services.recommender.communities.webocd.Cover;
import i5.las2peer.services.recommender.communities.webocd.CustomGraph;
import i5.las2peer.services.recommender.communities.webocd.OcdAlgorithmException;
import i5.las2peer.services.recommender.communities.webocd.RandomWalkLabelPropagationAlgorithm;
import i5.las2peer.services.recommender.communities.webocd.SpeakerListenerLabelPropagationAlgorithm;
import i5.las2peer.services.recommender.librec.data.DenseVector;
import i5.las2peer.services.recommender.librec.data.MatrixEntry;
import i5.las2peer.services.recommender.librec.data.SparseMatrix;
import i5.las2peer.services.recommender.librec.data.SparseVector;
import i5.las2peer.services.recommender.librec.data.VectorEntry;
import i5.las2peer.services.recommender.librec.util.Logs;
import y.base.Edge;
import y.base.Node;

public class CommunityDetector {
	
	private CommunityDetectionAlgorithm algorithm;
	
	private boolean overlapping = true;

	private SparseMatrix graph;
	
	private SparseMatrix membershipsMatrix;
	
	// Community memberships vector, only set when using Walktrap, not when using OCD algorithms
	private DenseVector membershipsVector;
	
	// DMID parameters
	private int dmidLeadershipIterationBound = 1000;
	private double dmidLeadershipPrecisionFactor = 0.001;
	private double dmidProfitabilityDelta = 0.1;
	
	// Walktrap parameters
	private int walktrapSteps = 2;
	
	// SLPA parameters
	private double slpaProbabilityThreshold = 0.15;
	private int slpaMemorySize = 100;
	
	private int communityDetectionTime;
	
	
	public enum CommunityDetectionAlgorithm{
		WALKTRAP, DMID, SLPA
	}
	
	/**
	 * Specify which algorithm to use for community detection
	 * @param algorithm community detection algorithm
	 */
	public void setAlgorithm(CommunityDetectionAlgorithm algorithm){
		this.algorithm = algorithm;
	}
	
	/**
	 * Specify the graph (given as an adjacency matrix) on which to perform comunity detection
	 * @param graph adjacency matrix
	 */
	public void setGraph(SparseMatrix graph){
		this.graph = graph;
	}
	
	/**
	 * Specify the parameters to use for DMID community detection
	 * @param iterationBound DMID iteration bound parameter
	 * @param precisionFactor DMID precision factor parameter
	 * @param profitabilityDelta DMID profitability delta parameter
	 */
	public void setDmidParameters(int iterationBound, double precisionFactor, double profitabilityDelta){
		dmidLeadershipIterationBound = iterationBound;
		dmidLeadershipPrecisionFactor = precisionFactor;
		dmidProfitabilityDelta = profitabilityDelta;
	}
	
	/**
	 * Specify the steps parameter to use for Walktrap community detection
	 * @param steps Walktrap steps parameter 
	 */
	public void setWalktrapParameters(int steps){
		walktrapSteps = steps;
	}
	/**
	 * Specify the parameters to use for SLPA community detection
	 * @param probabilityThresh SLPA probabilityThresh parameter
	 * @param memorySize SLPA memorySize parameter
	 */
	public void setSlpaParameters(double probabilityThresh, int memorySize){
		slpaProbabilityThreshold = probabilityThresh;
		slpaMemorySize = memorySize;
	}
	
	/**
	 * Specify whether to perform overlapping or non-overlapping community detection.
	 * 
	 * When using an overlapping community detection algorithm and this option is set
	 * to false then only the community assignments with the highest membership level
	 * are kept, so that the result is a non-overlapping community structure.
	 * 
	 * When using a non-overlapping community detection algorithm, then this option
	 * has no effect, since the community structure is non-overlapping in any case.
	 * 
	 * @param overlapping
	 */
	public void setOverlapping(boolean overlapping){
		this.overlapping = overlapping;
	}
	
	/**
	 * Perform community detection. Before calling this method the graph must be specified
	 * using the setGraph() method.
	 * @throws OcdAlgorithmException on community detection errors
	 * @throws InterruptedException when a thread is interrupted
	 */
	public void detectCommunities() throws OcdAlgorithmException, InterruptedException{
		Stopwatch sw = Stopwatch.createStarted();

		switch(algorithm){
			case WALKTRAP:
				detectWalktrap();
				break;
			case DMID:
				detectDmid();
				break;
			case SLPA:
				detectSlpa();
				break;
			default:
				break;
		}
		
		sw.stop();
		communityDetectionTime = (int) sw.elapsed(TimeUnit.SECONDS);
	}
	
	/**
	 * Returns the community structure as a membership matrix, where each row represents
	 * a node, each column represents a community and each matrix entry indicates the
	 * membership level of the corresponding node to the corresponding community.
	 * @return membership matrix
	 */
	public SparseMatrix getMemberships(){
		return membershipsMatrix;
	}
	
	/**
	 * Returns the community structure as a membership vector, where for each user u
	 * the user's community assignment is given at index u. The vector may therefore 
	 * only represent non-overlapping community structures.
	 * @return membership vector
	 */
	public DenseVector getMembershipsVector(){
		return membershipsVector;
	}
	
	/**
	 * Returns the number of communities detected.
	 * @return number of communities
	 */
	public int getNumCommunities(){
		return membershipsMatrix.numColumns();
	}
	
	/**
	 * Returns the time taken for community detection.
	 * @return time
	 */
	public int getComputationTime(){
		return communityDetectionTime;
	}
	
	/**
	 * Perform DMID community detection.
	 * @throws OcdAlgorithmException on community detection errors
	 * @throws InterruptedException when a thread is interrupted
	 */
	private void detectDmid() throws OcdAlgorithmException, InterruptedException {
		Logs.info(String.format("DMID: [LIB, LPF, PD] = [%s, %s, %s]",
				dmidLeadershipIterationBound, dmidLeadershipPrecisionFactor, dmidProfitabilityDelta));
		
		RandomWalkLabelPropagationAlgorithm dmidAlgo = new RandomWalkLabelPropagationAlgorithm();
		CustomGraph customGraph = getWebOCDCustomGraph();
		
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("leadershipIterationBound", Integer.toString(dmidLeadershipIterationBound));
		parameters.put("leadershipPrecisionFactor", Double.toString(dmidLeadershipPrecisionFactor));
		parameters.put("profitabilityDelta", Double.toString(dmidProfitabilityDelta));
		dmidAlgo.setParameters(parameters);
		
		Cover cover = dmidAlgo.detectOverlappingCommunities(customGraph);
		
		membershipsMatrix = cover.getMemberships();
		if (!overlapping)
			makeNonOverlapping();
		membershipsVector = computeMembershipsVector();
	}
	
	/**
	 * Perform SLPA community detection.
	 * @throws OcdAlgorithmException on community detection errors
	 * @throws InterruptedException when a thread is interrupted
	 */
	private void detectSlpa() throws OcdAlgorithmException, InterruptedException {
		Logs.info(String.format("SLPA: [PT, MS] = [%s, %s]",
				slpaProbabilityThreshold, slpaMemorySize));
		
		SpeakerListenerLabelPropagationAlgorithm slpaAlgo = new SpeakerListenerLabelPropagationAlgorithm();
		CustomGraph customGraph = getWebOCDCustomGraph();
		
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("probabilityThreshold", Double.toString(slpaProbabilityThreshold));
		parameters.put("memorySize", Integer.toString(slpaMemorySize));
		slpaAlgo.setParameters(parameters);
		
		Cover cover = slpaAlgo.detectOverlappingCommunities(customGraph);
		
		membershipsMatrix = cover.getMemberships();
		if (!overlapping)
			makeNonOverlapping();
		membershipsVector = computeMembershipsVector();
	}
	
	/**
	 * Construct a CustomGraph from the graph specified using the setGraph() method that
	 * can be used by the WebOCD community detection algorithms.
	 * @return graph
	 */
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
	
	/**
	 * Reduce an overlapping community structure to a non-overlapping community
	 * structure by only keeping the community with the highest membership level for
	 * each node.
	 */
	private void makeNonOverlapping() {
		int numNodes = membershipsMatrix.numRows();
		int numCommunities = membershipsMatrix.numColumns();
		
		Table<Integer, Integer, Double> membershipsTable = HashBasedTable.create();
		Multimap<Integer, Integer> membershipsColMap = HashMultimap.create();

		for (int node = 0; node < numNodes; node++){
			// get community with highest membership level and store in vector
			SparseVector communitiesVector = membershipsMatrix.row(node);
			double maxLevel = 0;
			int community = 0;
			for (VectorEntry e : communitiesVector){
				double level = e.get();
				if (level > maxLevel){
					maxLevel = level;
					community = e.index();
				}
			}
			membershipsTable.put(node, community, 1.0);
			membershipsColMap.put(community, node);
		}
		membershipsMatrix = new SparseMatrix(numNodes, numCommunities, membershipsTable, membershipsColMap);
	}

	/**
	 * Compute a memberships vector that represents the (non-overlapping) community structure.
	 * @return
	 */
	private DenseVector computeMembershipsVector(){
		int numNodes = membershipsMatrix.numRows();
		
		DenseVector vector = new DenseVector(numNodes);
		
		for (int node = 0; node < numNodes; node++){
			// get community with highest membership level and store in vector
			SparseVector communitiesVector = membershipsMatrix.row(node);
			double maxLevel = 0;
			int community = 0;
			for (VectorEntry e : communitiesVector){
				double level = e.get();
				if (level > maxLevel){
					maxLevel = level;
					community = e.index();
				}
			}
			vector.set(node, community);
		}
		
		return vector;
	}

	/**
	 * Perform Walktrap community detection.
	 */
	private void detectWalktrap() {
		Logs.info(String.format("Walktrap: [steps] = [%s]", walktrapSteps));
		
		Igraph igraph = new Igraph();
		
		igraph.setGraph(graph);
		igraph.detectCommunitiesWalktrap(walktrapSteps);
		membershipsMatrix = igraph.getMembershipsMatrix();
		membershipsVector = igraph.getMembershipsVector();
	}
}
