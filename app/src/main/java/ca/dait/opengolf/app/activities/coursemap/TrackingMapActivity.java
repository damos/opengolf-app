package ca.dait.opengolf.app.activities.coursemap;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Window;
import android.view.WindowManager;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.maps.android.SphericalUtil;

import java.util.Arrays;

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.app.gps.LocationService;
import ca.dait.opengolf.app.utlis.BitmapFactory;
import ca.dait.opengolf.app.utlis.Calculator;

/**
 * Created by darinamos on 2018-12-29.
 */
public abstract class TrackingMapActivity extends FragmentActivity implements OnMapReadyCallback, LocationSource {

    protected LayoutDriver layoutDriver;
    protected float waypointLinePx;
    protected float anchorFlagIconX;
    protected float anchorFlagIconY;
    private float defaultZoom;

    protected Bitmap waypointIcon;

    protected GoogleMap googleMap;

    private LocationService locationService;
    private OnLocationChangedListener locationSourceListener;

    protected boolean cameraOverride = false;

    protected Location currentLocation;
    protected LatLng currentPosition;
    protected Marker flag;
    protected Waypoint waypoint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_course_map);

        this.waypointIcon = BitmapFactory.fromDrawable(R.drawable.ic_waypoint, this.getResources());
        this.anchorFlagIconX = Float.valueOf(this.getString(R.string.anchorFlagIconX));
        this.anchorFlagIconY = Float.valueOf(this.getString(R.string.anchorFlagIconY));
        this.defaultZoom = Float.valueOf(this.getString(R.string.defaultZoom));

        this.waypointLinePx = Calculator.getPixelsFromDp(Float.valueOf(this.getString(R.string.waypointLineDp)));

        this.layoutDriver = new LayoutDriver(this);
        this.layoutDriver.clearControls();

        //Set max brightness. Golfers are usually outdoors in extreme brightness.
        Window window = this.getWindow();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        window.setAttributes(layoutParams);
    }

    @Override
    @SuppressLint("MissingPermission")
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        this.googleMap.setLocationSource(this);
        this.googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        UiSettings uiSettings = this.googleMap.getUiSettings();
        uiSettings.setMapToolbarEnabled(false);
        uiSettings.setCompassEnabled(false);
        uiSettings.setTiltGesturesEnabled(false);
        uiSettings.setMyLocationButtonEnabled(false);
        uiSettings.setZoomControlsEnabled(false);
    }

    protected void setInteractiveListeners(){
        this.googleMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {
                TrackingMapActivity.this.waypoint.onDrag(marker);
            }

            @Override
            public void onMarkerDrag(Marker marker) {
                TrackingMapActivity.this.waypoint.onDrag(marker);
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                TrackingMapActivity.this.waypoint.onDrag(marker);
            }
        });

        this.googleMap.setOnMarkerClickListener((marker) -> {
            if(this.waypoint != null && this.waypoint.equals(marker)){
                this.clearWayPoint();
            }
            return true;
        });
        this.googleMap.setOnCameraMoveStartedListener((reason) -> {
            if(reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE){
                this.userAction();
            }
        });
        this.layoutDriver.setCancelListener((view) -> {
            this.cancelUserAction();
            this.positionCamera();
        });
        this.layoutDriver.setWaypointButtonListener((view) -> {
            this.userAction();
            this.positionCamera(this.waypoint.pos);
        });

    }

    protected void userAction(){
        this.cameraOverride = true;
        this.layoutDriver.showCancelable();
    }

    protected void cancelUserAction(){
        this.cameraOverride = false;
    }

    protected void clearWayPoint(){
        if(this.waypoint != null) {
            this.waypoint.clear();
            this.waypoint = null;
            this.layoutDriver.hideWayPointAction();
        }
    }

    protected void startGpsUpdates(){
        Resources resources = this.getResources();
        final LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(resources.getInteger(R.integer.locationInterval));
        locationRequest.setFastestInterval(resources.getInteger(R.integer.locationFastestInterval));
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        this.locationService = new LocationService(this, this.googleMap, locationRequest, new LocationCallback(){
            public void onLocationResult(LocationResult locationResult) {

                TrackingMapActivity.this.currentLocation = locationResult.getLastLocation();
                TrackingMapActivity.this.currentPosition = new LatLng(
                            TrackingMapActivity.this.currentLocation.getLatitude(),
                        TrackingMapActivity.this.currentLocation.getLongitude());

                if(TrackingMapActivity.this.locationSourceListener != null){
                    TrackingMapActivity.this.locationSourceListener.onLocationChanged(
                            TrackingMapActivity.this.currentLocation);
                }

                TrackingMapActivity.this.layoutDriver.hideSpinner();

                TrackingMapActivity.this.updatePanel();
                if(!TrackingMapActivity.this.cameraOverride) {
                    TrackingMapActivity.this.positionCamera(TrackingMapActivity.this.currentPosition);
                }
            }
        });

        this.setInteractiveListeners();
        this.locationService.start();
    }

    @Override
    public void onRequestPermissionsResult (int requestCode, String[] permissions, int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(LocationService.checkPermissions(requestCode, permissions, grantResults)){
            //Re-try hideStartButton after user accepts permissions.
            this.locationService.start();
        }
        else{
            LocationService.noPermissionsFinish(this);
        }
    }

    @Override
    public void activate(OnLocationChangedListener onLocationChangedListener) {
        this.locationSourceListener = onLocationChangedListener;
    }
    @Override public void deactivate() {}

    protected void positionCamera() {
        this.positionCamera(null);
    }

    protected void positionCamera(LatLng positionIn){
        if(this.currentPosition != null) {
            if (this.flag != null) {
                LatLng topPosition = this.flag.getPosition();
                LatLng bottomPosition = (positionIn == null) ? this.currentPosition : positionIn;
                double distanceToGreen = SphericalUtil.computeDistanceBetween(bottomPosition, topPosition);
                float zoomLevel = Calculator.getZoom(this.findViewById(R.id.courseMap).getMeasuredHeight(), distanceToGreen);

                this.googleMap.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                                CameraPosition.builder()
                                        .target(
                                                LatLngBounds.builder()
                                                        .include(bottomPosition)
                                                        .include(topPosition)
                                                        .build().getCenter())
                                        .zoom(zoomLevel)
                                        .bearing((float) SphericalUtil.computeHeading(bottomPosition, topPosition)).build()
                        )
                );
            } else {
                this.googleMap.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                                CameraPosition.builder()
                                        .target(this.currentPosition)
                                        .zoom(this.defaultZoom).build()
                        )
                );
            }
        }
    }

    protected void updatePanel(){
        if(this.currentPosition != null && currentLocation!= null && this.flag != null) {
            double distance = SphericalUtil.computeDistanceBetween(this.currentPosition, this.flag.getPosition());
            this.layoutDriver.setGreenDistance(Calculator.getYards(distance), Calculator.getYards((double) this.currentLocation.getAccuracy()));
        }

        if (this.waypoint != null) {
            this.waypoint.updatePanel();
        }
    }

    @Override
    public void onPause(){
        if(this.locationService != null){
            this.locationService.stop();
        }
        super.onPause();
    }

    @Override
    public void onResume(){
        if(this.locationService != null){
            this.locationService.start();
        }
        super.onResume();
    }

    protected class Waypoint{
        protected LatLng pos;
        protected Marker marker;
        protected Polyline polyLine;

        protected Waypoint(LatLng pos){
            this.pos = pos;
            this.init();
        }

        protected void init(){
            this.marker = TrackingMapActivity.this.googleMap.addMarker(
                    new MarkerOptions()
                            .position(pos)
                            .icon(BitmapDescriptorFactory.fromBitmap(TrackingMapActivity.this.waypointIcon))
                            .anchor(0.5f,0.5f)
            );
            this.marker.setDraggable(true);
            this.updatePanel();
        }

        protected void clear(){
            if(this.polyLine != null) {
                this.polyLine.remove();
            }
            if(this.marker != null) {
                this.marker.remove();
            }
            TrackingMapActivity.this.layoutDriver.clearWayPoint();
        }

        protected void onDrag(Marker marker){
            this.marker = marker;
            this.pos = marker.getPosition();
            this.updatePanel();
        }

        protected void updatePosition(LatLng pos) {
            this.pos = pos;
            this.marker.setPosition(pos);
            this.updatePanel();
        }

        protected void updatePanel() {

            LatLng greenPosition = TrackingMapActivity.this.flag.getPosition();
            TrackingMapActivity.this.layoutDriver.setWayPoint(
                    Calculator.getYards(SphericalUtil.computeDistanceBetween(this.pos, greenPosition)),
                    Calculator.getYards(SphericalUtil.computeDistanceBetween(TrackingMapActivity.this.currentPosition, this.pos)));

            if(this.polyLine != null) {
                this.polyLine.setPoints(Arrays.asList(new LatLng[]{TrackingMapActivity.this.currentPosition,
                        this.pos, greenPosition}));
            }
            else{
                this.polyLine = TrackingMapActivity.this.googleMap.addPolyline(
                        new PolylineOptions()
                                .add(TrackingMapActivity.this.currentPosition, pos, greenPosition)
                                .color(Color.WHITE)
                                .endCap(new RoundCap())
                                .width(TrackingMapActivity.this.waypointLinePx)
                );
            }
        }

        protected boolean equals(Marker marker){
            return marker.equals(this.marker);
        }
    }

}