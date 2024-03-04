import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.io.IOUtils;

import weka.core.matrix.Matrix;

public class Utility
{
  public static void saveData(String directoryName, String filename,
      Matrix matrix) throws IOException
  {
    StringBuffer buffer = new StringBuffer(
        matrix.getRowDimension() * matrix.getColumnDimension() * 2);

    for (int r = 0; r < matrix.getRowDimension(); r++)
    {
      for (int c = 0; c < matrix.getColumnDimension(); c++)
      {
        if(c != 0)
          buffer.append(' ');

        buffer.append(matrix.get(r, c));
      }

      buffer.append('\n');
    }

    saveData(directoryName, filename, buffer.toString());
  }

  public static void saveData(String directoryName, String filename,
      String content) throws IOException
  {
    saveData(directoryName, filename, content, false);
  }

  public static void saveData(String directoryName, String filename,
      String content, boolean append) throws IOException
  {
    File file = new File(directoryName);

    file.mkdirs();

    FileWriter writer = new FileWriter(file.getPath() + "/" + filename, append);

    try
    {
      IOUtils.write(content, writer);
    }
    finally
    {
      writer.close();
    }
  }

  public static void deleteData(String directoryName, String filename)
  {
    File file = new File(directoryName + "/" + filename);

    if(file.exists())
      file.delete();
  }

  public static <T> void saveData(String directoryName, String filename,
      Collection<T> rows) throws IOException
  {
    saveData(directoryName, filename, rows, "\n");
  }

  public static <T> void saveData(String directoryName, String filename,
      Collection<T> rows, String rowSeparator) throws IOException
  {
    File file = new File(directoryName);

    file.mkdirs();

    FileWriter writer = new FileWriter(file.getPath() + "/" + filename);

    try
    {
      for (T row : rows)
      {
        writer.write(row.toString());
        writer.write(rowSeparator);
      }
    }
    finally
    {
      writer.close();
    }
  }

  public static Collection<Collection<String>> getCsv(String directoryName,
      String filename) throws IOException
  {
    return getMultiColumnData(directoryName, filename, ",");
  }

  public static Collection<Collection<String>> getMultiColumnData(
      String directoryName, String filename, String separatorRegEx)
      throws IOException
  {
    Collection<Collection<String>> rows = new ArrayList<Collection<String>>();
    String content = getData(directoryName, filename);
    String[] lines = content.split("\n");

    for (String line : lines)
    {
      if(line != null && !line.isEmpty())
      {
        List<String> row = new ArrayList<String>();
        String[] values = line.split(separatorRegEx);

        for (String value : values)
        {
          if(value != null)
          {
            value = value.trim();

            if(!value.isEmpty())
              row.add(value);
          }
        }

        rows.add(row);
      }
    }

    return rows;
  }

  public static List<String> getSingleColumnData(String directoryName,
      String filename) throws IOException
  {
    List<String> rows = new ArrayList<String>();
    String content = getData(directoryName, filename);
    String[] lines = content.split("\n");

    for (String line : lines)
    {
      if(line != null)
      {
        line = line.trim();

        if(!line.isEmpty())
          rows.add(line);
      }
    }

    return rows;
  }

  public static int getLargestValue(Matrix matrix, int row)
  {
    double max = Double.MIN_VALUE;
    int maxIndex = -1;

    for (int c = 0; c < matrix.getColumnDimension(); c++)
    {
      double value = matrix.get(row, c);

      if(value > max)
      {
        max = value;
        maxIndex = c;
      }
    }

    return maxIndex;
  }

  public static String getData(String directoryName, String filename)
      throws IOException
  {
    FileReader reader = new FileReader(directoryName + "/" + filename);

    try
    {
      return IOUtils.toString(reader);
    }
    finally
    {
      reader.close();
    }
  }

  public static Matrix getMatrix(String directoryName, String filename)
      throws IOException
  {
    Collection<Collection<String>> rows = getMultiColumnData(directoryName,
        filename, "\\s");
    int columnCount = getColumnCount(rows);
    Matrix matrix = new Matrix(rows.size(), columnCount);
    int r = 0;

    for (Collection<String> row : rows)
    {
      int c = 0;

      for (String value : row)
      {
        matrix.set(r, c, Double.parseDouble(value));
        c++;
      }

      r++;
    }

    return matrix;
  }

  public static int getLineCount(String directoryName, String filename)
      throws IOException
  {
    String data = getData(directoryName, filename).trim();
    String[] lines = data.split("\n");
    int lineCount = 0;

    for (String line : lines)
    {
      if(line != null)
        lineCount++;
    }

    return lineCount;
  }

  public static int getColumnCount(Collection<Collection<String>> rows)
  {
    return rows.iterator().next().size();
  }

  public static int[] randomSequence(int count, int selectCount)
  {
    assert (count >= selectCount);

    int[] randomSequence = new int[selectCount];
    List<Integer> sequence = new ArrayList<Integer>(count);

    for (int i = 0; i < count; i++)
      sequence.add(i);

    for (int i = 0; i < selectCount; i++)
    {
      int randomIndex = (int) (Math.random() * sequence.size());

      randomSequence[i] = sequence.remove(randomIndex);
    }

    return randomSequence;
  }

  public static <K, V extends Comparable<V>> List<Pair<K, V>> sortByValue(
      Map<K, V> map)
  {
    List<Pair<K, V>> sortedValues = new ArrayList<Pair<K, V>>();
    TreeMap<V, ArrayList<K>> sortedValueMap = new TreeMap<V, ArrayList<K>>();

    for (K key : map.keySet())
    {
      V value = map.get(key);
      ArrayList<K> keyList = sortedValueMap.get(value);

      if(keyList == null)
      {
        keyList = new ArrayList<K>();
        sortedValueMap.put(value, keyList);
      }

      keyList.add(key);
    }

    for (V value : sortedValueMap.descendingKeySet())
      for (K key : sortedValueMap.get(value))
        sortedValues.add(new Pair<K, V>(key, value));

    return sortedValues;
  }
}
