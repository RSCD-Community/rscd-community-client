public class nat extends Methods {

    private String serverMessage = null;
    private boolean MoveNow;

   public nat(mudclient mc){super(mc);}
  
    public void MainBody(String Args[]) {
        AutoLogin(true);
        Display("@Gre@EnzoNat for nats in house next to cake stall");
        Display("@Cya@fixed by pked pker");
        while(Running()) {
            AtObject2(539,1547);
            Wait(Rand(400,600));
            SleepIfAt(95);
             if(MoveNow) {
                ForceWalkToWait(539,1545);
                Wait(Rand(1000,2000));
                ForceWalkToWait(539,1546);
                MoveNow = false;
            }
        }
    }
    public void OnServerMessage(String message) {
        serverMessage = message;
        if(IsInStr(message,"standing")){
            MoveNow = true;

        }
    }
}