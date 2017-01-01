package awhina.birdbrain.birdsense;

import android.util.Log;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import java.net.Socket;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CopyOnWriteArrayList;

class AccelerometerListener implements SensorEventListener {
    private final int PORT = 1998;
    private final int ACCEPT_TIMEOUT = 100; // A tenth of a second

    private boolean streaming;
    private ServerSocket serverSocket;
    private CopyOnWriteArrayList<Socket> clients;

    AccelerometerListener() {
        streaming = false;
        clients = new CopyOnWriteArrayList<>();

        try {
            serverSocket = new ServerSocket(PORT);
            serverSocket.setSoTimeout(ACCEPT_TIMEOUT);
        } catch (IOException e) {
            Log.e(MainActivity.LOG_TAG, "IOException when creating socket: " + e.getMessage());
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    public void onSensorChanged(SensorEvent event) {
        Log.d(MainActivity.LOG_TAG, "SensorChanged: " + Float.toString(event.values[0]));
        for (Socket s : clients) {
            // We need space for three 32 bit (4 byte) floats, and one 64 bit (8 byte) long
            ByteBuffer byteValues = ByteBuffer.allocate(3 * 4 + 8);
            byteValues.putFloat(event.values[0]);
            byteValues.putFloat(event.values[1]);
            byteValues.putFloat(event.values[2]);
            byteValues.putLong(event.timestamp);

            try {
                s.getOutputStream().write(byteValues.array());
            } catch (IOException e) {
                // Assume this means that the client has disconnected
                Log.e(MainActivity.LOG_TAG,
                      "Socket disconnected: IOException when writing to socket");
                try {
                    s.close();
                } catch (IOException f) {
                    Log.e(MainActivity.LOG_TAG,
                          "Socket disconnected: IOException when closing socket");
                }

                clients.remove(s);
            }
        }
    }

    private void accepter() {
        while (streaming) {
            try {
                Socket client = serverSocket.accept();
                clients.add(client);
                Log.i(MainActivity.LOG_TAG,
                      "Socket accepted: " + client.getInetAddress().getHostAddress());
            } catch (SocketTimeoutException e) {
                // This will happen periodically so that the loop condition
                // is regularly reevaluated.
                continue;
            } catch (IOException e) {
                Log.e(MainActivity.LOG_TAG,
                      "IOException when accepting connection: " + e.getMessage());
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
                Log.e(MainActivity.LOG_TAG, "IOException when closing socket: " + e.getMessage());
            }
        }

        clients.clear();
    }
}
