import java.io.IOException;

public class QrySopWSum extends QryWSop {

    // List<Double> weights = new ArrayList<>();

    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        if ( r instanceof RetrievalModelIndri )
            return this.docIteratorHasMatchMin(r);
        else
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the WSUM operator.");
    }

    @Override
    public double getScore(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri (r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the WSUM operator.");
        }
    }

    public double getScoreIndri(RetrievalModel r) throws IOException {

        double score = 0.;
        int docid = this.docIteratorGetMatch();

        for ( int i = 0; i < this.args.size(); i++ ) {

            QrySop arg = (QrySop) this.args.get(i);

            if ( arg.docIteratorHasMatch(r) && arg.docIteratorGetMatch() == docid ) {
                score += arg.getScore(r) * weights.get(i) / this.getTotalWeight();
            } else {
                score += arg.getDefaultScore(r, docid) * weights.get(i) / this.getTotalWeight();
            }

        }

        return score;

    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {

        double score = 0.;

        for ( int i = 0; i < this.args.size(); i++ ) {
            QrySop arg = (QrySop) this.args.get(i);
            score += arg.getDefaultScore(r, docid) * weights.get(i) / this.getTotalWeight();
        }

        return score;

    }

}
