public class class331 {
   protected static boolean field3587;
   protected static String field3586;

   class331() throws Throwable {
   }

   public static int method4612(int var0, int var1, int var2, int var3, int var4, int var5) {
      if ((var5 & 1) == 1) {
         int var7 = var3;
         var3 = var4;
         var4 = var7;
      }

      var2 &= 3;
      if (var2 == 0) {
         return var0;
      } else if (var2 == 1) {
         return var1;
      } else {
         return var2 == 2 ? 7 - var0 - (var3 - 1) : 7 - var1 - (var4 - 1);
      }
   }

   public static int method1484(int var0, int var1, int var2, int var3, int var4, int var5) {
      if (1 == (var5 & 1)) {
         int var7 = var3;
         var3 = var4;
         var4 = var7;
      }

      var2 &= 3;
      if (var2 == 0) {
         return var1;
      } else if (var2 == 1) {
         return 7 - var0 - (var3 - 1);
      } else {
         return var2 == 2 ? 7 - var1 - (var4 - 1) : var0;
      }
   }
}
