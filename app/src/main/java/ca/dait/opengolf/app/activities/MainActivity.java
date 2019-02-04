package ca.dait.opengolf.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.app.drivers.AbstractInteractiveMapDriver;
import ca.dait.opengolf.app.drivers.CreateCourseMapDriver;
import ca.dait.opengolf.app.drivers.FreeRoamMapDriver;
import ca.dait.opengolf.app.drivers.PlayCourseMapDriver;
import ca.dait.opengolf.app.drivers.StartupMapDriver;
import ca.dait.opengolf.app.network.LocationService;

/**
 * Map activity that contains all interactive user experiences.
 */
public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final int ACTIVITY_RESULT_MENU_OVERLAY = 1;
    public static final int ACTIVITY_RESULT_MAP_DRIVER = 2;

    protected AbstractInteractiveMapDriver mapDriver;
    protected GoogleMap googleMap;

    protected LatLng lastPosition;
    protected LocationService locationService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_main);

        //Call to initialize Google Maps
        SupportMapFragment mapFragment = (SupportMapFragment)
                this.getSupportFragmentManager().findFragmentById(R.id.courseMap);
        mapFragment.getMapAsync(this);
    }

    @Override
    public final void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        this.mapDriver = new StartupMapDriver(this, this.googleMap);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if(requestCode == ACTIVITY_RESULT_MENU_OVERLAY){
            if(resultCode == RESULT_OK) {
                if(this.mapDriver.canRestart(intent)){
                    this.mapDriver.restart();
                }
                else {
                    this.mapDriver.clear();
                    int result = intent.getIntExtra(MenuOverlayActivity.INTENT_EXTRA_RESULT, 0);
                    switch (result) {
                        case MenuOverlayActivity.INTENT_RESULT_FREE_ROAM:
                            this.mapDriver = new FreeRoamMapDriver(this, this.googleMap);
                            break;
                        case MenuOverlayActivity.INTENT_RESULT_CREATE_COURSE:
                            this.mapDriver = new CreateCourseMapDriver(this, this.googleMap);
                            break;
                        case MenuOverlayActivity.INTENT_RESULT_PLAY_COURSE:
                            this.mapDriver = new PlayCourseMapDriver(this, this.googleMap, intent);
                            break;
                        default:
                            this.finish();
                    }
                }
            }
            else{
                this.finish();
            }
        }
        if(requestCode == ACTIVITY_RESULT_MAP_DRIVER){
            this.mapDriver.onActivityResult(resultCode, intent);
        }
    }

    @Override
    public void onRequestPermissionsResult (int requestCode, @NonNull  String[] permissions, @NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == LocationService.PERMISSION_REQUEST_CODE) {
            if (LocationService.checkPermissions(requestCode, permissions, grantResults) && this.locationService != null) {
                this.locationService.start();
            } else {
                LocationService.noPermissionsFinish(this);
            }
        }
    }

    @Override
    public void onPause(){
        if(this.mapDriver != null){
            this.mapDriver.pause();
        }
        super.onPause();
    }

    @Override
    public void onResume(){
        if(this.mapDriver != null){
            this.mapDriver.resume();
        }
        super.onResume();
    }

    public void setLastPosition(LatLng position){
        this.lastPosition = position;
    }

    public void setLocationService(LocationService locationService){
        this.locationService = locationService;
    }

    @Override
    public void onBackPressed(){
        this.showMainMenuOverlay();
    }

    public void showMainMenuOverlay(){
        if(this.mapDriver != null){
            this.mapDriver.stop();
        }
        Intent intent = new Intent(this, MenuOverlayActivity.class);
        if(this.lastPosition != null){
            intent.putExtra(MenuOverlayActivity.INTENT_EXTRA_LOCATION, this.lastPosition);
        }
        this.startActivityForResult(intent, ACTIVITY_RESULT_MENU_OVERLAY);
    }

}
