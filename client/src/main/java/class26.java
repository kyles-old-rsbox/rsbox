import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class class26 {
   static class363 field133;
   ExecutorService field139 = Executors.newSingleThreadExecutor();
   Future field137;
   final class13 field136;
   final Buffer field135;

   public class26(Buffer var1, class13 var2) {
      this.field135 = var1;
      this.field136 = var2;
      this.method367();
   }

   public boolean method369() {
      return this.field137.isDone();
   }

   public void method362() {
      this.field139.shutdown();
      this.field139 = null;
   }

   public Buffer method363() {
      try {
         return (Buffer)this.field137.get();
      } catch (Exception var3) {
         return null;
      }
   }

   void method367() {
      this.field137 = this.field139.submit(new class25(this, this.field135, this.field136));
   }
}
