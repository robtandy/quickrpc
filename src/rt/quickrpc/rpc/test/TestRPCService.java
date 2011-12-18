package rt.quickrpc.rpc.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import rt.quickrpc.rpc.RPCArgs;
import rt.quickrpc.rpc.RPCCommand;
import rt.quickrpc.rpc.RPCService;

public class TestRPCService {
	public int dp = 0;
	public int count = 0;
	
	public void sayHi (String name) {
		System.out.println ("Hi " + name);
	}
	
	public void serve () {
		RPCService rpc = null;
		try {
			rpc = new RPCService(3000);
			rpc.setDropPercent(dp);
			rpc.registerClass(HiArgs.class);
			rpc.registerClass(HiArgsResponse.class);
			
			rpc.register("HI", new RPCCommand() {
				
				public RPCArgs execute(RPCArgs args) {
					HiArgs h = (HiArgs) args;
					sayHi (h.name);
					return null;
				}
			});
			final HiArgsResponse res = new HiArgsResponse ();
			final TestRPCService me = this;
			rpc.register("HI2", new RPCCommand() {
				
				public RPCArgs execute(RPCArgs args) {
					HiArgs h = (HiArgs) args;
					sayHi ("[ Verision 2 ]" + h.name);
					me.count++;
					res.msg = "This is my response! " + me.count;
					return res;
				}
			});
		
		} catch (IOException e) {
			System.out.println (e);
			System.exit (1);
		}
		
		while (true) {
			try {
				Thread.sleep (30);
				rpc.upkeep ();
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println (e);
				System.exit (1);
			}
		}
	}
	
	public void client (InetSocketAddress remote) {
		RPCService rpc;
		try {
			rpc = new RPCService(0);
			rpc.setDropPercent(dp);

			rpc.registerClass(HiArgs.class);
			rpc.registerClass(HiArgsResponse.class);

			HiArgs hi = new HiArgs (); hi.name = "robert";
			for (int i = 0; i < 10; i++) {
				//rpc.remoteReliableCall ("HI", hi, remote);
				rpc.remoteReliableCallWithCallback ("HI2", hi, remote, new RPCCommand() {
					
					public RPCArgs execute(RPCArgs args) {
						HiArgsResponse h = (HiArgsResponse) args;
						System.out.println("server said, '" + h.msg);
						System.out.flush();
						return null;
					}
				});
			}
			while (!rpc.canExit()) {
				if (rpc.maxRequestAge() > 2000) {
					System.out.println ("Lost connection");
					System.exit (1);
				}
				rpc.upkeep();
				try {Thread.sleep (30);} catch (InterruptedException e) {}
			}
			System.out.println ("done");
		} catch (IOException e) {
			System.out.println (e);
			System.exit (1);
		}
	}
	
	public static void main(String[] args) {
	   Logger logger = Logger.getLogger("");
       logger.setLevel(Level.INFO);

//       Logger.getLogger("rt.quickrpc.dgram.ActiveConnection").setLevel (Level.ALL);
//       Logger.getLogger("rt.quickrpc.rpc.RPCService").setLevel (Level.FINER);
//       Logger.getLogger("rt.quickrpc.dgram.ActiveDatagram").setLevel (Level.FINER);

       for (Handler h : logger.getHandlers()) {
    	   h.setLevel(Level.ALL);
       }
       logger.finest("This is information message for testing ConsoleHandler");
	       
		
		if (args[0].equals("serv")) {
			TestRPCService t = new TestRPCService();
			t.dp = Integer.valueOf (args[1]).intValue();
			t.serve();

		} else if (args[0].equals("client")) {
			final InetSocketAddress addr = new InetSocketAddress (args[1], 
					Integer.valueOf(args[2]).intValue ());
			TestRPCService t = new TestRPCService();
			t.dp = Integer.valueOf (args[3]).intValue();

			t.client(addr);

		}
	}

}
