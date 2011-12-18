package rt.quickrpc.dgram;

import java.net.InetSocketAddress;
import java.util.Vector;

public class ReliableActiveConnection extends ActiveConnection {
	Vector <ReliableDatagram.Bundle> reliables;
	
	ReliableActiveConnection(InetSocketAddress sa) {
		super(sa);
		reliables = new Vector <ReliableDatagram.Bundle> ();
		// TODO Auto-generated constructor stub
	}
	
	

}
