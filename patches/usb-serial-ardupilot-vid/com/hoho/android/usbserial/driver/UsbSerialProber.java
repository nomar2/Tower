/*
 * Patched from usb-serial-android 0.1.0 (com.hoho.android.usbserial, the
 * "usb-serial-for-android" library). CDC_ACM_SERIAL's probe() now also
 * accepts ArduPilot's official USB vendor id (0x2DAE / 11694) regardless of
 * product id, since modern ArduPilot/ChibiOS boards implement native USB
 * CDC-ACM and use many different product ids across board variants and
 * bootloader/app state - the static getSupportedDevices() table can't list
 * them all. Scoped to the CDC_ACM prober only, so this vendor id isn't also
 * (wrongly) probed as FTDI/CP210x/Prolific.
 *
 * Modifications 2026 by Ramon Jose Moreno and Alejandro Moreno.
 */
package com.hoho.android.usbserial.driver;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.SparseArray;
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.Cp2102SerialDriver;
import com.hoho.android.usbserial.driver.FtdiSerialDriver;
import com.hoho.android.usbserial.driver.ProlificSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public enum UsbSerialProber {
    FTDI_SERIAL{

        @Override
        public List<UsbSerialDriver> probe(UsbManager manager, UsbDevice usbDevice) {
            UsbDeviceConnection connection;
            if (!UsbSerialProber.testIfSupported(usbDevice, (SparseArray<int[]>)FtdiSerialDriver.getSupportedDevices())) {
                return Collections.emptyList();
            }
            UsbDeviceConnection usbDeviceConnection = connection = manager.hasPermission(usbDevice) ? manager.openDevice(usbDevice) : null;
            if (connection == null) {
                return Collections.emptyList();
            }
            FtdiSerialDriver driver = new FtdiSerialDriver(usbDevice, connection);
            return Collections.singletonList(driver);
        }

        @Override
        public SparseArray<int[]> getSupportedDevices() {
            return FtdiSerialDriver.getSupportedDevices();
        }
    }
    ,
    CDC_ACM_SERIAL{

        @Override
        public List<UsbSerialDriver> probe(UsbManager manager, UsbDevice usbDevice) {
            UsbDeviceConnection connection;
            boolean isArduPilotVendor = usbDevice.getVendorId() == 11694;
            if (!isArduPilotVendor && !UsbSerialProber.testIfSupported(usbDevice, (SparseArray<int[]>)CdcAcmSerialDriver.getSupportedDevices())) {
                return Collections.emptyList();
            }
            UsbDeviceConnection usbDeviceConnection = connection = manager.hasPermission(usbDevice) ? manager.openDevice(usbDevice) : null;
            if (connection == null) {
                return Collections.emptyList();
            }
            CdcAcmSerialDriver driver = new CdcAcmSerialDriver(usbDevice, connection);
            return Collections.singletonList(driver);
        }

        @Override
        public SparseArray<int[]> getSupportedDevices() {
            return CdcAcmSerialDriver.getSupportedDevices();
        }
    }
    ,
    SILAB_SERIAL{

        @Override
        public List<UsbSerialDriver> probe(UsbManager manager, UsbDevice usbDevice) {
            UsbDeviceConnection connection;
            if (!UsbSerialProber.testIfSupported(usbDevice, (SparseArray<int[]>)Cp2102SerialDriver.getSupportedDevices())) {
                return Collections.emptyList();
            }
            UsbDeviceConnection usbDeviceConnection = connection = manager.hasPermission(usbDevice) ? manager.openDevice(usbDevice) : null;
            if (connection == null) {
                return Collections.emptyList();
            }
            Cp2102SerialDriver driver = new Cp2102SerialDriver(usbDevice, connection);
            return Collections.singletonList(driver);
        }

        @Override
        public SparseArray<int[]> getSupportedDevices() {
            return Cp2102SerialDriver.getSupportedDevices();
        }
    }
    ,
    PROLIFIC_SERIAL{

        @Override
        public List<UsbSerialDriver> probe(UsbManager manager, UsbDevice usbDevice) {
            UsbDeviceConnection connection;
            if (!UsbSerialProber.testIfSupported(usbDevice, (SparseArray<int[]>)ProlificSerialDriver.getSupportedDevices())) {
                return Collections.emptyList();
            }
            UsbDeviceConnection usbDeviceConnection = connection = manager.hasPermission(usbDevice) ? manager.openDevice(usbDevice) : null;
            if (connection == null) {
                return Collections.emptyList();
            }
            ProlificSerialDriver driver = new ProlificSerialDriver(usbDevice, connection);
            return Collections.singletonList(driver);
        }

        @Override
        public SparseArray<int[]> getSupportedDevices() {
            return ProlificSerialDriver.getSupportedDevices();
        }
    };


    protected abstract List<UsbSerialDriver> probe(UsbManager var1, UsbDevice var2);

    protected abstract SparseArray<int[]> getSupportedDevices();

    public static UsbSerialDriver findFirstDevice(UsbManager usbManager) {
        for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
            for (UsbSerialProber prober : UsbSerialProber.values()) {
                List<UsbSerialDriver> probedDevices = prober.probe(usbManager, usbDevice);
                if (probedDevices.isEmpty()) continue;
                return probedDevices.get(0);
            }
        }
        return null;
    }

    public static UsbSerialDriver openUsbDevice(UsbManager usbManager, UsbDevice device) {
        for (UsbSerialProber prober : UsbSerialProber.values()) {
            List<UsbSerialDriver> probedDevices = prober.probe(usbManager, device);
            if (probedDevices.isEmpty()) continue;
            return probedDevices.get(0);
        }
        return null;
    }

    public static List<UsbDevice> getAvailableSupportedDevices(UsbManager usbManager) {
        ArrayList<UsbDevice> supportedDevices = new ArrayList<UsbDevice>();
        block0: for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
            if (usbDevice.getVendorId() == 11694) {
                supportedDevices.add(usbDevice);
                continue;
            }
            for (UsbSerialProber prober : UsbSerialProber.values()) {
                if (!UsbSerialProber.testIfSupported(usbDevice, prober.getSupportedDevices())) continue;
                supportedDevices.add(usbDevice);
                continue block0;
            }
        }
        return supportedDevices;
    }

    public static List<UsbSerialDriver> findAllDevices(UsbManager usbManager) {
        ArrayList<UsbSerialDriver> result = new ArrayList<UsbSerialDriver>();
        for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
            result.addAll(UsbSerialProber.probeSingleDevice(usbManager, usbDevice));
        }
        return result;
    }

    public static List<UsbSerialDriver> probeSingleDevice(UsbManager usbManager, UsbDevice usbDevice) {
        ArrayList<UsbSerialDriver> result = new ArrayList<UsbSerialDriver>();
        for (UsbSerialProber prober : UsbSerialProber.values()) {
            List<UsbSerialDriver> probedDevices = prober.probe(usbManager, usbDevice);
            result.addAll(probedDevices);
        }
        return result;
    }

    @Deprecated
    public static UsbSerialDriver acquire(UsbManager usbManager) {
        return UsbSerialProber.findFirstDevice(usbManager);
    }

    @Deprecated
    public static UsbSerialDriver acquire(UsbManager usbManager, UsbDevice usbDevice) {
        List<UsbSerialDriver> probedDevices = UsbSerialProber.probeSingleDevice(usbManager, usbDevice);
        if (!probedDevices.isEmpty()) {
            return probedDevices.get(0);
        }
        return null;
    }

    private static boolean testIfSupported(UsbDevice usbDevice, SparseArray<int[]> supportedDevices) {
        int[] supportedProducts = (int[])supportedDevices.get(usbDevice.getVendorId());
        if (supportedProducts == null) {
            return false;
        }
        int productId = usbDevice.getProductId();
        for (int supportedProductId : supportedProducts) {
            if (productId != supportedProductId) continue;
            return true;
        }
        return false;
    }
}

