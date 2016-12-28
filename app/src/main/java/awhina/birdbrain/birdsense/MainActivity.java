package awhina.birdbrain.birdsense;

import android.os.Bundle;
import android.view.View;
import android.graphics.Color;
import android.app.AlertDialog;
import android.content.Context;
import android.hardware.Sensor;
import android.net.NetworkInfo;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.net.wifi.WifiManager;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.support.v7.app.AppCompatActivity;

import java.nio.ByteOrder;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {
    SensorManager sm;
    Sensor linearAccelerometer;
    AccelerometerListener listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check that the device has an accelerometer
        sm = (SensorManager)this.getSystemService(SENSOR_SERVICE);
        linearAccelerometer = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        // If not, show error dialog and disable button
        if (linearAccelerometer == null) {
            ToggleButton button = (ToggleButton) findViewById(R.id.toggleButton);
            button.setEnabled(false);
            createErrorDialog("No accelerometer available, cannot start sensor service");
        } else {
            listener = new AccelerometerListener(this);
        }
    }

    public void onToggleButtonClicked(View view) {
        ToggleButton button = (ToggleButton) view;
        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo nm = cm.getActiveNetworkInfo();

        // Check that wifi is available
        if (nm == null || !nm.isConnected() || nm.getType() != ConnectivityManager.TYPE_WIFI) {
            createErrorDialog("WiFi not available, cannot start sensor service.");
            button.setChecked(false);
            return;
        }

        TextView ipTextview = (TextView)findViewById(R.id.ip_textView);
        if (button.isChecked()) {
            // Get IP address, if possible
            String address = getIpAddress();
            if (address == null) {
                createErrorDialog("Could not get IP address");
                button.setChecked(false);
            }

            ipTextview.setText(address);
            ipTextview.setTextColor(Color.BLACK);

            // Start streaming data
            listener.start();
            sm.registerListener(listener, linearAccelerometer, (int)2e6);
        } else {
            listener.stop();
            sm.unregisterListener(listener);
            ipTextview.setText(R.string.ip_address_label);
            ipTextview.setTextColor(Color.LTGRAY);
        }
    }

    private void createErrorDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert).show();
    }

    private String getIpAddress() {
        WifiManager wifiManager = (WifiManager) this.getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        // If necessary, convert to little endian format
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            ipAddressString = null;
        }

        return ipAddressString;
    }
}
