import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *  An object that stores parameters for the BM25
 *  retrieval model and indicates to the query
 *  operators how the query should be evaluated.
 */

public class RetrievalModelBM25 extends RetrievalModel {

    double k_1;
    double b;
    double k_3;

    public RetrievalModelBM25() {
        k_1 = 1.2;
        b = 0.75;
        k_3 = 0;
    }

    public RetrievalModelBM25(double k_1, double b, double k_3) {
        this.k_1 = k_1;
        this.b = b;
        this.k_3 = k_3;
    }

    public double getModelParam(String paramName) {
        if ( paramName.equalsIgnoreCase("k_1") ||
             paramName.equalsIgnoreCase("k1") )  return k_1;
        if ( paramName.equalsIgnoreCase("b") )   return b;
        if ( paramName.equalsIgnoreCase("k_3") ||
             paramName.equalsIgnoreCase("k3") )  return k_3;
        else throw new IllegalArgumentException(
                paramName + " is not a valid parameter name for BM25 model");
    }

    @Override
    public String defaultQrySopName() {
        return new String ("#sum");
    }

    /**
     * Compute BM25 score on a specific document for a query.
     * Assume BOW query
     *
     * @param r       Retrieval model that determines how the score is calculated
     * @param docid   Internal document id
     * @param terms   A list of query terms
     * @param termVec Terms vector object for document `docid`
     * @param field   Field of interest
     * @return BM25 score for document `docid`
     * @throws IOException
     */
    public static double getScoreBM25(RetrievalModel r, int docid, List<String> terms, TermVector termVec, String field)
        throws IOException {

        if (termVec.stemsLength() == 0)
            return Double.MAX_VALUE;

        double score = 0;

        // Assume qtf = 1
        Set<String> termSet = new HashSet<>(terms);

        // Model parameters
        double k1 = ((RetrievalModelBM25) r).getModelParam("k_1");
        double b = ((RetrievalModelBM25) r).getModelParam("b");
        // double k3 = ((RetrievalModelBM25) r).getModelParam("k_3");

        // Idx parameters
        long numDocs = Idx.getNumDocs();
        long collLen = Idx.getSumOfFieldLengths(field);
        double docCount = (double) Idx.getDocCount(field);
        double avgDocLen = collLen / docCount;
        int docLen = Idx.getFieldLength(field, docid);

        for (int i = 1; i < termVec.stemsLength(); i++) {

            if (termSet.contains(termVec.stemString(i))) {

                double df = termVec.stemDf(i);
                double tf = termVec.stemFreq(i);
                // double qtf = 1;

                double rsjWeight = Math.max( 0, Math.log( (numDocs - df + 0.5) / (df + 0.5) ) );
                double tfWeight = tf / ( tf + k1 * ( (1 - b) + b * docLen / avgDocLen ) );
                // double userWeight = (k3 + qtf) / (k3 + qtf);
                // score += rsjWeight * tfWeight * userWeight;
                score += rsjWeight * tfWeight;

            }

        }

        return score;

    }

}
