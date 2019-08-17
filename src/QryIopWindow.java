import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class QryIopWindow extends QryIop {

    int dist;

    public QryIopWindow(int n) {
        dist = n;
    }

    /**
     *  Evaluate the query operator; the result is an internal inverted
     *  list that may be accessed via the internal iterators.
     *  @throws IOException Error accessing the Lucene index.
     */
    protected void evaluate () throws IOException {

        // Create an empty inverted list.
        this.invertedList = new InvList (this.getField());

        // If there are no query arguments, that's the final result.
        if ( args.size () == 0 ) {
            return;
        }

        // Initialize doc iterator (WINDOW is based on AND, so we use `docIteratorHasMatchAll`)
        while (this.docIteratorHasMatchAll(null)) {

            // Create an empty list of positions
            List<Integer> positions = new ArrayList<>();

            // Store the 1st arg (term)
            QryIop q_0 = (QryIop) this.args.get(0);

            // `this.docIteratorHasMatchAll()` will advance all doc iterators until they point to the same docID
            int docId = q_0.docIteratorGetMatch();

            // Initialize loc iterator
            while (true) {

                // Store min & max location
                int locId_min = Integer.MAX_VALUE, locId_max = Integer.MIN_VALUE;
                // Store current location
                int locId_i;
                // Store the iterator for the min location
                QryIop q_min = null;
                // Store whether there is a match
                boolean matched = true;

                for (int i = 0; i < this.args.size(); i++) {

                    QryIop q_i = (QryIop) this.args.get(i);

                    // If there is not a match at current location for the ith arg (exhausted), break
                    if (!q_i.locIteratorHasMatch()) {
                        matched = false;
                        break;
                    }

                    // Get the matched location for the ith arg
                    locId_i = q_i.locIteratorGetMatch();

                    // Update locId_min & locId_max
                    if (locId_min > locId_i) {
                        locId_min = locId_i;
                        q_min = q_i;
                    }
                    locId_max = locId_max > locId_i ? locId_max : locId_i;

                }   // End traversing arg

                // If the loc distance is **strictly less** than `dist`, add the largest locId to positions
                if ( matched && locId_max - locId_min < dist ) {

                    positions.add(locId_max);

                    // Advance all loc iterators
                    for (Qry qry : this.args) {
                        ((QryIop) qry).locIteratorAdvance();
                    }

                } else if ( matched && locId_max - locId_min >= dist ) {

                    // If distance isn't satisfied, only advance the min loc iterator
                    q_min.locIteratorAdvance();

                } else {
                    // If any of the term's loc in this doc is exhausted, move on to the next doc
                    break;
                }

            }

            // Append posting to invertedList ( posting = (docID, positionsList) ) if positions is not empty
            if (positions.size() != 0)
                this.invertedList.appendPosting(docId, positions);

            // Advance the 1st arg's doc iterator until it passes the current docID (which is the same for all args)
            this.args.get(0).docIteratorAdvancePast(docId);

        } // doc iterator reached the end

    }

}
