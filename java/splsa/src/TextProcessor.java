import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;

public class TextProcessor
{
	public static Set<String> getStopWords() throws IOException
	{
		Set<String> stopWords = new HashSet<String>();

		populateStopWords("src/common_stopwords.txt", stopWords);
		populateStopWords("src/domain_stopwords.txt", stopWords);
		return stopWords;
	}

	private static void populateStopWords(String filename, Set<String> stopWords)
	      throws IOException
	{
		FileReader reader = new FileReader(filename);

		try
		{
			String content = IOUtils.toString(reader);
			String[] tokens = content.split("\\s");

			for (String token : tokens)
				if(token != null && !token.isEmpty())
					stopWords.add(token.toLowerCase());
		}
		finally
		{
			reader.close();
		}
	}

	private static List<String> filterWords(Collection<String> words,
	      Set<String> stopWords) throws IOException
	{
		List<String> filteredWords = new ArrayList<String>();

		for (String word : words)
		{
			String lowerCaseWord = word.toLowerCase();

			if(lowerCaseWord.length() < 4)
				continue;

			if(stopWords.contains(lowerCaseWord))
				continue;

			filteredWords.add(lowerCaseWord);
		}

		return filteredWords;
	}

	private static Collection<String> matchWords(
	      Collection<String> words, Set<String> matchWords)
	{
		Collection<String> matchedWords = new ArrayList<String>();
		
		for(String word: words)
		{
			if(matchWords.contains(word))
				matchedWords.add(word);
		}
		
		return matchedWords;
	}

	public static Collection<String> extractWords(String content)
	{
		Collection<String> words = new ArrayList<String>();
		String[] tokens = content.split("[^a-zA-Z]");

		for (String token : tokens)
			if(token != null && !token.isEmpty())
				words.add(token);

		return words;
	}

	public static void extractBillWords() throws IOException, JSONException
	{
		Collection<Integer> billIds = BillPersister.getBillIds();
		int index = 1;
		int failedCount = 0;

		for (int billId : billIds)
		{
			try
			{
				Collection<String> words = extractWords(BillPersister
				      .getBill(billId));

				BillPersister.saveRawWords(billId, words);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				System.out.println("Unable to get the bill for bill id = " + billId
				      + ".");
				failedCount++;
			}

			if(index++ % 10 == 0)
				System.out.println("Attempted extracting words from " + index + " bills.");
		}

		System.out
		      .println("Attempted extracting words from " + index + " bills (failed: " + failedCount + ")");
	}

	public static void filterBillWords() throws IOException, JSONException
	{
		int index = 1;
		int totalWordCount = 0;
		int phase1FilteredCount = 0;
		int fileNotFoundCount = 0;
		int errorCount = 0;
		int phase1RemovedWordCount = 0;
		Set<String> stopWords = getStopWords();
		Map<Integer, List<String>> filteredWordsMap = new HashMap<Integer, List<String>>();
		Map<String, Integer> wordCountsMap = new HashMap<String, Integer>();
		Map<String, Set<Integer>> wordDocumentMap = new HashMap<String, Set<Integer>>();
		Collection<Integer> billIds = BillPersister.getBillIds();

		System.out.println("Cleaning filtered words cache...");

		for (int billId : billIds)
			BillPersister.deleteFilteredWords(billId);

		for (int billId : billIds)
		{
			try
			{
				Collection<String> rawWords = BillPersister.getRawWords(billId);
				List<String> filteredWords = filterWords(rawWords, stopWords);

				phase1RemovedWordCount += rawWords.size() - filteredWords.size();
				totalWordCount += rawWords.size();
				filteredWordsMap.put(billId, filteredWords);

				updateWordCountMap(wordCountsMap, filteredWords);
				updateWordDocumentMap(wordDocumentMap, billId, filteredWords);

				phase1FilteredCount++;
			}
			catch (FileNotFoundException e)
			{
				fileNotFoundCount++;
			}
			catch (Exception e)
			{
				errorCount++;
				e.printStackTrace();
				System.out.println("Unable to get the raw words for bill id = "
				      + billId + ".");
			}

			if(index++ % 100 == 0)
				System.out.println("Phase 1 Filtered words from " + index
				      + " bills.");
		}

		System.out.println("Phase 1 Filtered: " + phase1FilteredCount
		      + ", File Not Found: " + fileNotFoundCount + ", Error: "
		      + errorCount);

		List<Pair<String, Integer>> sortedWordCounts = sortWordsByDocumentFrequency(wordDocumentMap);
		Set<String> phase2Words = new HashSet<String>();

		for (Pair<String, Integer> pair: sortedWordCounts)
		{
			if(pair.getSecond() >= 10)
				phase2Words.add(pair.getFirst());
			else
				break;
		}
		
		index = 1;

		int phase2FilteredCount = 0;
		int phase2NotFilteredCount = 0;
		int phase2RemovedWordCount = 0;

		for (int billId : filteredWordsMap.keySet())
		{
			List<String> filteredWords = filteredWordsMap.get(billId);
			Collection<String> matchedWords = matchWords(filteredWords, phase2Words);

			if(matchedWords.size() > 0)
			{
				BillPersister.saveFilteredWords(billId, matchedWords);
				phase2FilteredCount++;
			}
			else
				phase2NotFilteredCount++;

			if(index++ % 100 == 0)
				System.out.println("Phase 2 Filtered words from " + index
				      + " bills.");
		}

		System.out.println("Phase 2 Filtered: " + phase2FilteredCount
		      + ", Phase 2 Not Filtered: " + phase2NotFilteredCount);
		System.out.println("Total word count: " + totalWordCount
		      + ", Phase 1 removed word count: " + phase1RemovedWordCount + " "
		      + "Phase 2 removed word count: " + phase2RemovedWordCount);
		System.out.println("Completed filtering words from " + index + " bills.");
	}

	private static void updateWordCountMap(Map<String, Integer> wordCountsMap,
	      List<String> words)
	{
		for (String word : words)
		{
			Integer wordCount = wordCountsMap.get(word);

			if(wordCount == null)
				wordCount = 0;

			wordCount++;
			wordCountsMap.put(word, wordCount);
		}
	}

	private static void updateWordDocumentMap(
	      Map<String, Set<Integer>> wordDocumentMap, int billId,
	      List<String> words)
	{
		for (String word : words)
		{
			Set<Integer> documents = wordDocumentMap.get(word);

			if(documents == null)
			{
				documents = new HashSet<Integer>();
				wordDocumentMap.put(word, documents);
			}

			if(!documents.contains(billId))
				documents.add(billId);
		}
	}

	private static List<Pair<String, Integer>> sortWordsByDocumentFrequency(
	      Map<String, Set<Integer>> wordDocumentMap) throws IOException
	{
		Map<String, Integer> wordDocumentFrequencyMap = new HashMap<String, Integer>();
			
		for(String word: wordDocumentMap.keySet())
			wordDocumentFrequencyMap.put(word, wordDocumentMap.get(word).size());
		
		List<Pair<String, Integer>> sortedWordsByFrequency = Utility
		      .sortByValue(wordDocumentFrequencyMap);

		DatasetPreparer.saveData(sortedWordsByFrequency, "sortedDocumentFrequency.txt");
		return sortedWordsByFrequency;
	}
	
	public static List<Pair<String, Integer>> sortByDocumentFrequency(
	      Map<String, Integer> termFrequency) throws IOException
	{
		List<Pair<String, Integer>> sortedWordsByFrequency = Utility
		      .sortByValue(termFrequency);

		DatasetPreparer.saveData(sortedWordsByFrequency, "wordFrequency.txt");
		return sortedWordsByFrequency;
	}
}
