import com.melissadata.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Profiler Object is a data quality tool that provides statistical analysis
 * and assessment of your data quality needs for consistency, uniqueness and correctness.
 *
 * <p>High-level flow of this sample:
 * <ol>
 *   <li>SETUP     - create an mdProfiler instance, license it, configure the analyses and
 *                   output file, point it at the data files, then InitializeDataFiles().</li>
 *   <li>INPUT     - define the columns (AddColumn), then read a CSV file and feed each
 *                   row in with SetColumn + AddRecord.</li>
 *   <li>PROCESS   - ProfileData() analyzes the whole table.</li>
 *   <li>READ      - pull table-level statistics with the GetTable* getters, and per-column
 *                   value/pattern distributions with the frequency iterators.</li>
 *   <li>CLEANUP   - delete() releases the object when finished.</li>
 * </ol>
 *
 * <p>The pieces in this file map onto that flow:
 * <ul>
 *   <li>main / RunAsConsole / ParseArguments : console harness (argument parsing + the interactive loop).</li>
 *   <li>ProfilerObject                 : thin wrapper around mdProfiler (setup + the profiling sequence).</li>
 *   <li>DataContainer                  : holds the input file path and a small path-wrapping helper.</li>
 * </ul>
 *
 * <p>Where mdProfiler comes from:
 * The mdProfiler and mdProfilerJNI classes in com/melissadata come from mdProfiler_JavaCode.zip,
 * which the accompanying MelissaProfilerObjectLinuxJava.sh script downloads and
 * expands into com/melissadata on every run. mdProfilerJNI declares the native methods
 * and loads libmdProfilerJavaWrapper.so, the JNI shim that calls into libmdProfiler.so.
 *
 * <p>Reference:
 * <ul>
 *   <li>Quickstart    : https://docs.melissa.com/on-premise-api/profiler-object/profiler-object-quickstart.html</li>
 *   <li>Release notes : https://releasenotes.melissa.com/on-premise-api/profiler-object/</li>
 *   <li>Result codes  : https://docs.melissa.com/on-premise-api/profiler-object/result-codes.html</li>
 * </ul>
 */
public class MelissaProfilerObjectLinuxJava {

  /**
   * Entry point. Reads the optional command-line arguments, then hands control to
   * RunAsConsole, which performs the actual Profiler Object setup and processing.
   *
   * @param args The raw command-line arguments
   * @throws IOException if reading from standard input fails
   */
  public static void main(String args[]) throws IOException {
    // Populated by ParseArguments below.
    String[] arguments = ParseArguments(args);
    String license = arguments[0];
    String testFile = arguments[1];
    String dataPath = arguments[2];

    RunAsConsole(license, testFile, dataPath);
  }

  /**
   * Reads the supported command-line options and returns them.
   *
   * <p>Recognized flags (each followed by its value):
   * <ul>
   *   <li>--license / -l   : the Melissa license string</li>
   *   <li>--file / -f      : path to the CSV file to profile in one-shot mode</li>
   *   <li>--dataPath / -d  : path to the Profiler Object data files</li>
   * </ul>
   *
   * <p>A flag is only consumed when the token after it is not itself a recognized flag,
   * and a flag in the final position is ignored, so a value-less flag cannot swallow the
   * next option.
   *
   * @param args The raw command-line arguments to parse.
   * @return A String array of { license, testFile, dataPath }.
   */
  public static String[] ParseArguments(String[] args) {
    String license = "", testFile = "", dataPath = "";
    List<String> argumentStrings = Arrays.asList("--license", "-l", "--file", "-f", "--dataPath", "-d");
    for (int i = 0; i < args.length - 1; i++) {
      if ((args[i].equals("--license") || args[i].equals("-l")) && (!argumentStrings.contains(args[i + 1]))) {
        if (args[i + 1] != null) {
          license = args[i + 1];
        }
      }
      if ((args[i].equals("--file") || args[i].equals("-f")) && (!argumentStrings.contains(args[i + 1]))) {
        if (args[i + 1] != null) {
          testFile = args[i + 1];
        }
      }

      if ((args[i].equals("--dataPath") || args[i].equals("-d")) && (!argumentStrings.contains(args[i + 1]))) {
        if (args[i + 1] != null) {
          dataPath = args[i + 1];
        }
      }
    }

    return new String[] { license, testFile, dataPath };

  }

  /**
   * Sets up the Profiler Object once, then drives the input -> process -> output cycle.
   *
   * <p>In interactive mode (no --file) it loops, asking for a file path each pass until the
   * user answers "N". In one-shot mode (--file supplied) it runs a single pass and exits.
   *
   * @param license  The Melissa license string used to initialize the object.
   * @param testFile A CSV file to profile in one-shot mode; if empty, the program prompts interactively.
   * @param dataPath Path to the Profiler Object data files.
   * @throws IOException if reading from standard input fails
   */
  public static void RunAsConsole(String license, String testFile, String dataPath) throws IOException {
    System.out
        .println("\n\n===================== WELCOME TO MELISSA PROFILER OBJECT LINUX JAVA =====================\n");

    // Construct the wrapper. This is where the object is licensed, configured, pointed
    // at the data files, and initialized (see the ProfilerObject constructor below).
    ProfilerObject profilerObject = new ProfilerObject(license, dataPath);

    boolean shouldContinueRunning = true;

    // Gate the program on a successful initialization. If the data files could not
    // be loaded (bad/expired license, missing or wrong-path data files, ...),
    // GetInitializeErrorString() returns the reason instead of "No error." and we
    // skip the processing loop entirely.
    if (!profilerObject.mdProfilerObj.GetInitializeErrorString().equals("No error.")) {
      shouldContinueRunning = false;
    }

    while (shouldContinueRunning) {
      // Holder for this pass's input file path.
      DataContainer dataContainer = new DataContainer();
      BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

      if (testFile.isEmpty()) {
        // Interactive mode: prompt the user for a file path.
        System.out.println("\nFill in each value to see the Profiler Object results");

        System.out.print("File Path: ");
        dataContainer.InputFilePath = stdin.readLine();

      } else {
        // One-shot mode: use the file path passed on the command line.
        dataContainer.InputFilePath = testFile;
      }

      // Print user input
      System.out
          .println("\n========================================= INPUTS ========================================\n");

      List<String> sections = dataContainer.GetWrapped(dataContainer.InputFilePath, 50);

      System.out.println("\t                Input File: " + sections.get(0));

      for (int i = 1; i < sections.size(); i++) {
        if ((i == sections.size() - 1) && sections.get(i).endsWith("/")) {
          sections.set(i, sections.get(i).substring(0, sections.get(i).length() - 1));
        }
        System.out.println("\t                            " + sections.get(i));
      }

      // Execute Profiler Object
      // Profiles the input file; the statistics are then read from the object below.
      profilerObject.ExecuteObjectAndResultCodes(dataContainer);

      // Print output
      // The output has two parts: table-level statistics (record/column counts and
      // duplicate analysis) read via GetTable* getters, then per-column frequency
      // distributions read with the iterator pattern further below.
      System.out
          .println("\n========================================= OUTPUT ========================================\n");

      System.out.println("\n                 Profiler Object Information:");
      System.out.println("\n                       TABLE STATISTICS\n\n");
      System.out
          .println("                        TableRecordCount           :  "
              + profilerObject.mdProfilerObj.GetTableRecordCount());
      System.out.println(
          "                        ColumnCount                :  " + profilerObject.mdProfilerObj.GetColumnCount());
      System.out.println("");
      System.out.println(
          "                        ExactMatchDistinctCount    :  "
              + profilerObject.mdProfilerObj.GetTableExactMatchDistinctCount());
      System.out.println(
          "                        ExactMatchDupesCount       :  "
              + profilerObject.mdProfilerObj.GetTableExactMatchDupesCount());
      System.out.println(
          "                        ExactMatchLargestGroup     :  "
              + profilerObject.mdProfilerObj.GetTableExactMatchLargestGroup());
      System.out.println("");
      System.out.println(
          "                        ContactMatchDistinctCount  :  "
              + profilerObject.mdProfilerObj.GetTableContactMatchDistinctCount());
      System.out.println(
          "                        ContactMatchDupesCount     :  "
              + profilerObject.mdProfilerObj.GetTableContactMatchDupesCount());
      System.out.println(
          "                        ContactMatchLargestGroup   :  "
              + profilerObject.mdProfilerObj.GetTableContactMatchLargestGroup());
      System.out.println("");
      System.out.println("                        HouseholdMatchDistinctCount:  "
          + profilerObject.mdProfilerObj.GetTableHouseholdMatchDistinctCount());
      System.out.println(
          "                        HouseholdMatchDupesCount   :  "
              + profilerObject.mdProfilerObj.GetTableHouseholdMatchDupesCount());
      System.out.println("                        HouseholdMatchLargestGroup :  "
          + profilerObject.mdProfilerObj.GetTableHouseholdMatchLargestGroup());
      System.out.println("");
      System.out.println(
          "                        AddressMatchDistinctCount  :  "
              + profilerObject.mdProfilerObj.GetTableAddressMatchDistinctCount());
      System.out.println(
          "                        AddressMatchDupesCount     :  "
              + profilerObject.mdProfilerObj.GetTableAddressMatchDupesCount());
      System.out.println(
          "                        AddressMatchLargestGroup   :  "
              + profilerObject.mdProfilerObj.GetTableAddressMatchLargestGroup());

      System.out.println("\n\n                       COLUMN STATISTICS\n\n");

      // STATE Iterator Example - walk the value-frequency list for the "state" column:
      // StartDataFrequency opens the iterator, GetDataFrequencyValue/Count read the current
      // entry, and GetNextDataFrequency advances until it returns something other than 1.
      System.out.println("                        STATE Value                 Count");
      profilerObject.mdProfilerObj.StartDataFrequency("state", mdProfiler.Order.OrderCountAscending);
      while (profilerObject.mdProfilerObj.GetNextDataFrequency("state") == 1) {
        System.out.printf("                             %-24s%-10s%n",
            profilerObject.mdProfilerObj.GetDataFrequencyValue("state"),
            profilerObject.mdProfilerObj.GetDataFrequencyCount("state"));
      }
      System.out.println("");

      // POSTAL Iterator Example - same pattern as above, but StartPatternFrequency
      // reports the frequency of value *patterns* (e.g. ZIP formats) for the "zip" column.
      System.out.println("                        POSTAL Pattern              Count");
      profilerObject.mdProfilerObj.StartPatternFrequency("zip", mdProfiler.Order.OrderCountAscending);
      do {
        System.out.printf("                             %-24s%-10s%n",
            profilerObject.mdProfilerObj.GetPatternFrequencyValue("zip"),
            profilerObject.mdProfilerObj.GetPatternFrequencyCount("zip"));
      } while (profilerObject.mdProfilerObj.GetNextPatternFrequency("zip") == 1);

      
      boolean isValid = false;

      // In one-shot mode there is nothing more to do after a single pass: mark the
      // input handled and stop the outer loop.
      if (!testFile.isEmpty()) {
        isValid = true;
        shouldContinueRunning = false;
      }

      // Interactive mode: ask whether to process another file. Keep prompting until
      // we get a valid Y/N. "N" ends the program; "Y" falls through to another pass.
      while (!isValid) {
        System.out.println("\nTest another file? (Y/N)");
        String testAnotherResponse = stdin.readLine();

        if (!testAnotherResponse.isEmpty()) {
          testAnotherResponse = testAnotherResponse.toLowerCase();
          if (testAnotherResponse.equals("y")) {
            isValid = true;
          } else if (testAnotherResponse.equals("n")) {
            isValid = true;
            shouldContinueRunning = false;
          } else {
            System.out.print("Invalid Response, please respond 'Y' or 'N'");
          }
        }
      }

    }

    // Release the Profiler Object's resources now that we are done with it.
    // Java programs should explicitly call this function to destroy the
    // Profiler object. Do not rely on Java's GC to do this.
    profilerObject.mdProfilerObj.delete();
    System.out
        .println("\n=========================== THANK YOU FOR USING MELISSA JAVA OBJECT =========================\n");
          
  }
}

/**
 * Wrapper that owns a single Melissa Profiler Object instance and encapsulates the two
 * things every Melissa object needs: one-time setup (license + config + data files) and
 * the per-file profiling sequence. Reuse one instance; do NOT re-initialize per file.
 */
class ProfilerObject {
  // Path to the Profiler Object data files.
  String dataFilePath;

  // The underlying Melissa Profiler Object instance.
  mdProfiler mdProfilerObj = new mdProfiler();

  /**
   * Performs the mandatory one-time setup, in this required order:
   * <ol>
   *   <li>SetLicenseString               - authorize the object.</li>
   *   <li>SetFileName / SetAppendMode    - choose the profiler output file and write mode.</li>
   *   <li>SetPathToProfilerDataFiles     - tell it where the data files live.</li>
   *   <li>Set*Analysis / SetDataAggregation - choose which analyses to run (1 = on).</li>
   *   <li>InitializeDataFiles            - load the data into memory.</li>
   * </ol>
   *
   * @param license  The Melissa license string used to authorize the object.
   * @param dataPath Path to the folder containing the Profiler Object data files.
   */
  public ProfilerObject(String license, String dataPath) {
    // Set license string and set path to data files
    mdProfilerObj.SetLicenseString(license);
    mdProfilerObj.SetFileName("testFile.prf");
    mdProfilerObj.SetAppendMode(mdProfiler.AppendMode.Overwrite);
    dataFilePath = dataPath;
    mdProfilerObj.SetPathToProfilerDataFiles(dataFilePath);

    // Choose which analyses the profiler runs. The default is 1 for all four; this
    // sample leaves sort analysis switched off.
    mdProfilerObj.SetSortAnalysis(0);
    mdProfilerObj.SetMatchUpAnalysis(1);
    mdProfilerObj.SetRightFielderAnalysis(1);
    mdProfilerObj.SetDataAggregation(1);

    // Load the data files. The returned ProgramStatus reports whether initialization succeeded.
    // If you see a different date than expected, check your license string and either download the new data files
    // or use the Melissa Updater program to update your data files.
    mdProfiler.ProgramStatus pStatus = mdProfilerObj.InitializeDataFiles();

    // If an issue occurred, please investigate the common causes.
    // Common causes: an invalid/expired license, or missing/wrong-path data files.
    if (pStatus != mdProfiler.ProgramStatus.ErrorNone) {
      System.out.println("Failed to Initialize Object.");
      System.out.println(pStatus);
      return;
    }

    // Diagnostic information, handy for confirming the object loaded the data you expect:

    // Build date of the data files
    System.out.println("                             DataBase Date: " + mdProfilerObj.GetDatabaseDate());

    // When the license stops working
    System.out.println("                           Expiration Date: " + mdProfilerObj.GetLicenseExpirationDate());

    // This number should match with the file properties of the Melissa Object binary file.
    // If TEST appears with the build number, there may be a license key issue.
    System.out.println("                            Object Version: " + mdProfilerObj.GetBuildNumber());
    System.out.println();

  }

  /**
   * Runs the full Profiler analysis for one input file. This is the canonical call
   * pattern to copy into your own application:
   * AddColumn (define the schema) -> StartProfiling -> per row SetColumn + AddRecord
   * -> ProfileData (analyze the table).
   *
   * @param data The record holder; its InputFilePath is the CSV file to profile.
   */
  public void ExecuteObjectAndResultCodes(DataContainer data) {

    // Define the table schema: one AddColumn per column, naming it and declaring the kind
    // of data it holds so the object knows how to profile each one.
    mdProfilerObj.AddColumn("first", mdProfiler.ProfilerColumnType.ColumnTypeVariableUnicodeString,
        mdProfiler.ProfilerDataType.DataTypeFirstName);
    mdProfilerObj.AddColumn("last", mdProfiler.ProfilerColumnType.ColumnTypeVariableUnicodeString,
        mdProfiler.ProfilerDataType.DataTypeLastName);
    mdProfilerObj.AddColumn("address", mdProfiler.ProfilerColumnType.ColumnTypeVariableUnicodeString,
        mdProfiler.ProfilerDataType.DataTypeAddress);
    mdProfilerObj.AddColumn("city", mdProfiler.ProfilerColumnType.ColumnTypeVariableUnicodeString,
        mdProfiler.ProfilerDataType.DataTypeCity);
    mdProfilerObj.AddColumn("state", mdProfiler.ProfilerColumnType.ColumnTypeVariableUnicodeString,
        mdProfiler.ProfilerDataType.DataTypeStateOrProvince);
    mdProfilerObj.AddColumn("zip", mdProfiler.ProfilerColumnType.ColumnTypeVariableUnicodeString,
        mdProfiler.ProfilerDataType.DataTypeZipOrPostalCode);

    // Read the whole CSV input file into memory (one string per line).
    List<String> records = new ArrayList<String>();
    try {
      records = Files.readAllLines(Paths.get(data.InputFilePath), StandardCharsets.UTF_8);
    } catch (IOException e) {
      System.out.println("Error: Unable to open the input file");
      System.exit(1);
    }

    // Begin a profiling session, then feed every row in below.
    mdProfilerObj.StartProfiling();

    // Inputting the records to the Profiler Object
    for (String record : records) {
      String[] fields = record.trim().split(",");
      mdProfilerObj.SetColumn("first", fields[0]);
      mdProfilerObj.SetColumn("last", fields[1]);
      mdProfilerObj.SetColumn("address", fields[2]);
      mdProfilerObj.SetColumn("city", fields[3]);
      mdProfilerObj.SetColumn("state", fields[4]);
      mdProfilerObj.SetColumn("zip", fields[5]);

      mdProfilerObj.AddRecord();
    }

    // All rows added; analyze the full table.
    mdProfilerObj.ProfileData();

    // ResultsCodes explain any issues Profiler Object has with the object.
    // List of result codes for Profiler Object
    // https://docs.melissa.com/on-premise-api/profiler-object/result-codes.html
  }
}

/**
 * Holds the input file path for one pass, plus a small display helper (GetWrapped wraps
 * a long path so it prints neatly across several lines).
 */
class DataContainer {
  // Input: path to the CSV file to profile.
  public String InputFilePath = "";

  // Output: comma-separated result codes; this sample writes its results to a file
  // rather than collecting them here.
  public String ResultCodes = "";

  /**
   * Splits a long file path into chunks no longer than maxLineLength so it prints
   * neatly across several lines in the console output. Display-only helper.
   */
  public List<String> GetWrapped(String path, int maxLineLength) {
    File file = new File(path);
    String filePath = file.getAbsolutePath();
    String[] lines = filePath.split("/");
    String currentLine = "";
    List<String> wrappedString = new ArrayList<>();
    for (String section : lines) {
      if ((currentLine + section).length() > maxLineLength) {
        wrappedString.add(currentLine.trim());
        currentLine = "";
      }
      if (section.contains(path)) {
        currentLine += section;
      } else {
        currentLine += section + "/";
      }
    }
    if (currentLine.length() > 0) {
      wrappedString.add(currentLine.trim());
    }
    return wrappedString;
  }

}
