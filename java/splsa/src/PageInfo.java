public class PageInfo
{
  private int count;
  private int total;

  public PageInfo(int count, int total)
  {
    this.count = count;
    this.total = total;
  }

  public int getCount()
  {
    return count;
  }

  public int getTotal()
  {
    return total;
  }
}
