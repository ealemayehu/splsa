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

    if(all || line.hasOption("runModel"))
      Model.run();

    if(line.hasOption("countAnalysisForBills"))
      Analysis.printAllCounts();
  }
}
