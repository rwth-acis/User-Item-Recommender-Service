package i5.las2peer.services.recommender.entities;

public class Tagging {
	private int userId;
	private int itemId;
	private long timestamp;
	private String tag;
	
	public Tagging (){
	}
	
	public Tagging (int userId, int itemId, long timestamp, String tag){
		this.userId = userId;
		this.itemId = itemId;
		this.timestamp = timestamp;
		this.tag = tag;
	}
	
	public int getUserId() {
		return userId;
	}
	public void setUserId(int userId) {
		this.userId = userId;
	}
	public int getItemId() {
		return itemId;
	}
	public void setItemId(int itemId) {
		this.itemId = itemId;
	}
	public long getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	public String getTag() {
		return tag;
	}
	public void setTag(String tag) {
		this.tag = tag;
	}

}
