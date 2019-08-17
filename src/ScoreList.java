/**
 * Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

/**
 *  This class implements the document score list data structure
 *  and provides methods for accessing and manipulating them.
 */
public class ScoreList {

    //  A utility class to create a <internalDocid, externalDocid, score>
    //  object.

    private class ScoreListEntry {
        private int docid;
        private String externalId;
        private double score;

        private ScoreListEntry(int internalDocid, double score) {
            this.docid = internalDocid;
            this.score = score;

            try {
                this.externalId = Idx.getExternalDocid(this.docid);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public String toString() {
            return "ScoreListEntry{" +
                    "docid=" + docid +
                    ", externalId='" + externalId + '\'' +
                    ", score=" + score +
                    '}';
        }
    }

    /**
     *  A list of document ids and scores.
     */
    private List<ScoreListEntry> scores;

    public ScoreList() {
        scores = new ArrayList<>();
    }

    /**
     * Copy constructor for ScoreList.
     * @param scoreList Another ScoreList instance
     */
    public ScoreList(ScoreList scoreList) {
        scores = new ArrayList<>(scoreList.scores);
    }

    /**
     *  Append a document score to a score list.
     *  @param docid An internal document id.
     *  @param score The document's score.
     */
    public void add(int docid, double score) {
        scores.add(new ScoreListEntry(docid, score));
    }

    /**
     *  Get the internal externalID of the n'th entry.
     *  @param n The index of the requested document.
     *  @return The internal document id.
     */
    public int getDocid(int n) {
        return this.scores.get(n).docid;
    }

    /**
     *  Get the score of the n'th entry.
     *  @param n The index of the requested document score.
     *  @return The document's score.
     */
    public double getDocidScore(int n) {
        return this.scores.get(n).score;
    }

    /**
     *  Set the score of the n'th entry.
     *  @param n The index of the score to change.
     *  @param score The new score.
     */
    public void setDocidScore(int n, double score) {
        this.scores.get(n).score = score;
    }

    /**
     *  Get the size of the score list.
     *  @return The size of the posting list.
     */
    public int size() {
        return this.scores.size();
    }

    /**
     *  Compare two ScoreListEntry objects.  Sort by score, then
     *  internal externalID.
     */
    public class ScoreListComparator implements Comparator<ScoreListEntry> {

        @Override
        public int compare(ScoreListEntry s1, ScoreListEntry s2) {
            if (s1.score > s2.score)
                return -1;
            else if (s1.score < s2.score)
                return 1;
            return s1.externalId.compareTo(s2.externalId);
        }
    }

    /**
     *  Sort the list by score and external document id.
     */
    public void sort() {
        this.scores.sort(new ScoreListComparator());
    }

    /**
     * Reduce the score list to the first num results to save on RAM.
     * @param num Number of results to keep.
     */
    public void truncate(int num) {
        // only truncate when current size is larger than `num`
        if (size() > num) {
            List<ScoreListEntry> truncated = new ArrayList<>(this.scores.subList(0, num));
            this.scores.clear();
            this.scores = truncated;
        }
    }

    /**
     * Clear the score list.
     */
    public void clear() {
        scores.clear();
    }

    /**
     * Get a list of document id's that are stored in the current score list.
     * @return A list of doc id's
     */
    public List<Integer> getDocidList() {
        List<Integer> docs = new ArrayList<>();
        for (int i = 0; i < size(); i++) {
            docs.add(this.getDocid(i));
        }
        return docs;
    }

    /**
     * Get a list of document scores that are stored in the current score list.
     * @return A list of doc scores
     */
    public List<Double> getScoreList() {
        List<Double> scores = new ArrayList<>();
        for (int i = 0; i < size(); i++) {
            scores.add(this.getDocidScore(i));
        }
        return scores;
    }

    /**
     * Get a set of document id's that are stored in the current score list.
     * @return A set of doc id's
     */
    public Set<Integer> getDocidSet() {
        return new HashSet<>(this.getDocidList());
    }

}
