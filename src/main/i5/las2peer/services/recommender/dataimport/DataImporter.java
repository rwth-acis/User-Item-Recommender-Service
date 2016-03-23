package i5.las2peer.services.recommender.dataimport;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.dbutils.DbUtils;

import i5.las2peer.services.recommender.DatabaseManager;

public class DataImporter {
	
	private DatabaseManager dbm;
	
	public DataImporter(DatabaseManager dbm){
		this.dbm = dbm;
	}

	public void importData(String data, String datasetName, String fileName) throws DatasetUnknownException, DatasetFileUnknownException, SQLException, IOException, ParseException{
		if(datasetName.contains("MovieLens")){
			if(fileName.contains("movies")){
				importMovieLensMovies(data);
			}
			else if(fileName.contains("ratings")){
				importMovieLensRatings(data);
			}
			else if(fileName.contains("tags")){
				importMovieLensTags(data);
			}
			else{
				throw new DatasetFileUnknownException();
			}
		}
		else{
			throw new DatasetUnknownException();
		}
	}
	
	public void importMovieLensMovies(String data) throws SQLException, ParseException, IOException{
		Connection conn = null;
		Statement deleteStmnt = null;
		PreparedStatement stmnt = null;
		
		// Parse data and check contained fields
		CSVParser parser = CSVParser.parse(data, CSVFormat.DEFAULT.withHeader());
		Map<String,Integer> headers = parser.getHeaderMap();
		if(!headers.containsKey("movieId")
				|| !headers.containsKey("title")
				|| !headers.containsKey("genres")){
			throw new ParseException("Data does not contain the correct fields.", 0);
		}
		
		try{
			conn = dbm.getConnection();

			// Clear table before importing dataset
			try{
				deleteStmnt = conn.createStatement();
				deleteStmnt.executeUpdate("DELETE FROM Movie");
			}
			finally{
				DbUtils.closeQuietly(stmnt);
			}
			
			// Add each record to the database
			try{
				stmnt = conn.prepareStatement("INSERT INTO Movie (MovieId,Title) VALUES (?, ?)");
				for(CSVRecord record : parser){
					if(!(record.isConsistent())){
						throw new ParseException("Record nr. " + record.getRecordNumber() + "contains " + record.size() + " fields. "
								+ "Should contain 3", 0);
					}
				    try{
				    	stmnt.setInt(1, Integer.valueOf(record.get("movieId")));
				    }
				    catch(NumberFormatException e){
				    	throw new ParseException("Numeric value could not be parsed.", 0);
				    }
					stmnt.setString(2, record.get("title"));
					stmnt.executeUpdate();
				}
			}
			finally{
				DbUtils.closeQuietly(stmnt);
			}
		}
		finally{
			DbUtils.closeQuietly(conn);
		}
	}
	
	public void importMovieLensRatings(String data) throws SQLException, IOException, ParseException{
		Connection conn = null;
		Statement deleteStmnt = null;
		PreparedStatement stmnt = null;
		
		// Parse data and check contained fields
		CSVParser parser = CSVParser.parse(data, CSVFormat.DEFAULT.withHeader());
		Map<String,Integer> headers = parser.getHeaderMap();
		if(!headers.containsKey("userId")
				|| !headers.containsKey("movieId")
				|| !headers.containsKey("rating")
				|| !headers.containsKey("timestamp")){
			throw new ParseException("Data does not contain the correct fields.", 0);
		}
		
		try{
			conn = dbm.getConnection();

			// Clear table before importing dataset
			try{
				deleteStmnt = conn.createStatement();
				deleteStmnt.executeUpdate("DELETE FROM Rating");
			}
			finally{
				DbUtils.closeQuietly(stmnt);
			}
			
			// Add each record to the database
			try{
				stmnt = conn.prepareStatement("INSERT INTO Rating (UserId, MovieId, Rating, Time) VALUES (?, ?, ?, ?)");
				for(CSVRecord record : parser){
					if(!record.isSet("userId") || !record.isSet("movieId") || !record.isSet("rating") || !record.isSet("timestamp")){
						throw new ParseException("Empty field(s) in record nr. " + record.getRecordNumber(), 0);
					}
					try{
						stmnt.setInt(1, Integer.valueOf(record.get("userId")));
						stmnt.setInt(2, Integer.valueOf(record.get("movieId")));
						stmnt.setFloat(3, Float.valueOf(record.get("rating")));
						stmnt.setTimestamp(4, new Timestamp(Long.valueOf(record.get("timestamp"))));
				    }
				    catch(NumberFormatException e){
				    	throw new ParseException("Numeric value could not be parsed.", 0);
				    }

					stmnt.executeUpdate();
				}
			}
			finally{
				DbUtils.closeQuietly(stmnt);
			}
		}
		finally{
			DbUtils.closeQuietly(conn);
		}
	}
	
	public void importMovieLensTags(String data) throws SQLException, IOException, ParseException{
		Connection conn = null;
		Statement deleteStmnt = null;
		PreparedStatement stmnt = null;
		
		// Parse data and check contained fields
		CSVParser parser = CSVParser.parse(data, CSVFormat.DEFAULT.withHeader());
		Map<String,Integer> headers = parser.getHeaderMap();
		if(!headers.containsKey("userId")
				|| !headers.containsKey("movieId")
				|| !headers.containsKey("tag")
				|| !headers.containsKey("timestamp")){
			throw new ParseException("Data does not contain the correct fields.", 0);
		}
		
		try{
			conn = dbm.getConnection();

			// Clear table before importing dataset
			try{
				deleteStmnt = conn.createStatement();
				deleteStmnt.executeUpdate("DELETE FROM Tag");
			}
			finally{
				DbUtils.closeQuietly(stmnt);
			}
			
			// Add each record to the database
			try{
				stmnt = conn.prepareStatement("INSERT INTO Tag (UserId, MovieId, Tag, Time) VALUES (?, ?, ?, ?)");
				for(CSVRecord record : parser){
					if(!record.isSet("userId") || !record.isSet("movieId") || !record.isSet("tag") || !record.isSet("timestamp")){
						throw new ParseException("Empty field(s) in record nr. " + record.getRecordNumber(), 0);
					}
					try{
						stmnt.setInt(1, Integer.valueOf(record.get("userId")));
						stmnt.setInt(2, Integer.valueOf(record.get("movieId")));
						stmnt.setString(3, record.get("tag"));
						stmnt.setTimestamp(4, new Timestamp(Long.valueOf(record.get("timestamp"))));
				    }	
				    catch(NumberFormatException e){
				    	throw new ParseException("Numeric value could not be parsed.", 0);
				    }	
					stmnt.executeUpdate();
				}
			}
			finally{
				DbUtils.closeQuietly(stmnt);
			}
		}
		finally{
			DbUtils.closeQuietly(conn);
		}
	}
	
}
