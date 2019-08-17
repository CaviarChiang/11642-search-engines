import java.io.File;

public class TestMain {

    public static void main(String[] args) {
        File parameterFile = new File (args[0]);
        System.out.println(parameterFile.exists());
    }

}
