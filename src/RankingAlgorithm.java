/**
 *  The root class in the retrieval algorithm hierarchy. This hierarchy
 *  is used to create objects that run a retrieval algorithm on the queries.
 */
public abstract class RankingAlgorithm {

    /**
     * The method that determines how the algorithm works and retrieves documents.
     * @throws Exception
     */
    public abstract void run() throws Exception;

}
