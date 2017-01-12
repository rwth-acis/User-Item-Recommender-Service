package i5.las2peer.services.recommender;

import java.io.IOException;
import java.net.HttpURLConnection;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
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
import i5.las2peer.services.recommender.service.DataImporter;
import i5.las2peer.services.recommender.service.DatabaseManager;
import i5.las2peer.services.recommender.service.ItemDao;
import i5.las2peer.services.recommender.service.RecommenderDao;
import i5.las2peer.services.recommender.service.UserDao;
import i5.las2peer.services.recommender.utils.Logger;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Info;
import io.swagger.annotations.SwaggerDefinition;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

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
	// Service methods for database initialization.
	// //////////////////////////////////////////////////////////////////////////////////////
	
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
			@DefaultValue("false") @QueryParam("force") boolean force) {
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
	
//	// //////////////////////////////////////////////////////////////////////////////////////
//	// Service methods for dataset import.
//	// //////////////////////////////////////////////////////////////////////////////////////
//
//	/**
//	 * Import dataset files
//	 * Imports a dataset file. So far the only supported dataset format is MovieLens. The movies.csv file must be imported first as
//	 * otherwise foreign key constraints are not satisfied when importing ratings or tags for movies that have not yet been imported.  
//	 * Curl commands that can be used to access this service to import MovieLens files:
//	 *  curl --header 'Content-Type: text/csv' --header 'Accept: text/plain' --data-binary @movies.csv 'http://localhost:8082/api/import/MovieLens/movies'
//	 *  curl --header 'Content-Type: text/csv' --header 'Accept: text/plain' --data-binary @ratings.csv 'http://localhost:8082/api/import/MovieLens/ratings'
//	 *  curl --header 'Content-Type: text/csv' --header 'Accept: text/plain' --data-binary @tags.csv 'http://localhost:8082/api/import/MovieLens/tags'
//	 * 
//	 * @param data Data to import
//	 * @param datasetName Dataset type, e.g. MovieLens
//	 * @param fileName File name, e.g. ratings
//	 * @return HTTP response with status code 200 for success and 500 for failure.
//	 */
//	@POST
//	@Path("/import/{dataset}/{file}")
//	@Consumes(MediaType.TEXT_CSV)
//	@Produces(MediaType.APPLICATION_JSON)
//	@ApiOperation(
//			value = "Import a dataset file",
//			notes = "Import a file from a dataset."
//					+ " Allowed datasets: MovieLens."
//					+ " Allowed files for the MovieLens dataset: movies, ratings, tags.")
//	@ApiResponses(value = {
//			@ApiResponse(code = HttpURLConnection.HTTP_CREATED, message = "Dataset inserted into database"),
//			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Could not read dataset or error accessing the database")
//	})
//	public HttpResponse importMovieLens(
//			@ApiParam(value="CSV data to import", required=true) @ContentParam String data,
//			@ApiParam(value="Dataset type", required=true) @PathParam("dataset") String datasetName,
//			@ApiParam(value="File name", required=true) @PathParam("file") String fileName)	{
//		HttpResponse response;
//		DataImporter importer = new DataImporter(dbm);
//		if (data == null || data.isEmpty()){
//			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
//		}
//		else{
//			try{
//				importer.importData(data, datasetName, fileName);
//			}
//			catch(Exception e){
//				Logger.logError(this, e);
//				response = new HttpResponse("",	HttpURLConnection.HTTP_INTERNAL_ERROR);
//				return response;
//			}
//		}
//		
//		response = new HttpResponse("", HttpURLConnection.HTTP_CREATED);
//		return response;
//	}
	
	// //////////////////////////////////////////////////////////////////////////////////////
	// Service methods for user management.
	// //////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Add user
	 * Adds a new user to the system.
	 * 
	 * @return ID of the new user.
	 */
	@POST
	@Path("/users")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Add user",
			notes = "Adds a new user to the system.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_CREATED, message = "User added successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error adding the user")
	})
	public HttpResponse addUser(){
		HttpResponse response;
		UserDao dao = new UserDao(dbm);
		int userId;
		try{
			userId = dao.addUser();
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		JSONObject json = new JSONObject();
		json.put("userId", userId);
		response = new HttpResponse(json.toJSONString(), HttpURLConnection.HTTP_CREATED);
		return response;
	}
	
	/**
	 * Remove user
	 * Remove a user from the system.
	 * 
	 * @param userIdStr User identifier given as path parameter.
	 * @return HTTP status code 200 for success and status code 500 for failure.
	 */
	@DELETE
	@Path("/users/{userId}")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Remove user",
			notes = "Remove a user from the system.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "User removed successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error removing the user"),
	})
	public HttpResponse deleteUser(@PathParam("userId") String userIdStr){
		HttpResponse response;
		UserDao dao = new UserDao(dbm);
		try{
			int userId = Integer.valueOf(userIdStr);
			dao.deleteUser(userId);
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		response = new HttpResponse("", HttpURLConnection.HTTP_OK);
		return response;
	}
	
	/**
	 * Retrieve users
	 * Retrieves all users from the system.
	 * 
	 * @return All users.
	 */
	@GET
	@Path("/users")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Retrieve users",
			notes = "Retrieves all users.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Users retrieved successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error retrieving the users"),
	})
	public HttpResponse getUsers(){
		HttpResponse response;
		UserDao dao = new UserDao(dbm);
		JSONArray jsonUsersArray;
		try{
			jsonUsersArray = dao.getUsers();
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		JSONObject json = new JSONObject();
		json.put("users", jsonUsersArray);
		response = new HttpResponse(json.toJSONString(), HttpURLConnection.HTTP_OK);
		return response;
	}

	/**
	 * Add rating
	 * Adds a rating event for a user.
	 * 
	 * @param userIdStr User identifier given as path parameter.
	 * @param itemIdStr Item identifier given as query parameter.
	 * @param timestampStr Timestamp given as query parameter.
	 * @param ratingStr Rating value given as query parameter.
	 * 
	 * @return HTTP status code 200 for success and status code 500 for failure.
	 */
	@POST
	@Path("/users/{userId}/events/ratings")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Add rating",
			notes = "Adds a rating event for a user.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_CREATED, message = "Rating added successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error adding the rating"),
	})
	public HttpResponse addRating(
			@ApiParam(required=true) @PathParam("userId") String userIdStr,
			@ApiParam(required=true) @QueryParam("itemId") String itemIdStr,
			@ApiParam(required=true) @QueryParam("timestamp") String timestampStr,
			@ApiParam(required=true) @QueryParam("rating") String ratingStr){
		HttpResponse response;
		UserDao dao = new UserDao(dbm);
		try{
			int userId = Integer.valueOf(userIdStr);
			int itemId = Integer.valueOf(itemIdStr);
			long timestamp = Long.valueOf(timestampStr);
			double rating = Double.valueOf(ratingStr);
			dao.addRating(userId, itemId, timestamp, rating);
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		response = new HttpResponse("", HttpURLConnection.HTTP_CREATED);
		return response;
	}
	
	/**
	 * Remove rating
	 * Removes a rating of a user.
	 * 
	 * @param userIdStr User identifier given as path parameter.
	 * @param itemIdStr Item identifier given as query parameter.
	 * 
	 * @return HTTP status code 200 for success and status code 500 for failure.
	 */
	@DELETE
	@Path("/users/{userId}/events/ratings")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Remove rating",
			notes = "Removes a rating event of a user.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Rating removed successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error removing the rating"),
	})
	public HttpResponse deleteRating(
			@ApiParam(required=true) @PathParam("userId") String userIdStr,
			@ApiParam(required=true) @QueryParam("itemId") String itemIdStr){
		HttpResponse response;
		UserDao dao = new UserDao(dbm);
		try{
			int userId = Integer.valueOf(userIdStr);
			int itemId = Integer.valueOf(itemIdStr);
			dao.deleteRating(userId, itemId);
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		response = new HttpResponse("", HttpURLConnection.HTTP_OK);
		return response;
	}

	/**
	 * Add tagging
	 * Adds a new tagging event to the system.
	 * 
	 * @param userIdStr User identifier given as path parameter.
	 * @param itemIdStr Item identifier given as query parameter.
	 * @param timestampStr Timestamp given as query parameter.
	 * @param tag Tag given as query parameter.
	 * 
	 * @return HTTP status code 200 for success and status code 500 for failure.
	 */
	@POST
	@Path("/users/{userId}/events/taggings")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Add tagging",
			notes = "Adds a tagging event for a user.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_CREATED, message = "Tagging event added successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error adding the tagging event")
	})
	public HttpResponse addTagging(
			@ApiParam(required=true) @PathParam("userId") String userIdStr,
			@ApiParam(required=true) @QueryParam("itemId") String itemIdStr,
			@ApiParam(required=true) @QueryParam("timestamp") String timestampStr,
			@ApiParam(required=true) @QueryParam("tag") String tag){
		HttpResponse response;
		UserDao dao = new UserDao(dbm);
		try{
			int userId = Integer.valueOf(userIdStr);
			int itemId = Integer.valueOf(itemIdStr);
			long timestamp = Long.valueOf(timestampStr);
			dao.addTagging(userId, itemId, timestamp, tag);
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		response = new HttpResponse("", HttpURLConnection.HTTP_CREATED);
		return response;
	}
	
	/**
	 * Remove tagging
	 * Removes a tagging of a user.
	 * 
	 * @param userIdStr User identifier given as path parameter.
	 * @param itemIdStr Item identifier given as query parameter.
	 * @param tag Tag given as query parameter.
	 * 
	 * @return HTTP status code 200 for success and status code 500 for failure.
	 */
	@DELETE
	@Path("/users/{userId}/events/taggings")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Remove tagging",
			notes = "Removes a tagging event of a user.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Tagging removed successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error removing the tagging"),
	})
	public HttpResponse deleteTagging(
			@ApiParam(required=true) @PathParam("userId") String userIdStr,
			@ApiParam(required=true) @QueryParam("itemId") String itemIdStr,
			@ApiParam(required=true) @QueryParam("tag") String tag){
		HttpResponse response;
		UserDao dao = new UserDao(dbm);
		try{
			int userId = Integer.valueOf(userIdStr);
			int itemId = Integer.valueOf(itemIdStr);
			dao.deleteTagging(userId, itemId, tag);
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		response = new HttpResponse("", HttpURLConnection.HTTP_OK);
		return response;
	}
	
	/**
	 * Retrieve ratings
	 * Retrieves all ratings of a user.
	 * 
	 * @param userIdStr User identifier given as path parameter.
	 * 
	 * @return All ratings of the user.
	 */
	@GET
	@Path("/users/{userId}/events/ratings")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Retrieve ratings",
			notes = "Retrieves all ratings of a user.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Ratings retrieved successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error retrieving the ratings"),
	})
	public HttpResponse getUserRatings(
			@ApiParam(required=true) @PathParam("userId") String userIdStr){
		HttpResponse response;
		UserDao dao = new UserDao(dbm);
		int userId;
		JSONArray jsonRatingsArray;
		try{
			userId = Integer.valueOf(userIdStr);
			jsonRatingsArray = dao.getRatings(userId);
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		JSONObject json = new JSONObject();
		json.put("userId", userId);
		json.put("ratings", jsonRatingsArray);
		response = new HttpResponse(json.toJSONString(), HttpURLConnection.HTTP_OK);
		return response;
	}

	/**
	 * Retrieve taggings
	 * Retrieves all taggings of a user.
	 * 
	 * @param userIdStr User identifier given as path parameter.
	 * 
	 * @return All taggings of the user.
	 */
	@GET
	@Path("/users/{userId}/events/taggings")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Retrieve taggings",
			notes = "Retrieves all taggings of a user.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Taggings retrieved successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error retrieving the taggings"),
	})
	public HttpResponse getUserTaggings(
			@ApiParam(required=true) @PathParam("userId") String userIdStr){
		HttpResponse response;
		UserDao dao = new UserDao(dbm);
		int userId;
		JSONArray jsonTaggingsArray;
		try{
			userId = Integer.valueOf(userIdStr);
			jsonTaggingsArray = dao.getTaggings(userId);
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		JSONObject json = new JSONObject();
		json.put("userId", userId);
		json.put("taggings", jsonTaggingsArray);
		response = new HttpResponse(json.toJSONString(), HttpURLConnection.HTTP_OK);
		return response;
	}

	/**
	 * Retrieve prediction
	 * Retrieves a rating prediction for a user.
	 * 
	 * @param userIdStr User identifier given as path parameter.
	 * @param itemIdStr Item identifier given as query parameter.
	 * 
	 * @return Predicted rating for the user and item.
	 */
	@GET
	@Path("/users/{userId}/predictions")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Retrieve prediction",
			notes = "Retrieves a rating prediction for a user.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Prediction retrieved successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error retrieving the prediction"),
	})
	public HttpResponse getUserPrediction(
			@ApiParam(required=true) @PathParam("userId") String userIdStr,
			@ApiParam(required=true) @QueryParam("itemId") String itemIdStr){
		HttpResponse response;
		UserDao dao = new UserDao(dbm);
		int userId;
		int itemId;
		double prediction;
		try{
			userId = Integer.valueOf(userIdStr);
			itemId = Integer.valueOf(itemIdStr);
			prediction = dao.getPrediction(userId, itemId);
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		JSONObject json = new JSONObject();
		json.put("userId", userId);
		json.put("itemId", itemId);
		json.put("prediction", prediction);
		response = new HttpResponse(json.toJSONString(), HttpURLConnection.HTTP_OK);
		return response;
	}

	/**
	 * Retrieve recommendations
	 * Retrieves a recommendation list for a user.
	 * 
	 * @param userIdStr User identifier given as path parameter.
	 * @param countStr Maximum number of items given as query parameter.
	 * 
	 * @return Recommendation list.
	 */
	@GET
	@Path("/users/{userId}/recommendations")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Retrieve recommendations",
			notes = "Retrieves a recommendation list for a user.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Recommendation retrieved successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error retrieving the recommendation"),
	})
	public HttpResponse getUserRecommendations(
			@ApiParam(required=true) @PathParam("userId") String userIdStr,
			@ApiParam(required=false) @DefaultValue("10") @QueryParam("count") String countStr){
		HttpResponse response;
		UserDao dao = new UserDao(dbm);
		int userId;
		int count;
		JSONArray jsonRecommendation;
		try{
			userId = Integer.valueOf(userIdStr);
			count = Integer.valueOf(countStr);
			jsonRecommendation = dao.getRecommendations(userId, count);
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		JSONObject json = new JSONObject();
		json.put("userId", userId);
		json.put("recommendations", jsonRecommendation);
		response = new HttpResponse(json.toJSONString(), HttpURLConnection.HTTP_OK);
		return response;
	}

	
	// //////////////////////////////////////////////////////////////////////////////////////
	// Service methods for item management.
	// //////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Add item
	 * Adds a new item to the system.
	 * 
	 * @return ID of the new item.
	 */
	@POST
	@Path("/items")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Add item",
			notes = "Adds a new item to the system.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_CREATED, message = "Item added successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error adding the item")
	})
	public HttpResponse addItem(){
		HttpResponse response;
		ItemDao dao = new ItemDao(dbm);
		int itemId;
		try{
			itemId = dao.addItem();
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		JSONObject json = new JSONObject();
		json.put("itemId", itemId);
		response = new HttpResponse(json.toJSONString(), HttpURLConnection.HTTP_CREATED);
		return response;
	}
	
	/**
	 * Remove item
	 * Remove a item from the system.
	 * 
	 * @param itemIdStr Item identifier given as path parameter.
	 * 
	 * @return HTTP status code 200 for success and status code 500 for failure.
	 */
	@DELETE
	@Path("/items/{itemId}")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Remove item",
			notes = "Removes an item from the system.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Item removed successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error removing the item"),
	})
	public HttpResponse deleteItem(@PathParam("itemId") String itemIdStr){
		HttpResponse response;
		ItemDao dao = new ItemDao(dbm);
		try{
			int itemId = Integer.valueOf(itemIdStr);
			dao.deleteItem(itemId);
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		response = new HttpResponse("", HttpURLConnection.HTTP_OK);
		return response;
	}
	
	
	/**
	 * Retrieve items
	 * Retrieves all items from the system.
	 * 
	 * @return All items.
	 */
	@GET
	@Path("/items")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Retrieve items",
			notes = "Retrieves all items.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Items retrieved successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error retrieving the items"),
	})
	public HttpResponse getItems(){
		HttpResponse response;
		ItemDao dao = new ItemDao(dbm);
		JSONArray jsonItemsArray;
		try{
			jsonItemsArray = dao.getItems();
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		JSONObject json = new JSONObject();
		json.put("items", jsonItemsArray);
		response = new HttpResponse(json.toJSONString(), HttpURLConnection.HTTP_OK);
		return response;
	}

	/**
	 * Retrieve ratings
	 * Retrieves all ratings of an item.
	 * 
	 * @param itemIdStr Item identifier given as path parameter.
	 * 
	 * @return All ratings of the item.
	 */
	@GET
	@Path("/items/{itemId}/events/ratings")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Retrieve ratings",
			notes = "Retrieves all ratings of an item.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Ratings retrieved successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error retrieving the ratings"),
	})
	public HttpResponse getItemRatings(
			@ApiParam(required=true) @PathParam("itemId") String itemIdStr){
		HttpResponse response;
		ItemDao dao = new ItemDao(dbm);
		int itemId;
		JSONArray jsonRatingsArray;
		try{
			itemId = Integer.valueOf(itemIdStr);
			jsonRatingsArray = dao.getRatings(itemId);
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		JSONObject json = new JSONObject();
		json.put("itemId", itemId);
		json.put("ratings", jsonRatingsArray);
		response = new HttpResponse(json.toJSONString(), HttpURLConnection.HTTP_OK);
		return response;
	}

	/**
	 * Retrieve taggings
	 * Retrieves all taggings of an item.
	 * 
	 * @param itemIdStr Item identifier given as path parameter.
	 * 
	 * @return All taggings of the item.
	 */
	@GET
	@Path("/items/{itemId}/events/taggings")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Retrieve taggings",
			notes = "Retrieves all taggings of an item.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Taggings retrieved successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error retrieving the taggings"),
	})
	public HttpResponse getItemTaggings(
			@ApiParam(required=true) @PathParam("itemId") String itemIdStr){
		HttpResponse response;
		ItemDao dao = new ItemDao(dbm);
		int itemId;
		JSONArray jsonTaggingsArray;
		try{
			itemId = Integer.valueOf(itemIdStr);
			jsonTaggingsArray = dao.getTaggings(itemId);
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		JSONObject json = new JSONObject();
		json.put("itemId", itemId);
		json.put("taggings", jsonTaggingsArray);
		response = new HttpResponse(json.toJSONString(), HttpURLConnection.HTTP_OK);
		return response;
	}

	
	// //////////////////////////////////////////////////////////////////////////////////////
	// Service methods for recommender system operation.
	// //////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Run recommender
	 * Run the recommender algorithm and compute rating predictions.
	 * 
	 * @param recAlgo Recommender algorithm
	 * @param cdAlgo Community detection algorithm
	 * @param cdWtSteps Steps parameter for Walktrap community detection algorithm
	 * @param graphMethod Method to use for graph construction
	 * @param graphKnnK Number of neighbors for k-nearest neighbor graph construction
	 * @param graphKnnSim Similarity measure to use for k-nearest neighbor graph construction
	 * @param recFactors Factor size for matrix factorization
	 * @param recIters Number of iterations for iterative learning
	 * @param recLearnRate Learning rate gamma for iterative learning
	 * @param recLearnRateN Learning rate gamma (neighborhood) for iterative learning
	 * @param recLearnRateF Learning rate gamma (factors) for iterative learning
	 * @param recLearnRateC Learning rate gamma (community baseline) for iterative learning
	 * @param recLearnRateCN Learning rate gamma (community neighborhood) for iterative learning
	 * @param recLearnRateCF Learning rate gamma (community factors) for iterative learning
	 * @param recLearnRateMu Learning rate gamma (mu/phi/psi parameters) for iterative learning for time-aware models
	 * @param recLambda Regularization factor lambda for iterative learning
	 * @param recLambdaB Regularization factor lambda (baseline) for iterative learning
	 * @param recLambdaN Regularization factor lambda (neighborhood) for iterative learning
	 * @param recLambdaF Regularization factor lambda (factors) for iterative learning
	 * @param recLambdaC Regularization factor lambda (community baseline) for iterative learning
	 * @param recLambdaCN Regularization factor lambda (community neighborhood) for iterative learning
	 * @param recLambdaCF Regularization factor lambda (community factors) for iterative learning
	 * @param recBeta Parameter beta for time-aware recommendation models
	 * @param recBins Number of time bins for time-aware recommendation models
	 * @param recTcnsvdCBins Number of time bins for community detection, i.e. to capture community drift
	 * @param recWrmfAlpha Alpha parameter for WRMF algorithm
	 * @param recKnnSim Similarity measure to use for k-nearest neighbor recommendation algorithm
	 * @param recKnnK Number of neighbors for k-nearest neighbor recommendation algorithm
	 * 
	 * @return HTTP response with status code 200 if the algorithm was successfully run and 500 for failure.
	 */
	@POST
	@Path("/recommender/run")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Run recommendation",
			notes = "Run the recommendation algorithm and compute rating predictions.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Recommender algorithm run successfully"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error running the algorithm")
	})
	public HttpResponse runRecommender(
			@ApiParam(required=false) @QueryParam("recAlgo") @DefaultValue("ItemAvg") String recAlgo,
			@ApiParam(required=false) @QueryParam("cdAlgo") @DefaultValue("WT") String cdAlgo,
			@ApiParam(required=false) @QueryParam("cdWtSteps") @DefaultValue("5") String cdWtSteps,
			@ApiParam(required=false) @QueryParam("graphMethod") @DefaultValue("ratings") String graphMethod,
			@ApiParam(required=false) @QueryParam("graphKnnK") @DefaultValue("20") String graphKnnK,
			@ApiParam(required=false) @QueryParam("graphKnnSim") @DefaultValue("pearson") String graphKnnSim,
			@ApiParam(required=false) @QueryParam("recFactors") @DefaultValue("20") String recFactors,
			@ApiParam(required=false) @QueryParam("recIters") @DefaultValue("30") String recIters,
			@ApiParam(required=false) @QueryParam("recLearnRate") @DefaultValue("0.01") String recLearnRate,
			@ApiParam(required=false) @QueryParam("recLearnRateN") @DefaultValue("0.01") String recLearnRateN,
			@ApiParam(required=false) @QueryParam("recLearnRateF") @DefaultValue("0.01") String recLearnRateF,
			@ApiParam(required=false) @QueryParam("recLearnRateC") @DefaultValue("0.01") String recLearnRateC,
			@ApiParam(required=false) @QueryParam("recLearnRateCN") @DefaultValue("0.01") String recLearnRateCN,
			@ApiParam(required=false) @QueryParam("recLearnRateCF") @DefaultValue("0.01") String recLearnRateCF,
			@ApiParam(required=false) @QueryParam("recLearnRateMu") @DefaultValue("0.0000001") String recLearnRateMu,
			@ApiParam(required=false) @QueryParam("recLambda") @DefaultValue("0.1") String recLambda,
			@ApiParam(required=false) @QueryParam("recLambdaB") @DefaultValue("0.1") String recLambdaB,
			@ApiParam(required=false) @QueryParam("recLambdaN") @DefaultValue("0.1") String recLambdaN,
			@ApiParam(required=false) @QueryParam("recLambdaF") @DefaultValue("0.1") String recLambdaF,
			@ApiParam(required=false) @QueryParam("recLambdaC") @DefaultValue("0.1") String recLambdaC,
			@ApiParam(required=false) @QueryParam("recLambdaCN") @DefaultValue("0.1") String recLambdaCN,
			@ApiParam(required=false) @QueryParam("recLambdaCF") @DefaultValue("0.1") String recLambdaCF,
			@ApiParam(required=false) @QueryParam("recBeta") @DefaultValue("0.04") String recBeta,
			@ApiParam(required=false) @QueryParam("recBins") @DefaultValue("30") String recBins,
			@ApiParam(required=false) @QueryParam("recTcnsvdCBins") @DefaultValue("1") String recTcnsvdCBins,
			@ApiParam(required=false) @QueryParam("recWrmfAlpha") @DefaultValue("1.0") String recWrmfAlpha,
			@ApiParam(required=false) @QueryParam("recKnnSim") @DefaultValue("pcc") String recKnnSim,
			@ApiParam(required=false) @QueryParam("recKnnK") @DefaultValue("50") String recKnnK
		){
		HttpResponse response;
		RecommenderDao dao = new RecommenderDao(dbm);
		try{
			dao.runRecommender(recAlgo, cdAlgo, cdWtSteps, graphMethod, graphKnnK, graphKnnSim,
					recFactors, recIters, recLearnRate, recLearnRateN, recLearnRateF,
					recLearnRateC, recLearnRateCN, recLearnRateCF, recLearnRateMu,
					recLambda, recLambdaB, recLambdaN, recLambdaF, recLambdaC, recLambdaCN, recLambdaCF,
					recBeta, recBins, recTcnsvdCBins, recWrmfAlpha, recKnnSim, recKnnK);
		}
		catch (Exception e){
			Logger.logError(this, e);
			response = new HttpResponse("", HttpURLConnection.HTTP_INTERNAL_ERROR);
			return response;
		}
		JSONObject json = new JSONObject();
		json.put("recAlgo", recAlgo);
		response = new HttpResponse(json.toJSONString(), HttpURLConnection.HTTP_OK);
		return response;
	}

//	/**
//	 * Retrieve status
//	 * Retrieve the recommender system status.
//	 * 
//	 * @return Status of the recommender system.
//	 */
//	@GET
//	@Path("/recommender/status")
//	@Produces(MediaType.APPLICATION_JSON)
//	@ApiOperation(
//			value = "Retrieve status",
//			notes = "Retrieve the recommender system status.")
//	@ApiResponses(value = {
//			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Status retrieved successfully"),
//			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Error retrieving the status")
//	})
//	public HttpResponse getRecommenderStatus(){
//		// TODO
//		// ... code ...
//		 return null;
//	}

	
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
