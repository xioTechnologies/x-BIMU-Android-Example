package rug.xio.xbimudemo;

import android.util.Log;

class SerialDecoder implements SerialConsumer {
	
	public static final int MESSAGE_OK = 1;
	public static final int MESSAGE_Error = 2;
	public static final int PACKET_Quaternion = 3;
	public static final int PACKET_Sensors = 4;
	public static final int PACKET_Thermometer = 5;
	public static final int PACKET_Battery = 6;
	
	@Override
	public void reciveBytes(byte[] buffer, int bytes) {
		for (int i = 0; i < bytes; i++) { 
			ProcessNewByte(buffer[i]); 
		}
	}

	
	/// <summary>
    /// Process new byte in data stream to decode ASCII and binary packets and detect the OK string.
    /// </summary>
    /// <param name="newByte">
    /// Newest byte received within data stream.
    /// </param>
    public void ProcessNewByte(byte newByte) {
    	
        DecodeASCII((char)newByte);
        DecodeBinary(newByte);
        DetectOK(newByte);
    }

    /// <summary>
    /// Buffer for detecting "OK\r".
    /// </summary>
    private byte[] okBuf = new byte[256];

    /// <summary>
    /// Buffer index for detecting "OK\r".
    /// </summary>
    private int okBufIndex = 0;

    // unsigned byte to int 
    private int ToInt(byte b) { 
    	
    	return b & 0xFF;
    	
    }
    
    // int to unsigned byte with overflow
    private byte ToByte(int i) { 
    	
    	return (byte)Overflow(i);
    }
    
    // overflow an int as if it were an unsigned byte
    private int Overflow(int i) { 
    	int v = i; 
    	
    	while (v < 0) { v += 256; } 
    	
    	return v % 256;
    }
    
    /// <summary>
    /// Detect "OK/r" in data stream.
    /// </summary>
    /// <param name="newByte">
    /// Newest byte received within data stream.
    /// </param>
    private void DetectOK(byte newByte) {    	
    	
        okBuf[okBufIndex] = newByte;
        
        // increment the buffer index (with overflow)
        okBufIndex = Overflow(okBufIndex + 1); 
        
        if (okBuf[Overflow(okBufIndex - 3)] == 'O' &&
            okBuf[Overflow(okBufIndex - 2)] == 'K' &&
            okBuf[Overflow(okBufIndex - 1)] == '\r') {
        	
            OnOKReceived(3);
        }
        
        if (okBuf[Overflow(okBufIndex - 6)] == 'E' &&
            okBuf[Overflow(okBufIndex - 5)] == 'R' &&
            okBuf[Overflow(okBufIndex - 4)] == 'R' &&
            okBuf[Overflow(okBufIndex - 3)] == 'O' &&
            okBuf[Overflow(okBufIndex - 2)] == 'R' &&
            okBuf[Overflow(okBufIndex - 1)] == ' ') {
        	
            OnErrorReceived(6);
        }
    }

    /// <summary>
    /// Buffer used for decoding ASCII packets.
    /// </summary>
    private String asciiBuf = "";

    /// <summary>
    /// Decodes ASCII packets within data stream.
    /// </summary>
    /// <param name="newByte">
    /// Newest byte received within data stream.
    /// </param>
    private void DecodeASCII(char newChar)
    {
        if (newChar == '\r') {
            try {
                // Split string to comma separated variables
                String[] vars = asciiBuf.split(",");

                // Validate checksum (http://en.wikipedia.org/wiki/Longitudinal_redundancy_check)
                byte checksum = 0;
                
                for (int i = 0; i <= asciiBuf.lastIndexOf(','); i++) {   // checksum does not include checksum characters               
                    checksum ^= (byte)asciiBuf.charAt(i);
                }
                
                if (ToInt(checksum) != ToInt(Byte.parseByte(vars[vars.length - 1]))) {
                    throw new Exception();  // checksum failed
                }

                // Decode according to packet header
                String ident = vars[0];  
                
            	if (ident.equals("Q")) {
            		
                    OnQuaternionReceived(asciiBuf.length() + 1, 
                		new int[] { 
                			Integer.parseInt(vars[1]), /* quaternion elements 0, 1, 2, 3   */
                			Integer.parseInt(vars[2]), 
                			Integer.parseInt(vars[3]), 
                			Integer.parseInt(vars[4]),  
                			Integer.parseInt(vars[5])  	/* counter                          */
                	});
            	}
            	else if (ident.equals("S")) {
            		
            		OnSensorsReceived(asciiBuf.length() + 1, 
        				new int[] { 
	            			Integer.parseInt(vars[1]),  /* gyroscope, X, Y, Z axes      */
	            			Integer.parseInt(vars[2]), 
	            			Integer.parseInt(vars[3]),
	                                                      
	            			Integer.parseInt(vars[4]), /* acceleroemter, X, Y, Z axes  */
	            			Integer.parseInt(vars[5]), 
	            			Integer.parseInt(vars[6]), 
	                                                      
	            			Integer.parseInt(vars[7]), /* magnetometer, X, Y, Z axes   */
	            			Integer.parseInt(vars[8]), 
	            			Integer.parseInt(vars[9]), 
	                                                      
	            			Integer.parseInt(vars[10]) /* counter                      */
        			});                                         
            	}
            	else if (ident.equals("B")) {
            		
            		OnBatteryReceived(asciiBuf.length() + 1,
        				new int[] { 
	            			Integer.parseInt(vars[1]),	/* battery voltage */
	            			Integer.parseInt(vars[2])	/* counter          */ 
            		});  
            	}
            	else {
            		throw new Exception();
                }
            }
            catch (Exception ex) { }
            
            asciiBuf = "";
        }
        else
        {
            asciiBuf += newChar;
        }
    }


    /// <summary>
    /// Length of each binary packet type.
    /// </summary>
    private enum PacketLengths {
    	
        Quaterion (11),
        Sensor (21),
        Battery (5),
        Max (21);    /* maximum packet length of all packet types */
        
        PacketLengths(int value) {    	
        	mValue = value; 
        }
        
        private int mValue; 
        
        public int getValue() { return mValue; }
    }

    /// <summary>
    /// Buffer used for decoding binary packets.
    /// </summary>
    private byte[] binBuf = new byte[256];

    /// <summary>
    /// Buffer index for decoding binary packets.
    /// </summary>
    private int binBufIndex = 0;

    /// <summary>
    /// Flag indicating is binary decoding is in sync.
    /// </summary>
    private boolean inSync = false;

    /// <summary>
    /// Number of bytes received since assumed header byte.
    /// </summary>
    private int byteCount = 0;

    /// <summary>
    /// Decodes data packets within data stream.
    /// </summary>
    /// <param name="newByte">
    /// Newest byte received within data stream.
    /// </param>
    private void DecodeBinary(byte newByte) {
    	
        // Add new byte to buffer
        binBuf[binBufIndex] = newByte;

        binBufIndex = Overflow(binBufIndex + 1); 
        
        // Check if out of sync
        byteCount++;
        
        if (byteCount > (int)PacketLengths.Max.getValue()) {
        	
            byteCount = (int)PacketLengths.Max.getValue(); // prevent overflow
            inSync = false;
        }

        // Decode quaternion packet
        if (binBufIndex >= (byte)PacketLengths.Quaterion.getValue()) {
        	
            if ((inSync ? (char)binBuf[0] : (char)binBuf[binBufIndex - (byte)PacketLengths.Quaterion.getValue()]) == 'Q') {
            	
                if (CalcChecksum((byte)PacketLengths.Quaterion.getValue()) == 0) {
                	
                    OnQuaternionReceived((int)PacketLengths.Quaterion.getValue(), 
                    	new int[] { 
                			(short)((ToInt(binBuf[Overflow(binBufIndex - 10)]) << 8) | ToInt(binBuf[Overflow(binBufIndex - 9)])),    	/* quaternion element 0 */
                            (short)((ToInt(binBuf[Overflow(binBufIndex - 8)]) << 8) | ToInt(binBuf[Overflow(binBufIndex - 7)])),     	/* quaternion element 1 */
                            (short)((ToInt(binBuf[Overflow(binBufIndex - 6)]) << 8) | ToInt(binBuf[Overflow(binBufIndex - 5)])),     	/* quaternion element 2 */
                            (short)((ToInt(binBuf[Overflow(binBufIndex - 4)]) << 8) | ToInt(binBuf[Overflow(binBufIndex - 3)])),     	/* quaternion element 3 */
                            ToInt(binBuf[Overflow(binBufIndex - 2)])                                    		/* counter              */
                    }); 
                    
                    binBufIndex = 0;
                    byteCount = 0;
                    inSync = true;
                }
            }
        }

        // Decode sensor packet
        if (binBufIndex >= (byte)PacketLengths.Sensor.getValue()) {
        	
            if ((inSync ? (char)binBuf[0] : (char)binBuf[Overflow(binBufIndex - (byte)PacketLengths.Sensor.getValue())]) == 'S') {
            	
                if (CalcChecksum((byte)PacketLengths.Sensor.getValue()) == 0) {
                	
                    OnSensorsReceived((int)PacketLengths.Sensor.getValue(), 
                    	new int[] { 
                    		(short)((ToInt(binBuf[Overflow(binBufIndex - 20)]) << 8) | ToInt(binBuf[Overflow(binBufIndex - 19)])),  	/* gyroscope X axis     */
		                    (short)((ToInt(binBuf[Overflow(binBufIndex - 18)]) << 8) | ToInt(binBuf[Overflow(binBufIndex - 17)])),  	/* gyroscope Y axis     */
		                    (short)((ToInt(binBuf[Overflow(binBufIndex - 16)]) << 8) | ToInt(binBuf[Overflow(binBufIndex - 15)])),  	/* gyroscope Z axis     */
		                    (short)((ToInt(binBuf[Overflow(binBufIndex - 14)]) << 8) | ToInt(binBuf[Overflow(binBufIndex - 13)])),  	/* accelerometer X axis */
		                    (short)((ToInt(binBuf[Overflow(binBufIndex - 12)]) << 8) | ToInt(binBuf[Overflow(binBufIndex - 11)])),  	/* accelerometer Y axis */
		                    (short)((ToInt(binBuf[Overflow(binBufIndex - 10)]) << 8) | ToInt(binBuf[Overflow(binBufIndex - 9 )])),  	/* accelerometer Z axis */
		                    (short)((ToInt(binBuf[Overflow(binBufIndex - 8 )]) << 8) | ToInt(binBuf[Overflow(binBufIndex - 7 )])),   	/* magnetometer X axis  */
		                    (short)((ToInt(binBuf[Overflow(binBufIndex - 6 )]) << 8) | ToInt(binBuf[Overflow(binBufIndex - 5 )])),   	/* magnetometer Y axis  */
		                    (short)((ToInt(binBuf[Overflow(binBufIndex - 4 )]) << 8) | ToInt(binBuf[Overflow(binBufIndex - 3 )])),   	/* magnetometer Z axis  */
		                    ToInt(binBuf[Overflow(binBufIndex - 2)])                                     		/* counter              */
                    });    		
                    
                    binBufIndex = 0;
                    byteCount = 0;
                    inSync = true;
                }
            }
        }

        // Decode battery packet
        if (binBufIndex >= (byte)PacketLengths.Battery.getValue()) {
        	
            if ((inSync ? (char)binBuf[0] : (char)binBuf[binBufIndex - (byte)PacketLengths.Battery.getValue()]) == 'B') {
            	
                if (CalcChecksum((byte)PacketLengths.Battery.getValue()) == 0) {
                	
                    OnBatteryReceived((int)PacketLengths.Battery.getValue(), 
                    		new int[] { 
                    			(short)((ToInt(binBuf[Overflow(binBufIndex - 4 )]) << 8) | ToInt(binBuf[Overflow(binBufIndex - 3 )])) ,   	/* battery voltage  */
                    			ToInt(binBuf[Overflow(binBufIndex - 2)])                               										/* counter          */
                    });                                 		
                                		
                    binBufIndex = 0;
                    byteCount = 0;
                    inSync = true;
                }
            }
        }
    }

    /// <summary>
    /// Calculate LRC checksum.
    /// </summary>
    /// <param name="packetLength">
    /// Length of packet including checksum.
    /// </param>
    /// <returns>
    /// Zero if checksum passed.
    /// </returns>
    /// <remarks>
    /// http://en.wikipedia.org/wiki/Longitudinal_redundancy_check
    /// </remarks>
    private byte CalcChecksum(byte packetLength) {
    	
        int tempRxBufIndex = Overflow(binBufIndex - ToInt(packetLength));
        int checksum = 0;
        
        while (tempRxBufIndex != binBufIndex) {
            checksum ^= ToInt(binBuf[tempRxBufIndex]);
            
            tempRxBufIndex = Overflow(tempRxBufIndex + 1); 
        }
        
        return ToByte(checksum);
    }

    public SerialMessageHandler OKReceived;
    protected void OnOKReceived(int length) { if (OKReceived != null) OKReceived.onSerialMessage(MESSAGE_OK, length); }

    public SerialMessageHandler ErrorReceived;
    protected void OnErrorReceived(int length) { if (ErrorReceived != null) ErrorReceived.onSerialMessage(MESSAGE_Error, length); }

    public SerialPacketHandler QuaternionReceived;
    protected void OnQuaternionReceived(int length, int[] args) { if (QuaternionReceived != null) QuaternionReceived.onSerialPacket(PACKET_Quaternion, length, args); }
    
    public SerialPacketHandler SensorsReceived;
    protected void OnSensorsReceived(int length, int[] args) { if (SensorsReceived != null) SensorsReceived.onSerialPacket(PACKET_Sensors, length, args); }

    public SerialPacketHandler ThermometerReceived;
    protected void OnThermometerReceived(int length, int[] args) { if (ThermometerReceived != null) ThermometerReceived.onSerialPacket(PACKET_Thermometer, length, args); }

    public SerialPacketHandler BatteryReceived;
    protected void OnBatteryReceived(int length, int[] args) { if (BatteryReceived != null) BatteryReceived.onSerialPacket(PACKET_Battery, length, args); }
}
