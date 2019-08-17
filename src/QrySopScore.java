/**
 * Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

    /**
     *  Document-independent values that should be determined just once.
     *  Some retrieval models have these, some don't.
     */

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchFirst(r);
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
        } else if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25(r);
        } else if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the SCORE operator.");
        }
    }

    /**
     *  getScore for the Unranked retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScoreUnrankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return 1.0;
        }
    }

    /**
     * getScore for the ranked boolean retrieval model.
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index.
     */
    public double getScoreRankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            // #SCORE operator can have only a single argument of type QryIop.
            return ((QryIop) this.args.get(0)).getScore(r);
        }
    }

    /**
     * getScore for the BM25 retrieval model.
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index.
     */
    public double getScoreBM25(RetrievalModel r) throws IOException {

        if ( !this.docIteratorHasMatchCache() )
            return 0.0;

        return ( (QryIop) this.args.get(0) ).getScore(r);

    }

    /**
     * getScore for the Indri retrieval model.
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index.
     */
    public double getScoreIndri(RetrievalModel r) throws IOException {

        if ( !this.docIteratorHasMatchCache() )
            return 0;

        return ((QryIop) this.args.get(0)).getScore(r);

    }

//    /**
//     * getScore for the Indri retrieval model.
//     * @param r The retrieval model that determines how scores are calculated.
//     * @return The document score.
//     * @throws IOException Error accessing the Lucene index.
//     */
//    public double getScoreIndri(RetrievalModel r) throws IOException {
//
//        QryIop arg = (QryIop) this.args.get(0);
//        int docId = arg.docIteratorGetMatch();
//        String field = arg.getField();
//
//        double dirichletPrior, mixture;
//
//        // Required statistics
//        // double tf = this.invertedList.getTf(docIteratorIndex);
//        double tf = arg.docIteratorGetMatchPosting().tf;
//        double docLen = Idx.getFieldLength(field, docId);
//        // set ctf = 0.5 when ctf = 0 (tf = 0)
//        // which only happens when we call the `getDefaultScore` method
//        double ctf = arg.getCtf();
//        double collLen = Idx.getSumOfFieldLengths(field);
//
//        // Model parameters
//        double mu = ((RetrievalModelIndri) r).getModelParam("mu");
//        double lambda = ((RetrievalModelIndri) r).getModelParam("lambda");
//
//        // Calculation
//        dirichletPrior = (tf + mu * ctf / collLen) / (docLen + mu);
//        mixture = (1 - lambda) * dirichletPrior + lambda * ctf / collLen;
//
//        return mixture;
//
//    }

    /**
     * getDefaultScore for the Indri retrieval model.
     * @param r The retrieval model that determines how scores are calculated.
     * @param docid The document id, referring to the document for which we want to calculate score.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index.
     */
    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {

        QryIop arg = (QryIop) this.args.get(0);
        String field = arg.getField();

        double dirichletPrior, mixture;

        // Required statistics
        double docLen = Idx.getFieldLength(field, (int) docid);
        double ctf = arg.getCtf();

        // **If ctf is zero, set it to 0.5**
        // set ctf = 0.5 when ctf = 0 (tf = 0)
        // which only happens when we call the `getDefaultScore` method
        if (ctf == 0)
            ctf = 0.5;

        double collLen = Idx.getSumOfFieldLengths(field);

        // Model parameters
        double mu = ((RetrievalModelIndri) r).getModelParam("mu");
        double lambda = ((RetrievalModelIndri) r).getModelParam("lambda");

        // Calculation
        dirichletPrior = (mu * ctf / collLen) / (docLen + mu);
        mixture = (1 - lambda) * dirichletPrior + lambda * ctf / collLen;

        return mixture;

    }

    /**
     *  Initialize the query operator (and its arguments), including any
     *  internal iterators.  If the query operator is of type QryIop, it
     *  is fully evaluated, and the results are stored in an internal
     *  inverted list that may be accessed via the internal iterator.
     *  @param r A retrieval model that guides initialization
     *  @throws IOException Error accessing the Lucene index.
     */
    public void initialize(RetrievalModel r) throws IOException {

        Qry q = this.args.get(0);
        q.initialize(r);
    }

}
