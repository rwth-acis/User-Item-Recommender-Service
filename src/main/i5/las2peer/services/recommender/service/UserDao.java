package i5.las2peer.services.recommender.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.dbutils.DbUtils;

import com.mysql.jdbc.Statement;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

public class UserDao {

	private DatabaseManager dbm;
	
	public UserDao(DatabaseManager dbm) {
		this.dbm = dbm;
	}

	public int addUser() throws SQLException{
		Connection conn = null;
		PreparedStatement stmnt = null;
		int id = -1;
		
		try{
			conn = dbm.getConnection();
			stmnt = conn.prepareStatement("INSERT INTO User VALUES ()", Statement.RETURN_GENERATED_KEYS);
			stmnt.executeUpdate();
			ResultSet rs = stmnt.getGeneratedKeys();
			if (rs.next())
				id = rs.getInt(1);
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
		}
		
		if (id < 0){
			throw new SQLException("No generated keys returned.");
		}
		return id;
	}

	public void deleteUser(int userId) throws SQLException{
		Connection conn = null;
		PreparedStatement deleteRatingsStmnt = null;
		PreparedStatement deleteTagsStmnt = null;
		PreparedStatement deleteUserStmnt = null;
		
		try{
			conn = dbm.getConnection();
			
			// first delete ratings and taggings, then delete the user
			deleteRatingsStmnt = conn.prepareStatement("DELETE FROM Rating WHERE UserId=?");
			deleteRatingsStmnt.setInt(1, userId);
			deleteRatingsStmnt.executeUpdate();
			
			deleteTagsStmnt = conn.prepareStatement("DELETE FROM Tag WHERE UserId=?");
			deleteTagsStmnt.setInt(1, userId);
			deleteTagsStmnt.executeUpdate();
			
			deleteUserStmnt = conn.prepareStatement("DELETE FROM User WHERE UserId=?");
			deleteUserStmnt.setInt(1, userId);
			deleteUserStmnt.executeUpdate();
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(deleteRatingsStmnt);
			DbUtils.closeQuietly(deleteTagsStmnt);
			DbUtils.closeQuietly(deleteUserStmnt);
		}
		return;
	}

	public JSONArray getUsers() throws SQLException {
		Connection conn = null;
		PreparedStatement stmnt = null;
		ResultSet rs = null;
		JSONArray usersArray = new JSONArray();
		
		try{
			conn = dbm.getConnection();
			stmnt = conn.prepareStatement("SELECT UserId FROM User");
			rs = stmnt.executeQuery();
			while (rs.next()){
				JSONObject userObj = new JSONObject();
				userObj.put("userId", rs.getInt(1));
				usersArray.add(userObj);
			}
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
			DbUtils.closeQuietly(rs);
		}
		
		return usersArray;
	}

	public void addRating(int userId, int itemId, long timestamp, double rating) throws SQLException {
		Connection conn = null;
		PreparedStatement stmnt = null;
		
		try{
			conn = dbm.getConnection();
			stmnt = conn.prepareStatement("INSERT INTO Rating (UserId,ItemId,Time,Rating) VALUES (?,?,?,?)");
			stmnt.setInt(1, userId);
			stmnt.setInt(2, itemId);
			stmnt.setTimestamp(3, new Timestamp(timestamp*1000));
			stmnt.setDouble(4, rating);
			stmnt.executeUpdate();
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
		}
		
		return;
	}

	public void addTagging(int userId, int itemId, long timestamp, String tag) throws SQLException {
		Connection conn = null;
		PreparedStatement stmnt = null;
		
		try{
			conn = dbm.getConnection();
			stmnt = conn.prepareStatement("INSERT INTO Tag (UserId,ItemId,Time,Tag) VALUES (?,?,?,?)");
			stmnt.setInt(1, userId);
			stmnt.setInt(2, itemId);
			stmnt.setTimestamp(3, new Timestamp(timestamp*1000));
			stmnt.setString(4, tag);
			stmnt.executeUpdate();
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
		}
		
		return;
	}
	
	public void deleteRating(int userId, int itemId) throws SQLException {
		Connection conn = null;
		PreparedStatement stmnt = null;
		
		try{
			conn = dbm.getConnection();
			stmnt = conn.prepareStatement("DELETE FROM Rating WHERE UserId=? AND ItemId=?");
			stmnt.setInt(1, userId);
			stmnt.setInt(2, itemId);
			stmnt.executeUpdate();
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
		}
		
		return;
	}

	public void deleteTagging(int userId, int itemId, String tag) throws SQLException {
		Connection conn = null;
		PreparedStatement stmnt = null;
		
		try{
			conn = dbm.getConnection();
			stmnt = conn.prepareStatement("DELETE FROM Tag WHERE UserId=? AND ItemId=? AND Tag=?");
			stmnt.setInt(1, userId);
			stmnt.setInt(2, itemId);
			stmnt.setString(3, tag);
			stmnt.executeUpdate();
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
		}
		
		return;
	}

	public JSONArray getRatings(int userId) throws SQLException {
		Connection conn = null;
		PreparedStatement stmnt = null;
		ResultSet rs = null;
		JSONArray ratingsArray = new JSONArray();
		
		try{
			conn = dbm.getConnection();
			stmnt = conn.prepareStatement("SELECT ItemId,Time,Rating FROM Rating WHERE UserId=?");
			stmnt.setInt(1, userId);
			rs = stmnt.executeQuery();
			while (rs.next()){
				JSONObject ratingObj = new JSONObject();
				ratingObj.put("itemId", rs.getInt(1));
				ratingObj.put("timestamp", rs.getTimestamp(2).getTime()/1000);
				ratingObj.put("rating", rs.getDouble(3));
				ratingsArray.add(ratingObj);
			}
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
			DbUtils.closeQuietly(rs);
		}
		
		return ratingsArray;
	}

	public JSONArray getTaggings(int userId) throws SQLException {
		Connection conn = null;
		PreparedStatement stmnt = null;
		ResultSet rs = null;
		JSONArray taggingsArray = new JSONArray();
		
		try{
			conn = dbm.getConnection();
			stmnt = conn.prepareStatement("SELECT ItemId,Time,Tag FROM Tag WHERE UserId=?");
			stmnt.setInt(1, userId);
			rs = stmnt.executeQuery();
			while (rs.next()){
				JSONObject taggingObj = new JSONObject();
				taggingObj.put("itemId", rs.getInt(1));
				taggingObj.put("timestamp", rs.getTimestamp(2).getTime()/1000);
				taggingObj.put("tag", rs.getString(3));
				taggingsArray.add(taggingObj);
			}
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
			DbUtils.closeQuietly(rs);
		}
		
		return taggingsArray;
	}

	public double getPrediction(int userId, int itemId) throws Exception {
		Connection conn = null;
		PreparedStatement stmnt = null;
		ResultSet rs = null;
		double prediction;
		
		try{
			conn = dbm.getConnection();
			stmnt = conn.prepareStatement("SELECT Prediction FROM Prediction WHERE UserId=? AND ItemId=?");
			stmnt.setInt(1, userId);
			stmnt.setInt(2, itemId);
			rs = stmnt.executeQuery();
			if (!rs.next()){
				throw new Exception("Prediction not available.");
			}
			prediction = rs.getDouble(1);
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
			DbUtils.closeQuietly(rs);
		}
		
		return prediction;
	}

	public JSONArray getRecommendations(int userId, int count) throws SQLException {
		Connection conn = null;
		PreparedStatement stmnt1 = null;
		PreparedStatement stmnt2 = null;
		ResultSet rs1 = null;
		ResultSet rs2 = null;
		JSONArray recommendationsArray = new JSONArray();
		Set<Integer> ratedItems = new HashSet<Integer>();
		
		try{
			conn = dbm.getConnection();
			stmnt1 = conn.prepareStatement("SELECT ItemId FROM Rating WHERE UserId=?");
			stmnt1.setInt(1, userId);
			rs1 = stmnt1.executeQuery();
			while (rs1.next()){
				int item = rs1.getInt(1);
				ratedItems.add(item);
			}			
			stmnt2 = conn.prepareStatement("SELECT ItemId,Prediction FROM Prediction WHERE UserId=? ORDER BY Prediction DESC");
			stmnt2.setInt(1, userId);
			rs2 = stmnt2.executeQuery();
			int rank = 0;
			while (rs2.next() && rank < count){
				int item = rs2.getInt(1);
				if (!ratedItems.contains(item)){
					rank++;
					double prediction = rs2.getDouble(2);
					JSONObject predictionObj = new JSONObject();
					predictionObj.put("rank", rank);
					predictionObj.put("itemId", item);
					predictionObj.put("prediction", prediction);
					recommendationsArray.add(predictionObj);
				}
			}
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt1);
			DbUtils.closeQuietly(stmnt2);
			DbUtils.closeQuietly(rs1);
			DbUtils.closeQuietly(rs2);
		}
		
		return recommendationsArray;
	}

}
