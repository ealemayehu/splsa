import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

public class Main
{
  public static void main(String[] args) throws Exception
  {
    Options options = new Options();

    options.addOption("all",
        "Executes the entire pipeline after cleaning any persisted data.");
    options.addOption("clean", "Deletes persisted data.");
    options.addOption("importBillInfos", "Imports bill information.");
    options.addOption("importGpoUrls",
        "Imports the url of the plain text version of the bills.");
    options.addOption("importBills",
        "Imports a plain text version of the bills.");
    options.addOption("extractBillWords", "Extracts the words of the bills.");
    options.addOption("filterBillWords", "Filters the words of the bills.");
    options.addOption("partitionBills",
        "Partitions the bills for model and analysis.");
    options.addOption("createControversyScoreDatasets",
        "Creates the controversy score datasets.");
    options.addOption("countAnalysisForBills",
        "Displays count statistics for bills.");
    options.addOption("createModelDatasets",
        "Creates the dataset used by this model and SLDA");
    options.addOption("runModel", "Runs the model.");
    options.addOption("k", true, "Comma separated list of K values with no space in between them");
    options.addOption("lambda", true, "Comma separated list of lambda values with no space in between them");
    options.addOption("eta", true, "Comma separated list of eta values with no space in between them");

    CommandLineParser parser = new DefaultParser();
    CommandLine line = parser.parse(options, args);
    boolean all = line.hasOption("all");

    if(all || line.hasOption("clean"))
      BillPersister.clean();

    if(all || line.hasOption("importBillInfos"))
      Importer.downloadBillInfos();

    if(all || line.hasOption("importGpoUrls"))
      Importer.populateGpoUrls();

    if(all || line.hasOption("importBills"))
      Importer.downloadBills();

    if(all || line.hasOption("extractBillWords"))
      TextProcessor.extractBillWords();

    if(all || line.hasOption("filterBillWords"))
      TextProcessor.filterBillWords();

    if(all || line.hasOption("partitionBills"))
      DatasetPreparer.partitionBills();

    if(all || line.hasOption("createControversyScoreDatasets"))
      DatasetPreparer.createControversyScoreDatasets();

    if(all || line.hasOption("createModelDatasets"))
      DatasetPreparer.createModelDatasets();
    
    int[] kValues = null;
    
    if (line.hasOption("k"))
      kValues = stringToInts(line.getOptionValue("k"));
    
    double[] lambdaValues = null;
    
    if (line.hasOption("lambda"))
      lambdaValues = stringToDoubles(line.getOptionValue("lambda"));
    
    double[] etaValues = null;;
    
    if (line.hasOption("eta"))
      etaValues = stringToDoubles(line.getOptionValue("eta"));

    if(all || line.hasOption("runModel"))
      Model.run(kValues, lambdaValues, etaValues);

    if(line.hasOption("countAnalysisForBills"))
      Analysis.printAllCounts();
  }
  
  private static double[] stringToDoubles(String commaSeparatedValues) {
    String[] stringValues = commaSeparatedValues.split("\\,");
    double[] doubleValues = new double[stringValues.length];
    
    for (int i = 0; i < stringValues.length; i++) {
      doubleValues[i] = Double.parseDouble(stringValues[i]);
    }
    
    return doubleValues;
  }
  
  private static int[] stringToInts(String commaSeparatedValues) {
    String[] stringValues = commaSeparatedValues.split("\\,");
    int[] intValues = new int[stringValues.length];
    
    for (int i = 0; i < stringValues.length; i++) {
      intValues[i] = Integer.parseInt(stringValues[i]);
    }
    
    return intValues;
  }
}
