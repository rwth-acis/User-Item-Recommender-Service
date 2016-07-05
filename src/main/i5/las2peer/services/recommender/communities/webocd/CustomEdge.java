package i5.las2peer.services.recommender.communities.webocd;

/**
 * Custom edge extension.
 * Holds edge meta information and is used for edge persistence.
 * @author Sebastian
 *
 */
public class CustomEdge {
	
	/*
	 * Database column name definitions.
	 */
//	private static final String idColumnName = "ID";
//	private static final String sourceIndexColumnName = "SOURCE_INDEX";
//	private static final String targetIndexColumnName = "TARGET_INDEX";
	protected static final String graphIdColumnName = "GRAPH_ID";
	protected static final String graphUserColumnName = "USER_NAME";
//	private static final String weightColumnName = "WEIGHT";	
	
	/**
	 * System generated persistence id.
	 */
	private int id;
	
	/**
	 * The graph that the edge belongs to.
	 */
//	private CustomGraph graph;
	
	/**
	 * The edge weight.
	 */
	private double weight = 1;
	
	/////////////////////////////////////////////////////////////////////////////////////////
	/////////// The following attributes are only of internal use for persistence purposes.
	/////////////////////////////////////////////////////////////////////////////////////////
	
	/*
	 * The source custom node.
	 * Only for persistence purposes.
	 */
//	private CustomNode source;
	
	/*
	 * The target custom node.
	 * Only for persistence purposes.
	 */
//	private CustomNode target;
	
//	/*
//	 * The points of the visual edge layout.
//	 * Only for persistence purposes.
//	 */
//	@ElementCollection
//	private List<PointEntity> points;
	
	
	//////////////////////////////////////////////////////////////////
	//////// Methods
	//////////////////////////////////////////////////////////////////
	
	/**
	 * Creates a new instance.
	 */
	protected CustomEdge() {
	}
	
	/**
	 * Copy constructor.
	 * @param customEdge The custom edge to copy.
	 */
	protected CustomEdge(CustomEdge customEdge) {
		this.weight = customEdge.weight;
	}
	
	/**
	 * Getter for the id.
	 * @return The id.
	 */
	public int getId() {
		return id;
	}

	/**
	 * Getter for the edge weight.
	 * @return The edge weight.
	 */
	protected double getWeight() {
		return weight;
	}

	/**
	 * Setter for the edge weight.
	 * @param weight The edge weight.
	 */
	protected void setWeight(double weight) {
		this.weight = weight;
	}

	/////////////////////////////////////////////////////////////////////////////////////////
	/////////// The following methods are only of internal use for persistence purposes.
	/////////////////////////////////////////////////////////////////////////////////////////
	
	/*
	 * Getter for the source node.
	 * Only for persistence purposes.
	 * @return The source custom node.
	 */
//	protected CustomNode getSource() {
//		return source;
//	}

	/*
	 * Setter for the source node.
	 * Only for persistence purposes.
	 * @param source The source custom node.
	 */
//	protected void setSource(CustomNode source) {
//		this.source = source;
//	}
	
	/*
	 * Getter for the target node.
	 * Only for persistence purposes.
	 * @return The target custom node.
	 */
//	protected CustomNode getTarget() {
//		return target;
//	}

	/*
	 * Setter for the target node.
	 * Only for persistence purposes.
	 * @param target The target custom node.
	 */
//	protected void setTarget(CustomNode target) {
//		this.target = target;
//	}
	
//	/*
//	 * Getter for the points of the visual edge layout.
//	 * Only for persistence purposes.
//	 * @return The points.
//	 */
//	protected List<PointEntity> getPoints() {
//		return points;
//	}
//
//	/*
//	 * Setter for the points of the visual edge layout.
//	 * Only for persistence purposes.
//	 * @param points The points.
//	 */
//	protected void setPoints(List<PointEntity> points) {
//		this.points = points;
//	}
	
	/*
	 * Updates a custom edge before it is being persistence.
	 * Only for persistence purposes.
	 * @param graph The graph that the edge is part of.
	 * @param edge The corresponding yFiles edge.
	 */
//	protected void update(CustomGraph graph, Edge edge) {
//		this.source = graph.getCustomNode(edge.source());
//		this.target = graph.getCustomNode(edge.target());
//		EdgeRealizer eRealizer = graph.getRealizer(edge);
//		this.points = new ArrayList<PointEntity>();
//		this.points.add(new PointEntity(eRealizer.getSourcePoint()));
//		this.points.add(new PointEntity(eRealizer.getTargetPoint()));
//		for(int i=0; i<eRealizer.pointCount(); i++) {
//			this.points.add(new PointEntity(eRealizer.getPoint(i)));
//		}
//		this.graph = graph;
//	}

	/*
	 * Creates the corresponding yFiles edge after the custom edge is loaded from persistence.
	 * Only for persistence purposes.
	 * @param graph The graph that the edge is part of.
	 * @param source The source node of the edge.
	 * @param target The target node of the edge.
	 * @return The edge.
	 */
//	protected Edge createEdge(CustomGraph graph, Node source, Node target) {
//		Edge edge = graph.createEdge(source, target);
//		EdgeRealizer eRealizer = graph.getRealizer(edge);
//		eRealizer.setSourcePoint(points.get(0).createPoint());
//		eRealizer.setTargetPoint(points.get(1).createPoint());
//		for(int i=2; i<points.size(); i++) {
//			PointEntity point = points.get(i);
//			eRealizer.addPoint(point.getX(), point.getY());;
//		}
//		return edge;
//	}
}
