package awhina.birdbrain.birdsense;

import android.os.Build;
import android.util.Log;
import android.util.Pair;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.hardware.Sensor;
import android.annotation.TargetApi;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import java.net.Socket;
import java.util.HashMap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.net.ServerSocket;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;

class AccelerometerListener implements SensorEventListener {
    private final int PORT = 1998;
    private final int ACCEPT_TIMEOUT = 100; // A tenth of a second

    private boolean streaming;
    private boolean nanosecondAccuracy;
    private ServerSocket serverSocket;
    // A map of clients and their connection times
    private HashMap<Socket, Pair<DatagramSocket, Long>> clients;

    AccelerometerListener() {
        streaming = false;
        clients = new HashMap<>();
        nanosecondAccuracy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;

        try {
            serverSocket = new ServerSocket(PORT);
            serverSocket.setSoTimeout(ACCEPT_TIMEOUT);
        } catch (IOException e) {
            Log.e(MainActivity.LOG_TAG, "IOException when creating socket: " + e.getMessage());
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onSensorChanged(SensorEvent event) {
        Log.d(MainActivity.LOG_TAG, "SensorChanged: " + Float.toString(event.values[0]));
        for (final Socket client : clients.keySet()) {
            final DatagramSocket udpSocket = clients.get(client).first;
            Long connectTime = clients.get(client).second;

            // We need space for four 32 bit (4 byte) floats
            ByteBuffer byteValues = ByteBuffer.allocate(4 * 4);
            byteValues.putFloat(event.values[0]);
            byteValues.putFloat(event.values[1]);
            byteValues.putFloat(event.values[2]);
            byteValues.putFloat(calculateTimestamp(event.timestamp, connectTime));

            AsyncTask<ByteBuffer, Void, Void> networkWriteAsyncTask = new AsyncTask<ByteBuffer, Void, Void>() {
                @Override
                protected Void doInBackground(ByteBuffer... params) {
                    try {
                        ByteBuffer values = params[0];
                        client.getOutputStream().write(values.array());
                        DatagramPacket packet = new DatagramPacket(values.array(),
                                                                   values.capacity(),
                                                                   client.getInetAddress(),
                                                                   PORT);
                        udpSocket.send(packet);
                    } catch (IOException e) {
                        // Assume this means that the client has disconnected
                        Log.e(MainActivity.LOG_TAG,
                                "Socket disconnected: " + e.getMessage());
                        try {
                            client.close();
                            udpSocket.close();
                        } catch (IOException f) {
                            Log.e(MainActivity.LOG_TAG,
                                  "Socket disconnected: IOException when closing socket");
                        }

                        clients.remove(client);
                    }

                    return null;
                }
            };

            networkWriteAsyncTask.execute(byteValues);
        }
    }

    private void accepter() {
        while (streaming) {
            try {
                DatagramSocket udpSocket = new DatagramSocket();
                Socket client = serverSocket.accept();
                //udpSocket.connect(client.getInetAddress(), (int)5e4);

                clients.put(client, Pair.create(udpSocket, getTime()));
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

    @TargetApi(17)
    private long getTime() {
        return nanosecondAccuracy ? SystemClock.elapsedRealtimeNanos() : SystemClock.elapsedRealtime();
    }

    private float calculateTimestamp(long timestampNanos, long connectTime) {
        float timestampSecs;

        if (nanosecondAccuracy) {
            long timestampDifferenceNanos = timestampNanos - connectTime;
            timestampSecs = timestampDifferenceNanos / 1e9f;
        } else { // If the accuracy is milliseconds
            long eventTimestampMillis = TimeUnit.MILLISECONDS.convert(timestampNanos, TimeUnit.NANOSECONDS);
            long timestampDifferenceMillis = eventTimestampMillis - connectTime;
            timestampSecs = timestampDifferenceMillis / 1e3f;
        }

        return timestampSecs;
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
        for (Socket client : clients.keySet()) {
            try {
                client.close();
            } catch (IOException e) {
                Log.e(MainActivity.LOG_TAG, "IOException when closing socket: " + e.getMessage());
            }
        }

        clients.clear();
    }
}
