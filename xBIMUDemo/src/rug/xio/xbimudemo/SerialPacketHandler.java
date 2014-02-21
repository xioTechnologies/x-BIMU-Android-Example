package rug.xio.xbimudemo;

// Handler interface for serial packets
public interface SerialPacketHandler {

	void onSerialPacket(int type, int length, int[] args);

}
