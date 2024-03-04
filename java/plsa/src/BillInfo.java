import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

public class BillInfo implements Comparable<BillInfo>
{
	private Chamber chamber;
	private int yesVoteCount;
	private int noVoteCount;
	private int abstainCount;
	private int number;
	private int govApiId;
	private Date voteDate;
	private String displayNumber;
	private int congress;
	private String govApiUrl;
	private int id;
	private String currentStatus;
	private String gpoUrl;
	private String category;
	private String title;
	private String titleWithoutNumber;
	private String billType;
	private String billTypeLabel;
	private String voteType;

	private static int nextId = 1;

	public BillInfo()
	{
		this.id = nextId++;
	}

	public BillInfo(int id)
	{
		this.id = id;
	}
	
	public static void setNextId(int nextId)
	{
		BillInfo.nextId = nextId;
	}

	public Chamber getChamber()
	{
		return chamber;
	}

	public void setChamber(Chamber chamber)
	{
		this.chamber = chamber;
	}

	public int getYesVoteCount()
	{
		return yesVoteCount;
	}

	public void setYesVoteCount(int yesVoteCount)
	{
		this.yesVoteCount = yesVoteCount;
	}

	public int getNoVoteCount()
	{
		return noVoteCount;
	}

	public void setNoVoteCount(int noVoteCount)
	{
		this.noVoteCount = noVoteCount;
	}

	public int getAbstainCount()
	{
		return abstainCount;
	}

	public void setAbstainCount(int abstainCount)
	{
		this.abstainCount = abstainCount;
	}

	public int getNumber()
	{
		return number;
	}

	public void setNumber(int number)
	{
		this.number = number;
	}

	public int getGovApiId()
	{
		return govApiId;
	}

	public void setGovApiId(int govApiId)
	{
		this.govApiId = govApiId;
	}

	public Date getVoteDate()
	{
		return voteDate;
	}

	public void setVoteDate(Date voteDate)
	{
		this.voteDate = voteDate;
	}

	public String getDisplayNumber()
	{
		return displayNumber;
	}

	public void setDisplayNumber(String displayNumber)
	{
		this.displayNumber = displayNumber;
	}

	public int getCongress()
	{
		return congress;
	}

	public void setCongress(int congress)
	{
		this.congress = congress;
	}

	public String getGovApiUrl()
	{
		return govApiUrl;
	}

	public void setGovApiUrl(String govApiUrl)
	{
		this.govApiUrl = govApiUrl;
	}

	public String getGpoUrl()
	{
		return gpoUrl;
	}

	public void setGpoUrl(String gpoUrl)
	{
		this.gpoUrl = gpoUrl;
	}

	public JSONObject toJson() throws JSONException
	{
		JSONObject json = new JSONObject(this);

		json.put("chamber", chamber.name());
		return json;
	}

	public int getId()
	{
		return id;
	}

	public String getCurrentStatus()
	{
		return currentStatus;
	}

	public void setCurrentStatus(String currentStatus)
	{
		this.currentStatus = currentStatus;
	}

	public String getCategory()
	{
		return category;
	}

	public void setCategory(String category)
	{
		this.category = category;
	}

	@SuppressWarnings("deprecation")
	public static BillInfo fromJson(JSONObject json) throws JSONException
	{
		BillInfo billInfo = new BillInfo(json.getInt("id"));

		billInfo.setNumber(json.getInt("number"));
		billInfo.setCongress(json.getInt("congress"));
		billInfo.setYesVoteCount(json.getInt("yesVoteCount"));
		billInfo.setDisplayNumber(json.getString("displayNumber"));
		billInfo.setGovApiUrl(json.getString("govApiUrl"));
		billInfo.setChamber(Chamber.valueOf(Chamber.class,
		      json.getString("chamber")));
		billInfo.setGovApiId(json.getInt("govApiId"));
		billInfo.setVoteDate(new Date(Date.parse(json.getString("voteDate"))));
		billInfo.setNoVoteCount(json.getInt("noVoteCount"));
		billInfo.setAbstainCount(json.getInt("abstainCount"));
		billInfo.setCurrentStatus(json.getString("currentStatus"));
		billInfo.setGpoUrl(json.optString("gpoUrl"));
		billInfo.setCategory(json.getString("category"));
		billInfo.setTitle(json.optString("title"));
		billInfo.setTitleWithoutNumber(json.optString("titleWithoutNumber"));
		billInfo.setBillType(json.optString("billType"));
		billInfo.setBillTypeLabel(json.optString("billTypeLabel"));
		billInfo.setVoteType(json.optString("voteType"));
		return billInfo;
	}

	public String getTitle()
	{
		return title;
	}

	public void setTitle(String title)
	{
		this.title = title;
	}

	public String getTitleWithoutNumber()
	{
		return titleWithoutNumber;
	}

	public void setTitleWithoutNumber(String titleWithoutNumber)
	{
		this.titleWithoutNumber = titleWithoutNumber;
	}

	public String getBillType()
	{
		return billType;
	}

	public void setBillType(String billType)
	{
		this.billType = billType;
	}

	public String getBillTypeLabel()
	{
		return billTypeLabel;
	}

	public void setBillTypeLabel(String billTypeLabel)
	{
		this.billTypeLabel = billTypeLabel;
	}

	public String getVoteType()
	{
		return voteType;
	}

	public void setVoteType(String voteType)
	{
		this.voteType = voteType;
	}

	@Override
	public int hashCode()
	{
		return congress + displayNumber.hashCode();
	}

	@Override
	public boolean equals(Object object)
	{
		if (object == null)
			return false;

		if (object instanceof BillInfo)
		{
			BillInfo billInfo = (BillInfo) object;

			return congress == billInfo.congress
			      && displayNumber.equals(billInfo.displayNumber);
		}

		return false;
	}

	@Override
	public int compareTo(BillInfo billInfo)
	{
		int comparison = congress - billInfo.congress;

		if (comparison != 0)
			return comparison;

		return displayNumber.compareTo(billInfo.displayNumber);
	}
}
