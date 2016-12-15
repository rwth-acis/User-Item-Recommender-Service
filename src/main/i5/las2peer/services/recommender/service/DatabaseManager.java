package i5.las2peer.services.recommender.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbutils.DbUtils;

import i5.las2peer.api.Configurable;


/**
 * This class manages database credentials and provides connection from a connection pooling system
 *
 */
public class DatabaseManager extends Configurable{

//	private String jdbcDriverClassName;
//	private String jdbcLogin;
//	private String jdbcPass;
//	private String jdbcUrl;
//	private String jdbcSchema;
	private static BasicDataSource dataSource;

//	public DatabaseManager() {
//		setFieldValues();
//		// instantiate a database manager to handle database connection pooling and credentials
//		dataSource = new BasicDataSource();
//		dataSource.setDefaultAutoCommit(true);
//		dataSource.setDriverClassName(jdbcDriverClassName);
//		dataSource.setUsername(jdbcLogin);
//		dataSource.setPassword(jdbcPass);
//		dataSource.setUrl(jdbcUrl + jdbcSchema);
//		dataSource.setValidationQuery("SELECT 1");
//		dataSource.setDefaultQueryTimeout(1000);
//		dataSource.setMaxConnLifetimeMillis(100000);
//	}

	public DatabaseManager(String jdbcDriverClassName, String jdbcLogin, String jdbcPass, String jdbcUrl,String jdbcSchema) {
		// prepare and configure data source
		dataSource = new BasicDataSource();
		dataSource.setDefaultAutoCommit(true);
		dataSource.setDriverClassName(jdbcDriverClassName);
		dataSource.setUsername(jdbcLogin);
		dataSource.setPassword(jdbcPass);
		dataSource.setUrl(jdbcUrl + jdbcSchema);
		dataSource.setValidationQuery("SELECT 1");
		dataSource.setDefaultQueryTimeout(1000);
		dataSource.setMaxConnLifetimeMillis(100000);
	}

	public Connection getConnection() throws SQLException {
		return dataSource.getConnection();
	}
	
	public void initializeDb(boolean force) throws SQLException{
		List<String> tableList;
		if (force){
			queryDropAllTables();
		}
		
		// Get list of existing tables
		tableList = queryGetTableList();
		
		// Create every table that is not in the list
		if (!tableList.contains("Item")){
			createItemTable();
		}
		if (!tableList.contains("User")){
			createUserTable();
		}
		if (!tableList.contains("Rating")){
			createRatingTable();
		}
		if (!tableList.contains("Tag")){
			createTagTable();
		}
		if (!tableList.contains("Prediction")){
			createPredictionTable();
		}
	}
	
	private void queryDropAllTables() throws SQLException{
		Connection conn = null;
		PreparedStatement stmnt = null;
		
		try{
			conn = getConnection();
			stmnt = conn.prepareStatement("DROP TABLE IF EXISTS Prediction,Rating,Tag,Item,User");
			stmnt.executeUpdate();
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
		}
	}

	private List<String> queryGetTableList() throws SQLException{
		Connection conn = null;
		PreparedStatement stmnt = null;
		ResultSet rs = null;
		List<String> tables = new LinkedList<String>();

		try{
			conn = getConnection();
			stmnt = conn.prepareStatement("SHOW TABLES");
			rs = stmnt.executeQuery();
			
			while (rs.next()){
				tables.add(rs.getString(1)); // result set contains one column with the table names
			}
		}
		finally{
			DbUtils.closeQuietly(conn,stmnt,rs);
		}
		return tables;
	}
	
	private void createItemTable() throws SQLException{
		Connection conn = null;
		PreparedStatement stmnt = null;
		
		try{
			conn = getConnection();
//			stmnt = conn.prepareStatement("CREATE TABLE Item ("
//					+ "ItemId INT NOT NULL AUTO_INCREMENT,"
//					+ "Name VARCHAR(45) DEFAULT NULL,"
//					+ "PRIMARY KEY (ItemId)"
//					+ ")");
			stmnt = conn.prepareStatement("CREATE TABLE Item ("
					+ "ItemId INT NOT NULL AUTO_INCREMENT,"
					+ "PRIMARY KEY (ItemId)"
					+ ")");
			stmnt.executeUpdate();
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
		}
	}
	
	private void createUserTable() throws SQLException{
		Connection conn = null;
		PreparedStatement stmnt = null;
		
		try{
			conn = getConnection();
//			stmnt = conn.prepareStatement("CREATE TABLE User ("
//					+ "UserId INT NOT NULL AUTO_INCREMENT,"
//					+ "Name VARCHAR(45) DEFAULT NULL,"
//					+ "PRIMARY KEY (UserId)"
//					+ ")");
			stmnt = conn.prepareStatement("CREATE TABLE User ("
					+ "UserId INT NOT NULL AUTO_INCREMENT,"
					+ "PRIMARY KEY (UserId)"
					+ ")");
			stmnt.executeUpdate();
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
		}
	}
	
	private void createRatingTable() throws SQLException{
		Connection conn = null;
		PreparedStatement stmnt = null;
		
		try{
			conn = getConnection();
			stmnt = conn.prepareStatement("CREATE TABLE Rating("
					+ "UserId int NOT NULL,"
					+ "ItemId int NOT NULL,"
					+ "Time datetime NOT NULL,"
					+ "Rating decimal(2,1) NOT NULL,"
					+ "PRIMARY KEY (UserId,ItemId),"
					+ "KEY FK_RatingItem (ItemId),"
					+ "KEY FK_RatingUser (UserId),"
					+ "CONSTRAINT FK_RatingItem "
						+ "FOREIGN KEY (ItemId) "
						+ "REFERENCES Item (ItemId) "
						+ "ON DELETE NO ACTION "
						+ "ON UPDATE NO ACTION,"
					+ "CONSTRAINT FK_RatingUser "
						+ "FOREIGN KEY (UserId) "
						+ "REFERENCES User (UserId) "
						+ "ON DELETE NO ACTION "
						+ "ON UPDATE NO ACTION"
					+ ")");
			stmnt.executeUpdate();
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
		}
	}

	private void createPredictionTable() throws SQLException{
		Connection conn = null;
		PreparedStatement stmnt = null;
		
		try{
			conn = getConnection();
			stmnt = conn.prepareStatement("CREATE TABLE Prediction("
					+ "UserId int NOT NULL,"
					+ "ItemId int NOT NULL,"
					+ "Prediction decimal(6,5) NOT NULL,"
					+ "PRIMARY KEY (UserId,ItemId),"
					+ "KEY FK_RatingItem (ItemId),"
					+ "KEY FK_RatingUser (UserId),"
					+ "CONSTRAINT FK_PredictionItem "
						+ "FOREIGN KEY (ItemId) "
						+ "REFERENCES Item (ItemId) "
						+ "ON DELETE NO ACTION "
						+ "ON UPDATE NO ACTION,"
					+ "CONSTRAINT FK_PredictionUser "
						+ "FOREIGN KEY (UserId) "
						+ "REFERENCES User (UserId) "
						+ "ON DELETE NO ACTION "
						+ "ON UPDATE NO ACTION"
					+ ")");
			stmnt.executeUpdate();
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
		}
	}

	private void createTagTable() throws SQLException{
		Connection conn = null;
		PreparedStatement stmnt = null;
		
		try{
			conn = getConnection();
			stmnt = conn.prepareStatement("CREATE TABLE Tag ("
					+ "UserId int NOT NULL,"
					+ "ItemId int NOT NULL,"
					+ "Time datetime NOT NULL,"
					+ "Tag varchar(45) NOT NULL,"
					+ "PRIMARY KEY (UserId,ItemId),"
					+ "KEY FK_TagItem (ItemId),"
					+ "KEY FK_TagUser (UserId),"
					+ "CONSTRAINT FK_TagItem "
						+ "FOREIGN KEY (ItemId) "
						+ "REFERENCES Item (ItemId) "
						+ "ON DELETE NO ACTION "
						+ "ON UPDATE NO ACTION,"
					+ "CONSTRAINT FK_TagUser "
						+ "FOREIGN KEY (UserId) "
						+ "REFERENCES User (UserId) "
						+ "ON DELETE NO ACTION "
						+ "ON UPDATE NO ACTION"
					+ ")");
			stmnt.executeUpdate();
		}
		finally{
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(stmnt);
		}
	}

}
