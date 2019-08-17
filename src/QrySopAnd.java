
/**
 * Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;

/**
 *  The AND operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch (RetrievalModel r) {
        // For Indri model, calc #AND scores for documents that have at least 1 query term
        if ( r instanceof RetrievalModelIndri )
            return this.docIteratorHasMatchMin(r);
        return this.docIteratorHasMatchAll (r);
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore (RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelUnrankedBoolean) {
            return this.getScoreUnrankedBoolean (r);
        } else if (r instanceof RetrievalModelRankedBoolean) {
            return this.getScoreRankedBoolean(r);
        } else if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the AND operator.");
        }

    }

    /**
     *  getScore for the UnrankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return 1.0;
        }
    }

    /**
     *  getScore for the RankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreRankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            double minScore = Double.MAX_VALUE;
            // int externalID = this.docIteratorGetMatch();

            for ( Qry arg : this.args ) {
                // arg could be of type QrySopOr, QrySopAnd, or QrySopScore (the bottom operator)
                // call QrySop.getScore recursively and polymorphically to get the document score
                double curScore = ((QrySop) arg).getScore(r);
                minScore = minScore > curScore ? curScore : minScore;
            }
            return minScore;
        }
    }

    /**
     *  getScore for the Indri retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreIndri(RetrievalModel r) throws IOException {

        double score = 1.;
        int docid = this.docIteratorGetMatch();

        for ( Qry arg : this.args ) {

            if ( arg.docIteratorHasMatch(r) && arg.docIteratorGetMatch() == docid ) {
                score *= ((QrySop) arg).getScore(r);
            } else {
                score *= ((QrySop) arg).getDefaultScore(r, docid);
            }

        }

        return Math.pow(score, 1. / this.args.size());

    }

    /**
     * Get a default score for the document based on Indri model
     * @param r The retrieval model that determines how scores are calculated. Assume RetrievalModelIndri
     * @param docid The given externalID
     * @return The document score
     * @throws IOException
     */
    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {

        double score = 1.;

        for ( Qry arg : this.args ) {
            score *= ((QrySop) arg).getDefaultScore(r, docid);
        }

        return Math.pow(score, 1. / this.args.size());
    }

}
