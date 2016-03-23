package i5.las2peer.services.recommender;

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
		if (!tableList.contains("Movie")){
			createMovieTable();
		}
		if (!tableList.contains("Rating")){
			createRatingTable();
		}
		if (!tableList.contains("Tag")){
			createTagTable();
		}
		
	}
	
	private void queryDropAllTables() throws SQLException{
		Connection conn = null;
		PreparedStatement stmnt = null;
		
		try{
			conn = getConnection();
			stmnt = conn.prepareStatement("DROP TABLE IF EXISTS Rating,Tag,Movie");
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
	
	private void createMovieTable() throws SQLException{
		Connection conn = null;
		PreparedStatement stmnt = null;
		
		try{
			conn = getConnection();
			stmnt = conn.prepareStatement("CREATE TABLE Movie ("
					+ "MovieId INT(11) NOT NULL AUTO_INCREMENT,"
					+ "Title VARCHAR(45) NOT NULL,"
					+ "PRIMARY KEY (MovieId)"
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
					+ "UserId int(11) NOT NULL,"
					+ "MovieId int(11) NOT NULL,"
					+ "Time datetime NOT NULL,"
					+ "Rating int(11) NOT NULL,"
					+ "PRIMARY KEY (UserId,MovieId),"
					+ "KEY FK_RatingMovie (MovieId),"
					+ "CONSTRAINT FK_RatingMovie "
						+ "FOREIGN KEY (MovieId) "
						+ "REFERENCES Movie (MovieId) "
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
					+ "UserId varchar(45) NOT NULL,"
					+ "MovieId int(11) NOT NULL,"
					+ "Time varchar(45) NOT NULL,"
					+ "Tag varchar(45) NOT NULL,"
					+ "PRIMARY KEY (UserId,MovieId),"
					+ "KEY FK_TagMovie (MovieId),"
					+ "CONSTRAINT FK_TagMovie "
						+ "FOREIGN KEY (MovieId) "
						+ "REFERENCES Movie (MovieId) "
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
