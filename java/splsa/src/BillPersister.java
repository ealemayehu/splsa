import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;

public class BillPersister
{
  private static final String BILL_PERSISTENCE_DIRECTORY = getBillPersistenceDirectory();
  private static final int INDENT_FACTOR = 3;

  private static String getBillPersistenceDirectory()
  {
    String directory = System.getProperty("bill.persistance.directory");

    if(directory == null)
    {
      directory = "../data/bills";
    }

    return directory;
  }

  public static void saveData(int billId, String filename, String content)
      throws IOException
  {
    assert (BILL_PERSISTENCE_DIRECTORY != null);
    Utility.saveData(BILL_PERSISTENCE_DIRECTORY + "/" + billId, filename,
        content);
  }

  public static void deleteData(int billId, String filename)
  {
    Utility.deleteData(BILL_PERSISTENCE_DIRECTORY + "/" + billId, filename);
  }

  public static boolean hasData(int billId, String filename)
  {
    File file = new File(
        BILL_PERSISTENCE_DIRECTORY + "/" + billId + "/" + filename);

    return file.exists();
  }

  public static String getData(int billId, String filename) throws IOException
  {
    return Utility.getData(BILL_PERSISTENCE_DIRECTORY + "/" + billId, filename);
  }

  public static List<Integer> getBillIdsWithFilteredWords()
  {
    return getBillIdsWithFile("billFilteredWords.txt");
  }

  public static List<Integer> getBillIdsWithBill()
  {
    return getBillIdsWithFile("bill.txt");
  }

  public static List<Integer> getBillIdsWithFile(String filename)
  {
    Collection<Integer> billIds = getBillIds();
    List<Integer> billIdsWithFile = new ArrayList<Integer>();

    for (Integer billId : billIds)
    {
      if(hasFile(billId, filename))
        billIdsWithFile.add(billId);
    }

    return billIdsWithFile;
  }

  public static String getCannonicalBillId(BillInfo info)
  {
    String title = info.getTitle();

    return title.substring(0, title.indexOf(':'));
  }

  public static boolean hasFile(int billId, String filename)
  {
    assert (BILL_PERSISTENCE_DIRECTORY != null);

    File file = new File(
        BILL_PERSISTENCE_DIRECTORY + "/" + billId + "/" + filename);

    return file.exists();
  }

  public static void saveBill(int billId, String content) throws IOException
  {
    saveData(billId, "bill.txt", content);
  }

  public static String getBill(int billId) throws IOException
  {
    return getData(billId, "bill.txt");
  }

  public static boolean hasBill(int billId)
  {
    return hasFile(billId, "bill.txt");
  }

  public static void saveBillInfos(Collection<BillInfo> billInfos)
      throws IOException, JSONException
  {
    for (BillInfo billInfo : billInfos)
      saveBillInfo(billInfo);

    System.out.println("Persisted " + billInfos.size() + " bill info(s).");
  }

  public static void saveBillInfo(BillInfo billInfo)
      throws IOException, JSONException
  {
    saveData(billInfo.getId(), "billInfo.json",
        billInfo.toJson().toString(INDENT_FACTOR));
  }

  public static Collection<String> getRawWords(int billId) throws IOException
  {
    return getWords(billId, "billRawWords.txt");
  }

  public static List<String> getFilteredWords(int billId) throws IOException
  {
    return getWords(billId, "billFilteredWords.txt");
  }

  public static boolean hasFilteredBillWords(int billId)
  {
    return hasData(billId, "billFilteredWords.txt");
  }

  public static Map<String, Integer> getCountByFilteredWord(int billId)
      throws IOException
  {
    Map<String, Integer> wordCountMap = new HashMap<String, Integer>();
    Collection<String> filteredWords = getFilteredWords(billId);

    for (String filteredWord : filteredWords)
    {
      Integer count = wordCountMap.get(filteredWord);

      if(count == null)
        count = 1;
      else
        count++;

      wordCountMap.put(filteredWord, count);
    }

    return wordCountMap;
  }

  public static void saveWords(int billId, String filename,
      Collection<String> words) throws IOException
  {
    StringBuffer buffer = new StringBuffer(words.size() * 10);

    for (String word : words)
    {
      buffer.append(word);
      buffer.append(" ");
    }

    saveData(billId, filename, buffer.toString());
  }

  public static List<String> getWords(int billId, String filename)
      throws IOException
  {
    String content = getData(billId, filename);
    String[] tokens = content.split(" ");
    List<String> words = new ArrayList<String>();

    for (String token : tokens)
      if(token != null && !token.isEmpty())
        words.add(token);

    return words;
  }

  public static void saveRawWords(int billId, Collection<String> words)
      throws IOException
  {
    saveWords(billId, "billRawWords.txt", words);
  }

  public static void saveFilteredWords(int billId, Collection<String> words)
      throws IOException
  {
    saveWords(billId, "billFilteredWords.txt", words);
  }

  public static void deleteFilteredWords(int billId)
  {
    deleteData(billId, "billFilteredWords.txt");
  }

  public static void clean() throws IOException
  {
    FileUtils.deleteDirectory(new File(BILL_PERSISTENCE_DIRECTORY));
  }

  public static Collection<Integer> getBillIds()
  {
    assert (BILL_PERSISTENCE_DIRECTORY != null);

    Collection<Integer> billIds = new ArrayList<Integer>();
    File file = new File(BILL_PERSISTENCE_DIRECTORY);

    if(file.exists())
    {
      File[] childFiles = file.listFiles();

      for (File childFile : childFiles)
      {
        if(childFile.isDirectory())
        {
          try
          {
            int billId = Integer.parseInt(childFile.getName());

            billIds.add(billId);
          }
          catch (Exception e)
          {
          }
        }
      }
    }

    return billIds;
  }

  public static Map<Integer, Integer> getBillNumberIdMap()
      throws IOException, JSONException
  {
    Collection<BillInfo> billInfos = getBillInfos();
    Map<Integer, Integer> billNumberIdMap = new HashMap<Integer, Integer>();

    for (BillInfo billInfo : billInfos)
      billNumberIdMap.put(billInfo.getNumber(), billInfo.getId());

    return billNumberIdMap;
  }

  public static Collection<BillInfo> getBillInfos()
      throws IOException, JSONException
  {
    Collection<Integer> billIds = getBillIds();
    Collection<BillInfo> billInfos = new ArrayList<BillInfo>();

    for (int billId : billIds)
      billInfos.add(getBillInfo(billId));

    return billInfos;
  }

  public static int getMaxBillId()
  {
    int maxBillId = 0;

    for (Integer billId : getBillIds())
    {
      if(billId > maxBillId)
        maxBillId = billId;
    }

    return maxBillId;
  }

  public static BillInfo getBillInfo(int billId)
      throws IOException, JSONException
  {
    return BillInfo.fromJson(new JSONObject(getData(billId, "billInfo.json")));
  }
}
