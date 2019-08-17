import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *  An object that stores parameters for the Indri
 *  retrieval model and indicates to the query
 *  operators how the query should be evaluated.
 */

public class RetrievalModelIndri extends RetrievalModel {

    double mu;
    double lambda;

    public RetrievalModelIndri() {
        mu = 2500;
        lambda = 0.4;
    }

    public RetrievalModelIndri(double mu, double lambda) {
        this.mu = mu;
        this.lambda = lambda;
    }

    public double getModelParam(String paramName) {
        if ( paramName.equalsIgnoreCase("mu") ) {
            return mu;
        }
        if ( paramName.equalsIgnoreCase("lambda") ) {
            return lambda;
        }
        throw new IllegalArgumentException
                (paramName + " is not a valid parameter name for Indri model");
    }

    @Override
    public String defaultQrySopName() {
        return new String("#and");
    }

    /**
     * Compute Indri score on a specific document for a query.
     * Assume BOW query
     *
     * @param r       Retrieval model that determines how the score is calculated
     * @param docid   Internal document id
     * @param terms   A list of query terms
     * @param termVec Terms vector object for document `docid`
     * @param field   Field of interest
     * @return Indri score for document `docid`
     * @throws IOException
     */
    public static double getScoreIndri(RetrievalModel r, int docid, List<String> terms, TermVector termVec, String field)
            throws IOException {

        if (termVec.stemsLength() == 0) {
            return Double.MAX_VALUE;
        }

        double score = 1d;
        Set<String> termSet = new HashSet<>(terms);
        Set<String> intersection = new HashSet<>();

        // Model parameters
        double mu = ((RetrievalModelIndri) r).getModelParam("mu");
        double lambda = ((RetrievalModelIndri) r).getModelParam("lambda");

        // Idx parameters
        double collLen = Idx.getSumOfFieldLengths(field);
        double docLen = Idx.getFieldLength(field, docid);

        for (int i = 0; i < termVec.stemsLength(); i++) {

            String stem = termVec.stemString(i);
            if (stem == null) {
                continue;
            }

            if (termSet.contains(stem)) {

                intersection.add(stem);

                double tf = termVec.stemFreq(i);
                double ctf = Idx.getTotalTermFreq(field, stem);
                double mle = ctf / collLen;

                double dirichletPrior = (tf + mu * mle) / (docLen + mu);
                double mixture = (1 - lambda) * dirichletPrior + lambda * mle;
                score *= mixture;

            }

        }

        if (intersection.size() == 0) {
            return 0;
        }

        // Set ctf = 0.5 when ctf = 0 (tf = 0)
        // which only happens when we call the `getDefaultScore` method

        for (String term : terms) {

            if (!intersection.contains(term)) {

                double tf = 0;
                double ctf = Idx.getTotalTermFreq(field, term);
                ctf = ctf == 0 ? 0.5 : ctf;
                double mle = ctf / collLen;

                double dirichletPrior = (tf + mu * mle) / (docLen + mu);
                double mixture = (1 - lambda) * dirichletPrior + lambda * mle;
                score *= mixture;

            }

        }

        score = Math.pow(score, 1.0 / terms.size());

        return score;

    }

}
