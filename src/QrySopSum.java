import java.io.IOException;

public class QrySopSum extends QrySop {

    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
    }

    @Override
    public double getScore(RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25 (r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the SUM operator.");
        }

    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        return 0;
    }

    public double getScoreBM25(RetrievalModel r) throws IOException {

        // `QrySopSum` operator has `args` of type `QrySopScore` or `QrySop`!!!

        if ( !this.docIteratorHasMatch(r) ) return 0;

        int docid = this.docIteratorGetMatch();

        double score = 0;
        for ( Qry arg : args ) {
            if ( arg.docIteratorHasMatch(r) && arg.docIteratorGetMatch() == docid )
                score += ((QrySop) arg).getScore(r);
        }

        return score;

    }

}
