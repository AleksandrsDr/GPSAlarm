package org.gpsalarm;

import android.Manifest;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.ArrayList;

import org.gpsalarm.R;

import static android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, GoogleMap.OnInfoWindowClickListener {
    //Date date = new Date(2020, 12, 24);
    private final static int MY_PERMISSION_FINE_LOCATIONS = 101;
    static String ringtonePath;
    static int maximumSpeed;
    static ArrayList<MarkerData> markerDataList = new ArrayList<>();
    GoogleMap myGoogleMap;
    GoogleApiClient myGoogleApiClient;
    Marker myMarker;    // Separate Marker object to allow operations with it.
    Circle myCircle;
    int alarmRadius;    // Used by markers. Can now be set through preferences.
    MediaPlayer mySound;
    LocationRequest myLocationRequest;  // Global variable for requesting location
    TrackerAlarmReceiver alarm = new TrackerAlarmReceiver();
    Button closePopUp;
    WifiManager wifiManager;
    LatLng addressGeo;
    String addressName;
    private boolean destinationReached = false;
    private PopupWindow pw;
    private LocationManager manager;
    private OnClickListener cancel_button_click_listener = new OnClickListener() {
        public void onClick(View v) {
            mySound.pause();
            removeEverything();
            destinationReached = false;
            pw.dismiss();
        }
    };
    private boolean flag = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent serviceIntent = new Intent(this, CloseService.class);
        startService(serviceIntent);
        Intent activityIntent = new Intent(this, SplashScreen.class);
        startService(activityIntent);

        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) buildAlertMessageNoGps();


        if (googleServicesAvailable()) {
            setContentView(R.layout.activity_map);
            initMap();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            setAlarmRadius(Integer.parseInt(prefs.getString("alarmRadius", "500")));
            maximumSpeed = Integer.parseInt(prefs.getString("maximumSpeed", "100"));
            ringtonePath = prefs.getString("alarmRingtone", DEFAULT_ALARM_ALERT_URI.toString());
            initSound();


            SharedPreferences.OnSharedPreferenceChangeListener prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

                    if (key.equals("mapType")) {
                        changeMapType(prefs.getString("2", "2"));
                    }
                    if (key.equals("alarmRadius")) {
                        setAlarmRadius(Integer.parseInt(prefs.getString(key, "500")));
                        removeEverything();
                    }
                    if (key.equals("alarmRingtone")) {
                        ringtonePath = prefs.getString("alarmRingtone", DEFAULT_ALARM_ALERT_URI.toString());
                        initSound();
                    }

                }
            };

            prefs.registerOnSharedPreferenceChangeListener(prefListener);

            alarm.setAlarm(MapsActivity.this);

            try {
                markerDataList = (ArrayList<MarkerData>) InternalStorage.readObject(this, "myFile"); // Retrieve the list from internal storage
            } catch (IOException e) {
                Log.e("File Read error: ", e.getMessage());
            } catch (ClassNotFoundException e) {
                Toast.makeText(this, "Failed to retrieve list from file", Toast.LENGTH_SHORT).show();
                Log.e("File Read error: ", e.getMessage());
            } catch (Exception e) {
                Log.e("Exception: ", e.getMessage());
            }

        }
        checkAndConnect();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.

        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);
        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                addressGeo = place.getLatLng();
                addressName = place.getName().toString();
                Log.i("V", "longitude: " + place.getLatLng().longitude);
            }

            @Override
            public void onError(Status status) {
            }

        });
        addNotificationAppRunning();
    }

    private void initMap() {
        MapFragment myMapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.fragment);
        myMapFragment.getMapAsync(this);            // Previously getMap

    }

    public boolean googleServicesAvailable() {
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();
        int isAvailable = api.isGooglePlayServicesAvailable(this);      // Can return 3 different values

        if (isAvailable == ConnectionResult.SUCCESS) {
            return true;
        } else if (api.isUserResolvableError(isAvailable)) {
            Dialog dialog = api.getErrorDialog(this, isAvailable, 0);
            dialog.show();
        } else if (api.isUserResolvableError(isAvailable)) {
            Dialog dialog = api.getErrorDialog(this, isAvailable, 0);
            dialog.show();
        } else {
            Toast.makeText(this, "Can't connect to play services!", Toast.LENGTH_LONG).show();
        }

        return false;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        myGoogleMap = googleMap;
        googleMap.getUiSettings().setZoomControlsEnabled(true);

        if (myGoogleMap != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            changeMapType(prefs.getString("mapType", "2"));

            myGoogleMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
                @Override
                public void onMarkerDragStart(Marker marker) {
                    if (myCircle != null) {
                        myCircle.remove();
                    }
                }

                @Override
                public void onMarkerDrag(Marker marker) {
                }

                @Override
                public void onMarkerDragEnd(Marker marker) {

                    LatLng coordinates = marker.getPosition();
                    myCircle = drawCircle(coordinates);

                    double roundedLatitude = Math.round(coordinates.latitude * 100000.0) / 100000.0;
                    double roundedLongitude = Math.round(coordinates.longitude * 100000.0) / 100000.0;

                    setMarker(roundedLatitude, roundedLongitude);

                }
            });

            myGoogleMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
                @Override
                public void onMapLongClick(LatLng point) {
                    if (myMarker != null) {
                        myGoogleMap.clear();
                    }
                    double roundedLatitude = Math.round(point.latitude * 100000.0) / 100000.0;
                    double roundedLongitude = Math.round(point.longitude * 100000.0) / 100000.0;
                    setMarker(roundedLatitude, roundedLongitude);

                }
            });

            myGoogleMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

                @Override
                public View getInfoWindow(Marker marker) {
                    return null;
                }

                @Override
                public View getInfoContents(Marker marker) {
                    View v = getLayoutInflater().inflate(R.layout.info_window, null);

                    TextView tvLocality = (TextView) v.findViewById(R.id.tv_locality);
                    TextView tvLat = (TextView) v.findViewById(R.id.tv_lat);
                    TextView tvLng = (TextView) v.findViewById(R.id.tv_lng);
                    TextView tvSnippet = (TextView) v.findViewById(R.id.tv_snippet);

                    LatLng coordinates = marker.getPosition();

                    tvLocality.setText(marker.getTitle());
                    tvLat.setText("Latitude: " + coordinates.latitude);
                    tvLng.setText("Longitude: " + coordinates.longitude);
                    tvSnippet.setText(marker.getSnippet());

                    return v;
                }
            });

            myGoogleMap.setOnInfoWindowClickListener(this);


        }


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            myGoogleMap.setMyLocationEnabled(true);
            myGoogleApiClient = new GoogleApiClient.Builder(this)       // This code is for updating current location
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            myGoogleApiClient.connect();
            zoom(15, 90, 40);
            if (MyStartActivity.selectedMarkerData != null && MyStartActivity.selectedMarkerData.isReal()) {
                setMarker(MyStartActivity.selectedMarkerData.getLatitude(), MyStartActivity.selectedMarkerData.getLongitude());
            }
            centerMap();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSION_FINE_LOCATIONS);
            }
        }
    }

    private void zoom(float zoom, float bearing, float tilt) {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location location = locationManager.getLastKnownLocation(locationManager.getBestProvider(criteria, false));
            if (location != null) {
                myGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 13));
                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(new LatLng(location.getLatitude(), location.getLongitude()))      // Sets the center of the map to location user
                        .zoom(zoom)                   // Sets the zoom
                        .bearing(bearing)                // Sets the orientation of the camera to east
                        .tilt(tilt)                   // Sets the tilt of the camera to 30 degrees
                        .build();                   // Creates a CameraPosition from the builder
                myGoogleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            }
        }
    }

    private void centerMap() {

        Location location = myGoogleMap.getMyLocation();

        if (myMarker != null) {
            LatLng myLocation = new LatLng(myMarker.getPosition().latitude, myMarker.getPosition().longitude);
            myGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 12));
        } else if (location != null) {
            LatLng myLocation = new LatLng(location.getLatitude(), location.getLongitude());
            myGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 12));
        } else {
            goToEurope();
        }
    }

    private void goToEurope() {
        LatLng coordinates = new LatLng(56.54204, 13.36096);
        CameraUpdate camUpdate = CameraUpdateFactory.newLatLngZoom(coordinates, 3);
        myGoogleMap.moveCamera(camUpdate);
    }

    private void goToLocationZoom(double lat, double lng, float zoom) {
        LatLng coordinates = new LatLng(lat, lng);
        CameraUpdate camUpdate = CameraUpdateFactory.newLatLngZoom(coordinates, zoom);
        myGoogleMap.moveCamera(camUpdate);
    }

    public void geoLocate(View view) {
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) buildAlertMessageNoGps();
        checkAndConnect();

        if (addressName != null) {
            double lat = addressGeo.latitude;
            double lng = addressGeo.longitude;
            double roundedLat = Math.round(lat * 100000.0) / 100000.0;
            double roundedLng = Math.round(lng * 100000.0) / 100000.0;
            goToLocationZoom(lat, lng, 15);
            setMarker(roundedLat, roundedLng);
        } else {
            Toast.makeText(this, "No such location found. \nTry a different keyword.", Toast.LENGTH_LONG).show();
        }
    }

    void setMarker(double lat, double lng) {
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) buildAlertMessageNoGps();
        checkAndConnect();

        if (myMarker != null) {                                      // If marker has a reference, remove it.
            removeEverything();
        }

        MarkerOptions options = new MarkerOptions()                 // This MarkerOptions object is needed to add a marker.
                .draggable(true)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.alarm_marker_40))      // Here it is possible to specify custom icon design.
                .position(new LatLng(lat, lng));
        myMarker = myGoogleMap.addMarker(options);
        myCircle = drawCircle(new LatLng(lat, lng));
        startLocationRequest();

    }

    private Circle drawCircle(LatLng latLng) {

        CircleOptions circleOptions = new CircleOptions()
                .center(latLng)
                .radius(alarmRadius)
                .fillColor(0x33FF0000)              // 33 for alpha (transparency)
                .strokeColor(Color.BLUE)
                .strokeWidth(3);

        return myGoogleMap.addCircle(circleOptions);
    }

    private void removeEverything() {
        if (myMarker != null) {
            myMarker.remove();
            myMarker = null;        // To save some space
            if (myCircle != null) {
                myCircle.remove();
                myCircle = null;        // memory saving
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);         // More on this line: http://stackoverflow.com/questions/10303898/oncreateoptionsmenu-calling-super
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.menuItemSettings:
                Intent j = new Intent(this, MyPreferencesActivity.class);
                if (myMarker != null) {
                    MyStartActivity.selectedMarkerData.setLatitude(myMarker.getPosition().latitude);
                    MyStartActivity.selectedMarkerData.setLongitude(myMarker.getPosition().longitude);
                }
                startActivity(j);
                return true;
            case R.id.menuItemHelp:
                Intent k = new Intent(this, MyHelpActivity.class);
                if (myMarker != null) {
                    MyStartActivity.selectedMarkerData.setLatitude(myMarker.getPosition().latitude);
                    MyStartActivity.selectedMarkerData.setLongitude(myMarker.getPosition().longitude);
                }
                startActivity(k);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    public void onInfoWindowClick(Marker marker) {
        // Toast.makeText(this, "Info Window long click", Toast.LENGTH_SHORT).show();
        View myView = (LayoutInflater.from(this)).inflate(R.layout.input_name, null);

        final AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setView(myView);
        final EditText userInput = (EditText) myView.findViewById(R.id.etxtInputName);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(userInput.getWindowToken(), 0);
        alertBuilder.setCancelable(true)
                .setTitle("Save Alarm")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String name = userInput.getText().toString();

                        if (TextUtils.isEmpty(name) || TextUtils.getTrimmedLength(name) < 1) {
                            Toast.makeText(MapsActivity.this, "Empty name not allowed. \nPlease try again.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        for (MarkerData markerData : markerDataList) {
                            if (markerData.getName().equals(name)) {
                                Toast.makeText(MapsActivity.this, "Duplicate name not allowed. \nPlease try again with a unique name.", Toast.LENGTH_LONG).show();
                                return;
                            }
                        }
                        addMarkerDataToList(name);
                        myMarker.hideInfoWindow();
                    }
                });


        Dialog myDialog = alertBuilder.create();
        myDialog.show();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        myLocationRequest = LocationRequest.create();
        myLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(myGoogleApiClient, myLocationRequest, this);
        }

        LocationServices.FusedLocationApi.requestLocationUpdates(myGoogleApiClient, myLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    double haversine(double lat1, double lon1, double lat2, double lon2) {

        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0] / 1000;
    }

    private void showPopup() {
        addNotificationEnd();
        LayoutInflater inflater = (LayoutInflater) MapsActivity.this
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.screen_popup,
                (ViewGroup) findViewById(R.id.popup_element));
        pw = new PopupWindow(layout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.showAtLocation(layout, Gravity.CENTER, 0, 0);

        pw.setOutsideTouchable(false);                                         //Dobavlenij kod 16.02.2017
        pw.setFocusable(false);                             // esli nado 4tob okno zakrivalosj pri kasanii vne ego, udalitj eti dve strochki

        pw.setOnDismissListener(new PopupWindow.OnDismissListener() {       //Dobavlenij kod 16.02.2017
            @Override
            public void onDismiss() {
                mySound.pause();
                removeEverything();
                destinationReached = false;
                pw.dismiss();
            }
        });

        closePopUp = (Button) layout.findViewById(R.id.btn_close_popup);
        closePopUp.setOnClickListener(cancel_button_click_listener);
    }

    @Override
    public void onLocationChanged(Location location) {
        // Called every time user changes location
        if (location == null) {
            Toast.makeText(this, "Can't get current location", Toast.LENGTH_LONG).show();
            Log.e("onLocationChanged", "Can't get current location");
            return;
        }
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        if (myMarker != null) {
            double distance = haversine(lat, lon, myMarker.getPosition().latitude, myMarker.getPosition().longitude);
            Log.d("distance:", String.valueOf(distance));
            Log.d("maxSpeed:", String.valueOf(maximumSpeed));
            // Check if reached destination
            if (distance <= myCircle.getRadius() / 1000) {
                stopLocationRequest();
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                v.vibrate(500);
                mySound.start();
                if (!destinationReached)
                    showPopup();
                destinationReached = true;
                Log.d("Destination", "reached");
            } else {
                // Else set interval for location depending on distance
                long interval = (long) (3600_000 * distance / maximumSpeed);
                if (interval < 1000) interval = 1000; // preserve minimal interval to 1s
                myLocationRequest.setInterval(interval); //
                myLocationRequest.setFastestInterval(interval);
                Log.d("Interval:", String.valueOf(myLocationRequest.getInterval()));
                Log.d("fastest Interval:", String.valueOf(myLocationRequest.getFastestInterval()));
            }
        }
    }

    public void changeMapType(String type) {
        switch (type) {
            case "1":
                myGoogleMap.setMapType(GoogleMap.MAP_TYPE_NONE);
                break;
            case "2":
                myGoogleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                break;
            case "3":
                myGoogleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                break;
            case "4":
                myGoogleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                break;
            case "5":
                myGoogleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                break;
        }
    }

    public void setAlarmRadius(int newRadius) {
        alarmRadius = newRadius;
    }

    public void initSound() {
        mySound = MediaPlayer.create(this, Uri.parse(ringtonePath));
    }

    public void addMarkerDataToList(String name) {
        MarkerData toBeAdded = new MarkerData();
        toBeAdded.setName(name);
        toBeAdded.setLatitude(myMarker.getPosition().latitude);
        toBeAdded.setLongitude(myMarker.getPosition().longitude);
        if (markerDataList.add(toBeAdded)) {
            if (saveMarkerDataList())
                Toast.makeText(this, "Alarm saved", Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(this, "File write failed", Toast.LENGTH_SHORT).show();
        } else
            Toast.makeText(this, "Failed to add alarm to list", Toast.LENGTH_SHORT).show();
    }

    private boolean saveMarkerDataList() {
        try {
            InternalStorage.writeObject(this, "myFile", markerDataList);
            return true;
        } catch (IOException e) {
            Toast.makeText(this, "Failed to save alarm", Toast.LENGTH_SHORT).show();
            Log.e("IOException", e.getMessage());
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSION_FINE_LOCATIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        myGoogleMap.setMyLocationEnabled(true);
                        myGoogleApiClient = new GoogleApiClient.Builder(this)       // This code is for updating current location
                                .addApi(LocationServices.API)
                                .addConnectionCallbacks(this)
                                .addOnConnectionFailedListener(this)
                                .build();
                        myGoogleApiClient.connect();
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "this app requires location permissions to be granted", Toast.LENGTH_LONG).show();
                    ActivityCompat.finishAffinity(MapsActivity.this);
                    System.exit(0);
                }
                break;
        }
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?\n" + "\"If no, programm will be closed.\"")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        dialog.cancel();
                        ActivityCompat.finishAffinity(MapsActivity.this);
                        System.exit(0);
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    //Dobavlenij kod!!!   14.02.2017

    private void buildAlertMessageNoWifi() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your Wi-Fi seems to be disabled, do you want to enable it?\n" + "\"If wi-fi not available, please connect via mobile data\"")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        enableWiFi();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    public void stopTrackingBut(View view) {

        Button button = (Button) findViewById(R.id.button4);
        if (flag) {
            button.setBackgroundColor(Color.RED);
            Toast.makeText(MapsActivity.this, "Tracking paused.", Toast.LENGTH_SHORT).show();
            flag = false;
            stopLocationRequest();
            button.setText("Start");
            Log.d("Tracking", "paused");
        } else {
            button.setBackgroundColor(Color.GREEN);
            Toast.makeText(MapsActivity.this, "Tracking restored.", Toast.LENGTH_SHORT).show();
            flag = true;
            startLocationRequest();
            button.setText("Pause");
            Log.d("Tracking", "started");
        }

    }

    void startLocationRequest() {
        myLocationRequest = LocationRequest.create();
        if (!myGoogleApiClient.isConnected())
            myGoogleApiClient.connect();
        myLocationRequest.setFastestInterval(1);
        myLocationRequest.setFastestInterval(1);
        myLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        Log.d("startLocationRequest", "started successfully");
    }

    void stopLocationRequest() {
        if (myGoogleApiClient != null && myGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(myGoogleApiClient, this);
            myGoogleApiClient.disconnect();
        }
        Log.d("stopLocationRequest", "stopped successfully");
    }

    public void enableWiFi() {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(true);
        startActivity(new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK));
        Toast.makeText(getApplicationContext(), "Wi-fi connecting..", Toast.LENGTH_LONG).show();
    }

    public void checkAndConnect() {
        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        // test for connection
        if (cm != null) {
            if (!(cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isAvailable() && cm.getActiveNetworkInfo().isConnected())) {
                buildAlertMessageNoWifi();
            }
        }

    }

    public void addNotificationAppRunning() {

        Notification.Builder mBuilder =
                new Notification.Builder(this)
                        .setSmallIcon(R.drawable.alarm1)
                        .setContentTitle("GPSAlarm")
                        .setContentText("Programm Running")
                        .setOngoing(true);
        Intent resultIntent = new Intent(this,

                MapsActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);


        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        mBuilder.setContentIntent(pendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(1, mBuilder.build());
    }

    public void addNotificationEnd() {

        Notification.Builder mBuilder =
                new Notification.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher_web)
                        .setContentTitle("GPSAlarm")
                        .setContentText("You have arrived!")
                        .setAutoCancel(true)
                        .setPriority(Notification.PRIORITY_MIN);
        Intent resultIntent = new Intent(this,

                MapsActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        mBuilder.setContentIntent(pendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(2, mBuilder.build());
    }
}