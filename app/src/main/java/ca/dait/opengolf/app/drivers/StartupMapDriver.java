package ca.dait.opengolf.app.drivers;

import android.content.Intent;
import android.location.Location;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.app.activities.MainActivity;
import ca.dait.opengolf.app.network.LocationService;

/**
 * Created by darinamos on 2019-01-16.
 */

public class StartupMapDriver extends AbstractInteractiveMapDriver {

    public StartupMapDriver(MainActivity mainActivity, GoogleMap googleMap) {
        super(mainActivity, googleMap);

        this.showRefreshing(true);
        LocationService locationService = new LocationService(
                this.mainActivity,
                LocationRequest.create()
                        .setNumUpdates(1)
                        .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY),
                // TODO: User could take a while to respond to the promt.. might have to re-consider how the LocationService is coded
                //locationRequest.setExpirationDuration(30000);,
                new LocationCallback() {
                    public void onLocationResult(LocationResult locationResult) {
                        StartupMapDriver.this.onLocation(locationResult.getLastLocation());
                    }
                }
        ).start();
        this.mainActivity.setLocationService(locationService);
    }

    public boolean canRestart(Intent intent){
        return false;
    }

    private void onLocation(Location location){
        LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
        this.mainActivity.setLastPosition(position);
        this.googleMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(position,
                        Float.valueOf(this.mainActivity.getString(R.string.zoomStartup)))
        );
        this.googleMap.setOnMapLoadedCallback(() -> {
            this.googleMap.setOnMapLoadedCallback(null);
            this.showRefreshing(false);
            this.mainActivity.showMainMenuOverlay();
        });
    }
}
