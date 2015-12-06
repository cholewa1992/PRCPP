import java.util.Random;
import java.io.*;
import akka.actor.*;
// -- MESSAGES --------------------------------------------------
@SuppressWarnings("serial")
class StartTransferMessage implements Serializable {
    public final ActorRef bank;
    public final ActorRef from;
    public final ActorRef to;
    public final int times;
    public StartTransferMessage(ActorRef bank, ActorRef from, ActorRef to, int times){
        this.bank = bank;
        this.from = from;
        this.to = to;
        this.times = times;
    }
}

@SuppressWarnings("serial")
class TransferMessage implements Serializable { 
   public final ActorRef from; 
   public final ActorRef to;
   public final int amount;
   public TransferMessage(ActorRef from, ActorRef to, int amount){
       this.from = from;
       this.to = to;
       this.amount = amount;
   }
}

@SuppressWarnings("serial")
class DepositMessage implements Serializable {
    public final int amount;
    public DepositMessage(int amount){
        this.amount = amount;
    }
}

@SuppressWarnings("serial")
class PrintBalanceMessage implements Serializable {}
    
// -- ACTORS --------------------------------------------------
class AccountActor extends UntypedActor { 
    private int balance = 0;
    public void onReceive(Object o) throws Exception {
        if(o instanceof DepositMessage){
            DepositMessage msg = (DepositMessage) o;
            balance += msg.amount; 
        }
        else if(o instanceof PrintBalanceMessage){
            System.out.println("Balance is " + balance);
        }   
    }
}

class BankActor extends UntypedActor {
    public void onReceive(Object o) throws Exception {
        if(o instanceof TransferMessage){
            TransferMessage msg = (TransferMessage) o;
            msg.to.tell(new DepositMessage(+msg.amount),getSelf());
            msg.from.tell(new DepositMessage(-msg.amount),getSelf());
        }
    }
}

class ClerkActor extends UntypedActor {
    private Random rand;
    public void onReceive(Object o) throws Exception {
        if(o instanceof StartTransferMessage){
            StartTransferMessage msg = (StartTransferMessage) o;
            rand = new Random(37 * msg.hashCode());
            for(int i = 0; i < msg.times; i++){
                int amount = rand.nextInt() * 1000;
                msg.bank.tell(new TransferMessage(msg.from, msg.to, amount), ActorRef.noSender());
            }
        }
    }
}
// -- MAIN --------------------------------------------------
public class ABC { // Demo showing how things work:
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("ABCSystem");
        /* TODO (CREATE ACTORS AND SEND START MESSAGES) */


        final ActorRef account1 = system.actorOf(Props.create(AccountActor.class), "Account1");
        final ActorRef account2 = system.actorOf(Props.create(AccountActor.class), "Account2");

        account1.tell(new DepositMessage(5000), ActorRef.noSender());
        account2.tell(new DepositMessage(5000), ActorRef.noSender());

        final ActorRef bank1 = system.actorOf(Props.create(BankActor.class), "Bank1");
        final ActorRef bank2 = system.actorOf(Props.create(BankActor.class), "Bank2");

        final ActorRef clerk1 = system.actorOf(Props.create(ClerkActor.class), "Clerk1");
        final ActorRef clerk2 = system.actorOf(Props.create(ClerkActor.class), "Clerk2");

        clerk1.tell(new StartTransferMessage(bank1, account1, account2, 10000), ActorRef.noSender());
        clerk2.tell(new StartTransferMessage(bank2, account2, account1, 10000), ActorRef.noSender());

        try {
            System.out.println("Press return to inspect...");
            System.in.read();

            account1.tell(new PrintBalanceMessage(), ActorRef.noSender());
            account2.tell(new PrintBalanceMessage(), ActorRef.noSender());

            System.out.println("Press return to terminate...");
            System.in.read();
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            system.shutdown();
        }
    }
}
