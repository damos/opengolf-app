package ca.dait.opengolf.app.network;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.GoogleMap;

/**
 * Created by darinamos on 2018-12-13.
 */

public class LocationService {

    public static final int PERMISSION_REQUEST_CODE = 1;

    private final Activity activity;
    private final GoogleMap googleMap; //Set's myLocationEnabled inside the permission check.
    private final LocationCallback callback;
    private final LocationRequest locationRequest;

    private final FusedLocationProviderClient locationClient;
    private final SettingsClient settingsClient;
    private final LocationSettingsRequest settingsRequest;

    public LocationService(Activity activity, LocationRequest locationRequest, LocationCallback callback) {
       this(activity, null, locationRequest, callback);
    }

    public LocationService(Activity activity, GoogleMap googleMap, LocationRequest locationRequest, LocationCallback callback) {
        this.activity = activity;
        this.googleMap = googleMap;
        this.locationRequest = locationRequest;
        this.callback = callback;

        this.locationClient = LocationServices.getFusedLocationProviderClient(activity);
        this.settingsClient = LocationServices.getSettingsClient(activity);
        this.settingsRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(this.locationRequest).build();
    }

    public LocationService start() {

        this.settingsClient.checkLocationSettings(this.settingsRequest)
            .addOnSuccessListener((locationSettingsResponse) -> {
                if (ActivityCompat.checkSelfPermission(LocationService.this.activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(LocationService.this.activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                    ActivityCompat.requestPermissions(LocationService.this.activity,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_CODE);

                } else {
                    if(this.googleMap != null){
                        this.googleMap.setMyLocationEnabled(true);
                    }
                    LocationService.this.locationClient.requestLocationUpdates(
                            LocationService.this.locationRequest, LocationService.this.callback, Looper.myLooper());
                }
            })
            .addOnFailureListener((exception) -> {
                Toast.makeText(LocationService.this.activity, "Check Location Settings Failed!", Toast.LENGTH_SHORT).show();
                LocationService.this.activity.finish();
            });
        return this;
    }

    public void stop() {
        this.locationClient.removeLocationUpdates(this.callback);
    }

    public static boolean checkPermissions(int requestCode, String[] permissions, int[] grantResults) {
        return (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED);
    }

    public static void noPermissionsFinish(Activity activity){
        Toast.makeText(activity, "Application requires GPS service permissions.", Toast.LENGTH_SHORT).show();
        activity.finish();
    }
}
