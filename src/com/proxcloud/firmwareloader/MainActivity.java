package com.proxcloud.firmwareloader;

import android.os.Bundle;
import android.app.*;
import android.provider.MediaStore;
import java.io.UnsupportedEncodingException;
import android.view.ViewConfiguration;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.net.URL;
import android.view.*;
import org.json.*;
import org.apache.http.client.HttpClient;
import org.apache.http.HttpResponse;
import java.net.HttpURLConnection;
import android.app.DownloadManager;
import android.os.AsyncTask;
import android.view.Menu;
import android.view.MenuInflater;
import com.github.angads25.filepicker.model.*;
import com.github.angads25.filepicker.view.*;
import com.github.angads25.filepicker.controller.*;
import android.os.Environment;
import android.widget.RadioButton;
import android.net.Uri;
import java.net.URI;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbEndpoint;
import java.io.*;
import java.util.List;
import android.widget.CheckBox;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import android.widget.Button;

import java.util.Arrays;
import android.preference.PreferenceFragment;



public class MainActivity extends Activity {


	public static class Help extends DialogFragment {
		@Override
   		public void onCreate(Bundle savedInstanceState) {
        		super.onCreate(savedInstanceState);
		}
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
			View v = inflater.inflate(R.layout.help, container, false);
			return v;
		}
	}

	public static class SettingsFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preferences);

		}
	}


	TextView textInfo;
	TextView textInfoInterface;
	TextView textEndPoint;

	static String pendingData;

	static UsbSerialPort usbPort = null;

	//TextView textDeviceName;
	TextView textStatus;

	Spinner spInterface;
	ArrayList<String> listInterface;
	ArrayList<UsbInterface> listUsbInterface;
	ArrayAdapter<String> adapterInterface;

	Spinner spEndPoint;
	ArrayList<String> listEndPoint;
	ArrayList<UsbEndpoint> listUsbEndpoint;
	ArrayAdapter<String> adapterEndpoint;

	private static final int targetVendorID= 9025;
	private static final int targetProductID = 32828;
	UsbDevice deviceFound = null;

	private static final String ACTION_USB_PERMISSION = 
			"com.android.example.USB_PERMISSION";
	PendingIntent mPermissionIntent;

	UsbInterface usbInterface;
	UsbDeviceConnection usbDeviceConnection;

	static Thread cmdReader;
	static MainActivity main = null;
	static ArrayList<Flasher.Cmd> incomming = new ArrayList<Flasher.Cmd>();
	static boolean doFlash = false;
	private static final int READ_REQUEST_CODE = 42;
	static String statusData = "";
	static String firmwareFilePath = "";
	static String proxcloudFilePath = "";
	static String proxmark3FilePath = "";
	static String bootloaderFilePath = "";
	static boolean flashBootloader = false;
	static final Flasher flasher = new Flasher();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

//getActionBar().setTitle("Proxcloud Firmware Loader");
//getSupportActionBar().setTitle("Hello world App"); 



		textStatus = (TextView)findViewById(R.id.textstatus);
		//MainActivity.one = MainActivity.this;
		//textDeviceName = (TextView)findViewById(R.id.textdevicename); 
		mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		registerReceiver(mUsbReceiver, filter);




		registerReceiver(mUsbDeviceReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));
		registerReceiver(mUsbDeviceReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));
		this.main = this;
		this.cmdReader = Flasher.readThread;
		if(!this.cmdReader.isAlive()){
			this.cmdReader.start();
		}


final Button connectBtn = (Button)findViewById(R.id.connectButton);
connectBtn.setOnClickListener(new View.OnClickListener(){
	public void onClick(View v){
		doRawDescriptors();
	}
});
connectBtn.setEnabled(false);

final Button button = (Button)findViewById(R.id.webButton);
button.setOnClickListener(new View.OnClickListener() {
	public void onClick(View v) {
		Intent intent= new Intent(Intent.ACTION_VIEW,Uri.parse("https://proxcloud.eu/account.html"));
		startActivity(intent);

		//if (MainActivity.usbPort != null){
		//	Flasher.getVersion(MainActivity.usbPort);
		//} else {
		//	Toast.makeText(MainActivity.this, "no devie port", Toast.LENGTH_LONG).show();
		//}
	}
});

final Button chooseBtn = (Button)findViewById(R.id.chooseButton);
chooseBtn.setEnabled(false); 
chooseBtn.setOnClickListener(new View.OnClickListener(){
	public void onClick(View v) {
		//Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		//intent.setType("*"+"/"+"*");
		//startActivityForResult(intent, READ_REQUEST_CODE);
		DialogProperties properties = new DialogProperties();
		properties.root = Environment.getExternalStorageDirectory();
		properties.selection_mode = DialogConfigs.SINGLE_MODE;
		properties.selection_type = DialogConfigs.FILE_SELECT;



		FilePickerDialog dialog = new FilePickerDialog(MainActivity.this,properties);
	 	dialog.setTitle("Select a File");
		dialog.setDialogSelectionListener(new DialogSelectionListener() {
			@Override
			public void onSelectedFilePaths(String[] files) {
				if (files != null && files.length > 0){
					final String path = files[0];
					MainActivity.main.runOnUiThread(new Runnable() {
						public void run() {
							MainActivity.firmwareFilePath = path;
							Toast.makeText(MainActivity.this, "Choose file: "+path,Toast.LENGTH_LONG).show();
						}
					});
				}
			}
		});
		dialog.show();

	}
});



final Button startFlashButton = (Button)findViewById(R.id.startFlash);
startFlashButton.setEnabled(false);
startFlashButton.setOnClickListener(new View.OnClickListener(){
	public  void onClick(View v){
		if (MainActivity.usbPort != null){ 
			flasher.startFlash(MainActivity.usbPort);
			MainActivity.doFlash = true;
                } else {
                        Toast.makeText(MainActivity.this, "no devie port", Toast.LENGTH_LONG).show();
                }
	}
});
		firmwareFilePath = proxcloudFilePath = searchFiles("proxcloud_firmware_");
		proxmark3FilePath = searchFiles("proxmark3_firmware_");
		bootloaderFilePath = searchFiles("proxmark_bootloader_");
		//connectUsb();
		textStatus.setText("Plugin Proxmark device to begin");
		new UpdateTask().execute("", "", "");

	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
    		switch (item.getItemId()) {
        		case R.id.action_settings:
				FragmentTransaction ft = getFragmentManager().beginTransaction();
				Help help = new Help();
				help.show(ft, "test");
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
        	MenuInflater inflater = getMenuInflater();
        	inflater.inflate(R.menu.menu, menu);
        	return true;
	}

	String searchFiles(final String pat){
		File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		//new File(Environment.DIRECTORY_DOWNLOADS);
		File [] files = dir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				//Flasher.Log("file: "+name.startsWith(pat)+" "+name);
				return name.startsWith(pat);
			}
		});
		if (files == null) {
			Flasher.Log("search null!");
			return "";
		}
		Flasher.Log("files len: "+files.length);
		Arrays.sort(files);
		if (files.length > 0){
			return files[files.length-1].getPath();
		}
		Flasher.Log("search none!");
		return "";
	}

	public void onCheckboxClicked(View view) {
		boolean checked = ((CheckBox) view).isChecked();
		flashBootloader = checked;
	}
	@Override
	protected void onDestroy() {
		releaseUsb();
		unregisterReceiver(mUsbReceiver);
		unregisterReceiver(mUsbDeviceReceiver);
		super.onDestroy();
	}

	private void connectUsb(){
		checkDeviceInfo();
		if(deviceFound != null){
			//doRawDescriptors();
		}
	}

	private void releaseUsb(){

		if(usbDeviceConnection != null){
			if(usbInterface != null){
				usbDeviceConnection.releaseInterface(usbInterface);
				usbInterface = null;
			}
			usbDeviceConnection.close();
			usbDeviceConnection = null;
		}
		deviceFound = null;
	}

	private void doRawDescriptors(){
		UsbDevice deviceToRead = deviceFound;
		UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);

		Boolean permitToRead = manager.hasPermission(deviceToRead);
		//textStatus.setText("do raw des: "+permitToRead);
		if(permitToRead){
			//doReadRawDescriptors(deviceToRead);
		}else{
			manager.requestPermission(deviceToRead, mPermissionIntent);
			//textStatus.setText("Permission: " + permitToRead);
		}
	}
	
	private final BroadcastReceiver mUsbReceiver = 
			new BroadcastReceiver() {

				@Override
				public void onReceive(Context context, Intent intent) {
					String action = intent.getAction();
					if (ACTION_USB_PERMISSION.equals(action)) {
						textStatus.setText("ACTION_USB_PERMISSION");
		                synchronized (this) {
		                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

		                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
		                        if(device != null){
final Button connectBtn = (Button)findViewById(R.id.connectButton);
connectBtn.setEnabled(false);
						textStatus.setText("Permission granted. You can now flash the proxmark3");
		                        	doReadRawDescriptors(device);
		                       }
		                    }
		                    else {
		                        textStatus.setText("permission denied, please try again");
		                    }
		                }
		            }
				}
	};

	private final BroadcastReceiver mUsbDeviceReceiver = 
			new BroadcastReceiver() {

				@Override
				public void onReceive(Context context, Intent intent) {
					String action = intent.getAction();

final Button connectBtn = (Button)findViewById(R.id.connectButton);
					if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {

						deviceFound = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
						//checkDeviceInfoSkipDeviceSearching();
						if(doFlash){
							doRawDescriptors();
						} else {
							connectBtn.setEnabled(true);
							textStatus.setText("device found, click connect to start");

						}
						//doRawDescriptors();
					}else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
						//Toast.makeText(MainActivity.this, "Device lost, maybe unpluged or rebooting", Toast.LENGTH_LONG).show();
						statusData = "";
						connectBtn.setEnabled(false);
						if (MainActivity.doFlash){
							textStatus.setText("Device rebooting to begin flash");
						} else {
							textStatus.setText("Device Disconnected");
						}
		final Button startFlashButton = (Button)findViewById(R.id.startFlash);
		startFlashButton.setEnabled(false);



						MainActivity.usbPort = null;
						//MainActivity.cmdReader.stop();
					}
				}
	};

	private void doReadRawDescriptors(UsbDevice device){
		final int STD_USB_REQUEST_GET_DESCRIPTOR = 0x06;
		final int LIBUSB_DT_STRING = 0x03;

		boolean forceClaim = true;

		byte[] buffer = new byte[255];
        int indexManufacturer = 14;
        int indexProduct = 15;
        String stringManufacturer = "";
        String stringProduct = "";

		UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
		usbDeviceConnection = manager.openDevice(device);
		if(usbDeviceConnection != null){
			usbInterface = device.getInterface(1);
			usbDeviceConnection.claimInterface(usbInterface, forceClaim);


//--------------------------
			byte[] rawDescriptors = usbDeviceConnection.getRawDescriptors();

			int lengthManufacturer = usbDeviceConnection.controlTransfer(
					UsbConstants.USB_DIR_IN|UsbConstants.USB_TYPE_STANDARD,
					STD_USB_REQUEST_GET_DESCRIPTOR,
					(LIBUSB_DT_STRING << 8) | rawDescriptors[indexManufacturer],
					0,
					buffer,
					0xFF,
					0);
			try {
				stringManufacturer = new String(buffer, 2, lengthManufacturer-2, "UTF-16LE");
			} catch (UnsupportedEncodingException e) {
		//Toast.makeText(MainActivity.this, e.toString(), Toast.LENGTH_LONG).show();
				textStatus.setText(e.toString());
			}
int lengthProduct = usbDeviceConnection.controlTransfer(
	UsbConstants.USB_DIR_IN|UsbConstants.USB_TYPE_STANDARD,
		STD_USB_REQUEST_GET_DESCRIPTOR,
		(LIBUSB_DT_STRING << 8) | rawDescriptors[indexProduct],
		0,
		buffer,
		0xFF,
		0);
			try {
				stringProduct = new String(buffer, 2, lengthProduct-2, "UTF-16LE");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			statusData = "Device Found";
		}else{
			Toast.makeText(MainActivity.this, "open failed", Toast.LENGTH_LONG).show();
			textStatus.setText("open failed");
		}
//I*****************************************

List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager); 

if (availableDrivers.isEmpty()) {
	Toast.makeText(MainActivity.this, "failed to find driver", Toast.LENGTH_LONG).show();
  //return;
} else {
	UsbSerialDriver driver = availableDrivers.get(0); 
	UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
	if (connection == null) {
		Toast.makeText(MainActivity.this, "con failed...", Toast.LENGTH_LONG).show();
	} else {
		UsbSerialPort port = driver.getPorts().get(0);
		try{
			port.open(connection);
			port.setParameters(115200, 8, UsbSerialPort.STOPBITS_2, UsbSerialPort.PARITY_NONE);
			MainActivity.usbPort = port;
			final Button startFlashButton = (Button)findViewById(R.id.startFlash); 
			startFlashButton.setEnabled(true);
if (MainActivity.doFlash) {
	startFlashButton.setEnabled(false);
	statusData = "Writing data, this may take a couple of minutes";
	//textStatus.setText("Writing data, this will take a fiew minutes");
	MainActivity.doFlash = false;
	Thread writeThread = new Thread(){
		@Override
		public void run(){
			boolean result = true;
			result = MainActivity.flasher.setFlashArea(MainActivity.flashBootloader, 
MainActivity.usbPort);
			if (!result){
				return;
			}
			Flasher.Log("flash: "+firmwareFilePath);
			result = MainActivity.flasher.flashData(MainActivity.usbPort, MainActivity.firmwareFilePath, false);
			if (result && flashBootloader){
				result = MainActivity.flasher.flashData(MainActivity.usbPort, MainActivity.bootloaderFilePath, true);
			}
			final boolean res = result;

			final String path = MainActivity.firmwareFilePath;
			MainActivity.flasher.sendRestart(MainActivity.usbPort);

			MainActivity.main.runOnUiThread(new Runnable() {
				public void run() {
					statusData = "Writing data finished";
					if (!res) {
						textStatus.setText("Error flashing data, maybe file is bad. "+path);
					} else {
						textStatus.setText("Writing data finished");
					}
				}
			});
		}
	};
	writeThread.start();

}
//MainActivity.cmdReader.start();
/*
byte buffer3[] = "xxxxxxxxx000dfgdfgfdgdfgfgfdgfdgdfgdfgdfhtergfdkljglsdjfsdlkfjsdlfkjsdklfj".getBytes();;

//get version cmd
//byte bufferSend[] = new byte[] { 0x7, 0x1, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0};

//start flash cmd
//byte bufferSend[] = new byte[] { 0x4, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0};


int numBytesRead = port.write(bufferSend, 1000);
numBytesRead = port.read(buffer3, 1000);
Toast.makeText(MainActivity.this, "read: "+numBytesRead+" - "+new String(buffer3), Toast.LENGTH_LONG).show();
textStatus.setText(new String(buffer3));

port.close();*/
			} catch (IOException e) {
				Toast.makeText(MainActivity.this, "pm3 error", Toast.LENGTH_LONG).show();
			}
		}
	}
}

	private void checkDeviceInfo() {

		deviceFound = null;
		UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
		HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
		Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

		while (deviceIterator.hasNext()) {
			UsbDevice device = deviceIterator.next();

			//if(device.getVendorId()==targetVendorID){
			//	if(device.getProductId()==targetProductID){
					deviceFound = device;
			//	}
			//}
		}
		if(deviceFound==null){
			textStatus.setText("device not found");
		}else{
			deviceFound.getInterfaceCount();
			//checkUsbDevicve(deviceFound);
		}

	}

	private void checkDeviceInfoSkipDeviceSearching() {
		//called when ACTION_USB_DEVICE_ATTACHED, 
		//device already found, skip device searching
		//checkUsbDevicve(deviceFound);
		textStatus.setText("device refresh...");
		connectUsb();
	}

public void onRadioButtonClicked(View view) {
    // Is the button now checked?
    boolean checked = ((RadioButton) view).isChecked();
    // Check which radio button was clicked final Button chooseBtn = 
	Button chooseBtn = (Button)findViewById(R.id.chooseButton);
	chooseBtn.setEnabled(false);

    switch(view.getId()) {
        case R.id.proxcloudRadio:
            if (checked){
		firmwareFilePath = proxcloudFilePath;
	    }
            break;
        case R.id.proxmark3Radio:
            if (checked){
                firmwareFilePath = proxmark3FilePath;
	    }
            break;
	case R.id.customRadio:
		if (checked){
			chooseBtn.setEnabled(true);
		}
		break;
    }
}


public void download(String url, String filename){
	DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
	request.setDescription("Downloading firmware file: "+filename);
	request.setTitle("Proxcloud firmware loader");
    	request.allowScanningByMediaScanner();
	request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
	request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
	// get download service and enqueue file
	DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
	manager.enqueue(request);

}
class UpdateTask extends AsyncTask<String, String, Void> {
    //private ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);
    InputStream inputStream = null;
    String result = "";
    protected void onPreExecute() {  
	Flasher.Log("pre uodate");
  }
	@Override
	protected Void doInBackground(String... params) {
		Flasher.Log("update...");
		URL url;
		HttpURLConnection urlConnection = null;
		try {
			url = new URL("https://proxcloud.eu/firmware-version.json");
			urlConnection = (HttpURLConnection) url.openConnection();
			InputStream in = urlConnection.getInputStream();
			InputStreamReader isw = new InputStreamReader(in);
			int data = isw.read();
        		while (data != -1) {
				char current = (char) data;
				result += current;
				data = isw.read();
			}
		} catch (Exception e) {
        		e.printStackTrace();
		} finally {
        		if (urlConnection != null) {
				urlConnection.disconnect();
			}
		}
		return null;
	} // protected Void doInBackground(String... params)
    protected void onPostExecute(Void v) {
        //parse JSON data
	Flasher.Log("post update");
        try {
            JSONArray jArray = new JSONArray(result);
            for(int i=0; i < jArray.length(); i++) {
                JSONObject jObject = jArray.getJSONObject(i);
                String name = jObject.getString("name");
		String type = jObject.getString("type");
		File root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		File file = new File(root, name);
Flasher.Log("path?  "+file.getPath());

		if(!file.exists()) {
//			Flasher.Log("path?  "+file.getPath());
			download("http://www.proxcloud.de/downloads/"+name, name);
		}
		if (type == "proxcloud"){
			firmwareFilePath = proxcloudFilePath = file.getPath();
		} else if (type == "proxmark3"){
			proxmark3FilePath = file.getPath();
		} else if (type == "bootloader") {
			bootloaderFilePath = file.getPath();
		}
            } // End Loop
        } catch (JSONException e) {
            Flasher.Log("JSONException", "Error: " + e.toString());
        } // catch (JSONException e)
    } // protected void onPostExecute(Void v)
} //class MyAsyncTask extends AsyncTask<String, String, Void>
}
