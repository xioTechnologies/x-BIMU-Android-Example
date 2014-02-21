package rug.xio.xbimudemo;

// Handler interface for serial messages
public interface SerialMessageHandler {

	void onSerialMessage(int type, int length);

}
