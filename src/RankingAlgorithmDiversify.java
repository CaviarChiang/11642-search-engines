import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 *  An object that determines how diversification algorithm
 *  (PM25 & xQuad) retrieves and ranks documents on the queries.
 */
public class RankingAlgorithmDiversify extends RankingAlgorithm {

    /**
     * A utility class for query intents that wraps the query id this
     * intent object belongs to, the intent id, and the intent text.
     */
    private static class Intent {

        /**
         * Query id  (jointly determines a specific intent with intent id)
         */
        String qid;
        /**
         * Intent id (jointly determines a specific intent with query id)
         */
        String intentId;
        /**
         * Intent content
         */
        String intentText;

        public Intent(String qid, String intentId) {
            this.qid = qid;
            this.intentId = intentId;
            this.intentText = null;
        }

        public Intent(String qid, String intentId, String intent) {
            this.qid = qid;
            this.intentId = intentId;
            this.intentText = intent;
        }

        /**
         * Copy constructor.
         * @param otherIntent The other intent object
         */
        public Intent(Intent otherIntent) {
            this.qid = otherIntent.qid;
            this.intentId = otherIntent.intentId;
            this.intentText = otherIntent.intentText;
        }

        public void setIntentText(String intentText) {
            this.intentText = intentText;
        }

        public String getQid() {
            return qid;
        }

        public String getIntentId() {
            return intentId;
        }

        public String getIntentText() {
            return intentText;
        }

        /**
         * Compare by qid and intent id.
         * @return The hashcode of the current intent object
         */
        @Override
        public int hashCode() {
            return Objects.hash(qid, intentId);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Intent intent = (Intent) o;
            return Objects.equals(qid, intent.qid) &&
                    Objects.equals(intentId, intent.intentId);
        }

        @Override
        public String toString() {
            return "Intent{" +
                    "qid='" + qid + '\'' +
                    ", intentId='" + intentId + '\'' +
                    ", intentText='" + intentText + '\'' +
                    '}';
        }

    }


    /**
     * A utility class for a query with its intents.
     */
    private static class QueryIntent {

        /**
         * Query id.
         */
        String qid;
        /**
         * A list of intents for this query.
         */
        List<Intent> intents;

        public QueryIntent(String qid) {
            this.qid = qid;
            intents = new ArrayList<>();
        }

        /**
         * Add a new intent for the current query object.
         * @param intentId Intent id for this query
         * @param intent Intent content
         */
        void addIntent(String intentId, String intent) {
            intents.add(new Intent(qid, intentId, intent));
        }

        /**
         * Get a list of intents for the current query object.
         * @return A copy of the intents member.
         */
        List<Intent> getIntents() {
            return new ArrayList<>(intents);
        }

        /**
         * Find the intent object with the given intent id from the current query object.
         * @param intentId The given intent id to search
         * @return The found intent object with the given intent id; if not found, returns null
         */
        Intent getIntentById(String intentId) {
            for (Intent in : intents) {
                if (in.intentId.equals(intentId)) {
                    return new Intent(in);
                }
            }
            return null;
        }

    }


    /**
     * This value determines the (maximum) number of documents in the relevance ranking
     * and the intent rankings that the software should use for diversification.
     */
    private int maxInputRankingsLength;
    /**
     * This value determines the number of documents in the
     * diversified ranking that the software will produce.
     */
    private int maxResultRankingLength;

    /**
     * The diversification algorithm: PM2 or xQuAD.
     */
    private String diversifyAlgorithm;
    /**
     * Parameter that determines the degree of diversification.
     */
    private double lambda;

    /**
     * The path to the query intents file.
     */
    private String intentsFile;
    /**
     * The name of a file that contains document rankings for queries and query intents.
     */
    private String initialRankingFile;

    /**
     * The retrieval model that determines the initial ranking.
     */
    private RetrievalModel retrievalModel;
    /**
     * The path to the training / test queries.
     */
    private String queryFilePath;
    /**
     * Final output path.
     */
    private String trecEvalOutputPath;

    /**
     * Mappings from query id to corresponding QueryIntent object
     */
    private Map<String, QueryIntent> queryIntents; // <qid, intents>
    /**
     * Mappings from query id to corresponding initial document scores
     */
    private Map<String, ScoreList> initialRanking; // <qid, scoreList>
    /**
     * Mappings from QueryIntent object to initial intent scores
     */
    private Map<Intent, ScoreList> intentRanking;  // <(qid, intentId, intent), scoreList>

    /**
     * Mappings from query id to candidate document set
     */
    private Map<String, Set<Integer>> candidateDocSets; // <qid, docid>


    /**
     * Constructor for RankingAlgorithmDiversity object.
     * @param parameters Configuration parameters
     */
    public RankingAlgorithmDiversify(Map<String, String> parameters) {

        maxInputRankingsLength = Integer.parseInt(parameters.get("diversity:maxInputRankingsLength"));
        maxResultRankingLength = Integer.parseInt(parameters.get("diversity:maxResultRankingLength"));

        diversifyAlgorithm = parameters.get("diversity:algorithm").toLowerCase();
        lambda = Double.parseDouble(parameters.get("diversity:lambda"));

        initialRankingFile = null; // optional
        if ( parameters.containsKey("diversity:initialRankingFile") ) {
            initialRankingFile = parameters.get("diversity:initialRankingFile");
        }
        intentsFile = parameters.get("diversity:intentsFile");

        queryFilePath = parameters.get("queryFilePath");
        trecEvalOutputPath = parameters.get("trecEvalOutputPath");
        retrievalModel = QryEval.initializeRetrievalModel(parameters); // might be null

        queryIntents = new HashMap<>();
        initialRanking = new HashMap<>();
        intentRanking = new HashMap<>();
        candidateDocSets = new HashMap<>();

    }

    /**
     * Run the diversify algorithm to retrieve & rank documents.
     * @throws Exception
     */
    @Override
    public void run() throws Exception {

        readIntentsFile(intentsFile);

        if (initialRankingFile != null || retrievalModel == null) {

            readInitialRankingFile(initialRankingFile, maxInputRankingsLength);

        } else {

            processQueryFile(queryFilePath, retrievalModel, maxInputRankingsLength);

        }

        normalizeScores();

        generateFinalRanking();

    }

    /**
     * Read from query intents file to populate `queryIntents`: <qid, intents>.
     * @param filename Path to intentsFile
     * @throws IOException if I/O related exception occurs
     */
    private void readIntentsFile(String filename)
        throws IOException {

        queryIntents.clear();

        BufferedReader input = new BufferedReader( new FileReader(filename) );

        String line;
        while ( (line = input.readLine()) != null ) {

            if (!line.trim().isEmpty()) {

                int dotIdx = line.indexOf(".");
                int colonIdx = line.indexOf(":");
                String qid = line.substring(0, dotIdx);
                String intentId = line.substring(dotIdx + 1, colonIdx);
                String intentText = line.substring(colonIdx + 1).trim();

                if ( !queryIntents.containsKey(qid) ) {
                    QueryIntent queryIntent = new QueryIntent(qid);
                    queryIntent.addIntent(intentId, intentText);
                    queryIntents.put(qid, queryIntent);
                } else {
                    QueryIntent queryIntent = queryIntents.get(qid);
                    queryIntent.addIntent(intentId, intentText);
                }

            }

        }

        input.close();

    }

    /**
     * Read from initial ranking file to populate initialRanking, intentRanking, and candidateDocSets.
     * Assume the initial ranking file is already sorted by score
     *
     * @param filename Path to initialRankingFile
     * @param maxInputRankingsLength The (maximum) number of documents in the relevance ranking
     *                               and the intent rankings that'll be used for diversification
     * @throws Exception
     */
    private void readInitialRankingFile(String filename, int maxInputRankingsLength)
            throws Exception {

        // Cache internal and external docid mappings
        Map<String, Integer> docIdMappings = new HashMap<>();

        // Init initialRanking and intentInitialRanking
        initialRanking.clear();   // <qid, scoreList>
        intentRanking.clear();    // <(qid, intentId, intent), scoreList>
        candidateDocSets.clear(); // <qid, docid>

        // Init temp scoreList
        ScoreList scoreList = new ScoreList();

        // Init file I/O
        BufferedReader input = null;
        String line = null;
        input = new BufferedReader( new FileReader(filename) );

        // Keep track of the previous query id & the previous query intent
        String prev_qid = null;
        Intent prev_intent = null;

        while ( (line = input.readLine()) != null ) {

            // Skip empty or dummy lines
            if (!line.trim().isEmpty() && !line.contains("dummy")) {

                // Split line by spaces and tabs
                String[] data = line.split("[\\s\t]+");

                String qid = data[0];

                String externalId = data[2];
                int docid;
                if (docIdMappings.containsKey(externalId)) {
                    docid = docIdMappings.get(externalId);
                } else {
                    docid = Idx.getInternalDocid(externalId);
                    docIdMappings.put(externalId, docid);
                }

                double score = Double.parseDouble(data[4].trim());

                // If current line if a query
                if (!qid.contains(".")) {

                    // Put the last query intent to rankings map, if not null
                    if (prev_intent != null) {
                        // scoreList.sort();
                        scoreList.truncate(maxInputRankingsLength);
                        intentRanking.put(prev_intent, new ScoreList(scoreList));
                        scoreList.clear();
                        prev_intent = null;
                    }

                    // If first line or the line indicates the same query
                    if (prev_qid == null || qid.equals(prev_qid)) {
                        scoreList.add(docid, score);
                    } else {
                        // If different qid
                        // scoreList.sort();
                        scoreList.truncate(maxInputRankingsLength);
                        candidateDocSets.put(prev_qid, new HashSet<>(scoreList.getDocidList()));
                        initialRanking.put(prev_qid, new ScoreList(scoreList));
                        scoreList.clear();
                        scoreList.add(docid, score);
                    }

                    // Update previous qid
                    prev_qid = qid;

                } else {

                    // Put the last qid to rankings map, if not null
                    if (prev_qid != null) {
                        // scoreList.sort();
                        scoreList.truncate(maxInputRankingsLength);
                        candidateDocSets.put(prev_qid, new HashSet<>(scoreList.getDocidList()));
                        initialRanking.put(prev_qid, new ScoreList(scoreList));
                        scoreList.clear();
                        prev_qid = null;
                    }

                    int divideIdx = qid.indexOf(".");
                    String intentId = qid.substring(divideIdx + 1);
                    qid = qid.substring(0, divideIdx);
                    Intent intent = queryIntents.get(qid).getIntentById(intentId);
                    // Intent intent = new Intent(qid, intentId);

                    if (prev_intent == null || intent.equals(prev_intent)) {
                        scoreList.add(docid, score);
                    } else {
                        // scoreList.sort();
                        scoreList.truncate(maxInputRankingsLength);
                        intentRanking.put(prev_intent, new ScoreList(scoreList));
                        scoreList.clear();
                        scoreList.add(docid, score);
                    }

                    prev_intent = intent;

                }

            }

        }

        // Put the last intent to rankings map, if not null
        if (prev_intent != null) {
            // scoreList.sort();
            scoreList.truncate(maxInputRankingsLength);
            intentRanking.put(prev_intent, new ScoreList(scoreList));
        }

        // Put the last qid to rankings map, if not null
        if (prev_qid != null) {
            // scoreList.sort();
            scoreList.truncate(maxInputRankingsLength);
            initialRanking.put(prev_qid, new ScoreList(scoreList));
        }

        input.close();

        assert initialRanking.size() == candidateDocSets.size() :
                "# of candidate documents should equal size of the initial ranking map";

    }

    /**
     * Retrieve documents to populate initialRanking, intentRanking, and candidateDocSets.
     * @param queryFilePath Path to the training/testing queries
     * @param model Retrieval model
     * @param maxInputRankingsLength The (maximum) number of documents in the relevance ranking
     *      *                        and the intent rankings that'll be used for diversification
     * @throws IOException if I/O related exception occurs
     */
    private void processQueryFile(String queryFilePath, RetrievalModel model, int maxInputRankingsLength)
        throws IOException {

        initialRanking.clear();
        intentRanking.clear();
        candidateDocSets.clear();

        BufferedReader input = null;
        String qLine = null;
        input = new BufferedReader( new FileReader(queryFilePath) );

        while ( (qLine = input.readLine()) != null ) {

            int d = qLine.indexOf(':');

            if (d < 0) {
                throw new IllegalArgumentException
                        ("Syntax error:  Missing ':' in query line.");
            }

            QryEval.printMemoryUsage(false);

            String qid = qLine.substring(0, d);
            String query = qLine.substring(d + 1);

            System.out.println("Query " + qLine);

            ScoreList r = null;
            r = QryEval.processQuery(qid, query, model);
            if (r != null) {
                r.truncate(maxInputRankingsLength);
                candidateDocSets.put(qid, new HashSet<>(r.getDocidList()));
                initialRanking.put(qid, new ScoreList(r));
            }

            if (queryIntents.containsKey(qid)) {

                QueryIntent queryIntent  = queryIntents.get(qid);
                List<Intent> intents = queryIntent.getIntents();

                for (int i = 0; i < intents.size(); i++) {
                    Intent intent = intents.get(i);
                    r = QryEval.processQuery(qid, intent.getIntentText(), model);
                    if (r != null) {
                        r.truncate(maxInputRankingsLength);
                        intentRanking.put(intent, new ScoreList(r));
                    }
                }

            }

        }

        input.close();

        assert initialRanking.size() == candidateDocSets.size() :
                "# of candidate documents should equal size of the initial ranking map";

    }

    /**
     * Returns the max score and sum of all scores from the score list.
     * @param scores A ScoreList object
     * @param includeDocs A set of documents whose scores are to consider
     * @return An array containing the max score and the sum of all scores from the score list
     */
    private static double[] getScoreListMaxAndSum(ScoreList scores, Set<Integer> includeDocs) {
        /*
         * Double.MIN_VALUE is positive. Refer to:
         * [Why is Double.MIN_VALUE in not negative](https://stackoverflow.com/questions/3884793/why-is-double-min-value-in-not-negative)
         * Use -Double.MAX_VALUE for negative min double value
         */
        double max = -Double.MAX_VALUE;
        double sum = 0;
        for (int i = 0; i < scores.size(); i++) {
            if ( includeDocs.contains(scores.getDocid(i)) ) {
                double score = scores.getDocidScore(i);
                sum += score;
                max = Math.max(max, score);
            }
        }
        return new double[] {max, sum};
    }

    /**
     * Returns the max max value and max sum of score lists for a specific query.
     * @param qid Query id
     * @return An array containing the max max value and the max sum of scores for the given query
     */
    private double[] getMaxScoreListMaxAndSum(String qid) {

        double[] maxAndSum = getScoreListMaxAndSum(initialRanking.get(qid), candidateDocSets.get(qid));
        double maxMax = maxAndSum[0];
        double max;
        double maxSum = maxAndSum[1];
        double sum;

        QueryIntent queryIntent = queryIntents.get(qid);
        List<Intent> intents = queryIntent.getIntents();

        for (Intent intent : intents) {
            ScoreList intentScores = intentRanking.get(intent);
            if (intentScores == null) {
                continue;
            }
            maxAndSum = getScoreListMaxAndSum(intentScores, candidateDocSets.get(qid));
            max = maxAndSum[0];
            maxMax = Math.max(max, maxMax);
            sum = maxAndSum[1];
            maxSum = Math.max(sum, maxSum);
        }

        return new double[] {maxMax, maxSum};
    }

    /**
     * Normalize scores to range (0,1).
     * Don't normalize when max score is less than 1
     */
    private void normalizeScores() {

        for (String qid : initialRanking.keySet()) {
            double[] maxAndSum = getMaxScoreListMaxAndSum(qid);
            double maxMax = maxAndSum[0];
            double maxSum = maxAndSum[1];
            // Don't normalize when maxMax is already less than 1
             if (maxMax <= 1) {
                 continue;
             }
            // Normalize initial document ranking for qid
            ScoreList docScores = initialRanking.get(qid);
            for (int i = 0; i < docScores.size(); i++) {
                docScores.setDocidScore(i, docScores.getDocidScore(i) / maxSum);
            }
            // Normalize initial intents ranking for qid
            QueryIntent queryIntent = queryIntents.get(qid);
            List<Intent> intents = queryIntent.getIntents();
            for (int i = 0; i < intents.size(); i++) {
                Intent intent = intents.get(i);
                ScoreList intentScores  = intentRanking.get(intent);
                if (intentScores == null) {
                    continue;
                }
                for (int j = 0; j < intentScores.size(); j++) {
                    intentScores.setDocidScore(j, intentScores.getDocidScore(j) / maxSum);
                }
            }
        }

    }

    /**
     * Returns the max score in a given ScoreList object.
     * @param scores A ScoreList object
     * @return The max score in the score list
     */
    private static int getMaxScoreDocid(ScoreList scores) {

        double max = 0;
        int docid = -1;

        for (int i = 0; i < scores.size(); i++) {
            double score = scores.getDocidScore(i);
            if (docid == -1 || score > max) {
                max = score;
                docid = scores.getDocid(i);
            }
        }

        return docid;
    }

    /**
     * Returns the sum of numbers in an array in range(start, end).
     * @param arr An array
     * @param start The start index
     * @param end The end index
     * @return The sum of numbers in the array in range(start, end)
     */
    private static double getRangeSum(double[] arr, int start, int end) {
        double sum = 0;
        for (int i = start; i <= end; i++) {
            sum += arr[i];
        }
        return sum;
    }

    /**
     * Returns a mapping from a docid to an array of scores consisting
     * of the document's initial ranking and intent relevance scores.
     * @param queryIntent The QueryIntent object for a query with a list of intents
     * @param candidateDocs The candidate documents for a query
     * @param initialCandidateRanking The initial documents ranking for a query
     * @return A mapping from a docid to an array of document scores
     */
    private Map<Integer, double[]> getDocQueryIntentScoreMap(QueryIntent queryIntent, Set<Integer> candidateDocs, ScoreList initialCandidateRanking) {

        Map<Integer, double[]> docQueryIntentScoreMap = new HashMap<>();
        List<Intent> intents = queryIntent.getIntents();

        for (int i = 0; i < initialCandidateRanking.size(); i++) {
            int docid = initialCandidateRanking.getDocid(i);
            double docScore = initialCandidateRanking.getDocidScore(i);
            if (!docQueryIntentScoreMap.containsKey(docid)) {
                double[] scores = new double[intents.size() + 1];
                scores[0] = docScore;
                docQueryIntentScoreMap.put(docid, scores);
            } else {
                double[] scores = docQueryIntentScoreMap.get(docid);
                scores[0] = docScore;
            }
        }

        for (int i = 0; i < intents.size(); i++) {
            Intent intent = intents.get(i);
            ScoreList intentScores = intentRanking.get(intent);
            if (intentScores == null) {
                continue;
            }
            for (int j = 0; j < intentScores.size(); j++) {
                int docid = intentScores.getDocid(j);
                if (!candidateDocs.contains(docid)) {
                    continue;
                }
                double docIntentScore = intentScores.getDocidScore(j);
                if (!docQueryIntentScoreMap.containsKey(docid)) {
                    double[] scores = new double[intents.size() + 1];
                    scores[i + 1] = docIntentScore;
                    docQueryIntentScoreMap.put(docid, scores);
                } else {
                    double[] scores = docQueryIntentScoreMap.get(docid);
                    scores[i + 1] = docIntentScore;
                }
            }
        }

        return docQueryIntentScoreMap;

    }

    /**
     * Re-rank the initial ranking using xQuAD diversification algorithm.
     * @param queryIntent The QueryIntent object for a query with a list of intents
     * @param candidateDocs The candidate documents for a query
     * @param initialCandidateRanking The initial documents ranking for a query
     * @return The re-ranked score list with diversification included
     */
    private ScoreList xQuadDiversify(QueryIntent queryIntent, Set<Integer> candidateDocs, ScoreList initialCandidateRanking) {

        Map<Integer, double[]> docQueryIntentScoreMap = getDocQueryIntentScoreMap(queryIntent, candidateDocs, initialCandidateRanking);

        int size = Math.min(docQueryIntentScoreMap.size(), maxResultRankingLength);
        Set<Integer> initialDocSet = new HashSet<>(candidateDocs);
        List<Intent> intents = queryIntent.getIntents();

        ScoreList diversifiedRanking = new ScoreList();
        double[] prefixWeights = new double[intents.size() + 1];
        Arrays.fill(prefixWeights, lambda * 1. / intents.size());

        while ( diversifiedRanking.size() < size ) {

            int maxDocid = -1;
            double maxXquadScore = 0;

            for (Integer docid : initialDocSet) {

                double[] scores = docQueryIntentScoreMap.get(docid);
                double xquadScore = (1 - lambda) * scores[0];
                for (int i = 1; i < scores.length; i++) {
                    xquadScore += prefixWeights[i] * scores[i];
                }

                if (maxDocid == -1 || xquadScore > maxXquadScore) {
                    maxXquadScore = xquadScore;
                    maxDocid = docid;
                }

            }

            double[] maxDocScores = docQueryIntentScoreMap.get(maxDocid);
            initialDocSet.remove(maxDocid);
            diversifiedRanking.add(maxDocid, maxXquadScore);

            // Update prefix weights
            for (int i = 1; i < maxDocScores.length; i++) {
                prefixWeights[i] *= (1 - maxDocScores[i]);
            }

        }

        // Sort the diversified ranking by xQuAD score (but no need)
        diversifiedRanking.sort();

        return diversifiedRanking;

    }

    /**
     * Re-rank the initial ranking using PM25 diversification algorithm.
     * @param queryIntent The QueryIntent object for a query with a list of intents
     * @param candidateDocs The candidate documents for a query
     * @param initialCandidateRanking The initial documents ranking for a query
     * @return The re-ranked score list with diversification included
     */
    private ScoreList pm2Diversify(QueryIntent queryIntent, Set<Integer> candidateDocs, ScoreList initialCandidateRanking) {

        Map<Integer, double[]> docQueryIntentScoreMap = getDocQueryIntentScoreMap(queryIntent, candidateDocs, initialCandidateRanking);

        int size = Math.min(docQueryIntentScoreMap.size(), maxResultRankingLength);
        Set<Integer> initialDocSet = new HashSet<>(candidateDocs);
        List<Intent> intents = queryIntent.getIntents();

        ScoreList diversifiedRanking = new ScoreList();

        double[] votes = new double[intents.size() + 1];
        // Initial votes = ranking depth / # intents
        Arrays.fill(votes, maxResultRankingLength * 1. / intents.size());
        double[] slots = new double[intents.size() + 1];
        double[] quotients = new double[intents.size() + 1];

        while ( diversifiedRanking.size() < size ) {

            // Select intent with max quotient score
            int maxIntent = -1;
            double maxQuotientScore = 0;

            for (int i = 1; i <= intents.size(); i++) {
                quotients[i] = votes[i] / (2 * slots[i] + 1);
                if (maxIntent == -1 || quotients[i] > maxQuotientScore) {
                    maxQuotientScore = quotients[i];
                    maxIntent = i;
                }
            }

            // Select document with max pm2 score
            int maxDocid = -1;
            double maxPM2Score = 0;

            for (Integer docid : initialDocSet) {

                double[] scores = docQueryIntentScoreMap.get(docid);
                double pm2Score = lambda * quotients[maxIntent] * scores[maxIntent];
                for (int i = 1; i <= intents.size(); i++) {
                    if (i != maxIntent) {
                        pm2Score += (1 - lambda) * quotients[i] * scores[i];
                    }
                }

                if (maxDocid == -1 || pm2Score > maxPM2Score) {
                    maxPM2Score = pm2Score;
                    maxDocid = docid;
                }

            }

            // Update slots
            double[] maxDocScores = docQueryIntentScoreMap.get(maxDocid);
            double sum = getRangeSum(maxDocScores, 1, maxDocScores.length - 1);
            for (int i = 1; i <= intents.size(); i++) {
                // Check if sum is 0 (avoid NaN)
                if (sum != 0) {
                    slots[i] += maxDocScores[i] / sum;
                }
            }

            initialDocSet.remove(maxDocid);
            diversifiedRanking.add(maxDocid, maxPM2Score);

        }

        // Sort the diversified ranking by PM2 score (but no need)
        diversifiedRanking.sort();

        return diversifiedRanking;

    }

    /**
     * Generate final ranking using specified diversity algorithm.
     */
    private void generateFinalRanking() {

        for (String qid : initialRanking.keySet()) {

            QueryIntent queryIntent = queryIntents.get(qid);
            ScoreList initialCandidateRanking = initialRanking.get(qid);
            Set<Integer> candidateDocs = candidateDocSets.get(qid);
            ScoreList r = null;

            if (diversifyAlgorithm.equals("pm2")) {
                r = pm2Diversify(queryIntent, candidateDocs, initialCandidateRanking);
            } else if (diversifyAlgorithm.equals("xquad")) {
                r = xQuadDiversify(queryIntent, candidateDocs, initialCandidateRanking);
            } else {
                throw new IllegalArgumentException
                        ("Diversity algorithm not supported: " + diversifyAlgorithm);
            }

            // Write final ranking to trecEvalOutputPath
            QryEval.genOutputFile(qid, r, trecEvalOutputPath, maxResultRankingLength);

        }

    }


    /**
     * Simple test program.
     * @param args Arguments
     */
    public static void main(String[] args) {

    }

}
