package rt.quickrpc.dgram;

public interface ReliabilityListener {
	public void retransmitted (ReliableDatagram.Bundle b);
	public void reliableAcked (ReliableDatagram.Bundle b);
}
