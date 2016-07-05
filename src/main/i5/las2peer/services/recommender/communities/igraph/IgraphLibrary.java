package i5.las2peer.services.recommender.communities.igraph;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

interface IgraphLibrary extends Library{
	IgraphLibrary INSTANCE = (IgraphLibrary) Native.loadLibrary((Platform.isWindows() ? "libigraph-0.7.1.dll" : "libigraph-0.7.1.so"), IgraphLibrary.class);
	
	int igraph_version(PointerByReference version_string, IntByReference major, IntByReference minor, IntByReference subminor);
	
	/* e.g. we want to run fast greedy modularity
	 * int igraph_community_fastgreedy(
	 * 		const igraph_t *graph,
	 *		const igraph_vector_t *weights,
	 *		igraph_matrix_t *merges,
	 *		igraph_vector_t *modularity, 
	 *		igraph_vector_t *membership);
	 *
	 */
	
	public static class igraph_t extends Structure {
		/*
		 * Definition in igraph_datatype.h:
				typedef struct igraph_s {
					igraph_integer_t n;
					igraph_bool_t directed;
					igraph_vector_t from;
					igraph_vector_t to;
					igraph_vector_t oi;
					igraph_vector_t ii;
					igraph_vector_t os;
					igraph_vector_t is;
					void *attr;
				} igraph_t;
		 */
	    public int n;
	    public int directed;
	    public igraph_vector_t from;
	    public igraph_vector_t to;
	    public igraph_vector_t oi;
	    public igraph_vector_t ii;
	    public igraph_vector_t os;
	    public igraph_vector_t is;
	    public Pointer attr;
	    public static class ByReference extends igraph_t implements Structure.ByReference { }
	    public igraph_t() {
	    	super();
		}
	    public igraph_t(Pointer ptr) {
			super(ptr);
		}
		protected List<String> getFieldOrder() { 
	        return Arrays.asList(new String[] {"n", "directed", "from", "to", "oi", "ii", "os", "is", "attr"});
	    }
	}
	
	/*
	 * Definition in igraph_vector_type.h
			typedef struct TYPE(igraph_vector) {
				BASE* stor_begin;
				BASE* stor_end;
				BASE* end;
			} TYPE(igraph_vector);
	 */
	public static class igraph_vector_t extends Structure {
	    public DoubleByReference stor_begin;
	    public DoubleByReference stor_end;
	    public DoubleByReference end;
	    protected List<String> getFieldOrder() { 
	        return Arrays.asList(new String[] {"stor_begin", "stor_end", "end"});
	    }
	}
	public static class igraph_vector_bool_t extends Structure {
	    public IntByReference stor_begin;
	    public IntByReference stor_end;
	    public IntByReference end;
	    protected List<String> getFieldOrder() { 
	        return Arrays.asList(new String[] {"stor_begin", "stor_end", "end"});
	    }
	}
	
	public static class igraph_vector_ptr_t extends Structure {
	    public PointerByReference stor_begin;
	    public PointerByReference stor_end;
	    public PointerByReference end;
	    protected List<String> getFieldOrder() { 
	        return Arrays.asList(new String[] {"stor_begin", "stor_end", "end"});
	    }
	}
	
	/*
	 * Definition in igraph-matrix_pmt.h
		typedef struct TYPE(igraph_matrix) {
			  TYPE(igraph_vector) data;
			  long int nrow, ncol;
			} TYPE(igraph_matrix);
	*/
		
	public static class igraph_matrix_t extends Structure {
	    public igraph_vector_t data;
	    public int nrow;
	    public int ncol;
	    protected List<String> getFieldOrder() { 
	        return Arrays.asList(new String[] {"data", "nrow", "ncol"});
	    }
	}
	
	/*
	typedef struct igraph_arpack_options_t {
		   INPUT 
		  char bmat[1];			I-standard problem, G-generalized 
		  int n; 				Dimension of the eigenproblem 
		  char which[2];		LA, SA, LM, SM, BE 
		  int nev;              Number of eigenvalues to be computed 
		  igraph_real_t tol;	Stopping criterion 
		  int ncv;			 	Number of columns in V 
		  int ldv;			 	Leading dimension of V 
		  int ishift;		 	0-reverse comm., 1-exact with tridiagonal 
		  int mxiter;           Maximum number of update iterations to take 
		  int nb;				Block size on the recurrence, only 1 works 
		  int mode;		 		The kind of problem to be solved (1-5)
						   			1: A*x=l*x, A symmetric
						   			2: A*x=l*M*x, A symm. M pos. def.
						   			3: K*x = l*M*x, K symm., M pos. semidef.
						   			4: K*x = l*KG*x, K s. pos. semidef. KG s. indef.
						   			5: A*x = l*M*x, A symm., M symm. pos. semidef. 
		  int start;			0: random, 1: use the supplied vector 
		  int lworkl;			Size of temporary storage, default is fine 
		  igraph_real_t sigma;  The shift for modes 3,4,5 
		  igraph_real_t sigmai; The imaginary part of shift for rnsolve 
		   OUTPUT 
		  int info;		 		What happened, see docs 
		  int ierr;				What happened  in the dseupd call 
		  int noiter;			The number of iterations taken 
		  int nconv;
		  int numop;			Number of OP*x operations 
		  int numopb;			Number of B*x operations if BMAT='G' 
		  int numreo;			Number of steps of re-orthogonalizations 
		   INTERNAL 
		  int iparam[11];
		  int ipntr[14];
		} igraph_arpack_options_t;
	 */
	
		public static class igraph_arpack_options_t extends Structure {
		    public byte[] bmat = new byte[1];
		    public int n;
		    public byte[] which = new byte[2];
		    public int nev;
		    public double tol;
		    public int ncv;
		    public int ldv;
		    public int ishift;
		    public int mxiter;
		    public int nb;
		    public int mode;
		    public int start;
		    public int lworkl;
		    public double sigma;
		    public double sigma1;
		    public int info;
		    public int ierr;
		    public int noiter;
		    public int nconv;
		    public int numop;
		    public int numopb;
		    public int numreo;
		    public int[] iparam = new int[11];
		    public int[] ipntr = new int[14];
		    protected List<String> getFieldOrder() { 
		        return Arrays.asList(new String[] {"bmat","n","which","nev","tol","ncv","ldv","ishift","mxiter","nb","mode","start","lworkl",
		        		"sigma","sigma1","info","ierr","noiter","nconv","numop","numopb","numreo","iparam","ipntr"});
		    }
		}
		
		/*
		typedef int igraph_community_leading_eigenvector_callback_t(
				const igraph_vector_t *membership,
				long int comm, 
				igraph_real_t eigenvalue,
				const igraph_vector_t *eigenvector,
				igraph_arpack_function_t *arpack_multiplier,
			        void *arpack_extra,
			        void *extra);
		*/
		public static class igraph_community_leading_eigenvector_callback_t extends Structure {
		    public igraph_vector_t membership;
		    public int comm;
		    public double eigenvalue;
		    public igraph_vector_t eigenvector;
		    protected List<String> getFieldOrder() { 
		        return Arrays.asList(new String[] {"membership", "comm", "eigenvalue", "eigenvector"});
		    }
		}
		

//	/*
//	   typedef struct igraph_attribute_combination_t {
//		  igraph_vector_ptr_t list;
//		} igraph_attribute_combination_t;
//	 */
//	public static class igraph_attribute_combination_t extends Structure {
//	    public igraph_vector_ptr_t list;
//	    protected List<String> getFieldOrder() { 
//	        return Arrays.asList(new String[] {"list"});
//	    }
//	}
//	
//	/*
//	typedef struct s_vector_ptr {
//		  void** stor_begin;
//		  void** stor_end;
//		  void** end;
//		  igraph_finally_func_t* item_destructor;
//		} igraph_vector_ptr_t;
//	*/
//	public static class igraph_vector_ptr_t extends Structure {
//	    public igraph_vector_ptr_t list;
//	    protected List<String> getFieldOrder() { 
//	        return Arrays.asList(new String[] {"list"});
//	    }
//	}
	
	
	
	// Create an empty graph with n nodes. Enum for directed: IGRAPH_UNDIRECTED=0, IGRAPH_DIRECTED=1.
	int igraph_empty(igraph_t graph, int n, int directed);
	
	// Count the number of nodes
	int igraph_vcount(igraph_t graph);
	
	// Count the number of edges
	int igraph_ecount(igraph_t graph);
	
	// Add an edge
	int igraph_add_edge(igraph_t graph, int from, int to);
	
	// Add multiple edges. Use null pointer for attr.
	// The edges are given in a vector, the first two elements define the first edge (the order is from , to for directed graphs).
	// The vector should contain even number of integer numbers between zero and the number of vertices in the graph minus one (inclusive)
	int igraph_add_edges(igraph_t graph, igraph_vector_t edges, Pointer attr);
	
	// Add nodes. The number of nodes to add is given by nv. Use null pointer for attr.
	int igraph_add_vertices(igraph_t graph, int nv, Pointer attr);
	
	// Decompose a graph into connected components. mode: 0 for weak and 1 for strong connectivity. maxcompno=-1 for unlimited number ofcomponents
//	int igraph_decompose(igraph_t graph, igraph_vector_ptr_t components, igraph_connectedness_t mode, NativeLong maxcompno, NativeLong minelements);
	int igraph_decompose(igraph_t graph, igraph_vector_ptr_t components, int mode, int maxcompno, int minelements);
	
	// Calculates the (weakly or strongly) connected components in a graph.
	int igraph_clusters(igraph_t graph, igraph_vector_t membership, igraph_vector_t csize, IntByReference no, int mode);
	
	// Removes loop and/or multiple edges from the graph.
	//  multiple: if 1, multiple edges will be removed
	//  loops: if 1, loops (self edges) will be removed.  
//	int igraph_simplify(igraph_t graph, int multiple, int loops, igraph_attribute_combination_t edge_comb);
	
	// Initialize a vector, set all elements to zero.
	int igraph_vector_init (igraph_vector_t v, NativeLong size);
	int igraph_vector_init (igraph_vector_t v, int size);
	int igraph_vector_init (igraph_vector_bool_t v, int size);
	int igraph_vector_init (igraph_vector_ptr_t v, int size);
	
	// Reserve memory for a vector
//	int igraph_vector_reserve (igraph_vector_t v, NativeLong size);
	int igraph_vector_reserve (igraph_vector_t v, int size);
	
	// Access an element of a vector.
	double igraph_vector_e (igraph_vector_t v, NativeLong pos);
	double igraph_vector_e (igraph_vector_t v, int pos);
	PointerByReference igraph_vector_e (igraph_vector_ptr_t v, int pos);
	
	// Assignment to an element of a vector.
	void igraph_vector_set (igraph_vector_t v, NativeLong pos, double value);
	void igraph_vector_set (igraph_vector_t v, int pos, double value);
	
	// Append an element to a vector.
	int igraph_vector_push_back (igraph_vector_t v, double e);
	
	//  Gives the size (=length) of the vector.
//	NativeLong igraph_vector_size (igraph_vector_t v);
	int igraph_vector_size (igraph_vector_t v);
	int igraph_vector_size (igraph_vector_bool_t v);
	int igraph_vector_size (igraph_vector_ptr_t v);
	
	// Initializes a matrix.
//	int igraph_matrix_init(igraph_matrix_t m, NativeLong nrow, NativeLong ncol);
	int igraph_matrix_init(igraph_matrix_t m, int nrow, int ncol);
	
	// The number of rows in a matrix.
	// long int igraph_matrix_nrow(const igraph_matrix_t *m);
	int igraph_matrix_nrow(igraph_matrix_t m);
	
	// The number of columns in a matrix.
	// long int igraph_matrix_ncol(const igraph_matrix_t *m);
	int igraph_matrix_ncol(igraph_matrix_t m);
	
	// Initialize ARPACK options
	void igraph_arpack_options_init(igraph_arpack_options_t o);
	
	
	// Community detection based on statistical mechanics
	/*
	int igraph_community_spinglass(
			   const igraph_t *graph,
		       const igraph_vector_t *weights,
		       igraph_real_t *modularity,
		       igraph_real_t *temperature,
		       igraph_vector_t *membership, 
		       igraph_vector_t *csize, 
		       igraph_integer_t spins,
		       igraph_bool_t parupdate,
		       igraph_real_t starttemp,
		       igraph_real_t stoptemp,
		       igraph_real_t coolfact,
		       igraph_spincomm_update_t update_rule,
		       igraph_real_t gamma,
		        // the rest is for the NegSpin implementation 
		       igraph_spinglass_implementation_t implementation,
 			    //  igraph_matrix_t *adhesion, 
 			    //  igraph_matrix_t *normalised_adhesion, 
 			    //  igraph_real_t *polarization, 
		       igraph_real_t gamma_minus);
	*/
	int igraph_community_spinglass(
			igraph_t graph,
			igraph_vector_t weights,
			double modularity,
			double temperature,
			igraph_vector_t membership, 
		    igraph_vector_t csize,
		    int spins,
		    int parupdate,
		    double starttemp,
		    double stoptemp,
		    double coolfact,
		    int update_rule,
		    double gamma,
		    int implementation,
		    double gamma_minus);
	
	int igraph_community_spinglass(
			igraph_t graph,
			igraph_vector_t weights,
			Pointer modularity,
			Pointer temperature,
			igraph_vector_t membership, 
		    igraph_vector_t csize,
		    int spins,
		    int parupdate,
		    double starttemp,
		    double stoptemp,
		    double coolfact,
		    int update_rule,
		    double gamma,
		    int implementation,
		    double gamma_minus);
	
	
	/* Leading eigenvector community finding (proper version)
	int igraph_community_leading_eigenvector(const igraph_t *graph,
	    const igraph_vector_t *weights,
		igraph_matrix_t *merges,
		igraph_vector_t *membership,
		igraph_integer_t steps,
		igraph_arpack_options_t *options, 
		igraph_real_t *modularity,
		igraph_bool_t start,
		igraph_vector_t *eigenvalues,
		igraph_vector_ptr_t *eigenvectors,
		igraph_vector_t *history,
	    igraph_community_leading_eigenvector_callback_t *callback,
	    void *callback_extra);
	*/
	int igraph_community_leading_eigenvector(
			igraph_t graph,
		    igraph_vector_t weights,
			igraph_matrix_t merges,
			igraph_vector_t membership,
			int steps,
			igraph_arpack_options_t options, 
			DoubleByReference modularity,
			int start,
			igraph_vector_t eigenvalues,
			igraph_vector_ptr_t eigenvectors,
			igraph_vector_t history,
		    igraph_community_leading_eigenvector_callback_t callback,
		    Pointer callback_extra);
	
	// Finding community structure by greedy optimization of modularity
	int igraph_community_fastgreedy(
			igraph_t graph,
			igraph_vector_t weights,
			igraph_matrix_t merges,
			igraph_vector_t modularity,
			igraph_vector_t membership);
	
	/*
	int igraph_community_walktrap(
			  const igraph_t *graph, 
		      const igraph_vector_t *weights,
		      int steps,						// Integer constant, the length of the random walks. Paper uses t=2 and t=5.
		      igraph_matrix_t *merges,
		      igraph_vector_t *modularity, 
		      igraph_vector_t *membership);
 	*/
	int igraph_community_walktrap(
			igraph_t graph,
			igraph_vector_t weights,
			int steps,
			igraph_matrix_t merges,
			igraph_vector_t modularity,
			igraph_vector_t membership);
	
	/*
	int igraph_community_edge_betweenness(
			  const igraph_t *graph, 
		      igraph_vector_t *result,
		      igraph_vector_t *edge_betweenness,
		      igraph_matrix_t *merges,
		      igraph_vector_t *bridges,
		      igraph_vector_t *modularity,
		      igraph_vector_t *membership,
		      igraph_bool_t directed,
		      const igraph_vector_t *weights);
	*/
	int igraph_community_edge_betweenness(
			igraph_t graph,
			igraph_vector_t result,
			igraph_vector_t edge_betweenness,
			igraph_matrix_t merges,
			igraph_vector_t bridges,
			igraph_vector_t modularity,
			igraph_vector_t membership,
			int directed,
			igraph_vector_t weights);
	
	/* Finding community structure by multi-level optimization of modularity
	int igraph_community_multilevel(
			  const igraph_t *graph,
			  const igraph_vector_t *weights,
			  igraph_vector_t *membership,
			  igraph_matrix_t *memberships,
			  igraph_vector_t *modularity);
	*/
	int igraph_community_multilevel(
			igraph_t graph,
			igraph_vector_t weights,
			igraph_vector_t membership,
			igraph_matrix_t memberships,
			igraph_vector_t modularity);
	
	/* Community detection based on label propagation
	int igraph_community_label_propagation(const igraph_t *graph,
            igraph_vector_t *membership,
            const igraph_vector_t *weights,
            const igraph_vector_t *initial,
            igraph_vector_bool_t *fixed, 
            igraph_real_t *modularity);
	*/
	int igraph_community_label_propagation(
			igraph_t graph,
            igraph_vector_t membership,
            igraph_vector_t weights,
            igraph_vector_t initial,
            igraph_vector_bool_t fixed, 
            DoubleByReference modularity);
	
	/* Find community structure that minimizes the expected
	int igraph_community_infomap(const igraph_t * graph,
            const igraph_vector_t *e_weights,
            const igraph_vector_t *v_weights,
            int nb_trials,
            igraph_vector_t *membership,
            igraph_real_t *codelength);
	*/
	int igraph_community_infomap(
			igraph_t graph,
            igraph_vector_t e_weights,
            igraph_vector_t v_weights,
            int nb_trials,
            igraph_vector_t membership,
            DoubleByReference codelength);
	
}
