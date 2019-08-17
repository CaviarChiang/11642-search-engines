import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

/**
 *  An object that determines how Learning to Rank algorithm
 *  retrieves and ranks documents on the queries.
 */
public class RankingAlgorithmLetor extends RankingAlgorithm {

    private static class DocumentVector implements Comparable<DocumentVector> {

        /**
         * Number of features stored in the document vector
         */
        static final int N_FEATURES = 18;
        /**
         * Degree of precision on double values when calculating numerical features
         */
        static final int N_DIGITS = 16;
        /**
         * Feature names
         */
        static final String FEATURE_NAMES[] = {
                "SpamScore",
                "UrlDepth",
                "FromWikipedia",
                "PageRank",
                "BM25ScoreBody",
                "IndriScoreBody",
                "OverlapScoreBody",
                "BM25ScoreTitle",
                "IndriScoreTitle",
                "OverlapScoreTitle",
                "BM25ScoreUrl",
                "IndriScoreUrl",
                "OverlapScoreUrl",
                "BM25ScoreInlink",
                "IndriScoreInlink",
                "OverlapInlink",
                "QueryTermDensity",
                "TF-IDF"
        };

        /**
         * External document ID
         */
        String externalID;
        /**
         * Document feature vector
         */
        double[] vector;
        /**
         * Relevance score
         */
        double target;

        DocumentVector(String _externalID) {
            externalID = _externalID;
            vector = new double[N_FEATURES];
            target = 0;
        }

        DocumentVector(String _externalID, double _target) {
            externalID = _externalID;
            vector = new double[N_FEATURES];
            target = _target;
        }

        public void setVector(double[] _vec) {
            System.arraycopy(_vec, 0, vector, 0, N_FEATURES);
        }

        public void setTarget(double target) {
            this.target = target;
        }

        @Override
        public int compareTo(DocumentVector o) {
            // sort by relevance score in descending order
            return Double.compare(o.target, target);
        }

        /**
         * Display document feature vectors.
         *
         * @return The document feature vector in string format
         */
        String dispVector() {
            DecimalFormat df = new DecimalFormat("#.0000000000000000");
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < N_FEATURES; i++) {
                builder.append(i + 1).append(":").append( df.format(vector[i]) ).append(" ");
            }
            builder.append(" # ").append(externalID);
            return builder.toString();
        }

        /**
         * Display document feature vectors, except for features that are disabled.
         *
         * @param exclude features to ignore; feature index starts at 1
         * @return The document feature vector in string format
         */
         String dispVector(Set<String> exclude) {
             DecimalFormat df = new DecimalFormat("#.0000000000000000");
             StringBuilder builder = new StringBuilder();
             for (int i = 0; i < N_FEATURES; i++) {
                 if ( !exclude.contains(String.valueOf(i + 1)) ) {
                     builder.append(i + 1).append(":").append( df.format(vector[i]) ).append(" ");
                 }
             }
             builder.append(" # ").append(externalID);
             return builder.toString();
         }

        @Override
        public String toString() {
            return String.valueOf(target) + " " + dispVector();
        }

    }

    /**
     * Number of top documents to consider from the initial ranking pool
     */
    private static final int N_TOP_DOC = 100;

    /**
     * Training relevance assessment file and training query file
     */
    private String trainingQueryFile;
    private String trainingQrelsFile;

    /**
     * Training feature vectors output path
     */
    private String trainingFeatureVectorsFile;
    /**
     * Features to ignore
     */
    private Set<String> featureDisable;

    /**
     * Testing feature vectors output path and testing document scores output path
     */
    private String testingFeatureVectorsFile;
    private String testingDocumentScores;

    /**
     * Testing query file and retrieved documents' output path
     */
    private String queryFilePath;
    private String trecEvalOutputPath;

    /**
     * Retrieval models for retrieval and scoring
     */
    private RetrievalModel initialRankingModel;
    private RetrievalModel bm25;
    private RetrievalModel indri;
    /**
     * SVM caller
     */
    private SVM svm;

    /**
     * Constructor for RankingAlgorithmLetor object.
     *
     * @param parameters User specified parameters for retrieval algorithm letor.
     */
    public RankingAlgorithmLetor(Map<String, String> parameters) {

        trainingQueryFile = parameters.get("letor:trainingQueryFile");
        trainingQrelsFile = parameters.get("letor:trainingQrelsFile");
        trainingFeatureVectorsFile = parameters.get("letor:trainingFeatureVectorsFile");

        featureDisable = new HashSet<>();
        if (parameters.containsKey("letor:featureDisable")) {
            String[] exclude = parameters.get("letor:featureDisable").split(",");
            featureDisable.addAll(Arrays.asList(exclude));
        }

        String svmRankLearnPath = parameters.get("letor:svmRankLearnPath");
        String svmRankClassifyPath = parameters.get("letor:svmRankClassifyPath");
        String svmRankModelFile = parameters.get("letor:svmRankModelFile");
        double svmRankParamC = 0.001;
        if (parameters.containsKey("letor:svmRankParamC")) {
            svmRankParamC = Double.parseDouble(parameters.get("letor:svmRankParamC").trim());
        }
        svm = new SVM(svmRankLearnPath, svmRankClassifyPath, svmRankModelFile, svmRankParamC);

        testingFeatureVectorsFile = parameters.get("letor:testingFeatureVectorsFile");
        testingDocumentScores = parameters.get("letor:testingDocumentScores");

        queryFilePath = parameters.get("queryFilePath");
        trecEvalOutputPath = parameters.get("trecEvalOutputPath");

        double k1 = Double.parseDouble(parameters.get("BM25:k_1"));
        double b = Double.parseDouble(parameters.get("BM25:b"));
        double k3 = Double.parseDouble(parameters.get("BM25:k_3"));
        initialRankingModel = new RetrievalModelBM25(k1, b, k3);
        bm25 = initialRankingModel;

        double indri_mu = Double.parseDouble(parameters.get("Indri:mu").trim());
        double indri_lambda = Double.parseDouble(parameters.get("Indri:lambda").trim());
        indri = new RetrievalModelIndri(indri_mu, indri_lambda);

    }

    /**
     * Run the learning to rank algorithm to retrieve & rank documents.
     * It first reads the training relevance assessment file and the
     * training query file. It then processes each training query and
     * computes the documents' feature vectors for each <query, document>
     * pair, and writes the training feature vectors to local disk.
     * It then calls the SVM executable to train the ranking model.
     *
     * For testing queries, it retrieves and ranks documents using BM25
     * retrieval model, and constructs the documents' feature vectors
     * for each <query, document> pair, and writes the testing feature
     * vectors to local disk. It then calls the SVM executable to predict
     * the relevance score for each top document using the trained model.
     * Finally, it re-ranks the top documents and generates the final output.
     *
     * @throws Exception
     */
    @Override
    public void run() throws Exception {

        // Read trainingQrelsFile;
        Map<String, List<DocumentVector>> qrels = readTrainingQrelsFile();
        /*
         * Read and process training query file.
         * For each training query, compute its documents' feature vectors,
         * and generate training feature vectors file
         */
        processQueryFile(trainingQueryFile, qrels);

        // Fit SVM using training feature vectors file
        svm.fit(trainingFeatureVectorsFile);
        /*
         * Get initial ranking for top documents
         * For each testing query, get its initial score list from retrieval initialRankingModel,
         * construct and calculate its documents' feature vectors,
         * and write testing feature vectors to disk file.
         */
        Map<String, List<DocumentVector>> initialTestRanking = getInitialRanking(queryFilePath, initialRankingModel);
        // Predict document's scores using testing feature vectors file
        svm.predict(testingFeatureVectorsFile, testingDocumentScores);
        // Re-rank top documents and generate final trecEvalOutput file
        getFinalRanking(initialTestRanking, testingDocumentScores, trecEvalOutputPath);

    }

    /**
     * Read trainingQrelsFile.
     *
     * @return A map with key being qid, value being a sorted list of document vectors
     *         For each qid, its list of document vectors is sorted by descending relevance
     * @throws Exception
     */
    public Map<String, List<DocumentVector>> readTrainingQrelsFile()
            throws Exception {

        // Use LinkedHashMap to keep insertion order on the keys (qid)
        Map<String, List<DocumentVector>> qrels = new LinkedHashMap<>();

        BufferedReader input = null;
        String line = null;
        input = new BufferedReader( new FileReader(trainingQrelsFile) );

        while ((line = input.readLine()) != null) {

            String[] data = line.split("\\s+");
            String qid = data[0];
            String externalID = data[2];
            double rel = Double.parseDouble(data[3]);

            if (!qrels.containsKey(qid)) {
                List<DocumentVector> docs = new ArrayList<>();
                docs.add(new DocumentVector(externalID, rel));
                qrels.put(qid, docs);
            } else {
                List<DocumentVector> docs = qrels.get(qid);
                docs.add(new DocumentVector(externalID, rel));
            }

        }

        input.close();

        for ( List<DocumentVector> docs : qrels.values() ) {
            Collections.sort(docs);
        }

        return qrels;

    }

    /**
     * Read and process (training) query file.
     * For each query, compute its documents' feature vectors,
     * and generate feature vectors file
     *
     * @param queryFilePath Path to training query file
     * @param qrels A map with key being qid, value being a sorted list of document vectors
     * @throws Exception
     */
    public void processQueryFile(String queryFilePath, Map<String, List<DocumentVector>> qrels)
        throws Exception {

        BufferedReader input = null;

        String qLine = null;
        input = new BufferedReader( new FileReader(queryFilePath) );

        while ((qLine = input.readLine()) != null) {

            int d = qLine.indexOf(':');

            if (d < 0) {
                throw new IllegalArgumentException
                        ("Syntax error:  Missing ':' in query line.");
            }

            QryEval.printMemoryUsage(false);

            String qid = qLine.substring(0, d);
            String query = qLine.substring(d + 1);

            System.out.println("Query " + qLine);

            if (!qrels.containsKey(qid)) {
                continue;
            }

            List<DocumentVector> vecs = calcFeatureVecs(query, qrels.get(qid));
            qrels.put(qid, vecs);
            // Write training feature vectors to disk file
            genFeatureVecsFile(trainingFeatureVectorsFile, qid, vecs);

        }

        input.close();

    }

    /**
     * Write out a list of documents' features vectors for a specific query -> .LeToRTrain.txt
     * Format: relevance qid:qid 1:featVal ...  # externalID
     *
     * @param outPath Output path to the training/testing feature vectors
     * @param qid Query id
     * @param vecs A list of documents' feature vectors to write
     */
    private void genFeatureVecsFile(String outPath, String qid, List<DocumentVector> vecs) {

        try (
                BufferedWriter bw = new BufferedWriter( new FileWriter(outPath, true) )
                ) {

            for (DocumentVector doc : vecs) {
                String line = String.format("%f qid:%s %s%n", doc.target, qid, doc.dispVector(featureDisable));
                bw.write(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Calculate documents' feature vectors for a specific query.
     *
     * @param query Original query (assume BOW query)
     * @param docs A sorted list of document vectors (features not calculated)
     * @return A sorted list of documents' feature vectors
     * @throws Exception
     */
    private List<DocumentVector> calcFeatureVecs(String query, List<DocumentVector> docs)
            throws Exception {

        for (DocumentVector doc : docs) {

            double[] featVec = new double[DocumentVector.N_FEATURES];

            for (int i = 0; i < DocumentVector.N_FEATURES; i++) {

                // Double.MAX_VALUE as init value
                featVec[i] = Double.MAX_VALUE;

            }

            // Convert to internal docid
            int docid = Idx.getInternalDocid(doc.externalID);
            if (docid == -1) continue;

            // f1 spam score
            if (!featureDisable.contains("1")) {
                featVec[0] = Integer.parseInt (Idx.getAttribute ("spamScore", docid));
            }

            // f2 url depth & f3 fromWikipedia
            if (!featureDisable.contains("2") || !featureDisable.contains("3")) {
                String rawUrl = Idx.getAttribute ("rawUrl", docid);
                rawUrl = rawUrl.replaceAll("http://", "").replace("https://", "");
                if (!featureDisable.contains("2")) {
                    featVec[1] = rawUrl.length() - rawUrl.replace("/","").length();
                }
                if (!featureDisable.contains("3")) {
                    featVec[2] = rawUrl.contains("wikipedia.org") ? 1d : 0d;
                }
            }

            // f4 PageRank score
            if (!featureDisable.contains("4")) {
                featVec[3] = Double.parseDouble(Idx.getAttribute ("PageRank", docid));
            }

            // Tokenize query
            List<String> terms = Arrays.asList(QryParser.tokenizeString(query));

            // f5: BM25 score for <q, dbody>
            // f6: Indri score for <q, dbody>
            // f7: Term overlap score for <q, dbody>
            // f17: query term density
            // f18: tf-idf
            if (        !featureDisable.contains("5") || !featureDisable.contains("6") || !featureDisable.contains("7")
                    || !featureDisable.contains("17") || !featureDisable.contains("18")     ) {
                TermVector tvBody = new TermVector(docid, "body");
                if (!featureDisable.contains("5"))
                    featVec[4] = RetrievalModelBM25.getScoreBM25(bm25, docid, terms, tvBody, "body");
                if (!featureDisable.contains("6"))
                    featVec[5] = RetrievalModelIndri.getScoreIndri(indri, docid, terms, tvBody, "body");
                if (!featureDisable.contains("7"))
                    featVec[6] = getOverlapScore(terms, tvBody);
                if (!featureDisable.contains("17"))
                    featVec[16] = getQueryTermDensity(terms, docid, tvBody);
                if (!featureDisable.contains("18"))
                    featVec[17] = getTfIdfScore(terms, tvBody);
            }

            // f8: BM25 score for <q, dtitle>
            // f9: Indri score for <q, dtitle>
            // f10: Term overlap score for <q, dtitle>
            if (!featureDisable.contains("8") || !featureDisable.contains("9") || !featureDisable.contains("10")) {
                TermVector tvTitle = new TermVector(docid, "title");
                if (!featureDisable.contains("8"))
                    featVec[7] = RetrievalModelBM25.getScoreBM25(bm25, docid, terms, tvTitle, "title");
                if (!featureDisable.contains("9"))
                    featVec[8] = RetrievalModelIndri.getScoreIndri(indri, docid, terms, tvTitle, "title");
                if (!featureDisable.contains("10"))
                    featVec[9] = getOverlapScore(terms, tvTitle);
            }

            // f11: BM25 score for <q, durl>
            // f12: Indri score for <q, durl>
            // f13: Term overlap score for <q, durl>
            if (!featureDisable.contains("11") || !featureDisable.contains("12") || !featureDisable.contains("13")) {
                TermVector tvUrl = new TermVector(docid, "url");
                if (!featureDisable.contains("11"))
                    featVec[10] = RetrievalModelBM25.getScoreBM25(bm25, docid, terms, tvUrl, "url");
                if (!featureDisable.contains("12"))
                    featVec[11] = RetrievalModelIndri.getScoreIndri(indri, docid, terms, tvUrl, "url");
                if (!featureDisable.contains("13"))
                    featVec[12] = getOverlapScore(terms, tvUrl);
            }

            // f14: BM25 score for <q, dlink>
            // f15: Indri score for <q, dlink>
            // f16: Term overlap score for <q, dlink>
            if (!featureDisable.contains("14") || !featureDisable.contains("15") || !featureDisable.contains("16")) {
                TermVector tvInlink = new TermVector(docid, "inlink");
                if (!featureDisable.contains("14"))
                    featVec[13] = RetrievalModelBM25.getScoreBM25(bm25, docid, terms, tvInlink, "inlink");
                if (!featureDisable.contains("15"))
                    featVec[14] = RetrievalModelIndri.getScoreIndri(indri, docid, terms, tvInlink, "inlink");
                if (!featureDisable.contains("16"))
                    featVec[15] = getOverlapScore(terms, tvInlink);
            }

            // Set document vector
            doc.setVector(featVec);

        }

        // Normalize document vectors
        normalize(docs);

        return docs;

    }

    /**
     * Normalize document feature vectors.
     *
     * @param docs A list of document vectors to normalize (for a given query).
     */
    private void normalize(List<DocumentVector> docs) {

        double[] min = new double[DocumentVector.N_FEATURES];
        double[] max = new double[DocumentVector.N_FEATURES];

        // First pass finds the min & max array over all documents
        for (int i = 0; i < DocumentVector.N_FEATURES; i++) {
            double min_i = Double.MAX_VALUE;
            double max_i = Double.MIN_VALUE;
            for (DocumentVector doc : docs) {
                double val = doc.vector[i];
                if (val != Double.MAX_VALUE) {
                    min_i = val < min_i ? val : min_i;
                    max_i = val > max_i ? val : max_i;
                }
            }
            min[i] = min_i;
            max[i] = max_i;
        }

        // Second pass normalizes the doc vector
        for (DocumentVector doc : docs) {
            double[] normalized = new double[DocumentVector.N_FEATURES];
            for (int i = 0; i < DocumentVector.N_FEATURES; i++) {
                double val = doc.vector[i];
                // Special treatment
                if (val == Double.MAX_VALUE || max[i] == min[i]) {
                    normalized[i] = 0;
                }
                // Regular treatment
                else {
                    normalized[i] = (val - min[i]) / (max[i] - min[i]);
                }
            }
            // Reset document vector
            doc.setVector(normalized);
        }

    }

    /**
     * Returns term overlap score.
     * Term overlap is defined as the percentage of query terms that match the document field
     *
     * @param terms A list of tokenized query terms
     * @param termVec Term vector for a specific document and field
     * @return Term overlap score
     */
    private double getOverlapScore(List<String> terms, TermVector termVec) {

        // Double.MAX_VALUE indicates no value or invalid value for a feature
        if (termVec.stemsLength() == 0)
            return Double.MAX_VALUE;

        // Assuming qtf = 1
        Set<String> termSet = new HashSet<>(terms);

        double score = 0;
        for (int i = 1; i < termVec.stemsLength(); i++) {
            if (termSet.contains(termVec.stemString(i)))
                score++;
        }

        return score / terms.size();

    }

    /**
     * Returns query term density.
     * Divides the number of times the query terms are mentioned by the total number of words on the page
     *
     * @param terms A list of tokenized query terms
     * @param docid Internal docid
     * @param termVec Term vector for a specific document and field
     * @return Query term density
     * @throws IOException
     */
    private double getQueryTermDensity(List<String> terms, int docid, TermVector termVec)
        throws IOException {

        if (termVec.stemsLength() == 0)
            return Double.MAX_VALUE;

        double queryTermCount = 0;

        // Assuming qtf = 1
        Set<String> termSet = new HashSet<>(terms);

        for (int i = 1; i < termVec.stemsLength(); i++) {
            if (termSet.contains(termVec.stemString(i))) {
                queryTermCount += termVec.stemFreq(i);
            }
        }

        return queryTermCount / Idx.getFieldLength("body", docid);
    }

    /**
     * Returns document tf-idf score.
     *
     * @param terms A list of tokenized query terms
     * @param termVec Term vector for a specific document and field
     * @return Document tf-idf score
     * @throws IOException
     */
    private double getTfIdfScore(List<String> terms, TermVector termVec)
        throws IOException {

        double score = 0;
        long numDocs = Idx.getNumDocs();

        // Assuming qtf = 1
        Set<String> termSet = new HashSet<>(terms);

        for (int i = 1; i < termVec.stemsLength(); i++) {
            if (termSet.contains(termVec.stemString(i))) {
                int tf = termVec.stemFreq(i);
                int df = termVec.stemDf(i);
                double idf = Math.max(0, Math.log( (numDocs - df + 0.5) / (df + 0.5)) );

                score += tf * idf;
            }
        }

        return score;
    }

    /**
     * Get the initial ranking (top N documents) for each testing query.
     * For each query, get its initial score list from retrieval initialRankingModel,
     * construct and calculate its documents' feature vectors,
     * and write feature vectors to disk file.
     *
     * @param queryFilePath Testing query file path
     * @param model Retrieval initialRankingModel for initial document ranking
     * @return A map with key being qid, and value being a list of document vectors
     * @throws Exception
     */
    public Map<String, List<DocumentVector>> getInitialRanking(String queryFilePath, RetrievalModel model)
        throws Exception {

        Map<String, List<DocumentVector>> qrels = new LinkedHashMap<>();

        try (
                BufferedReader input = new BufferedReader( new FileReader(queryFilePath) )
            ) {

            String qLine = null;

            while ((qLine = input.readLine()) != null) {

                int d = qLine.indexOf(':');
                if (d < 0) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Missing ':' in query line.");
                }

                String qid = qLine.substring(0, d);
                String query = qLine.substring(d + 1);

                // Get initial sorted score list, truncated by the number of pre-specified top documents
                ScoreList r = QryEval.processQuery(qid, query, model); // might be null
                r.truncate(N_TOP_DOC);

                // For each query, construct its list of document vectors
                List<DocumentVector> docs = new ArrayList<>();
                for (int i = 0; i < r.size(); i++) {
                    String externalDocid = Idx.getExternalDocid(r.getDocid(i));
                    docs.add(new DocumentVector(externalDocid, 0));
                }

                // For each query, calculate its documents' feature vectors and add to map
                List<DocumentVector> vecs = calcFeatureVecs(query, docs);
                qrels.put(qid, vecs);

                // For each query, write testing feature vectors to disk file
                genFeatureVecsFile(testingFeatureVectorsFile, qid, vecs);

            }

        }

        return qrels;
    }

    /**
     * Get the final ranking of documents for each query.
     * Read the testing document scores file after SVM is called,
     * and generate final trecEvalOutput file.
     *
     * @param initialRanking A map with key being qid, and value being a list of document vectors
     * @param outPath trecEvalOutputPath
     */
    public void getFinalRanking(Map<String, List<DocumentVector>> initialRanking, String newDocumentScoresFile, String outPath) {

        try (
                BufferedReader br = new BufferedReader(new FileReader((newDocumentScoresFile)));
                BufferedWriter bw = new BufferedWriter(new FileWriter(outPath, true))   ) {

            DecimalFormat df = new DecimalFormat("#.000000000000");

            for (Map.Entry<String, List<DocumentVector>> entry : initialRanking.entrySet()) {

                String qid = entry.getKey();
                List<DocumentVector> docs = entry.getValue();

                if (docs.size() < 1) {

                    String line = qid + " Q0 dummy 1 0 run-1\n";
                    bw.write(line);

                } else {

                    for (DocumentVector doc : docs) {
                        doc.setTarget(Double.parseDouble(br.readLine()));
                        // need to make sure qid and score matches (map shuffles the qid insertion order)
                        // that's why we need a LinkedHashMap to also keep qid in order of insertion
                    }
                    // Re-rank documents after updating new scores
                    Collections.sort(docs);

                    for (int i = 0; i < docs.size(); i++) {
                        DocumentVector doc = docs.get(i);
                        String line = String.format(
                                "%s Q0 %s %d %s letor%n",
                                qid, doc.externalID, i + 1, df.format(doc.target));
                        bw.write(line);
                    }

                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Simple test program.
     *
     * @param args Arguments
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        Idx.open("index-cw09");
        System.out.println(Idx.getAttribute("PageRank", Idx.getInternalDocid("clueweb09-en0000-04-30449")));
    }

}
