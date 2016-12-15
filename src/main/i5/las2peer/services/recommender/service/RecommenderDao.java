package i5.las2peer.services.recommender.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.dbutils.DbUtils;

import com.google.common.collect.Table;

import i5.las2peer.services.recommender.entities.Rating;
import i5.las2peer.services.recommender.entities.Tagging;
import i5.las2peer.services.recommender.librec.main.LibRec;
import net.minidev.json.JSONObject;

public class RecommenderDao {

	private DatabaseManager dbm;
	
	public RecommenderDao(DatabaseManager dbm) {
		this.dbm = dbm;
	}

	public void runRecommender(
			String recAlgo,
			String cdAlgo,
			String cdWtSteps,
			String graphMethod,
			String graphKnnK,
			String graphKnnSim,
			String recFactors,
			String recIters,
			String recLearnRate,
			String recLearnRateN,
			String recLearnRateF,
			String recLearnRateC,
			String recLearnRateCN,
			String recLearnRateCF,
			String recLearnRateMu,
			String recLambda,
			String recLambdaB,
			String recLambdaN,
			String recLambdaF,
			String recLambdaC,
			String recLambdaCN,
			String recLambdaCF,
			String recBeta,
			String recBins,
			String recTcnsvdCBins,
			String recWrmfAlpha,
			String recKnnSim,
			String recKnnK
			) throws Exception {
		// Check if input is valid
		List<String> recAlgos = Arrays.asList(new String[] {"itemavg","itemknn","wrmf","svd","nsvd","tsvd","tnsvd",
				"cnsvd","cnsvdfast","tcnsvd","tcnsvdfast"});
		List<String> cdAlgos = Arrays.asList(new String[] {"wt","dmid","slpa"});
		List<String> graphMethods = Arrays.asList(new String[] {"ratings","tags"});
		List<String> graphKnnSims = Arrays.asList(new String[] {"cosine","pearson","jmsd"});
		List<String> recKnnSims = Arrays.asList(new String[] {"cos","cos-binary","msd","cpc","exjaccard","pcc"});
		
		if (!recAlgos.contains(recAlgo.toLowerCase()))
			throw new Exception("Invalid input: recommendation algorithm.");
		if (!cdAlgos.contains(cdAlgo.toLowerCase()))
			throw new Exception("Invalid input: community detection algorithm.");
		if (!isInt(cdWtSteps))
			throw new Exception("Invalid input: Walktrap steps.");
		if (!graphMethods.contains(graphMethod))
			throw new Exception("Invalid input: graph construction method.");
		if (!isInt(graphKnnK))
			throw new Exception("Invalid input: graph construction k parameter.");
		if (!graphKnnSims.contains(graphKnnSim))
			throw new Exception("Invalid input: graph construction similarity measure.");
		if (!isInt(recFactors))
			throw new Exception("Invalid input: matrix factorization factor size.");
		if (!isInt(recIters))
			throw new Exception("Invalid input: max. number of learning iterations.");
		if (!isDouble(recLearnRate)
				|| !isDouble(recLearnRateN)
				|| !isDouble(recLearnRateF)
				|| !isDouble(recLearnRateC)
				|| !isDouble(recLearnRateCN)
				|| !isDouble(recLearnRateCF)
				|| !isDouble(recLearnRateMu))
			throw new Exception("Invalid input: learning rates.");
		if (!isDouble(recLambda)
				|| !isDouble(recLambdaB)
				|| !isDouble(recLambdaN)
				|| !isDouble(recLambdaF)
				|| !isDouble(recLambdaC)
				|| !isDouble(recLambdaCN)
				|| !isDouble(recLambdaCF)
				|| !isDouble(recBeta))
			throw new Exception("Invalid input: regularization factors.");
		if (!isInt(recBins))
			throw new Exception("Invalid input: time bins.");
		if (!isInt(recTcnsvdCBins))
			throw new Exception("Invalid input: TCNSVD community time bins.");
		if (!isDouble(recWrmfAlpha))
			throw new Exception("Invalid input: WRMF alpha parameter.");
		if (!recKnnSims.contains(recKnnSim))
			throw new Exception("Invalid input: ItemKNN similarity measure.");
		if (!isInt(recKnnK)){
			throw new Exception("Invalid input: ItemKNN k parameter");
		}
			
		// Create LibRec object and configure
		LibRec librec = new LibRec(recAlgo);
		
		librec.setParameter("timeComNeighSVD++",
				String.format("-beta %s -bins %s -cbins %s", recBeta, recBins, recTcnsvdCBins));
		librec.setParameter("cd.algo", cdAlgo);
		librec.setParameter("cd.walktrap.steps", cdWtSteps);
		librec.setParameter("graph.method", graphMethod);
		librec.setParameter("graph.knn.k", graphKnnK);
		librec.setParameter("graph.knn.sim", graphKnnSim);
		librec.setParameter("num.factors", recFactors);
		librec.setParameter("num.max.iter", recIters);
		librec.setParameter("learn.rate", String.format("%s -n %s -f %s -c %s -cn %s -cf %s  -max -1 -decay 0.95",
				recLearnRate, recLearnRateN, recLearnRateF, recLearnRateC, recLearnRateCN, recLearnRateCF));
		librec.setParameter("reg.lambda", String.format("%s -n %s -f %s -c %s -cn %s -cf %s",
				recLambda, recLambdaN, recLambdaF, recLambdaC, recLambdaCN, recLambdaCF));
		librec.setParameter("timeSVD++", String.format("-beta %s -bins %s", recBeta, recBins));
		librec.setParameter("WRMF", String.format("-alpha", recWrmfAlpha));
		librec.setParameter("similarity", recKnnSim);
		librec.setParameter("num.neighbors", recKnnK);
		librec.setParameter("evaluation.setup", "--early-stop loss");
		
		// Get rating data from database
		List<Rating> ratings = queryGetRatings();
		List<Tagging> tags = queryGetTaggings();
		
		// Set rating and tagging data
		librec.setRatings(ratings);
		librec.setTaggings(tags);
		librec.printDatasetSpecifications();
		
		// Build model
		librec.buildModel();
		
		// Predict ratings for all user-item pairs
		Table <Integer,Integer,Double> predictions = librec.getAllPredictions();
		
		// Store ratings in database
		queryPutPredictions(predictions);
		
		return;
	}

	public JSONObject getRecommenderStatus() throws SQLException{
		Connection conn = null;
		PreparedStatement stmnt = null;
		
		try{
			conn = dbm.getConnection();
			stmnt = conn.prepareStatement("INSERT INTO User VALUES ()", Statement.RETURN_GENERATED_KEYS);
			stmnt.executeUpdate();
			ResultSet rs = stmnt.getGeneratedKeys();
			if (rs.next()){
				
			}
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
		}
		JSONObject json = new JSONObject();
		return json;
	}

	private List<Rating> queryGetRatings() throws SQLException {
		Connection conn = null;
		PreparedStatement stmnt = null;
		ResultSet rs = null;
		List<Rating> ratingsList = new LinkedList<Rating>();
		
		try{
			conn = dbm.getConnection();
			stmnt = conn.prepareStatement("SELECT UserId,ItemId,Time,Rating FROM Rating");
			rs = stmnt.executeQuery();
			while (rs.next()){
				Rating rating = new Rating();
				rating.setUserId(rs.getInt(1));
				rating.setItemId(rs.getInt(2));
				rating.setTimestamp(rs.getTimestamp(3).getTime()/1000);
				rating.setRating(rs.getDouble(4));
				ratingsList.add(rating);
			}
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
			DbUtils.closeQuietly(rs);
		}
		
		return ratingsList;
	}

	private List<Tagging> queryGetTaggings() throws SQLException {
		Connection conn = null;
		PreparedStatement stmnt = null;
		ResultSet rs = null;
		List<Tagging> taggingsList = new LinkedList<Tagging>();
		
		try{
			conn = dbm.getConnection();
			stmnt = conn.prepareStatement("SELECT UserId,ItemId,Time,Tag FROM Tag");
			rs = stmnt.executeQuery();
			while (rs.next()){
				Tagging tagging = new Tagging();
				tagging.setUserId(rs.getInt(1));
				tagging.setItemId(rs.getInt(2));
				tagging.setTimestamp(rs.getTimestamp(3).getTime()/1000);
				tagging.setTag(rs.getString(4));
				taggingsList.add(tagging);
			}
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
			DbUtils.closeQuietly(rs);
		}
		
		return taggingsList;
	}

	private void queryPutPredictions(Table<Integer, Integer, Double> predictions) throws SQLException {
		Connection conn = null;
		Statement deleteStmnt = null;
		PreparedStatement insertStmnt = null;
		
		try{
			conn = dbm.getConnection();
			deleteStmnt = conn.createStatement();
			deleteStmnt.executeUpdate("DELETE FROM Prediction");
			
			insertStmnt = conn.prepareStatement("INSERT INTO Prediction (UserId,ItemId,Prediction) VALUES (?,?,?)");
			for (Table.Cell<Integer,Integer,Double> cell : predictions.cellSet()){
				int user = cell.getRowKey();
				int item = cell.getColumnKey();
				double prediction = cell.getValue();
				insertStmnt.setInt(1, user);
				insertStmnt.setInt(2, item);
				insertStmnt.setDouble(3, prediction);
				insertStmnt.executeUpdate();
			}
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(insertStmnt);
			DbUtils.closeQuietly(deleteStmnt);
		}
	}

	private boolean isInt(String s){
		try{
			Integer.parseInt(s);
		}
		catch (NumberFormatException e){
			return false;
		}
		return true;
	}
	
	private boolean isDouble(String s){
		try{
			Double.parseDouble(s);
		}
		catch (NumberFormatException e){
			return false;
		}
		return true;
	}
	
}
