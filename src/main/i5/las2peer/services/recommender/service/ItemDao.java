package i5.las2peer.services.recommender.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.dbutils.DbUtils;

import com.mysql.jdbc.Statement;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

public class ItemDao {
	private DatabaseManager dbm;
	
	public ItemDao(DatabaseManager dbm) {
		this.dbm = dbm;
	}

	public int addItem() throws SQLException{
		Connection conn = null;
		PreparedStatement stmnt = null;
		int id = -1;
		
		try{
			conn = dbm.getConnection();
			stmnt = conn.prepareStatement("INSERT INTO Item VALUES ()", Statement.RETURN_GENERATED_KEYS);
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

	public void deleteItem(int itemId) throws SQLException{
		Connection conn = null;
		PreparedStatement deleteRatingsStmnt = null;
		PreparedStatement deleteTagsStmnt = null;
		PreparedStatement deleteItemStmnt = null;
		
		try{
			conn = dbm.getConnection();
			
			// first delete ratings and taggings, then delete the user
			deleteRatingsStmnt = conn.prepareStatement("DELETE FROM Rating WHERE ItemId=?");
			deleteRatingsStmnt.setInt(1, itemId);
			deleteRatingsStmnt.executeUpdate();
			
			deleteTagsStmnt = conn.prepareStatement("DELETE FROM Tag WHERE ItemId=?");
			deleteTagsStmnt.setInt(1, itemId);
			deleteTagsStmnt.executeUpdate();
			
			deleteItemStmnt = conn.prepareStatement("DELETE FROM Item WHERE ItemId=?");
			deleteItemStmnt.setInt(1, itemId);
			deleteItemStmnt.executeUpdate();
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(deleteRatingsStmnt);
			DbUtils.closeQuietly(deleteTagsStmnt);
			DbUtils.closeQuietly(deleteItemStmnt);
		}
		return;
	}

	public JSONArray getItems() throws SQLException {
		Connection conn = null;
		PreparedStatement stmnt = null;
		ResultSet rs = null;
		JSONArray itemsArray = new JSONArray();
		
		try{
			conn = dbm.getConnection();
			stmnt = conn.prepareStatement("SELECT ItemId FROM Item");
			rs = stmnt.executeQuery();
			while (rs.next()){
				JSONObject itemObj = new JSONObject();
				itemObj.put("ItemId", rs.getInt(1));
				itemsArray.add(itemObj);
			}
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
			DbUtils.closeQuietly(rs);
		}
		
		return itemsArray;
	}

	public JSONArray getRatings(int itemId) throws SQLException {
		Connection conn = null;
		PreparedStatement stmnt = null;
		ResultSet rs = null;
		JSONArray ratingsArray = new JSONArray();
		
		try{
			conn = dbm.getConnection();
			stmnt = conn.prepareStatement("SELECT UserId,Time,Rating FROM Rating WHERE ItemId=?");
			stmnt.setInt(1, itemId);
			rs = stmnt.executeQuery();
			while (rs.next()){
				JSONObject ratingObj = new JSONObject();
				ratingObj.put("userId", rs.getInt(1));
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

	public JSONArray getTaggings(int itemId) throws SQLException {
		Connection conn = null;
		PreparedStatement stmnt = null;
		ResultSet rs = null;
		JSONArray taggingsArray = new JSONArray();
		
		try{
			conn = dbm.getConnection();
			stmnt = conn.prepareStatement("SELECT UserId,Time,Tag FROM Tag WHERE ItemId=?");
			stmnt.setInt(1, itemId);
			rs = stmnt.executeQuery();
			while (rs.next()){
				JSONObject taggingObj = new JSONObject();
				taggingObj.put("userId", rs.getInt(1));
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

}
