import java.util.concurrent.ThreadFactory;

final class class198 implements ThreadFactory {
   public Thread newThread(Runnable var1) {
      return new Thread(var1, "OSRS Maya Anim Load");
   }
}
