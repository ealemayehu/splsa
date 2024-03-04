import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Importer
{
	private final static int[] CONGRESS = new int[]
	{100, 101, 102, 103, 104, 105, 106, 107, 109, 110, 111, 112, 113, 114};
	private final static int LIMIT = 600;
	private final static String BASE_VOTE_API_URL = "https://www.govtrack.us/api/v2/vote";
	private static Pattern gpoUrlPattern = Pattern
	      .compile("http\\:\\/\\/www\\.gpo\\.gov\\/fdsys\\/pkg([^\\.])+\\.");
	private static SimpleDateFormat dateFormat = new SimpleDateFormat(
	      "yyyy-MM-dd'T'HH:mm:ss");

	public static void downloadBillInfos() throws IOException, JSONException,
	      ParseException
	{
		Collection<BillInfo> billInfos = new TreeSet<BillInfo>();
		Map<Integer, Integer> billNumberIdMap = BillPersister
		      .getBillNumberIdMap();

		BillInfo.setNextId(BillPersister.getMaxBillId() + 1);

		populateBillInfos(billInfos, billNumberIdMap, LIMIT);
		BillPersister.saveBillInfos(billInfos);
	}

	public static void downloadBills() throws IOException, JSONException
	{
		Collection<BillInfo> billInfos = BillPersister.getBillInfos();
		int index = 1;
		int totalCount = 0;
		int downloadCount = 0;

		for (BillInfo billInfo : billInfos)
		{
			totalCount++;
			
			if(downloadBill(billInfo))
				downloadCount++;

			if(index % 10 == 0)
				System.out.println("Downloaded " + downloadCount + " bills out of " + totalCount);

			index++;
		}
		
		System.out.println("Total bills attempted to download: " + totalCount + ", downloaded: " + downloadCount);
	}

	public static boolean downloadBill(BillInfo billInfo) throws IOException
	{
		if(BillPersister.hasBill(billInfo.getId()))
			return false;

		String content = null;
		
		try
		{
			String url = billInfo.getGpoUrl();

			assert (url != null);
			assert (!url.isEmpty());
			
			url = url.replace("http://", "https://");
			
			content = getContentText(url);
			
			assert (content != null);
			assert (!content.isEmpty());
		}
		catch (Throwable e)
		{
			e.printStackTrace();
			System.err.println("Unable to download the bill for bill id = "
			      + billInfo.getId());
			return false;
		}

		content = content.replace("<html><body><pre>", "");
		content = content.replace("</pre></body></html>", "");
		BillPersister.saveBill(billInfo.getId(), content);
		return true;
	}

	public static void populateGpoUrls() throws IOException, JSONException
	{
		Collection<BillInfo> billInfos = BillPersister.getBillInfos();
		int index = 1;

		for (BillInfo billInfo : billInfos)
		{
			try
			{
				populateGpoUrl(billInfo);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				System.out.println("Unable to get the GPO URL for bill id = "
				      + billInfo.getId());
			}

			BillPersister.saveBillInfo(billInfo);

			if(index % 10 == 0)
				System.out.println("Populated " + index + " GPO urls.");

			index++;
		}
	}

	public static void populateBillInfos(Collection<BillInfo> billInfos,
	      Map<Integer, Integer> billNumberIdMap, int limit) throws IOException,
	      JSONException, ParseException
	{
		for (int congress : CONGRESS)
			populateBillInfos(billInfos, billNumberIdMap, congress, limit);
	}

	public static void populateBillInfos(Collection<BillInfo> billInfos,
	      Map<Integer, Integer> billNumberIdMap, int congress, int limit)
	      throws IOException, JSONException, ParseException
	{
		PageInfo pageInfo = null;
		int offset = -1 * limit;

		do
		{
			offset += limit;
			pageInfo = populateBillInfos(billInfos, billNumberIdMap, congress,
			      offset, limit);
		}
		while (offset + pageInfo.getCount() < pageInfo.getTotal());
	}

	public static PageInfo populateBillInfos(Collection<BillInfo> billInfos,
	      Map<Integer, Integer> billNumberIdMap, int congress, int offset,
	      int limit) throws IOException, JSONException, ParseException
	{
		String apiUrl = BASE_VOTE_API_URL + "?congress=" + congress + "&offset="
		      + offset + "&limit=" + limit + "&order_by=created";
		String content = getContentText(apiUrl);
		JSONObject main = new JSONObject(content);
		JSONArray votes = main.getJSONArray("objects");
		int i;
		int added = 0;

		for (i = 0; i < votes.length(); i++)
		{
			JSONObject vote = votes.getJSONObject(i);

			if(!vote.has("related_bill") || vote.isNull("related_bill"))
				continue;

			JSONObject relatedBill = vote.getJSONObject("related_bill");
			int number = relatedBill.getInt("number");
			Integer id = billNumberIdMap.get(number);
			BillInfo billInfo;
			
			if(id != null)
				billInfo = new BillInfo(id);
			else
				billInfo = new BillInfo();

			billInfo.setGovApiId(vote.getInt("id"));
			billInfo.setVoteDate(dateFormat.parse(vote.getString("created")));
			billInfo.setNumber(number);
			billInfo.setCurrentStatus(relatedBill.getString("current_status"));
			billInfo.setDisplayNumber(relatedBill.getString("display_number"));
			billInfo.setNoVoteCount(vote.getInt("total_minus"));
			billInfo.setYesVoteCount(vote.getInt("total_plus"));
			billInfo.setAbstainCount(vote.getInt("total_other"));
			billInfo.setCategory(vote.getString("category"));
			billInfo.setGovApiUrl(relatedBill.getString("link"));
			billInfo.setBillType(relatedBill.getString("bill_type"));
			billInfo.setBillTypeLabel(relatedBill.getString("bill_type_label"));
			billInfo.setTitle(relatedBill.getString("title"));
			billInfo.setTitleWithoutNumber(relatedBill
			      .getString("title_without_number"));
			billInfo.setVoteType(relatedBill.optString("vote_type"));
			billInfo.setCongress(congress);

			if("house".equals(vote.get("chamber")))
				billInfo.setChamber(Chamber.House);
			else
				billInfo.setChamber(Chamber.Senate);

			billInfos.remove(billInfo);
			billInfos.add(billInfo);
			added++;
		}

		JSONObject meta = main.getJSONObject("meta");
		int total = meta.getInt("total_count");

		System.out.println("Populated bills (congress = " + congress
		      + ", offset = " + offset + ", limit = " + limit + ", added = "
		      + added + ", total = " + total + ").");
		return new PageInfo(i, total);
	}

	public static void populateGpoUrl(BillInfo billInfo) throws IOException
	{
		String gpoUrl = billInfo.getGpoUrl();
		
		if(gpoUrl != null && !gpoUrl.isEmpty())
			return;
			
		String govApiPage = getContentText(billInfo.getGovApiUrl() + "/text");
		Matcher match = gpoUrlPattern.matcher(govApiPage);

		if(match.find())
		{
			String url = match.group().replace("pdf", "html") + "htm";

			billInfo.setGpoUrl(url);
		}
	}

	public static String getContentText(String link) throws IOException
	{
		HttpsURLConnection connection = null;
		InputStream stream = null;
		String content = null;

		try
		{
			URL url = new URL(link);

			connection = (HttpsURLConnection) url.openConnection();
         connection.setConnectTimeout(20000);
         connection.setReadTimeout(20000);
			stream = connection.getInputStream();
			content = IOUtils.toString(stream, connection.getContentEncoding());
			
			if(content.isEmpty())
			{
				System.err.println("Code: " + connection.getResponseCode() + ", Length: " + connection.getContentLength());
			}
		}
		finally
		{
			if(stream != null)
			{
				try
				{
					stream.close();
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}

			if(connection != null)
			{
				try
				{
					connection.disconnect();
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		}

		return content;
	}
}
