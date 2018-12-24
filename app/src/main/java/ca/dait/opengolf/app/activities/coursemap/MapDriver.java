package ca.dait.opengolf.app.activities.coursemap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.view.View;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
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

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.app.utlis.BitmapFactory;
import ca.dait.opengolf.app.utlis.Calculator;
import ca.dait.opengolf.entities.course.CourseDetails;

/**
 * Created by darinamos on 2018-03-11.
 */
public class MapDriver extends LocationCallback implements GoogleMap.OnMapClickListener, LocationSource {

    private View mapView;
    private Context context;
    private CourseDetails courseDetails;
    private GoogleMap googleMap;

    private LayoutDriver layoutDriver;

    private int currentHoleNo = 0;
    private Marker[] courseMarkers;
    private Waypoint waypoint;

    private Location currentLocation;
    private LatLng currentPosition;
    private double distanceToGreen = -1;

    private OnLocationChangedListener locationSourceListener;

    private boolean cameraOverride = false;

    //private final Bitmap flagIcon;
    private final Bitmap selectPointIcon;

    @SuppressLint("MissingPermission")
    protected MapDriver(Context context, View mapView, CourseDetails courseDetails, GoogleMap googleMap, LayoutDriver controlPanel){

        //this.flagIcon = BitmapFactory.fromDrawable(R.drawable.ic_golf, context.getResources());
        this.selectPointIcon = BitmapFactory.fromDrawable(R.drawable.ic_waypoint, context.getResources());

        this.context = context;
        this.mapView = mapView;
        this.courseDetails = courseDetails;
        this.googleMap = googleMap;
        this.googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        UiSettings uiSettings = this.googleMap.getUiSettings();
        uiSettings.setMapToolbarEnabled(false);
        uiSettings.setCompassEnabled(false);
        uiSettings.setRotateGesturesEnabled(false);
        uiSettings.setTiltGesturesEnabled(false);
        uiSettings.setMyLocationButtonEnabled(false);
        uiSettings.setScrollGesturesEnabled(false);
        uiSettings.setZoomControlsEnabled(false);
        uiSettings.setZoomGesturesEnabled(false);

        //TODO: Should this be here? Technically we need to re-check permissions.
        this.googleMap.setLocationSource(this);
        this.googleMap.setMyLocationEnabled(true);

        this.googleMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
            @Override
            public void onCameraMoveStarted(int reason) {
                if(reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE){
                    MapDriver.this.userAction();
                }
            }
        });

        this.googleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener(){
            @Override public boolean onMarkerClick(Marker marker){
                if(MapDriver.this.waypoint != null && MapDriver.this.waypoint.equals(marker)){
                    MapDriver.this.clearWayPoint();
                }
                return true; //Always return true to disable default behavior (center marker in map)
            }
        });

        this.layoutDriver = controlPanel;
        this.layoutDriver.setPreviousListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MapDriver.this.goToHole(MapDriver.this.currentHoleNo - 1);
            }
        });
        this.layoutDriver.setNextListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MapDriver.this.goToHole(MapDriver.this.currentHoleNo + 1);
            }
        });
        this.layoutDriver.setCancelListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MapDriver.this.cancelUserAction();
                MapDriver.this.repositionCamera();
            }
        });
    }

    private void userAction(){
        this.cameraOverride = true;
        this.layoutDriver.showCancelable();

        //Enable extra map controls
        UiSettings uiSettings = this.googleMap.getUiSettings();
        uiSettings.setScrollGesturesEnabled(true);
        uiSettings.setRotateGesturesEnabled(true);
    }

    private void cancelUserAction(){
        this.cameraOverride = false;
        this.layoutDriver.clearCancelable();

        if(this.waypoint != null){
            this.waypoint.clear();
            this.waypoint = null;
        }

        //Disable extra map controls
        UiSettings uiSettings = this.googleMap.getUiSettings();
        uiSettings.setScrollGesturesEnabled(false);
        uiSettings.setRotateGesturesEnabled(false);
    }

    protected void drawPreview(){
        CourseDetails.Hole holes[] = this.courseDetails.getHoles();
        this.courseMarkers = new Marker[holes.length];
        final LatLngBounds.Builder boundsBuilder = LatLngBounds.builder();

        for(int i = 0; i < holes.length; i++){
            LatLng pos = new LatLng(holes[i].getLat(), holes[i].getLon());
            boundsBuilder.include(pos);

            this.courseMarkers[i] = this.googleMap.addMarker(
                    new MarkerOptions()
                            .position(pos)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_golf_flag))
                            //TODO: Set these externally in config.
                            .anchor(.27f, .95f)
            );
        }

        float previewZoom = Float.valueOf(this.context.getString(R.string.coursePreviewZoom));
        MapDriver.this.googleMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(boundsBuilder.build().getCenter(), previewZoom)
        );
        MapDriver.this.googleMap.setMinZoomPreference(previewZoom);
    }

    /**
     * Clears the preview screen and starts the first hole.
     */
    public void start(){
        this.layoutDriver.start();
        for(Marker m : this.courseMarkers){
            m.setVisible(false);
        }
        this.goToHole(0);
        this.googleMap.setOnMapClickListener(this);
        UiSettings uiSettings = this.googleMap.getUiSettings();
        uiSettings.setZoomGesturesEnabled(true);
    }

    @Override
    public void onLocationResult(LocationResult locationResult) {
        //Update geo position
        this.currentLocation = locationResult.getLastLocation();
        this.currentPosition = new LatLng(this.currentLocation.getLatitude(), this.currentLocation.getLongitude());
        if(this.locationSourceListener != null){
            this.locationSourceListener.onLocationChanged(this.currentLocation);
        }

        this.drawPanel();
        this.repositionCamera();
    }

    public void drawPanel(){
        if(this.currentPosition != null) {
            Marker currentHoleMarker = this.courseMarkers[this.currentHoleNo];
            this.distanceToGreen = SphericalUtil.computeDistanceBetween(this.currentPosition, currentHoleMarker.getPosition());
            this.layoutDriver.setGreenDistance(Calculator.getYards(this.distanceToGreen), Calculator.getYards((double) this.currentLocation.getAccuracy()));

            if (this.waypoint != null) {
                this.waypoint.draw();
            }
        }
    }

    @Override
    public void activate(OnLocationChangedListener onLocationChangedListener) {
        this.locationSourceListener = onLocationChangedListener;
    }
    @Override public void deactivate() {}


    private void repositionCamera(){
        if(!this.cameraOverride && this.currentPosition != null) {
            Marker currentHoleMarker = this.courseMarkers[this.currentHoleNo];
            float zoomLevel = Calculator.getZoom(this.mapView.getMeasuredHeight(), this.distanceToGreen);

            this.googleMap.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                            CameraPosition.builder()
                                    .target(
                                            LatLngBounds.builder()
                                                    .include(this.currentPosition)
                                                    .include(currentHoleMarker.getPosition())
                                                    .build().getCenter())
                                    .zoom(zoomLevel)
                                    .bearing((float) SphericalUtil.computeHeading(
                                            this.currentPosition,
                                            currentHoleMarker.getPosition())).build()
                    )
            );
            this.googleMap.setMinZoomPreference(zoomLevel);
        }
    }

    private void goToHole(int newHoleNo){
        this.courseMarkers[this.currentHoleNo].setVisible(false);
        this.courseMarkers[newHoleNo].setVisible(true);
        this.currentHoleNo = newHoleNo;

        this.layoutDriver.setHoleNo(newHoleNo + 1);

        if(newHoleNo <= 0){
            this.layoutDriver.setNavState(LayoutDriver.NavState.BEGINNING);
        }
        else if(newHoleNo >= this.courseDetails.getHoles().length - 1){
            this.layoutDriver.setNavState(LayoutDriver.NavState.END);
        }
        else{
            this.layoutDriver.setNavState(LayoutDriver.NavState.MIDDLE);
        }

        //Always reset the zoom and map when holes change
        this.cancelUserAction();
        this.drawPanel();
        this.repositionCamera();
    }

    @Override
    public void onMapClick(LatLng latLng) {
        if(this.waypoint == null){
            this.waypoint = new Waypoint(latLng);
        }
        else{
            this.waypoint.clear();
            this.waypoint = new Waypoint(latLng);
        }
        this.layoutDriver.showCancelable();
        this.drawPanel();
    }

    public void clearWayPoint(){
        if(this.waypoint != null) {
            this.waypoint.clear();
            this.waypoint = null;
        }
        //If the camera hasn't been moved manually, we can remove the cancel UI
        if(!this.cameraOverride){
            this.layoutDriver.clearCancelable();
        }
    }

    private class Waypoint {
        private final LatLng pos;

        private Marker marker;
        private Polyline polyLine;

        private Waypoint(LatLng pos){
            this.pos = pos;
        }

        private void clear(){
            if(this.polyLine != null) {
                this.polyLine.remove();
            }
            if(this.marker != null) {
                this.marker.remove();
            }
            MapDriver.this.layoutDriver.clearWayPoint();
        }

        private void draw() {
            if(this.polyLine != null) {
                this.polyLine.remove();
            }
            if(this.marker == null){
                this.marker = MapDriver.this.googleMap.addMarker(
                        new MarkerOptions()
                                .position(pos)
                                .icon(BitmapDescriptorFactory.fromBitmap(MapDriver.this.selectPointIcon))
                                .anchor(0.5f,0.5f)
                );
            }

            LatLng greenPosition = MapDriver.this.courseMarkers[MapDriver.this.currentHoleNo].getPosition();

            MapDriver.this.layoutDriver.setWayPoint(
                    Calculator.getYards(SphericalUtil.computeDistanceBetween(this.pos, greenPosition)),
                    Calculator.getYards(SphericalUtil.computeDistanceBetween(MapDriver.this.currentPosition, this.pos)));

            this.polyLine = MapDriver.this.googleMap.addPolyline(
                    new PolylineOptions()
                            .add(MapDriver.this.currentPosition, pos, greenPosition)
                            .color(Color.WHITE)
                            .endCap(new RoundCap())
                            .width(15)
            );
        }

        private boolean equals(Marker marker){
            return marker.equals(this.marker);
        }
    }
}
