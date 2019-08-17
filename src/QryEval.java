/*
 *  Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.3.3.
 */

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

/**
 * This software illustrates the architecture for the portion of a
 * search engine that evaluates queries.  It is a guide for class
 * homework assignments, so it emphasizes simplicity over efficiency.
 * It implements an unranked Boolean retrieval model, however it is
 * easily extended to other retrieval models.  For more information,
 * see the ReadMe.txt file.
 */
public class QryEval {

    //  --------------- Constants and variables ---------------------

    private static final String USAGE =
            "Usage:  java QryEval paramFile\n\n";

    private static final String[] TEXT_FIELDS =
            {"body", "title", "url", "inlink"};

    /**
     * Stores global parameters
     */
    private static Map<String, String> parameters = new HashMap<>();

    /**
     * No query expansion by default
     */
    private static boolean doExpansion = false;
    /**
     * No initial reference system for query expansion by default
     */
    private static Map<String, ScoreList> expansionRefScoreMap = null;
    /**
     * Caches corpus length for all text fields
     */
    private static Map<String, Long> sumOfFieldLengths = null;

    //  --------------- Methods ---------------------------------------

    /**
     * @param args The only argument is the parameter file name.
     * @throws Exception Error accessing the Lucene index.
     */
    public static void main(String[] args) throws Exception {

        //  This is a timer that you may find useful.  It is used here to
        //  time how long the entire program takes, but you can move it
        //  around to time specific parts of your code.

        Timer timer = new Timer();
        timer.start();

        //  Check that a parameter file is included, and that the required
        //  parameters are present.  Just store the parameters.  They get
        //  processed later during initialization of different system
        //  components.

        if (args.length < 1) {
            throw new IllegalArgumentException(USAGE);
        }

        // Map<String, String> parameters = readParameterFile(args[0]);
        parameters = readParameterFile(args[0]);

        //  Open the index
        Idx.open(parameters.get("indexPath"));

        // Initializes mapping between field name and corresponding corpus lengths for fast access
        initializeCorpusLengthsMapping();

        // If the retrieval algorithm specified is letor
        if (  parameters.containsKey("retrievalAlgorithm") &&
                parameters.get("retrievalAlgorithm").toLowerCase().equals("letor")  ) {

            // Initialize the ranking algorithm
            RankingAlgorithm letor = new RankingAlgorithmLetor(parameters);
            // Run the algorithm.
            letor.run();

        } else if ( parameters.containsKey("diversity") &&
                    parameters.get("diversity").equalsIgnoreCase("true") ) {

            // Initialize the ranking algorithm
            RankingAlgorithm diversify = new RankingAlgorithmDiversify(parameters);
            // Run the algorithm.
            diversify.run();

        } else {

            // Initialize the retrieval model
            RetrievalModel model = initializeRetrievalModel(parameters);

            //  Perform experiments.
            processQueryFile(parameters.get("queryFilePath"), model);

        }

        //  Clean up.
        timer.stop();
        System.out.println("Time:  " + timer);
    }

    /**
     * Initializes mapping between field name and corresponding corpus lengths.
     * `Idx.getSumOfFieldLengths("body")` is extremely slow, to boost speed,
     *  better to cache it one time rather than compute it every time we want it
     *
     * @throws IOException if error access lucene index
     */
    static void initializeCorpusLengthsMapping() throws IOException {

        if (sumOfFieldLengths == null) {
            sumOfFieldLengths = new HashMap<>();
        }

        for (String field : TEXT_FIELDS) {
            long corpusLen = Idx.getSumOfFieldLengths(field);
            sumOfFieldLengths.put(field, corpusLen);
        }

    }

    /**
     * Allocate the retrieval model and initialize it using parameters
     * from the parameter file.
     *
     * @return The initialized retrieval model
     */
    static RetrievalModel initializeRetrievalModel(Map<String, String> parameters) {

        RetrievalModel model = null;

        if (!parameters.containsKey("retrievalAlgorithm")) {
            return null;
        }

        String modelString = parameters.get("retrievalAlgorithm").toLowerCase();

        if (modelString.equals("unrankedboolean")) {

            model = new RetrievalModelUnrankedBoolean();

        } else if (modelString.equals("rankedboolean")) {

            model = new RetrievalModelRankedBoolean();

        } else if (modelString.equals("bm25")) {

            // Define default BM25 parameters

            double bm25_k_1 = 1.2, bm25_b = 0.75, bm25_k_3 = 0;

            // Extract model parameters

            if (parameters.containsKey("BM25:k_1") && !parameters.get("BM25:k_1").isEmpty())
                bm25_k_1 = Double.parseDouble(parameters.get("BM25:k_1").trim());
            if (parameters.containsKey("BM25:b") && !parameters.get("BM25:b").isEmpty())
                bm25_b = Double.parseDouble(parameters.get("BM25:b").trim());
            if (parameters.containsKey("BM25:k_3") && !parameters.get("BM25:k_3").isEmpty())
                bm25_k_3 = Double.parseDouble(parameters.get("BM25:k_3").trim());

            model = new RetrievalModelBM25(bm25_k_1, bm25_b, bm25_k_3);

        } else if (modelString.equals("indri")) {

            // Define default Indri parameters

            double indri_mu = 2500, indri_lambda = 0.4;

            // Extract model parameters

            if (parameters.containsKey("Indri:mu") && !parameters.get("Indri:mu").isEmpty())
                indri_mu = Double.parseDouble(parameters.get("Indri:mu").trim());
            if (parameters.containsKey("Indri:lambda") && !parameters.get("Indri:lambda").isEmpty())
                indri_lambda = Double.parseDouble(parameters.get("Indri:lambda").trim());

            model = new RetrievalModelIndri(indri_mu, indri_lambda);

        } else {
            throw new IllegalArgumentException
                    ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
        }

        return model;
    }

    /**
     * Print a message indicating the amount of memory used. The caller can
     * indicate whether garbage collection should be performed, which slows the
     * program but reduces memory usage.
     *
     * @param gc If true, run the garbage collector before reporting.
     */
    public static void printMemoryUsage(boolean gc) {

        Runtime runtime = Runtime.getRuntime();

        if (gc)
            runtime.gc();

        System.out.println("Memory used:  "
                + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
    }


    /**
     * Read reference document ranking for queries.
     *
     * @param filename path to the initial ranking file
     * @return A score map with key being the query id and
     *          value being a score list for that query id
     * @throws Exception
     */
    static Map<String, ScoreList> readInitialRanking(String filename)
            throws Exception {

        // Cache internal and external docid mappings
        Map<String, Integer> docIdMappings = new HashMap<>();

        // Init scoreMap and scoreList
        Map<String, ScoreList> scoreMap = new HashMap<>();
        ScoreList scoreList = new ScoreList();

        // Init file I/O
        BufferedReader input = null;
        String line = null;
        input = new BufferedReader( new FileReader(filename) );

        // Keep track of the previous query id
        String prev_qid = null;

        while ((line = input.readLine()) != null) {

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

                // If first line or the line indicates the same query
                if (prev_qid == null || qid.equals(prev_qid)) {
                    scoreList.add(docid, score);
                } else {
                    // If different qid, sort the current scoreList,
                    // make a deep copy and add the copy to the previous qid's scoreMap
                    scoreList.sort();
                    scoreMap.put(prev_qid, new ScoreList(scoreList));
                    // Then clear the scoreList and add the current score to the current qid's scoreMap
                    scoreList.clear();
                    scoreList.add(docid, score);
                }
                // Update previous qid
                prev_qid = qid;
            }
        }

        // Put the last qid to rankings map
        scoreList.sort();
        scoreMap.put(prev_qid, new ScoreList(scoreList));

        input.close();

        return scoreMap;

    }

    /**
     * Expand query using Indri expansion algo.
     *
     * @param referenceRanking Reference ranking system, a ScoreList of query and document scores
     * @return The expanded query
     * @throws IOException
     */
    static String expandQuery(ScoreList referenceRanking, int fbDocs, int fbTerms, int fbMu) throws IOException {

        // Build the expanded query
        StringBuilder expandedQuery = new StringBuilder("#wand (");

        Map<String, Double> termScores = new HashMap<>();     // stores score for each term t
        Map<String, Double> termMLE = new HashMap<>();        // stores corpus-specific statistic for term t
        Map<String, List<Integer>> invList = new HashMap<>(); // inverted list for term t

        // The initial query Qoriginal retrieves the top-ranked n documents
        // int docSize = Math.min(fbDocs, referenceRanking.size());
        referenceRanking.truncate(fbDocs);

        // Extract potential expansion terms from top n documents

        // `Idx.getSumOfFieldLengths("body")` is extremely slow, to boost speed,
        // better to cache it one time rather than compute it every time we call `expandQuery`
        double corpusLen = sumOfFieldLengths.get("body");

        for (int i = 0; i < referenceRanking.size(); i++) {
            int docid = referenceRanking.getDocid(i);
            double docScore = referenceRanking.getDocidScore(i);
            double docLen = Idx.getFieldLength("body", docid);

            // Retrieve the term vector for externalID
            TermVector termVector = new TermVector(docid, "body");

            // Calculate a score for each potential expansion term
            // termVector[0] = null: START FROM 1 !!!
            for (int j = 0; j < termVector.stemsLength(); j++) {
                String term = termVector.stemString(j);

                // Ignore any candidate expansion term that contains a period ('.') or a comma (',')
                if (term == null || term.contains(".") || term.contains(",")) continue;

                // Update inverted list for the current term
                if (invList.containsKey(term)) {
                    invList.get(term).add(docid);
                } else {
                    List<Integer> docs = new ArrayList<>();
                    docs.add(docid);
                    invList.put(term, docs);
                }

                double tf = termVector.stemFreq(j);

                // Get/Update corpus-specific statistic (p(t|C) for the current term
                double mle;
                if (termMLE.containsKey(term)) {
                    mle = termMLE.get(term);
                } else {
                    double ctf = termVector.totalStemFreq(j);
                    mle = ctf / corpusLen;
                    termMLE.put(term, mle);
                }

                double score = (tf + fbMu * mle) / (docLen + fbMu);
                double idf = Math.log(1. / mle);
                double weightedScore = score * docScore * idf;

                termScores.put(term, termScores.getOrDefault(term, 0d) + weightedScore);
            }
        }

        /*
         * Now we get m expansion terms from top n docs
         * However, the term score for these m terms is not complete yet
         * because some of the top n docs (referenceRanking) might not contain all the m terms
         * but they still need to contribute to the final term score
         * Therefore, we need to update term score on docs whose tf = 0
         * */

        for (String term : termScores.keySet()) {
            List<Integer> docs = invList.get(term);
            for (int i = 0; i < referenceRanking.size(); i++) {
                int docid = referenceRanking.getDocid(i);
                if (!docs.contains(docid)) {
                    double docScore = referenceRanking.getDocidScore(i);
                    double docLen = Idx.getFieldLength("body", docid);

                    double mle = termMLE.get(term);
                    double score = (0 + fbMu * mle) / (docLen + fbMu);
                    double idf = Math.log(1. / mle);
                    double weightedScore = score * docScore * idf;

                    termScores.put(term, termScores.get(term) + weightedScore);
                }
            }
        }

        // Sort the term scores by score value, leaving only m top terms in the map
        termScores = MapUtil.sortByDescValue(termScores, fbTerms);

        // Use the top m terms to create an expansion query Qlearned
        for (Map.Entry<String, Double> entry : termScores.entrySet()) {
            String term = entry.getKey();
            double score = entry.getValue();
            expandedQuery.append(String.format("%.4f %s ", score, term));
        }

        expandedQuery.append(")");
        return expandedQuery.toString();
    }

    /**
     * Get a weighted combination of the original query and the expanded query.
     *
     * @param q_original The original query
     * @param q_expanded The expanded query
     * @param fbOrigWeight The weight on the original query
     * @return The combined query.
     */
    static String getCombinedQuery(String q_original, String q_expanded, double fbOrigWeight) {

        StringBuilder query = new StringBuilder("#wand (");
        query.append(fbOrigWeight).append(" ").append(q_original);
        query.append(1 - fbOrigWeight).append(" ").append(q_expanded);
        query.append(")");

        return query.toString();
    }

    /**
     * Process the query file.
     *
     * @param queryFilePath
     * @param model
     * @throws IOException Error accessing the Lucene index.
     */
    static void processQueryFile(String queryFilePath,
                                 RetrievalModel model)
            throws Exception {

        BufferedReader input = null;

        // Set global parameters for query expansion
        if (parameters.containsKey("fb") && parameters.get("fb").equalsIgnoreCase("true")) {
            doExpansion = true;
            if (parameters.containsKey("fbInitialRankingFile") && !parameters.get("fbInitialRankingFile").isEmpty()) {
                expansionRefScoreMap = readInitialRanking(parameters.get("fbInitialRankingFile"));
            }
        }

        try {
            String qLine = null;

            input = new BufferedReader(new FileReader(queryFilePath));

            //  Each pass of the loop processes one query.

            while ((qLine = input.readLine()) != null) {
                int d = qLine.indexOf(':');

                if (d < 0) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Missing ':' in query line.");
                }

                printMemoryUsage(false);

                String qid = qLine.substring(0, d);    // 704
                String query = qLine.substring(d + 1); // #OR( Green party political views )

                System.out.println("Query " + qLine);

                ScoreList r = null;

                r = processQuery(qid, query, model);

                if (r != null) {
                    // printResults(qid, r);
                    genOutputFile( qid, r, parameters.get("trecEvalOutputPath"),
                            Integer.parseInt(parameters.get("trecEvalOutputLength").trim()) );
                    System.out.println();
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();
        }
    }

    /**
     * Process one query string.
     *
     * @param q_original A string that contains the original query.
     * @param model The retrieval model determines how matching and scoring is done.
     * @return Search results (retrieved document plus score)
     * @throws IOException Error accessing the index
     */
    static ScoreList processQuery(String qid, String q_original, RetrievalModel model)
            throws IOException {

        String defaultOp = model.defaultQrySopName();
        q_original = defaultOp + "(" + q_original + ")";     // #or(#OR( Green party political views ))
        Qry q = null;
        ScoreList r = null;

        // If specified query expansion
        if (doExpansion) {
            ScoreList referenceRanking;
            String fbExpansionQueryFile;
            double fbOrigWeight;
            String q_expanded;

            // If initial reference ranking system is specified, the reference ranking
            // (ScoreList) for current qid is obtained by looking up in the expansionRefScoreMap
            if (expansionRefScoreMap != null) {
                referenceRanking = expansionRefScoreMap.get(qid);
            } else {
                // If no initial reference system, the reference ranking (ScoreList) for
                // the current qid is obtained by processing the original query as usual
                q = QryParser.getQuery(q_original);
                referenceRanking = processQry(q, model);
            }

            // Use the Indri query expansion algorithm to produce an expanded query
            int fbDocs = Integer.parseInt(parameters.get("fbDocs").trim());   // # of docs to use for query expansion
            int fbTerms = Integer.parseInt(parameters.get("fbTerms").trim()); // # of terms to add to query
            int fbMu = Integer.parseInt(parameters.get("fbMu").trim());       // amt of smoothing used to calc p(r|d)

            q_expanded = expandQuery(referenceRanking, fbDocs, fbTerms, fbMu);

            // Write the expanded query to a file
            fbExpansionQueryFile = parameters.get("fbExpansionQueryFile");
            genExpandedQueryFile(qid, q_expanded, fbExpansionQueryFile);

            // Create a combined query as #wand (w q_original + (1-w) q_expanded)
            fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight").trim());
            q_expanded = getCombinedQuery(q_original, q_expanded, fbOrigWeight);

            // Use the combined query to retrieve documents
            q = QryParser.getQuery(q_expanded);

        } else {
            // Use the original query to retrieve documents;
            q = QryParser.getQuery(q_original);
        }

        // Show the query that is evaluated
        System.out.println("    --> " + q);
        r = processQry(q, model);

        return r;
    }

    /**
     * Process one Qry object.
     *
     * @param q A query object representing a (preprocessed) query
     * @param model The retrieval model determines how matching and scoring is done.
     * @return A **sorted** score list (retrieved document plus score)
     * @throws IOException
     */
    static ScoreList processQry(Qry q, RetrievalModel model) throws IOException {
        if (q == null) return null;

        ScoreList r = new ScoreList();  // <internalDocid, externalDocid, score>

        if (q.args.size() > 0) {        // Ignore empty queries

            q.initialize(model);

            while (q.docIteratorHasMatch(model)) {
                int docid = q.docIteratorGetMatch();
                double score = ((QrySop) q).getScore(model);
                r.add(docid, score);    // ScoreListEntry(internalDocid, score)
                q.docIteratorAdvancePast(docid);
            }
        }

        // Return the sorted score list
        r.sort();

        return r;
    }

    /**
     * Print the query results.
     * <p>
     * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
     * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
     * <p>
     * QueryID Q0 DocID Rank Score RunID
     *
     * @param queryName Original query.
     * @param result    A list of document ids and scores
     * @throws IOException Error accessing the Lucene index.
     */
    static void printResults(String queryName, ScoreList result) throws IOException {

        System.out.println(queryName + ":  ");
        if (result.size() < 1) {
            System.out.println("\t" + queryName + "\tQ0\tdummy\t1\t0\trun-1");
        } else {
            result.sort();
            for (int i = 0; i < Math.min(result.size(), Integer.parseInt(parameters.get("trecEvalOutputLength").trim())); i++) {
                System.out.println("\t" + queryName + "\tQ0" + "\t" + Idx.getExternalDocid(result.getDocid(i))
                        + "\t" + (i + 1) + "\t" + result.getDocidScore(i) + "\trun-1");
            }
        }
    }

    /**
     * Output the query results to file specified in the parameter file.
     * Output format:
     *      QueryID Q0 DocID Rank Score RunID
     * @param qid    Original query id.
     * @param result A list of document ids and scores.
     * @param outPath Specified output path.
     * @param limit Number of top results to consider.
     * @throws IOException Error accessing the Lucene index.
     */
    static void genOutputFile(String qid, ScoreList result, String outPath, int limit) {

        // Notes on `BufferedWriter`:
        // The point of `BufferedWriter` is basically to consolidate lots of little writes
        // into far fewer big writes. Make sure you **`flush`** it when you're finished with it -
        // and calling `close()` will do this and flush/close the underlying writer anyway.
        // In other words, just write, write, write and close :) The only time you normally
        // need to call flush manually is if you really, really need the data to be on disk NOW.

        try (
                BufferedWriter bw = new BufferedWriter( new FileWriter(outPath, true) )
                ) {
            DecimalFormat df = new DecimalFormat("#.000000000000000000");

            // result.sort();
            if (result.size() > limit) {
                result.truncate(limit);
            }

            // handle empty result
            if ( result.size() < 1 ) {

                String line = qid + " Q0 dummy 1 0 run-1\n";
                bw.write(line);

            } else {

                for (int i = 0; i < result.size(); i++) {
                    String line = qid + " Q0 " + Idx.getExternalDocid(result.getDocid(i)) + " "
                            + (i + 1) + " " + df.format( result.getDocidScore(i) )+ " run-1\n";
                    // System.out.print(line);
                    bw.write(line);
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // System.out.println("Done writing output..."); // or maybe error occurred
        }

    }

    /**
     * Output the expanded query to file specified in the parameter file.
     *
     * @param qid Original query id.
     * @param query The expanded query for qid.
     * @param outPath Specified output path.
     */
    static void genExpandedQueryFile(String qid, String query, String outPath) {
        try (
                BufferedWriter bw = new BufferedWriter( new FileWriter(outPath, true) )
        ) {

            if (query != null && !query.isEmpty()) {
                bw.write(qid + ": " + query + "\n");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Read the specified parameter file, and confirm that the required
     * parameters are present.  The parameters are returned in a
     * HashMap.  The caller (or its minions) are responsible for processing
     * them.
     *
     * @return The parameters, in <key, value> format.
     */
    private static Map<String, String> readParameterFile(String parameterFileName)
            throws IOException {

        Map<String, String> parameters = new HashMap<>();

        File parameterFile = new File(parameterFileName);

        if (!parameterFile.canRead()) {
            throw new IllegalArgumentException
                    ("Can't read " + parameterFileName);
        }

        Scanner scan = new Scanner(parameterFile);
        String line = null;
        do {
            line = scan.nextLine();
            String[] pair = line.split("=");
            parameters.put(pair[0].trim(), pair[1].trim());
        } while (scan.hasNext());

        scan.close();

        if (    !(parameters.containsKey("indexPath")
                && parameters.containsKey("queryFilePath")
                && parameters.containsKey("trecEvalOutputPath")
                // && parameters.containsKey("retrievalAlgorithm")
        )) {
            throw new IllegalArgumentException
                    ("Required parameters were missing from the parameter file.");
        }

        if ( !parameters.containsKey("trecEvalOutputLength") &&
              parameters.containsKey("diversity:maxResultRankingLength") ) {
            parameters.put("trecEvalOutputLength", parameters.get("diversity:maxResultRankingLength"));
        }

        // If the "fb" parameter is missing or empty set it to false
        if (!parameters.containsKey("fb") || parameters.get("fb").isEmpty()) {
            parameters.put("fb", "false");

            // Other param validations
            if (!parameters.containsKey("fbDocs") || parameters.get("fbDocs").isEmpty()) {
                parameters.put("fbDocs", "100");
            }

            if (!parameters.containsKey("fbTerms") || parameters.get("fbTerms").isEmpty()) {
                parameters.put("fbTerms", "10");
            }

            if (!parameters.containsKey("fbMu") || parameters.get("fbMu").isEmpty()) {
                parameters.put("fbMu", "0");
            }

            if (!parameters.containsKey("fbOrigWeight") || parameters.get("fbOrigWeight").isEmpty()) {
                parameters.put("fbOrigWeight", "0.5");
            }

            if (!parameters.containsKey("fbExpansionQueryFile") || parameters.get("fbExpansionQueryFile").isEmpty()) {
                parameters.put("fbExpansionQueryFile", "output/fb-expansion-query-" + Math.floor(Math.random() * 100000) + ".teIn");
            }

        }

        return parameters;
    }

}
