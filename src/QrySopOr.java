/**
 * Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore(RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelUnrankedBoolean) {
            return this.getScoreUnrankedBoolean(r);
        } else if (r instanceof RetrievalModelRankedBoolean) {
            return this.getScoreRankedBoolean(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the OR operator.");
        }
    }

    /**
     *  getScore for the UnrankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreUnrankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return 1.0;
        }
    }

    /**
     * getScore for the RankedBoolean retrieval model.
     * @param r the retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    private double getScoreRankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            double maxScore = 0;
            int docid = this.docIteratorGetMatch();

            for ( Qry arg : this.args ) {
                // check whether the current query operator has a match
                if (    arg.docIteratorHasMatch(r) &&
                        arg.docIteratorGetMatch() == docid    ) {
                    // arg could be of type QrySopOr, QrySopAnd, or QrySopScore (the bottom operator)
                    // call QrySop.getScore recursively and polymorphically to get the document score
                    double curScore = ((QrySop) arg).getScore(r);
                    maxScore = maxScore < curScore ? curScore : maxScore;
                }
            }
            return maxScore;
        }
    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        // For now, Indri model doesn't provide support for #OR
        return 0;
    }

}
