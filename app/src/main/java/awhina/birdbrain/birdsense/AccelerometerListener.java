package awhina.birdbrain.birdsense;

import android.widget.Toast;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import java.net.Socket;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.net.ServerSocket;
import java.util.concurrent.CopyOnWriteArrayList;

class AccelerometerListener implements SensorEventListener {
    private final int PORT = 1998;
    private final int ACCEPT_TIMEOUT = 100; // A tenth of a second
    private final int SENSOR_ARRAY_SIZE = (3 * Float.SIZE) / Byte.SIZE;

    private boolean streaming;
    private MainActivity parent;
    private ServerSocket serverSocket;
    private CopyOnWriteArrayList<Socket> clients;

    AccelerometerListener(MainActivity parent) {
        streaming = false;
        this.parent = parent;
        clients = new CopyOnWriteArrayList<>();

        try {
            serverSocket = new ServerSocket(PORT);
            serverSocket.setSoTimeout(ACCEPT_TIMEOUT);
            serverSocket.setReceiveBufferSize(SENSOR_ARRAY_SIZE);
        } catch (IOException e) {
            Toast.makeText(parent, "IOException when creating socket",
                           Toast.LENGTH_LONG).show();
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    public void onSensorChanged(SensorEvent event) {
        Toast.makeText(parent, Float.toString(event.values[2]), Toast.LENGTH_SHORT).show();
        for (Socket s : clients) {
            ByteBuffer byteValues = ByteBuffer.allocate(SENSOR_ARRAY_SIZE);
            byteValues.putFloat(event.values[0]);
            byteValues.putFloat(event.values[1]);
            byteValues.putFloat(event.values[2]);

            try {
                s.getOutputStream().write(byteValues.array());
            } catch (IOException e) {
                Toast.makeText(parent, "IOException when writing to socket",
                               Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void accepter() {
        while (streaming) {
            try {
                Socket client = serverSocket.accept();
                clients.add(client);
            } catch (SocketTimeoutException e) {
                // This will happen periodically so that the loop condition
                // is regularly reevaluated.
                continue;
            } catch (IOException e) {
                Toast.makeText(parent, "IOException when accepting connection",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    void start() {
        streaming = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                accepter();
            }
        }).start();
    }

    void stop() {
        streaming = false;
        for (Socket client : clients) {
            try {
                client.close();
            } catch (IOException e) {
                Toast.makeText(parent, "IOException when closing socket",
                               Toast.LENGTH_SHORT).show();
            }
        }

        clients.clear();
    }
}
