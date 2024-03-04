import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import org.json.JSONException;

public class DatasetPreparer
{
	public static final String DATASET_PERSISTENCE_DIRECTORY = getDatasetPersistenceDirectory();
	private static final double TRAINING_DATASET_SHARE = 0.8;
	private static final double CROSS_VALIDATION_DATASET_SHARE = 0.5;
	private static final int MAX_VOCABULARY_SIZE = 10000;

	private static String getDatasetPersistenceDirectory()
	{
		String directory = System.getProperty("dataset.persistance.directory");

		if(directory == null)
		{
			directory = "../data/dataset";
		}

		return directory;
	}

	public static void partitionBills() throws IOException
	{
		Collection<Integer> billIds = BillPersister.getBillIdsWithFilteredWords();
		Collection<Integer> trainingPartition = new TreeSet<Integer>();
		Collection<Integer> analysisPartition = new TreeSet<Integer>();
		Collection<Integer> crossValidationPartition = new TreeSet<Integer>();
		Collection<Integer> testingPartition = new TreeSet<Integer>();

		saveData(billIds, "fullDataset.txt");
		partition(billIds, trainingPartition, analysisPartition,
		      TRAINING_DATASET_SHARE);
		saveData(trainingPartition, "trainingDataset.txt");
		partition(analysisPartition, crossValidationPartition, testingPartition,
		      CROSS_VALIDATION_DATASET_SHARE);
		saveData(crossValidationPartition, "crossValidationDataset.txt");
		saveData(testingPartition, "testingDataset.txt");
		System.out.println("Partitioned " + billIds.size() + " bills into "
		      + trainingPartition.size() + " for training, "
		      + crossValidationPartition.size() + " for cross validation and "
		      + testingPartition.size() + " for testing.");
	}

	public static void createControversyScoreDatasets()
	      throws IOException, JSONException
	{
		createControversyScoresDataset("trainingDataset.txt",
		      "trainingControversyScores.txt");
		createControversyScoresDataset("crossValidationDataset.txt",
		      "crossValidationControversyScores.txt");
		createControversyScoresDataset("testingDataset.txt",
		      "testingControversyScores.txt");

		createRoundedControversyScoresDataset("trainingDataset.txt",
		      "trainingRoundedControversyScores.txt");
		createRoundedControversyScoresDataset("crossValidationDataset.txt",
		      "crossValidationRoundedControversyScores.txt");
		createRoundedControversyScoresDataset("testingDataset.txt",
		      "testingRoundedControversyScores.txt");
	}

	public static void createControversyScoresDataset(String billIdFilename,
	      String outputFilename) throws IOException, JSONException
	{
		Collection<Double> controversyScores = getControversyScores(
		      billIdFilename);

		saveData(controversyScores, outputFilename);
		System.out.println(
		      "Created the controversy score dataset: " + outputFilename);
	}

	public static void createRoundedControversyScoresDataset(
	      String billIdFilename, String outputFilename)
	      throws IOException, JSONException
	{
		Collection<Double> controversyScores = getControversyScores(
		      billIdFilename);
		List<Integer> roundedControversyScores = new ArrayList<Integer>();

		for (double controversyScore : controversyScores)
			roundedControversyScores.add((int) Math.round(controversyScore));

		saveData(roundedControversyScores, outputFilename);
		System.out.println(
		      "Created the rounded controversy score dataset: " + outputFilename);
	}

	public static String[] getWordsAsArray(Map<String, Integer> vocabulary)
	{
		String[] words = new String[vocabulary.size()];

		for (String word : vocabulary.keySet())
			words[vocabulary.get(word)] = word;

		return words;
	}

	public static List<Double> getControversyScores(String billIdFilename)
	      throws IOException, JSONException
	{
		Collection<String> billIds = getSingleColumnData(billIdFilename);
		List<Double> controversyScores = new ArrayList<Double>();

		for (String billId : billIds)
		{
			int id = Integer.parseInt(billId);

			if(BillPersister.hasFilteredBillWords(id))
			{
				BillInfo billInfo = BillPersister.getBillInfo(id);
				double y = billInfo.getYesVoteCount();
				double n = billInfo.getNoVoteCount();
				double controversyScore = 1.0 - Math.abs(y - n) / (y + n);

				controversyScores.add(controversyScore);
			}
		}

		return controversyScores;
	}

	public static Map<String, Integer> getVocabularyDataset() throws IOException
	{
		Map<String, Integer> vocabulary = new HashMap<String, Integer>();
		Collection<String> rows = DatasetPreparer
		      .getSingleColumnData("vocabulary.txt");
		int index = 0;

		for (String row : rows)
			vocabulary.put(row, index++);

		return vocabulary;
	}

	public static Collection<Map<String, Integer>> getFilteredWordCountByBill(
	      String purpose) throws IOException
	{
		Collection<Map<String, Integer>> wordCountMaps = new ArrayList<Map<String, Integer>>();
		Collection<String> billIds = getSingleColumnData(purpose + "Dataset.txt");

		for (String billId : billIds)
		{
			int id = Integer.parseInt(billId);

			if(BillPersister.hasFilteredBillWords(id))
			{
				Map<String, Integer> wordCountMap = BillPersister
				      .getCountByFilteredWord(id);

				wordCountMaps.add(wordCountMap);
			}
		}

		return wordCountMaps;
	}

	public static List<List<WordInfo>> getWordCountsByBill(String filename)
	      throws IOException
	{
		List<List<WordInfo>> wordCountsList = new ArrayList<List<WordInfo>>();
		String content = getData(filename);
		String[] lines = content.split("\n");

		for (String line : lines)
		{
			if(line != null && line.isEmpty() == false)
			{
				List<WordInfo> wordCounts = parseCompactWordCountRow(line);

				wordCountsList.add(wordCounts);
			}
		}

		return wordCountsList;
	}

	public static List<WordInfo> parseCompactWordCountRow(String row)
	{
		String[] tokens = row.split("\\s|:");
		List<WordInfo> wordCounts = new ArrayList<WordInfo>();
		boolean isId = true;
		int id = 0;

		for (String token : tokens)
		{
			if(token != null && !token.isEmpty())
			{
				if(isId)
				{
					id = Integer.parseInt(token);
					isId = false;
				}
				else
				{
					int count = Integer.parseInt(token);
					WordInfo wordInfo = new WordInfo();

					wordInfo.setId(id);
					wordInfo.setCount(count);
					wordCounts.add(wordInfo);
					isId = true;
				}
			}
		}

		return wordCounts;
	}

	public static void createModelDatasets() throws IOException
	{
		System.out.println("Creating Model datasets...");

		Collection<Map<String, Integer>> wordCountMaps = getFilteredWordCountByBill(
		      "training");
		SortedMap<String, Integer> mergedWordCountMap = mergeWordCountMaps(
		      wordCountMaps);
		Map<String, Integer> vocabularyMap = createVocabulary(mergedWordCountMap);

		System.out.println("Created vocabulary.");

		createSplsaDataset("training", vocabularyMap);
		createSplsaDataset("crossValidation", vocabularyMap);
		createSplsaDataset("testing", vocabularyMap);

		createSldaDataset("training", vocabularyMap);
		createSldaDataset("crossValidation", vocabularyMap);
		createSldaDataset("testing", vocabularyMap);

		System.out.println("Created Model datasets.");
	}

	public static void createSplsaDataset(String purpose,
	      Map<String, Integer> vocabularyMap) throws IOException
	{
		createWordCountDataset(purpose, "Lct", vocabularyMap,
		      WordCountFormat.LCT);
	}

	public static void createSldaDataset(String purpose,
	      Map<String, Integer> vocabularyMap) throws IOException
	{
		createWordCountDataset(purpose, "Slda", vocabularyMap,
		      WordCountFormat.SLDA);
	}

	public static void printWordStats(
	      Collection<Map<String, Integer>> wordCountMaps, int wordId,
	      Map<String, Integer> vocabularyMap)
	{
		int minWordCount = Integer.MAX_VALUE;
		int maxWordCount = 0;
		int totalWordCount = 0;
		int documentCount = 0;

		for (Map<String, Integer> wordCountMap : wordCountMaps)
		{
			for (String word : wordCountMap.keySet())
			{
				Integer id = vocabularyMap.get(word);
				int wordCount = wordCountMap.get(word);

				if(id == wordId)
				{
					if(wordCount < minWordCount)
						minWordCount = wordCount;

					if(wordCount > maxWordCount)
						maxWordCount = wordCount;

					documentCount++;
					totalWordCount += wordCount;
				}
			}
		}

		System.out.println(
		      "WordStats: id = " + wordId + ", minWordCount = " + minWordCount
		            + ", maxWordCount = " + maxWordCount + ", totalWordCount = "
		            + totalWordCount + ", documentCount = " + documentCount);
	}

	public static void createWordCountDataset(String purpose, String type,
	      Map<String, Integer> vocabularyMap, WordCountFormat wordCountFormat)
	      throws IOException
	{
		List<String> rows = new ArrayList<String>();
		int totalWordCount = 0;
		Collection<Map<String, Integer>> wordCountMaps = getFilteredWordCountByBill(
		      purpose);

		if(wordCountFormat == WordCountFormat.SLDA)
			rows.add(purpose + "List <- list(");

		boolean first = true;
		int documentCount = 0;

		for (Map<String, Integer> wordCountMap : wordCountMaps)
		{
			DocumentRow documentRow;

			documentRow = createCompactWordCountRow(wordCountMap, vocabularyMap,
			      wordCountFormat);

			if(!documentRow.row.isEmpty())
			{
				String row;

				if(wordCountFormat == WordCountFormat.SLDA)
				{
					if(first)
					{
						first = false;
						row = "";
					}
					else
						row = ", ";

					row += documentRow.row;
				}
				else
					row = documentRow.row;

				rows.add(row);

				totalWordCount += documentRow.totalWordCount;
				documentCount++;
			}
		}

		if(wordCountFormat == WordCountFormat.SLDA)
			rows.add(")");

		String filename = purpose + type + "Dataset.txt";

		saveData(rows, filename);
		System.out.println("Created " + type + " dataset for " + purpose
		      + " (Word count = " + totalWordCount + ", document count = "
		      + documentCount + ")");
	}

	private static DocumentRow createCompactWordCountRow(
	      Map<String, Integer> wordCountMap, Map<String, Integer> vocabularyMap,
	      WordCountFormat wordCountFormat)
	{
		StringBuffer buffer = new StringBuffer(wordCountMap.size() * 3);
		boolean first = true;
		int totalWordCount = 0;

		if(wordCountFormat == WordCountFormat.SLDA)
			buffer.append("matrix(as.integer(c(");

		for (String word : wordCountMap.keySet())
		{
			Integer id = vocabularyMap.get(word);

			if(id != null)
			{
				if(first == false)
					buffer.append(
					      wordCountFormat == WordCountFormat.LCT ? ' ' : ',');
				else
					first = false;

				buffer.append(id);

				int wordCount = wordCountMap.get(word);

				if(wordCountFormat == WordCountFormat.LCT)
					buffer.append(':');
				else
					buffer.append(',');

				buffer.append(wordCount);
				totalWordCount += wordCount;
			}
		}

		if(wordCountFormat == WordCountFormat.SLDA)
		{
			buffer.append(")), 2, ");
			buffer.append(wordCountMap.size());
			buffer.append(')');
		}

		return new DocumentRow(totalWordCount, buffer.toString());
	}

	public static Map<String, Integer> createVocabulary(
	      SortedMap<String, Integer> mergedWordCountMap) throws IOException
	{
		Map<String, Integer> vocabularyMap = new TreeMap<String, Integer>();

		int id = 0;

		for (String word : mergedWordCountMap.keySet())
			vocabularyMap.put(word, id++);

		saveData(vocabularyMap.keySet(), "vocabulary.txt");
		return vocabularyMap;
	}

	public static SortedMap<String, Integer> mergeWordCountMaps(
	      Collection<Map<String, Integer>> wordCountMaps)
	{
		SortedMap<String, Integer> mergedWordCountMap = new TreeMap<String, Integer>();

		for (Map<String, Integer> wordCountMap : wordCountMaps)
		{
			for (String word : wordCountMap.keySet())
			{
				Integer count = mergedWordCountMap.get(word);

				if(count == null)
					count = 0;

				count += wordCountMap.get(word);
				mergedWordCountMap.put(word, count);
			}
		}

		return mergedWordCountMap;
	}

	public static SortedMap<String, Integer> selectTopWords(
	      Map<String, Integer> wordCountMap)
	{
		List<Pair<String, Integer>> wordsSortedByCount = Utility
		      .sortByValue(wordCountMap);
		SortedMap<String, Integer> topWordsCountMap = new TreeMap<String, Integer>();

		for (int i = 0; i < wordsSortedByCount.size()
		      && i < MAX_VOCABULARY_SIZE; i++)
		{
			Pair<String, Integer> pair = wordsSortedByCount.get(i);

			topWordsCountMap.put(pair.getFirst(), pair.getSecond());
		}

		return topWordsCountMap;
	}

	public static <T> void saveData(Collection<T> rows, String filename)
	      throws IOException
	{
		saveData(rows, filename, "\n");
	}

	public static <T> void saveData(Collection<T> rows, String filename,
	      String rowSeparator) throws IOException
	{
		assert (DATASET_PERSISTENCE_DIRECTORY != null);
		Utility.saveData(DATASET_PERSISTENCE_DIRECTORY, filename, rows,
		      rowSeparator);
	}

	public static Collection<String> getSingleColumnData(String filename)
	      throws IOException
	{
		Collection<String> rows = new ArrayList<String>();
		String[] tokens = getData(filename).split("\n");

		for (String token : tokens)
			if(token != null && !token.isEmpty())
				rows.add(token);

		return rows;
	}

	public static Collection<Collection<String>> getMultiColumnData(
	      String filename, String columnSeparatorRegEx) throws IOException
	{
		Collection<Collection<String>> rows = new ArrayList<Collection<String>>();
		String[] tokens = getData(filename).split("\n");

		for (String token : tokens)
		{
			if(token != null && !token.isEmpty())
			{
				Collection<String> columns = getColumns(token,
				      columnSeparatorRegEx);

				rows.add(columns);
			}
		}

		return rows;
	}

	public static Collection<String> getColumns(String line,
	      String separatorRegEx)
	{
		String[] tokens = line.split(separatorRegEx);
		Collection<String> columns = new ArrayList<String>();

		for (String token : tokens)
			if(token != null && !token.isEmpty())
				columns.add(token);

		return columns;
	}

	public static void saveData(String filename, String content)
	      throws IOException
	{
		saveData(filename, content, false);
	}

	public static void saveData(String filename, String content, boolean append)
	      throws IOException
	{
		assert (DATASET_PERSISTENCE_DIRECTORY != null);
		Utility.saveData(DATASET_PERSISTENCE_DIRECTORY, filename, content,
		      append);
	}

	public static String getData(String filename) throws IOException
	{
		return Utility.getData(DATASET_PERSISTENCE_DIRECTORY, filename);
	}

	public static void partition(Collection<Integer> dataset,
	      Collection<Integer> partition1, Collection<Integer> partition2,
	      double partition1Share)
	{
		int partition1Size = (int) (dataset.size() * partition1Share);
		List<Integer> dataSetCopy = new ArrayList<Integer>();

		dataSetCopy.addAll(dataset);

		for (int i = 0; i < partition1Size; i++)
		{
			int selectedIndex = (int) (Math.random() * dataSetCopy.size());

			partition1.add(dataSetCopy.get(selectedIndex));
			dataSetCopy.remove(selectedIndex);
		}

		partition2.addAll(dataSetCopy);
	}

	private static class DocumentRow
	{
		public int totalWordCount;
		public String row;

		public DocumentRow(int totalWordCount, String row)
		{
			this.totalWordCount = totalWordCount;
			this.row = row;
		}
	}

	private static enum WordCountFormat
	{
		LCT, SLDA
	}
}
