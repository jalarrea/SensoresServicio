package com.example.jorge.blue.servicios;

/**
 * Created by JORGE on 31/5/18.
 */

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;


import com.example.jorge.blue.R;
import com.example.jorge.blue.entidades.ConexionSQLiteHelper;
import com.example.jorge.blue.utils.Utilities;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import static com.example.jorge.blue.utils.Identifiers.BT_address;

public class ServiceReceiver extends Service{

    public static PowerManager.WakeLock wakeLock;

    Context thisContext = this;
    Handler bluetoothIn;
    int c = 20;
    final int handlerState = 0;
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder DataStringIN = new StringBuilder();
    private ConnectedThread MyConexionBT;
    private ArrayAdapter mPairedDevicesArrayAdapter;
    // Identificador unico de servicio - SPP UUID
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    ConexionSQLiteHelper conn = new ConexionSQLiteHelper(this, "medicion", null, 1);

    //-------------------------------------------

    @Override
    public void onCreate(){
        Log.d("hi", "Servicio Creado");
        //ListView IdLista = new ListView(thisContext);
        btAdapter= BluetoothAdapter.getDefaultAdapter();
        mPairedDevicesArrayAdapter = new ArrayAdapter(this, R.layout.nombres_dispositivos);
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        //IdLista.setAdapter(mPairedDevicesArrayAdapter);
        if (pairedDevices.size() > 0)
        {
            for (BluetoothDevice device : pairedDevices) { //EN CASO DE ERROR LEER LA ANTERIOR EXPLICACION
                mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                Log.d("BTA1", device.getAddress());
                BT_address = device.getAddress();

            }
        }
        //String info = IdLista.getAdapter().toString();
        //Log.d("adap", info);
        //String address = info.substring(info.length() - 17);
        //Log.d("BTA2", address);



        //MANTENER ENCENDIDO EL CPU DEL CELULAR AL APAGAR LA PANTALLA
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakelockTag");
        wakeLock.acquire();



    }

    @Override
    public int onStartCommand(Intent intent, int flag, int idProcess)
    {
        //Intent intent = getIntent();
        //Consigue la direccion MAC desde DeviceListActivity via EXTRA
        //address = intent.getStringExtra(DispositivosBT.EXTRA_DEVICE_ADDRESS);//<-<- PARTE A MODIFICAR >->->
        //Setea la direccion MAC
        //Log.d("BT", "address obtenida");

        //Setea la direccion MAC

        bluetoothIn = new Handler() {
            public void handleMessage(android.os.Message msg) {
                Log.d("BT", "Handler creado");
                if (msg.what == handlerState) {
                    String readMessage = (String) msg.obj;
                    DataStringIN.append(readMessage);

                    int endOfLineIndex = DataStringIN.indexOf("#");

                    if (endOfLineIndex > 0) {
                        String medicion = DataStringIN.substring(0, endOfLineIndex);
                        String[] parts = medicion.split(",");
                        String sensorId = parts[0];
                        String type = parts[1];
                        String value = parts[2];
                        String unit = parts[3];
                        String location = parts[4];
                        Long tsLong = System.currentTimeMillis()/1000;
                        String ts = tsLong.toString();
                        registrarMedicion(ts, type, value, unit, location, sensorId);
                        DataStringIN.delete(0, DataStringIN.length());
                    }
                }
            }
        };

        btAdapter = BluetoothAdapter.getDefaultAdapter(); // get Bluetooth adapter

        BluetoothDevice device = btAdapter.getRemoteDevice(BT_address);


        while (true) {
            try {
                btSocket = createBluetoothSocket(device);
                Log.d("BT", "Socket creado");
            } catch (IOException e) {
                Toast.makeText(getBaseContext(), "La creacción del Socket fallo", Toast.LENGTH_LONG).show();
            }
            // Establece la conexión con el socket Bluetooth.
            try {
                btSocket.connect();
                Log.d("BT", "Socket conectado");
                break;


            } catch (IOException e) {
                try {
                    btSocket.close();
                    Log.d("BT", "Socket cerrado");
                } catch (IOException e2) {
                }
            }
        }
        MyConexionBT = new ConnectedThread(btSocket);
        MyConexionBT.start();


        return START_STICKY;
    }

    @Override
    public void onDestroy()
    {
        if (btSocket!=null)
        {
            try {btSocket.close();}
            catch (IOException e)
            { Toast.makeText(getBaseContext(), "Error", Toast.LENGTH_SHORT).show();;}
        }
        wakeLock.release();

    }



    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException
    {
        //crea un conexion de salida segura para el dispositivo
        //usando el servicio UUID
        return device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }



    //Crea la clase que permite crear el evento de conexion
    private class ConnectedThread extends Thread
    {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket)
        {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try
            {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run()
        {
            byte[] buffer = new byte[256];
            int bytes;

            // Se mantiene en modo escucha para determinar el ingreso de datos
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    String readMessage = new String(buffer, 0, bytes);
                    // Envia los datos obtenidos hacia el evento via handler
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }
        //Envio de trama, esto sirve para enviar desde el celular al BT, por ahora no se la usa
        public void write(String input)
        {
            try {
                mmOutStream.write(input.getBytes());
            }
            catch (IOException e)
            {
                //si no es posible enviar datos se cierra la conexión
                Toast.makeText(getBaseContext(), "La Conexión fallo", Toast.LENGTH_LONG).show();
            }
        }

    }


    public void registrarMedicion(String ts, String type, String value, String unit, String location, String id)
    {
        SQLiteDatabase db = conn.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Utilities.CAMPO_TIMESTAMP, ts);
        values.put(Utilities.CAMPO_TYPE, type);
        values.put(Utilities.CAMPO_VALUE, value);
        values.put(Utilities.CAMPO_UNIT, unit);
        values.put(Utilities.CAMPO_LOCATION, location);
        values.put(Utilities.CAMPO_SENSORID, id);

        long result = db.insert(Utilities.TABLA_MEDICION, Utilities.CAMPO_SENSORID, values);
        Log.d("DB", "ingresado el timestamp, dato, unit:" + ts +","+ value + "," + unit);
        db.close();

    }

}
