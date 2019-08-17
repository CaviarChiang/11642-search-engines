import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * An object that calls the SVM executable and trains
 * the SVM model based on input feature vectors.
 */
public class SVM {

    /**
     * Path to the `svm_rank_learn` executable
     */
    private String svmRankLearnPath;
    /**
     * Path to the `svm_rank_classify` executable
     */
    private String svmRankClassifyPath;
    /**
     * Model output path that specifies where to write the trained model
     */
    private String svmRankModelFile;
    /**
     * SVM regularization param
     */
    private double svmRankParamC;

    public SVM(String svmRankLearnPath, String svmRankClassifyPath, String svmRankModelFile) {
        this.svmRankLearnPath = svmRankLearnPath;
        this.svmRankClassifyPath = svmRankClassifyPath;
        this.svmRankModelFile = svmRankModelFile;
        this.svmRankParamC = 0.001;
    }

    public SVM(String svmRankLearnPath, String svmRankClassifyPath, String svmRankModelFile, double svmRankParamC) {
        this.svmRankLearnPath = svmRankLearnPath;
        this.svmRankClassifyPath = svmRankClassifyPath;
        this.svmRankModelFile = svmRankModelFile;
        this.svmRankParamC = svmRankParamC;
    }

    /**
     * Train the SVM model based on the input training feature vectors.
     *
     * @param trainingFeatureVectorsFile Training feature vectors input path
     * @throws Exception if SVM crashes
     */
    public void fit(String trainingFeatureVectorsFile)
            throws Exception {
        // Runs svm_rank_learn from within Java to train the model
        Process cmdProc = Runtime.getRuntime().exec(
                new String[] {
                        svmRankLearnPath,
                        "-c", String.valueOf(svmRankParamC),
                        trainingFeatureVectorsFile,
                        svmRankModelFile });

        consume(cmdProc);
    }

    /**
     * Predict the document relevance score based on the input testing feature vectors.
     *
     * @param testingFeatureVectorsFile Testing feature vectors input path
     * @param testingDocumentScores Testing document scores output path
     * @throws Exception if SVM crashes
     */
    public void predict(String testingFeatureVectorsFile, String testingDocumentScores)
            throws Exception {
        // Runs svm_rank_learn from within Java to train the model
        Process cmdProc = Runtime.getRuntime().exec(
                new String[] {
                        svmRankClassifyPath,
                        testingFeatureVectorsFile,
                        svmRankModelFile,
                        testingDocumentScores });

        consume(cmdProc);
    }

    /**
     * Run the SVM executable.
     *
     * @param cmdProc Process object that specifies any command line arguments
     * @throws Exception
     */
    private void consume(Process cmdProc)
            throws Exception {
        // The stdout/stderr consuming code MUST be included.
        // It prevents the OS from running out of output buffer space and stalling.

        // consume stdout and print it out for debugging purposes
        BufferedReader stdoutReader = new BufferedReader(
                new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            System.out.println(line);
        }
        // consume stderr and print it for debugging purposes
        BufferedReader stderrReader = new BufferedReader(
                new InputStreamReader(cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null) {
            System.out.println(line);
        }

        // get the return value from the executable. 0 means success, non-zero
        // indicates a problem
        int retValue = cmdProc.waitFor();
        if (retValue != 0) {
            throw new Exception("SVM Rank crashed.");
        }
    }

    public String getSvmRankLearnPath() {
        return svmRankLearnPath;
    }

    public String getSvmRankClassifyPath() {
        return svmRankClassifyPath;
    }

    public double getSvmRankParamC() {
        return svmRankParamC;
    }

    public String getSvmRankModelFile() {
        return svmRankModelFile;
    }

    public void setSvmRankParamC(double svmRankParamC) {
        this.svmRankParamC = svmRankParamC;
    }
}
