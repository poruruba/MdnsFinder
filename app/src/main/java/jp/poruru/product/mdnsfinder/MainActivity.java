package jp.poruru.product.mdnsfinder;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, Handler.Callback, AdapterView.OnItemSelectedListener {
    public static final String TAG = "MainActivity";

    static final int HANDLER_WHAT_ADD_SERVICE = 0;
    static final int HANDLER_WHAT_RESOLVED_SERVICE = 1;
    static final int HANDLER_WHAT_RESOLVE_FAILED_SERVICE = 2;

    NsdManager nsdManager;
    NsdManager.DiscoveryListener discoveryListener;
    NsdManager.ResolveListener resolveListener;
    boolean isInitialized = false;
    boolean isDiscovering = false;
    boolean isResolving = false;
    ProgressBar progressBar;

    class ServiceInfoItem {
        public String name;
        public NsdServiceInfo info;
        public NsdServiceInfo resolvedInfo;

        public ServiceInfoItem(String name, NsdServiceInfo info){
            this.name = name;
            this.info = info;
            this.resolvedInfo = null;
        }
    }
    List<ServiceInfoItem> list;
    ListAdapter listAdapter;
    UIHandler handler;

//    String SERVICE_TYPE = "_http._tcp.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new UIHandler(this);
        list = new ArrayList<>();
        listAdapter = new ListAdapter(this);
        ListView listView;
        listView = (ListView)findViewById(R.id.list_service);
        listView.setAdapter(listAdapter);

        Spinner spin;
        ArrayAdapter<String> adapter;

        adapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item,
                getResources().getStringArray(R.array.spin_type_items)
        );
        spin = (Spinner)findViewById(R.id.spin_type_kind);
        spin.setAdapter(adapter);
        spin.setOnItemSelectedListener(this);

        adapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item,
                getResources().getStringArray(R.array.spin_service_items)
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spin = (Spinner)findViewById(R.id.spin_type);
        spin.setAdapter(adapter);

        adapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item,
                getResources().getStringArray(R.array.spin_protocol_items)
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spin = (Spinner)findViewById(R.id.spin_protocol);
        spin.setAdapter(adapter);

        nsdManager = (NsdManager)getApplicationContext().getSystemService(Context.NSD_SERVICE);
        if( nsdManager == null ){
            Toast.makeText(this, getString(R.string.toast_message3), Toast.LENGTH_LONG).show();
            return;
        }

        discoveryListener = createDiscoveryListener();
        resolveListener = createResolveListener();
        if( discoveryListener == null || resolveListener == null ){
            Toast.makeText(this, getString(R.string.toast_message3), Toast.LENGTH_LONG).show();
            return;
        }

        Button btn;
        btn = (Button)findViewById(R.id.btn_start_discovery);
        btn.setOnClickListener(this);
        ImageButton imgbtn;
        imgbtn = (ImageButton) findViewById(R.id.imgbtn_info);
        imgbtn.setOnClickListener(this);

        isInitialized = true;
    }

    void startDiscoverServices(){
        Log.d(TAG, "startDiscoverServices called");

        tearDownDiscoverServices();

        list.clear();
        listAdapter.notifyDataSetChanged();
        TextView text;
        text = (TextView) findViewById(R.id.txt_empty);
        text.setVisibility(View.VISIBLE);

        Spinner spin;
        spin = (Spinner)findViewById(R.id.spin_type_kind);
        String type;
        if( spin.getSelectedItemPosition() == 0){
            spin = (Spinner)findViewById(R.id.spin_type);
            type = (String)spin.getSelectedItem();
        }else{
            EditText edit;
            edit = (EditText)findViewById(R.id.edit_type_custom);
            type = edit.getText().toString();
        }
        spin = (Spinner)findViewById(R.id.spin_protocol);
        String protocol = (String)spin.getSelectedItem();
        String serviceType = "_" + type + "._" + protocol + ".";
        Log.d(TAG, "discover service: " + serviceType);
        if( nsdManager != null )
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    void tearDownDiscoverServices() {
        Log.d(TAG, "tearDownDiscoverServices called");

        if (nsdManager != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            }catch(Exception ex){}
            if( progressBar != null )
                progressBar.setVisibility(View.INVISIBLE);
        }
    }

    NsdManager.DiscoveryListener createDiscoveryListener() {
        Log.d(TAG, "createDiscoveryListener called");

        return new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                Log.d(TAG, "onServiceFound name=" + service.getServiceName() + " type=" + service.getServiceType());
                Log.d(TAG, service.toString());

                handler.sendUIMessage(HANDLER_WHAT_ADD_SERVICE, service);
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.d(TAG, "service lost: " + service);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Discovery stopped: " + serviceType);
                isDiscovering = false;
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);
                isDiscovering = false;
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);
                isDiscovering = false;
            }
        };
    }

    NsdManager.ResolveListener createResolveListener(){
        Log.d(TAG, "createResolveListener called");

        return new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Resolve failed: " + errorCode);

                isResolving = false;
                handler.sendUIMessage(HANDLER_WHAT_RESOLVE_FAILED_SERVICE, serviceInfo);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Resolve Succeeded. " + serviceInfo);

                isResolving = false;
                handler.sendUIMessage(HANDLER_WHAT_RESOLVED_SERVICE, serviceInfo);
            }
        };
    }

    @Override
    public void onClick(View view) {
        if( !isInitialized ){
            Toast.makeText(this, getString(R.string.toast_message3), Toast.LENGTH_LONG).show();
            return;
        }

        int id = view.getId();
        if( id == R.id.btn_start_discovery) {
            Button btn;
            btn = (Button) findViewById(R.id.btn_start_discovery);
            if( !isDiscovering ) {
                startDiscoverServices();
                isDiscovering = true;
                btn.setText(getString(R.string.btn_stop_discovery));
            }else {
                tearDownDiscoverServices();
                isDiscovering = false;
                btn.setText(getString(R.string.btn_start_discovery));
            }
        }else
        if( id == R.id.imgbtn_info ){
            new AlertDialog.Builder(this)
                    .setTitle("Info")
                    .setMessage(getString(R.string.dlg_info_message))
                    .setPositiveButton(getString(R.string.dlg_close), null)
                    .show();
        }
    }

    @Override
    public boolean handleMessage(Message message) {
        int what = message.what;
        switch(what) {
            case HANDLER_WHAT_ADD_SERVICE: {
                NsdServiceInfo serviceInfo = (NsdServiceInfo) message.obj;
                String name = serviceInfo.getServiceName();
                for (int i = 0; i < list.size(); i++) {
                    ServiceInfoItem item = (ServiceInfoItem) list.get(i);
                    if (name.equals(item.name) )
                        return true;
                }
                if( list.size() <= 0 ){
                    TextView text;
                    text = (TextView) findViewById(R.id.txt_empty);
                    text.setVisibility(View.GONE);
                }
                ServiceInfoItem item = new ServiceInfoItem(serviceInfo.getServiceName(), serviceInfo);
                list.add(item);
                listAdapter.notifyDataSetChanged();
                break;
            }
            case HANDLER_WHAT_RESOLVED_SERVICE: {
                NsdServiceInfo serviceInfo = (NsdServiceInfo) message.obj;
                String name = serviceInfo.getServiceName();
                for (int i = 0; i < list.size(); i++) {
                    ServiceInfoItem item = (ServiceInfoItem) list.get(i);
                    if (name.equals(item.name)){
                        item.resolvedInfo = serviceInfo;
                        progressBar.setVisibility(View.INVISIBLE);
                        showServiceInfoDialog(this, serviceInfo);
                        break;
                    }
                }
                break;
            }
            case HANDLER_WHAT_RESOLVE_FAILED_SERVICE:{
                NsdServiceInfo serviceInfo = (NsdServiceInfo) message.obj;
                String name = serviceInfo.getServiceName();
                for (int i = 0; i < list.size(); i++) {
                    ServiceInfoItem item = (ServiceInfoItem) list.get(i);
                    if (name.equals(item.name)){
                        progressBar.setVisibility(View.INVISIBLE);
                        Toast.makeText(this, getString(R.string.toast_message2), Toast.LENGTH_LONG).show();
                        break;
                    }
                }
                break;
            }
        }

        return true;
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        EditText edit = (EditText)findViewById(R.id.edit_type_custom);
        Spinner spin = (Spinner) findViewById(R.id.spin_type);
        if( i == 0 ){
            edit.setVisibility(View.GONE);
            spin.setVisibility(View.VISIBLE);
        }else if( i == 1 ){
            edit.setVisibility(View.VISIBLE);
            spin.setVisibility(View.GONE);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
    }

    void showServiceInfoDialog(Context context, NsdServiceInfo info){
        Log.d(TAG, "showServiceInfoDialog called");

        LayoutInflater factory = LayoutInflater.from(context);
        final View view = factory.inflate(R.layout.dialog_service, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("ServiceInfo");
        builder.setNegativeButton(getString(R.string.dlg_close), null);
        TextView text;
        text = (TextView)view.findViewById(R.id.txt_dlg_name);
        text.setText(info.getServiceName());
        text = (TextView)view.findViewById(R.id.txt_dlg_type);
        text.setText(info.getServiceType());
        text = (TextView)view.findViewById(R.id.txt_dlg_host);
        text.setText(info.getHost().toString());
        text = (TextView)view.findViewById(R.id.txt_dlg_port);
        text.setText(Integer.toString(info.getPort()));
        builder.setView(view);
        final AlertDialog dialog = builder.create();
        dialog.show();
    }

    class ListAdapter extends BaseAdapter {
        private Context context;

        public ListAdapter(Context context) {
            super();
            this.context = context;
        }

        public int getCount() {
            return list.size();
        }

        public ServiceInfoItem getItem(int position) {
            return list.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ServiceInfoItem item = (ServiceInfoItem) getItem(position);
            if(convertView == null){
                LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.list_row, null);
            }
            if(item != null){
                TextView text;
                Button button;
                ProgressBar bar;

                bar = (ProgressBar)convertView.findViewById(R.id.progressBar);
                bar.setVisibility(View.INVISIBLE);
                text = (TextView) convertView.findViewById(R.id.textView);
                text.setText(item.name);
                button = (Button) convertView.findViewById(R.id.button);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG, "onClick called");
                        try {
                            if( item.resolvedInfo != null ){
                                showServiceInfoDialog(context, item.resolvedInfo);
                                return;
                            }
                            if( isResolving ){
                                Toast.makeText(getApplicationContext(), context.getString(R.string.toast_message1), Toast.LENGTH_LONG).show();
                                return;
                            }
                            nsdManager.resolveService(item.info, resolveListener);
                            isResolving = true;

                            progressBar = bar;
                            progressBar.setVisibility(View.VISIBLE);
                        }catch(Exception ex){
                            Log.e(TAG, ex.getMessage());
                        }
                    }
                });
            }
            return convertView;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nsdManager != null)
            tearDownDiscoverServices();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nsdManager != null) {
            if( isDiscovering )
                startDiscoverServices();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nsdManager != null)
            tearDownDiscoverServices();
    }
}