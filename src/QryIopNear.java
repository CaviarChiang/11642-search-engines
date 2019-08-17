/**
 *  Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *  The NEAR/n operator for all retrieval models.
 */
public class QryIopNear extends QryIop {

    int dist;

    public QryIopNear(int n) {
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
        if (args.size () == 0) {
            return;
        }

        // Initialize doc iterator (NEAR is based on AND, so we use `docIteratorHasMatchAll`)
        while (this.docIteratorHasMatchAll(null)) {

            // Create an empty list of positions
            List<Integer> positions = new ArrayList<>();

            // Store the 1st arg (term)
            QryIop q_0 = (QryIop) this.args.get(0);

            // `this.docIteratorHasMatchAll()` will advance all doc iterators until they point to the same docID
            int docId = q_0.docIteratorGetMatch();

            // Initialize loc iterator
            while (q_0.locIteratorHasMatch()) {

                // Store prior traversed location
                int locId_prev = q_0.locIteratorGetMatch();
                // Store current location
                int locId_i = 0;
                // Store whether there is a match
                boolean matched = false;

                for (int i = 1; i < this.args.size(); i++) {

                    QryIop q_i = (QryIop) this.args.get(i);
                    // Advance the ith arg's loc iterator until it passes the previous location (makes sure loc_left < loc_right)
                    q_i.locIteratorAdvancePast(locId_prev);

                    // If there is not a match at current location for the ith arg, break
                    if (!q_i.locIteratorHasMatch()) {
                        matched = false;
                        break;
                    }
                    // Get the matched location for the ith arg
                    locId_i = q_i.locIteratorGetMatch();

                    // If the loc distance is larger than `dist`, break
                    if (locId_i - locId_prev > dist) {
                        matched = false;
                        break;
                    }
                    // Set match to true and update the previous loc
                    locId_prev = locId_i;
                    matched = true;
                }
                // End traversing arg

                if (matched) {

                    // If there is a match, add the largest locId to positions
                    positions.add(locId_i);

                    // Advance all loc iterators
                    for (Qry qry : this.args) {
                        ((QryIop) qry).locIteratorAdvance();
                    }

                } else {

                    // If there is no match, only advance the first loc iterator
                    q_0.locIteratorAdvance();

                }

            } // loc iterator reached the end

            // Append posting to invertedList ( posting = (docID, positionsList) ) if positions is not empty
            if (positions.size() != 0)
                this.invertedList.appendPosting(docId, positions);

            // Advance the 1st arg's doc iterator until it passes the current docID (which is the same for all args)
            this.args.get(0).docIteratorAdvancePast(docId);

        } // doc iterator reached the end

    }

}


