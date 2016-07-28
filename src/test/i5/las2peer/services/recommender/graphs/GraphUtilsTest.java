package i5.las2peer.services.recommender.graphs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import i5.las2peer.p2p.LocalNode;
import i5.las2peer.p2p.ServiceNameVersion;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.services.recommender.RecommenderMain;
import i5.las2peer.services.recommender.librec.data.SparseMatrix;
import i5.las2peer.services.recommender.rmi.RMIMyService;
import i5.las2peer.testing.MockAgentFactory;
import i5.las2peer.webConnector.WebConnector;
import i5.las2peer.webConnector.client.ClientResponse;
import i5.las2peer.webConnector.client.MiniClient;

/**
 * Example Test Class demonstrating a basic JUnit test structure.
 *
 */
public class GraphUtilsTest {

	/**
	 * Test the the construction of TF-IDF matrices to be used by the GF kNN algorithm
	 */
	@Test
	public void testGetTfidfMatrix()
	{
		Table<Integer,Integer,Double> table = HashBasedTable.create();
		
		/*
		 * Create table
		 * u\i  0  1  2  
		 *  0   1     
		 *  1   
		 *  2   2     4
		 *  3   3     1
		 *  4         2
		 */
		table.put(0, 0, 1.0);
		table.put(2, 0, 2.0);
		table.put(2, 2, 4.0);
		table.put(3, 0, 3.0);
		table.put(3, 2, 1.0);
		table.put(4, 2, 2.0);
		
		int numUsers = 5;
		int numItems = 3;
		int numElements = 6;
		
		System.out.println("numUsers=" + numUsers
				+ " numItems=" + numItems
				+ " numElements=" + numElements);
		
		SparseMatrix matrix = new SparseMatrix(5, 3, table);
		
		GFSparseMatrix gfUserMatrix = GraphUtils.getTfidfMatrix(matrix, true);
		GFSparseMatrix gfItemMatrix = GraphUtils.getTfidfMatrix(matrix, false);
		
		// user matrix should contain 5 vectors of length 3
		System.out.println("gfUserMatrix: numVector=" + gfUserMatrix.numVector
				+ " numDimension=" + gfUserMatrix.numDimension
				+ " VectorIndex(0)=" + gfUserMatrix.getVectorIndex(0)
				+ " VectorIndex(numUsers)=" + gfUserMatrix.getVectorIndex(numUsers));
		assertTrue(gfUserMatrix.numVector == numUsers);
		assertTrue(gfUserMatrix.numDimension == numItems);
		assertTrue(gfUserMatrix.getVectorIndex(0) == 0);
		assertTrue(gfUserMatrix.getVectorIndex(numUsers) == numElements-1);
		
		// item matrix should contain 3 vectors of length 5
		System.out.println("gfItemMatrix: numVector=" + gfItemMatrix.numVector
				+ " numDimension=" + gfItemMatrix.numDimension
				+ " VectorIndex(0)=" + gfItemMatrix.getVectorIndex(0)
				+ " VectorIndex(numItems)=" + gfItemMatrix.getVectorIndex(numItems));
		assertTrue(gfItemMatrix.numVector == numItems);
		assertTrue(gfItemMatrix.numDimension == numUsers);
		assertTrue(gfItemMatrix.getVectorIndex(0) == 0);
		assertTrue(gfItemMatrix.getVectorIndex(numItems) == numElements-1);
		
		System.out.println("gfUserMatrix:");
		for (int vectorIdx = 0; vectorIdx < gfUserMatrix.numVector; vectorIdx++){
			System.out.print("Vector " + vectorIdx + ": ");
			for (int elementIdx = gfUserMatrix.getVectorIndex(vectorIdx); elementIdx < gfUserMatrix.getVectorIndex(vectorIdx+1); elementIdx++){
				System.out.print(gfUserMatrix.getDimension(elementIdx) + "/" + gfUserMatrix.getValue(elementIdx) + " ");
			}
			System.out.println();
		}
		
		System.out.println("gfItemMatrix:");
		for (int vectorIdx = 0; vectorIdx < gfItemMatrix.numVector; vectorIdx++){
			System.out.print("Vector " + vectorIdx + ": ");
			for (int elementIdx = gfItemMatrix.getVectorIndex(vectorIdx); elementIdx < gfItemMatrix.getVectorIndex(vectorIdx+1); elementIdx++){
				System.out.print(gfItemMatrix.getDimension(elementIdx) + "/" + gfItemMatrix.getValue(elementIdx) + " ");
			}
			System.out.println();
		}

	}

}
