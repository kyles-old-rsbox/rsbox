import java.util.zip.Inflater;

public class class531 {
   Inflater field5187;

   public class531() {
      this(-1, 1000000, 1000000);
   }

   class531(int var1, int var2, int var3) {
   }

   public void method9212(class519 var1, byte[] var2) {
      if (var1.field5127[var1.field5129] == 31 && var1.field5127[var1.field5129 + 1] == -117) {
         if (this.field5187 == null) {
            this.field5187 = new Inflater(true);
         }

         try {
            this.field5187.setInput(var1.field5127, 10 + var1.field5129, var1.field5127.length - (var1.field5129 + 10 + 8));
            this.field5187.inflate(var2);
         } catch (Exception var5) {
            this.field5187.reset();
            throw new RuntimeException("");
         }

         this.field5187.reset();
      } else {
         throw new RuntimeException("");
      }
   }
}
