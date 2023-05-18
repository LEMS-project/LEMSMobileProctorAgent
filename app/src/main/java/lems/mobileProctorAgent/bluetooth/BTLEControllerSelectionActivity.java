package lems.mobileProctorAgent.bluetooth;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import lems.mobileProctorAgent.AppConstants;
import lems.mobileProctorAgent.R;

public class BTLEControllerSelectionActivity extends AppCompatActivity {
    private final static String LOG_TAG = BTLEControllerSelectionActivity.class.getName();
    private BTDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_btlecontroller_selection);

        Log.w(LOG_TAG, "Create BTLE Scan");
        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Register for broadcasts when a device is discovered.
        registerReceiver(btActionFoundReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            this.btRequestEnableLauncher.launch(enableBtIntent);
        }

        // Initializes list view adapter.
        this.mLeDeviceListAdapter = new BTDeviceListAdapter(this::onDeviceSelection);
        final RecyclerView recylerView = (RecyclerView) this.findViewById(R.id.btDeviceListContainer);
        recylerView.setAdapter(this.mLeDeviceListAdapter);
        recylerView.setLayoutManager(new LinearLayoutManager(this));

        this.scanLeDevice(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    private void onDeviceSelection(BluetoothDevice device) {
        Log.i(LOG_TAG, "Device selected: " + device.getName() + " : " + device.getAddress());
        device.connectGatt(this, false, this.bluetoothGattCallback);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
        }
        return true;
    }

    private void scanLeDevice(final boolean enable) {
        if (!enable) {
            this.mScanning = false;
            this.mBluetoothAdapter.cancelDiscovery();
            invalidateOptionsMenu();
            return;
        }
        // Stops scanning after a pre-defined scan period.
        mHandler.postDelayed(() -> scanLeDevice(false), SCAN_PERIOD);

        // Start discovery
        mScanning = true;
        this.addPairedDevices();
        mBluetoothAdapter.startDiscovery();
        invalidateOptionsMenu();
    }

    private void addPairedDevices() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device: pairedDevices) {
            this.mLeDeviceListAdapter.addDevice(device, true);
        }
    }

    private final ActivityResultLauncher<Intent> btRequestEnableLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), (res) -> {
                if (res.getResultCode() == Activity.RESULT_CANCELED) {
                    finish();
                    return;
                }
            });

    private final BroadcastReceiver btActionFoundReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //final String deviceName = device.getName();
                //final String deviceHardwareAddress = device.getAddress(); // MAC address
                Log.i(LOG_TAG, "DEVICE FOUND! " + device.getName() + " | " + device.getAddress());
                mLeDeviceListAdapter.addDevice(device, false);
            }
        }
    };

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
                Log.i(LOG_TAG, "GATT Connection change for connected");
                // Attempts to discover services after successful connection.
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                Log.i(LOG_TAG, "GATT Connection change for disconnected");
                runOnUiThread(() -> {
                    mLeDeviceListAdapter.addDeviceInfoError(gatt.getDevice());
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(LOG_TAG, "Services discovered for bt " + gatt.getDevice().getAddress());
                int nbServices = gatt.getServices().size();
                BluetoothGattService svc = gatt.getService(UUID.fromString(AppConstants.BT_GATT_SERVICE));
                if (svc != null) {
                    Log.i(LOG_TAG, "MAAAATCH Service: " + svc.getUuid().toString());
                    onDeviceFound(gatt.getDevice().getAddress());
                    /*svc.getCharacteristics().forEach(charac -> {
                        Log.i(LOG_TAG, "  Charac: " + charac.getUuid().toString());
                    });*/
                } else {
                    runOnUiThread(() -> {
                        mLeDeviceListAdapter.addDeviceInfo(gatt.getDevice(), nbServices, svc != null);
                    });
                }
            } else {
                Log.w(LOG_TAG, "onServicesDiscovered received: " + status);
                runOnUiThread(() -> {
                    mLeDeviceListAdapter.addDeviceInfoError(gatt.getDevice());
                });
            }
        }

    };

    private void onDeviceFound(String deviceMacAddress) {
        this.setResult(RESULT_OK, BTLEControllerContract.createReturnedIntent(deviceMacAddress));
        this.finish();
    }

    private static class BTDeviceListAdapter extends RecyclerView.Adapter<BTDeviceListAdapter.ViewHolder> {
        private final ArrayList<BTDeviceInfo> btDevices = new ArrayList<>();
        private final Consumer<BluetoothDevice> selectDevice;

        public BTDeviceListAdapter(Consumer<BluetoothDevice> selectDevice) {
            this.selectDevice = selectDevice;
        }

        public void addDevice(BluetoothDevice device, boolean paired) {
            BTDeviceInfo info = new BTDeviceInfo(device, paired);
            if (!this.btDevices.contains(info)) {
                this.btDevices.add(info);
                this.notifyItemInserted(this.btDevices.size() - 1);
            }
        }

        public void addDeviceInfoError(BluetoothDevice device) {
            final int idx = this.findDeviceInfoIdxFromDevice(device);
            if (idx >= 0) {
                final BTDeviceInfo info = this.btDevices.get(idx);
                info.infoRetrieved = true;
                info.infoError = true;
                this.notifyItemChanged(idx);
            }
        }

        public void addDeviceInfo(BluetoothDevice device, int nbServices, boolean serviceMatch) {
            final int idx = this.findDeviceInfoIdxFromDevice(device);
            if (idx >= 0) {
                final BTDeviceInfo info = this.btDevices.get(idx);
                info.infoRetrieved = true;
                info.infoError = false;
                info.nbServices = nbServices;
                info.serviceMatch = serviceMatch;
                this.notifyItemChanged(idx);
            }
        }

        public void clear() {
            this.btDevices.clear();
            this.notifyDataSetChanged();
        }

        private int findDeviceInfoIdxFromDevice(BluetoothDevice device) {
            int i = 0;
            for (BTDeviceInfo di : this.btDevices) {
                if (di.device.equals(device)) {
                    return i;
                }
                i += 1;
            }
            return -1;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            //R.layout.listitem_btle_device
            // Create a new view, which defines the UI of the list item
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.listitem_btle_device, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final BTDeviceInfo deviceInfo = this.btDevices.get(position);
            holder.setDeviceInfo(deviceInfo);
            holder.setClickConsumer((h) -> this.selectDevice.accept(deviceInfo.device));
        }

        @Override
        public int getItemCount() {
            return this.btDevices.size();
        }

        private static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView deviceAdress;
            private final TextView deviceName;
            private final TextView serviceInfo;
            private Consumer<ViewHolder> clickConsumer;

            public ViewHolder(View view) {
                super(view);
                this.deviceAdress = (TextView) view.findViewById(R.id.device_address);
                this.deviceName = (TextView) view.findViewById(R.id.device_name);
                this.serviceInfo = (TextView) view.findViewById(R.id.services_info);
                view.setClickable(true);
                view.setOnClickListener((e) -> this.onViewClick());
            }

            public void setDeviceInfo(BTDeviceInfo deviceInfo) {
                // base info
                this.deviceAdress.setText(deviceInfo.device.getAddress());
                String deviceName = deviceInfo.device.getName();
                if (deviceName == null || deviceName.isEmpty()) {
                    deviceName = "Unknown device";
                }
                if (deviceInfo.paired) {
                    deviceName += " (*)";
                }
                this.deviceName.setText(deviceName);
                // services info
                if (deviceInfo.infoRetrieved) {
                    if (deviceInfo.infoError) {
                        this.serviceInfo.setText("Cannot retrieved services info");
                    } else {
                        this.serviceInfo.setText(String.format(Locale.getDefault(), "%d services, service found: %s",
                                deviceInfo.nbServices, deviceInfo.serviceMatch ? "yes" : "no"));
                    }
                }
            }

            public void setClickConsumer(Consumer<ViewHolder> clickConsumer) {
                this.clickConsumer = clickConsumer;
            }

            public void onViewClick() {
                if (this.clickConsumer != null) {
                    this.clickConsumer.accept(this);
                }
            }
        }

        private static class BTDeviceInfo {
            final BluetoothDevice device;
            final boolean paired;
            boolean infoRetrieved;
            boolean infoError;
            int nbServices;
            boolean serviceMatch;

            public BTDeviceInfo(BluetoothDevice device, boolean paired) {
                this.device = device;
                this.paired = paired;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                BTDeviceInfo that = (BTDeviceInfo) o;
                return device.equals(that.device);
            }

            @Override
            public int hashCode() {
                return Objects.hash(device);
            }
        }
    }

}