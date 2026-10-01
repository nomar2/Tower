/*
 * Patched from usb-serial-android 0.1.0 (com.hoho.android.usbserial). Added
 * ArduPilot's pid.codes-allocated vendor/product ids (0x1209 / 0x5740,0x5741)
 * to getSupportedDevices() - some ArduPilot boards (e.g. Pixhawk 2.4.8 clones)
 * use this shared open-source VID instead of ArduPilot's own official VID
 * (0x2DAE, handled separately as a wildcard in UsbSerialProber since it isn't
 * shared with unrelated projects the way 0x1209 is).
 *
 * Modifications 2026 by Ramon Jose Moreno and Alejandro Moreno.
 */
package com.hoho.android.usbserial.driver;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.util.Log;
import android.util.SparseArray;
import com.hoho.android.usbserial.driver.CommonUsbSerialDriver;
import java.io.IOException;

public class CdcAcmSerialDriver
extends CommonUsbSerialDriver {
    private final String TAG = CdcAcmSerialDriver.class.getSimpleName();
    private UsbInterface mControlInterface;
    private UsbInterface mDataInterface;
    private UsbEndpoint mControlEndpoint;
    private UsbEndpoint mReadEndpoint;
    private UsbEndpoint mWriteEndpoint;
    private boolean mRts = false;
    private boolean mDtr = false;
    private static final int USB_RECIP_INTERFACE = 1;
    private static final int USB_RT_ACM = 33;
    private static final int SET_LINE_CODING = 32;
    private static final int GET_LINE_CODING = 33;
    private static final int SET_CONTROL_LINE_STATE = 34;
    private static final int SEND_BREAK = 35;

    public CdcAcmSerialDriver(UsbDevice device, UsbDeviceConnection connection) {
        super(device, connection);
    }

    @Override
    public void open() throws IOException {
        int interfaceCount = this.mDevice.getInterfaceCount();
        Log.d((String)this.TAG, (String)("claiming interfaces, count=" + interfaceCount));
        if (interfaceCount == 0) {
            throw new IOException("No available usb interfaces.");
        }
        Log.d((String)this.TAG, (String)"Claiming control interface.");
        this.mControlInterface = this.mDevice.getInterface(0);
        Log.d((String)this.TAG, (String)("Control iface=" + this.mControlInterface));
        if (!this.mConnection.claimInterface(this.mControlInterface, true)) {
            throw new IOException("Could not claim control interface.");
        }
        int controlEndpointCount = this.mControlInterface.getEndpointCount();
        if (controlEndpointCount == 0) {
            throw new IOException("No available control interface endpoints.");
        }
        this.mControlEndpoint = this.mControlInterface.getEndpoint(0);
        Log.d((String)this.TAG, (String)("Control endpoint direction: " + this.mControlEndpoint.getDirection()));
        Log.d((String)this.TAG, (String)"Claiming data interface.");
        this.mDataInterface = this.mDevice.getInterface(1);
        Log.d((String)this.TAG, (String)("data iface=" + this.mDataInterface));
        if (!this.mConnection.claimInterface(this.mDataInterface, true)) {
            throw new IOException("Could not claim data interface.");
        }
        int dataEndpointCount = this.mDataInterface.getEndpointCount();
        if (dataEndpointCount < 2) {
            throw new IOException("No available data interface endpoints.");
        }
        this.mReadEndpoint = this.mDataInterface.getEndpoint(1);
        Log.d((String)this.TAG, (String)("Read endpoint direction: " + this.mReadEndpoint.getDirection()));
        this.mWriteEndpoint = this.mDataInterface.getEndpoint(0);
        Log.d((String)this.TAG, (String)("Write endpoint direction: " + this.mWriteEndpoint.getDirection()));
    }

    private int sendAcmControlMessage(int request, int value, byte[] buf) {
        return this.mConnection.controlTransfer(33, request, value, 0, buf, buf != null ? buf.length : 0, 5000);
    }

    @Override
    public void close() throws IOException {
        this.mConnection.close();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public int read(byte[] dest, int timeoutMillis) throws IOException {
        int numBytesRead;
        Object object = this.mReadBufferLock;
        synchronized (object) {
            int readAmt = Math.min(dest.length, this.mReadBuffer.length);
            numBytesRead = this.mConnection.bulkTransfer(this.mReadEndpoint, this.mReadBuffer, readAmt, timeoutMillis);
            if (numBytesRead < 0) {
                return 0;
            }
            System.arraycopy(this.mReadBuffer, 0, dest, 0, numBytesRead);
        }
        return numBytesRead;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public int write(byte[] src, int timeoutMillis) throws IOException {
        int offset;
        int amtWritten;
        for (offset = 0; offset < src.length; offset += amtWritten) {
            int writeLength;
            Object object = this.mWriteBufferLock;
            synchronized (object) {
                byte[] writeBuffer;
                writeLength = Math.min(src.length - offset, this.mWriteBuffer.length);
                if (offset == 0) {
                    writeBuffer = src;
                } else {
                    System.arraycopy(src, offset, this.mWriteBuffer, 0, writeLength);
                    writeBuffer = this.mWriteBuffer;
                }
                amtWritten = this.mConnection.bulkTransfer(this.mWriteEndpoint, writeBuffer, writeLength, timeoutMillis);
            }
            if (amtWritten > 0) continue;
            throw new IOException("Error writing " + writeLength + " bytes at offset " + offset + " length=" + src.length);
        }
        return offset;
    }

    @Override
    public void setParameters(int baudRate, int dataBits, int stopBits, int parity) {
        byte parityBitesByte;
        byte stopBitsByte;
        switch (stopBits) {
            case 1: {
                stopBitsByte = 0;
                break;
            }
            case 3: {
                stopBitsByte = 1;
                break;
            }
            case 2: {
                stopBitsByte = 2;
                break;
            }
            default: {
                throw new IllegalArgumentException("Bad value for stopBits: " + stopBits);
            }
        }
        switch (parity) {
            case 0: {
                parityBitesByte = 0;
                break;
            }
            case 1: {
                parityBitesByte = 1;
                break;
            }
            case 2: {
                parityBitesByte = 2;
                break;
            }
            case 3: {
                parityBitesByte = 3;
                break;
            }
            case 4: {
                parityBitesByte = 4;
                break;
            }
            default: {
                throw new IllegalArgumentException("Bad value for parity: " + parity);
            }
        }
        byte[] msg = new byte[]{(byte)(baudRate & 0xFF), (byte)(baudRate >> 8 & 0xFF), (byte)(baudRate >> 16 & 0xFF), (byte)(baudRate >> 24 & 0xFF), stopBitsByte, parityBitesByte, (byte)dataBits};
        this.sendAcmControlMessage(32, 0, msg);
    }

    @Override
    public boolean getCD() throws IOException {
        return false;
    }

    @Override
    public boolean getCTS() throws IOException {
        return false;
    }

    @Override
    public boolean getDSR() throws IOException {
        return false;
    }

    @Override
    public boolean getDTR() throws IOException {
        return this.mDtr;
    }

    @Override
    public void setDTR(boolean value) throws IOException {
        this.mDtr = value;
        this.setDtrRts();
    }

    @Override
    public boolean getRI() throws IOException {
        return false;
    }

    @Override
    public boolean getRTS() throws IOException {
        return this.mRts;
    }

    @Override
    public void setRTS(boolean value) throws IOException {
        this.mRts = value;
        this.setDtrRts();
    }

    private void setDtrRts() {
        int value = (this.mRts ? 2 : 0) | (this.mDtr ? 1 : 0);
        this.sendAcmControlMessage(34, value, null);
    }

    public static SparseArray<int[]> getSupportedDevices() {
        SparseArray supportedDevices = new SparseArray(7);
        supportedDevices.put(9025, (Object)new int[]{1, 67, 16, 66, 59, 68, 63, 68, 32822});
        supportedDevices.put(5824, (Object)new int[]{1155});
        supportedDevices.put(1003, (Object)new int[]{8260});
        supportedDevices.put(7855, (Object)new int[]{4});
        supportedDevices.put(9900, (Object)new int[]{1155, 17});
        supportedDevices.put(4617, (Object)new int[]{22336, 22337}); // 0x1209: 0x5740, 0x5741 (ArduPilot pid.codes allocation)
        return supportedDevices;
    }
}

