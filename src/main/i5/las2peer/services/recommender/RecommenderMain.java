package i5.las2peer.services.recommender;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.sql.SQLException;
import java.text.ParseException;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import i5.las2peer.api.Service;
import i5.las2peer.restMapper.HttpResponse;
import i5.las2peer.restMapper.MediaType;
import i5.las2peer.restMapper.RESTMapper;
import i5.las2peer.restMapper.annotations.ContentParam;
import i5.las2peer.restMapper.annotations.Version;
import i5.las2peer.restMapper.tools.ValidationResult;
import i5.las2peer.restMapper.tools.XMLCheck;
import i5.las2peer.services.recommender.dataimport.DataImporter;
import i5.las2peer.services.recommender.dataimport.DatasetFileUnknownException;
import i5.las2peer.services.recommender.dataimport.DatasetUnknownException;
import i5.las2peer.services.recommender.utils.Logger;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Info;
import io.swagger.annotations.SwaggerDefinition;

/**
 * Recommender Service
 * 
 * Recommender service that uses the LAS2peer Web-Connector for RESTful access to it.
 * 
 */
@Path("/api")
@Version("0.1") // this annotation is used by the XML mapper
@Api(value="Recommender")
@SwaggerDefinition(
		info = @Info(
			title = "Recommender Service",
			version = "0.1",
			description = "A recommender service that uses the LAS2peer Web-Connector for RESTful access to it."
		))
public class RecommenderMain extends Service {

	/*
	 * Database configuration
	 */
	private String jdbcDriverClassName;
	private String jdbcLogin;
	private String jdbcPass;
	private String jdbcUrl;
	private String jdbcSchema;
	private DatabaseManager dbm;

	public RecommenderMain() {
		// read and set properties values
		// IF THE SERVICE CLASS NAME IS CHANGED, THE PROPERTIES FILE NAME NEED TO BE CHANGED TOO!
		setFieldValues();
		// instantiate a database manager to handle database connection pooling and credentials
		dbm = new DatabaseManager(jdbcDriverClassName, jdbcLogin, jdbcPass, jdbcUrl, jdbcSchema);
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	// Service methods.
	// //////////////////////////////////////////////////////////////////////////////////////
	
	// Service methods for database initialization.
	
	/**
	 * Method that initializes the database.
	 * It checks if the required tables exist. If they exist it is assumed that they are set up correctly.
	 * If a table is missing it is created with the corresponding fields, keys, etc.
	 * 
	 * @param force true forces existing tables to be dropped and recreated.
	 * returns HTTP response with status code 200 for success and 500 for failure.
	 * @return HttpResponse 200 for success and 500 for error
	 */
	@GET
	@Path("/initdb")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "Initialize Database",
			notes = "Create the required tables in the database if they do not already exist.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Tables created"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Database error")
	})
	public HttpResponse initializeDb(
			@ApiParam(value="Force existing tables to be dropped and recreated", required=false)
			@DefaultValue("false") @QueryParam("force")
				boolean force) {
		HttpResponse response;
		
		try{
			dbm.initializeDb(force);
			response = new HttpResponse("Tables created", HttpURLConnection.HTTP_OK);
		}
		catch(Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("Database error", HttpURLConnection.HTTP_INTERNAL_ERROR);
		}
		
		return response;
	}
	
	// Service methods for dataset import.

	/**
	 * Import dataset files
	 * Imports a dataset file. So far the only supported dataset format is MovieLens. The movies.csv file must be imported first as
	 * otherwise foreign key constraints are not satisfied when importing ratings or tags for movies that have not yet been imported.  
	 * Curl commands that can be used to access this service to import MovieLens files:
	 *  curl --header 'Content-Type: text/csv' --header 'Accept: text/plain' --data-binary @movies.csv 'http://localhost:8082/api/import/MovieLens/movies'
	 *  curl --header 'Content-Type: text/csv' --header 'Accept: text/plain' --data-binary @ratings.csv 'http://localhost:8082/api/import/MovieLens/ratings'
	 *  curl --header 'Content-Type: text/csv' --header 'Accept: text/plain' --data-binary @tags.csv 'http://localhost:8082/api/import/MovieLens/tags'
	 * 
	 * @param data Data to import
	 * @param datasetName Dataset type, e.g. MovieLens
	 * @param fileName File name, e.g. ratings
	 * @return HTTP response with status code 200 for success and 500 for failure.
	 */
	@POST
	@Path("/import/{dataset}/{file}")
	@Consumes(MediaType.TEXT_CSV)
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "Import a dataset file",
			notes = "Import a file from a dataset."
					+ " Allowed datasets: MovieLens."
					+ " Allowed files for the MovieLens dataset: movies, ratings, tags.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Dataset inserted into database"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Could not read dataset or error accessing the database")
	})
	public HttpResponse importMovieLens(
			@ApiParam(value="CSV data to import", required=true) @ContentParam String data,
			@ApiParam(value="Dataset type", required=true) @PathParam("dataset") String datasetName,
			@ApiParam(value="File name", required=true) @PathParam("file") String fileName)	{
		
		HttpResponse response;
		
		DataImporter importer = new DataImporter(dbm);
		
		if (data == null || data.isEmpty()){
			response = new HttpResponse("No data received",
					HttpURLConnection.HTTP_INTERNAL_ERROR);
		}
		else{
			try{
				importer.importData(data, datasetName, fileName);
			}
			catch(DatasetUnknownException e){
				Logger.logError(this, e);
				response = new HttpResponse("Dataset name \'" + datasetName + "\' is not allowed. Allowed: \'MovieLens\'",
						HttpURLConnection.HTTP_INTERNAL_ERROR);
				return response;
			}
			catch(DatasetFileUnknownException e){
				Logger.logError(this, e);
				response = new HttpResponse("Dataset file name \'" + fileName + "\' is not allowed. Allowed for MovieLens: \'movies\', \'ratings\', \'tags\'",
						HttpURLConnection.HTTP_INTERNAL_ERROR);
				return response;
			}
			catch(ParseException e){
				Logger.logError(this, e);
				response = new HttpResponse("Error parsing the dataset: " + e.getMessage(),
						HttpURLConnection.HTTP_INTERNAL_ERROR);
				return response;
			}
			catch(IOException e){
				Logger.logError(this, e);
				response = new HttpResponse("I/O error while parsing: " + e.getMessage(),
						HttpURLConnection.HTTP_INTERNAL_ERROR);
				return response;
			}
			catch(SQLException e){
				Logger.logError(this, e);
				response = new HttpResponse("SQL error: " + e.getMessage(),
						HttpURLConnection.HTTP_INTERNAL_ERROR);
				return response;
			}
			catch(Exception e){
				Logger.logError(this, e);
				response = new HttpResponse("Unknown error: " + e.getMessage(),
						HttpURLConnection.HTTP_INTERNAL_ERROR);
				e.printStackTrace();
				return response;
			}
		}
		
		response = new HttpResponse("Dataset inserted into database", HttpURLConnection.HTTP_OK);
		return response;
	}
	
	// Service methods for database initialization.

	
	/**
	 * Description
	 * ...
	 * 
	 * @param param1 Description of parameter param1
	 * @param param2 Description of parameter param2
	 * @return HTTP response with status code 200 for success and 500 for failure.
	 */
	@POST
	@Path("/import/{dataset}/{file}")
	@Consumes(MediaType.TEXT_CSV)
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "Import a dataset file",
			notes = "Import a file from a dataset."
					+ " Allowed datasets: MovieLens."
					+ " Allowed files for the MovieLens dataset: movies, ratings, tags.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Dataset inserted into database"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Could not read dataset or error accessing the database")
	})
	public HttpResponse myMethod(
			@ApiParam(value="Short description", required=true) @ContentParam String param1,
			@ApiParam(value="Short description", required=true) @PathParam("param2") String param2){
		// ... code ...
		 return null;
	}
	
	// //////////////////////////////////////////////////////////////////////////////////////
	// Methods required by the LAS2peer framework.
	// //////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Method for debugging purposes.
	 * Here the concept of restMapping validation is shown.
	 * It is important to check, if all annotations are correct and consistent.
	 * Otherwise the service will not be accessible by the WebConnector.
	 * Best to do it in the unit tests.
	 * To avoid being overlooked/ignored the method is implemented here and not in the test section.
	 * @return true, if mapping correct
	 */
	public boolean debugMapping() {
		String XML_LOCATION = "./restMapping.xml";
		String xml = getRESTMapping();

		try {
			RESTMapper.writeFile(XML_LOCATION, xml);
		} catch (IOException e) {
			Logger.logError(this, e);
		}

		XMLCheck validator = new XMLCheck();
		ValidationResult result = validator.validate(xml);

		if (result.isValid()) {
			return true;
		}
		Logger.logError(this, result.getMessage());
		return false;
	}

	/**
	 * This method is needed for every RESTful application in LAS2peer. There is no need to change!
	 * 
	 * @return the mapping
	 */
	public String getRESTMapping() {
		String result = "";
		try {
			result = RESTMapper.getMethodsAsXML(this.getClass());
		} catch (Exception e) {
			Logger.logError(this, e);
		}
		return result;
	}

}
