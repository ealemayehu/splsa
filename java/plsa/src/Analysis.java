import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONException;

public class Analysis
{
	public static Map<String, Integer> getCurrentStatusCounts()
	      throws IOException, JSONException
	{
		return getCounts(new CategoryMapper()
		{
			@Override
         public String getCategoryName(BillInfo billInfo)
         {
				return billInfo.getCurrentStatus();
         }

			@Override
         public int getCategoryValue(BillInfo billInfo)
         {
	         return 1;
         }
		});
	}
	
	public static Map<String, Integer> getCategoryCounts()
	      throws IOException, JSONException
	{
		return getCounts(new CategoryMapper()
		{
			@Override
         public String getCategoryName(BillInfo billInfo)
         {
				return billInfo.getCategory();
         }

			@Override
         public int getCategoryValue(BillInfo billInfo)
         {
	         return 1;
         }
		});
	}
	
	public static Map<String, Integer> getCounts(CategoryMapper mapper)
	      throws IOException, JSONException
	{
		Map<String, Integer> countMap = new TreeMap<String, Integer>();
		Collection<BillInfo> billInfos = BillPersister.getBillInfos();

		for (BillInfo billInfo : billInfos)
		{
			String categoryName = mapper.getCategoryName(billInfo);
			Integer sum = countMap.get(categoryName);
			int value = mapper.getCategoryValue(billInfo);

			if (sum == null)
				sum = value;
			else
				sum += value;

			countMap.put(categoryName, sum);
		}

		return countMap;
	}
	
	public static void printAllCounts() throws IOException, JSONException
	{
		printCounts("Current status counts", getCurrentStatusCounts());
		printCounts("Category counts", getCategoryCounts());
	}
	

	public static void printCounts(String title, Map<String, Integer> countMap)
	{
		System.out.println(title + ": ");

		for (String categoryName : countMap.keySet())
			System.out.println(categoryName + ": " + countMap.get(categoryName));
	}
	
	public static interface CategoryMapper
	{
		String getCategoryName(BillInfo billInfo);
		int getCategoryValue(BillInfo billInfo);
	}
}
