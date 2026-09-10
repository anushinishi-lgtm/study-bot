public class Main {
    static void rightAngleTringleWithSpace(int n)
    {
        for(int i=0; i<n; i++)
        {
            for(int j=0; j<n; j++)
            {
                if(j>i)
                    System.out.print(" ");
                else
                System.out.print("*");
            }
            System.out.println();
        }
    }
    static void pattern(int n)
    {
        for(int i=0; i<5; i++)
        {
            for(int j=0; j<5; j++)
            {
                if (j == 0 || j == n - 1 || i == 0 || i == n - 1) {
                    System.out.print("* ");
                } else {
                    System.out.print("  ");
                }
            }
            System.out.println();
        }
    }
    public static void main(String[] args)
    {
        rightAngleTringleWithSpace(5);
    }
}
