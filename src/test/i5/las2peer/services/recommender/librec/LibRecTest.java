package i5.las2peer.services.recommender.librec;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.Table;

import i5.las2peer.services.recommender.entities.Rating;
import i5.las2peer.services.recommender.entities.Tagging;
import i5.las2peer.services.recommender.librec.intf.Recommender;
import i5.las2peer.services.recommender.librec.main.LibRec;

/**
 * Test the LibRec class:
 * - setting data and parameters
 * - building recommender models
 * - getting predictions
 * - performing evaluation 
 */
public class LibRecTest {
	
	private static List<Rating> ratings;
	private static List<Tagging> tags;
	private static List<Integer> users;
	private static List<Integer> items;

	@BeforeClass
	public static void setSystemParameters() {
//		String igraphLibraryPath = System.getProperty("user.dir");//or another absolute or relative path
		System.setProperty("java.library.path", "igraph");
	}	
	
	/**
	 * Called before the tests start.
	 * Set up rating and tagging data to be used in the tests. 
	 */
	@BeforeClass
	public static void initData() {
		// Create small sets of ratings and taggings with 4 users and 6 items.
		// User and item indexes are not sequential, e.g. items 2, 3 and 6 do not exist 
		// One user and one item do not have any rating
		// u\i  0   1   4   5   7   8
		//  3  3.0         3.5
		//  5      1.5     2.5
		//  6
		//  7  2.0                 3.0
		
		ratings = new LinkedList<Rating>();
		ratings.add(new Rating(3, 0, 1268910300, 3.0));
		ratings.add(new Rating(3, 5, 1199567220, 3.5));
		ratings.add(new Rating(5, 1, 1312553280, 1.5));
		ratings.add(new Rating(5, 5, 1298721350, 2.5));
		ratings.add(new Rating(7, 0, 1301598230, 2.0));
		ratings.add(new Rating(7, 8, 1246328910, 3.0));
		
		tags = new LinkedList<Tagging>();
		tags.add(new Tagging(3, 4, 1268910300, "comedy"));
		tags.add(new Tagging(3, 5, 1199567220, "action"));
		tags.add(new Tagging(3, 6, 1312553280, "comedy"));
		tags.add(new Tagging(6, 4, 1298721350, "comedy"));
		tags.add(new Tagging(7, 5, 1246328910, "action"));
		
		users = Arrays.asList(new Integer[] {3,5,6,7});
		items = Arrays.asList(new Integer[] {0,1,4,5,7,8});
	}
	
	/**
	 * Called after the tests have finished.
	 * Nothing to do.
	 */
	@AfterClass
	public static void afterTesting() {
	}
	
	
	/**
	 * Test the NSVD model
	 */
	@Test
	public void testNSVD() throws Exception {
		testModel("nsvd");
	}

	/**
	 * Test the TNSVD model
	 * Skip this test because learning sometimes diverges, causing an error
	 */
	@Ignore
	@Test
	public void testTNSVD() throws Exception {
		testModel("tnsvd");
	}

	/**
	 * Test the CNSVD model
	 */
	@Test
	public void testCNSVD() throws Exception {
		testModel("cnsvd");
	}

	/**
	 * Test the CNSVD-Fast model
	 */
	@Test
	public void testCNSVDFast() throws Exception {
		testModel("cnsvdfast");
	}

	/**
	 * Test the TCNSVD model
	 * Skip this test because learning sometimes diverges, causing an error
	 */
	@Ignore
	@Test
	public void testTCNSVD() throws Exception {
		testModel("tcnsvd");
	}

	/**
	 * Test the TCNSVD-Fast model
	 */
	@Test
	public void testTCNSVDFast() throws Exception {
		testModel("tcnsvdfast");
	}
	
	/**
	 * Test DMID community detection
	 * Skip this test because DMID exceeds iteration bound and fails
	 * TODO: identify reason and adjust test
	 */
	@Ignore
	@Test
	public void testDMID() throws Exception {
		Map<String,String> config = new HashMap<String,String>();
		config.put("cd.algo", "dmid");
		testModel("cnsvd", config);
	}

	/**
	 * Test Walktrap community detection
	 */
	@Test
	public void testWT() throws Exception {
		Map<String,String> config = new HashMap<String,String>();
		config.put("cd.algo", "wt");
		testModel("cnsvd", config);
	}

	/**
	 * Test ratings-based graph construction
	 */
	@Test
	public void testRatingsGraph() throws Exception {
		Map<String,String> config = new HashMap<String,String>();
		config.put("graph.method", "knn");
		testModel("cnsvd", config);
	}

	/**
	 * Test tag-based graph construction
	 */
	@Test
	public void testTagsGraph() throws Exception {
		Map<String,String> config = new HashMap<String,String>();
		config.put("graph.method", "tags");
		testModel("cnsvd", config);
	}

	private void testModel(String model) throws Exception{
		// use an empty config map
		Map<String,String> config = new HashMap<String,String>();
		testModel(model, config);
	}
	
	private void testModel(String model, Map<String,String> config) throws Exception{
		// Instantiate LibRec
		LibRec librec = new LibRec(model);
		
		// set configuration
		for (Map.Entry<String,String> e : config.entrySet()){
			librec.setParameter(e.getKey(), e.getValue());
		}
		
		// set rating and tagging data
		librec.setRatings(ratings, users, items);
		librec.setTaggings(tags);
		
		// build the recommender model
		librec.buildModel();
		
		// get all predictions and check if the number of returned prediction is correct
		Table <Integer,Integer,Double> predictions = librec.getAllPredictions();
		int numPredictions = predictions.size();
		assertTrue(String.format("Number of predictions is %d, expected 24", numPredictions),
				numPredictions == 24);
		
		// get individual predictions for all user-item pairs
		for (int user : users){
			for (int item : items){
				librec.getPrediction(user, item);
			}
		}
		
		// perform an evaluation
		librec.evaluate();
		librec.getEvalResult(Recommender.Measure.MAE);
		librec.getEvalResult(Recommender.Measure.RMSE);
		librec.getEvalResult(Recommender.Measure.NDCG);
		librec.getEvalResult(Recommender.Measure.Pre10);
		librec.getEvalResult(Recommender.Measure.Rec10);
	}
}
