
/**
 * Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;

/**
 *  The AND operator for all retrieval models.
 */
public class QrySopWAnd extends QryWSop {

    // List<Double> weights = new ArrayList<>();

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch (RetrievalModel r) {
        // For Indri model, calc #WAND scores for documents that have at least 1 query term
        if ( r instanceof RetrievalModelIndri )
            return this.docIteratorHasMatchMin(r);
        else
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the WAND operator.");
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore (RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the WAND operator.");
        }

    }

    private double getScoreIndri(RetrievalModel r) throws IOException {

        double score = 1.;
        int docid = this.docIteratorGetMatch();

        for ( int i = 0; i < this.args.size(); i++ ) {

            QrySop arg = (QrySop) this.args.get(i);

            if ( arg.docIteratorHasMatch(r) && arg.docIteratorGetMatch() == docid ) {
                score *= Math.pow(arg.getScore(r), weights.get(i) / this.getTotalWeight());
            } else {
                score *= Math.pow(arg.getDefaultScore(r, docid), weights.get(i) / this.getTotalWeight());
            }

        }

        return score;

    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {

        double score = 1.;

        for ( int i = 0; i < this.args.size(); i++ ) {
            QrySop arg = (QrySop) this.args.get(i);
            score *= Math.pow(arg.getDefaultScore(r, docid), weights.get(i) / this.getTotalWeight());
        }

        return score;

    }

}
