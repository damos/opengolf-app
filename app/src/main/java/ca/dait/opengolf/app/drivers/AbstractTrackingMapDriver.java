package ca.dait.opengolf.app.drivers;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.view.WindowManager;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
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
import ca.dait.opengolf.app.activities.MainActivity;
import ca.dait.opengolf.app.network.LocationService;
import ca.dait.opengolf.app.utlis.BitmapFactory;
import ca.dait.opengolf.app.utlis.Calculator;

/**
 * Created by darinamos on 2019-01-15.
 */

public abstract class AbstractTrackingMapDriver extends AbstractInteractiveMapDriver {

    protected Bitmap waypointIcon;
    protected float waypointLinePx;

    protected boolean cameraOverride = false;
    protected LocationService locationService;

    protected Location currentLocation;
    protected LatLng currentPosition;
    protected Flag flag;
    protected Waypoint waypoint;

    public AbstractTrackingMapDriver(MainActivity mainActivity, GoogleMap googleMap){
        super(mainActivity, googleMap);
        this.waypointIcon = BitmapFactory.fromDrawable(R.drawable.ic_waypoint, this.mainActivity.getResources());
        this.waypointLinePx = Calculator.getPixelsFromDp(Float.valueOf(this.mainActivity.getString(R.string.waypointLineDp)));
    }

    protected void start(){
        Resources resources = this.mainActivity.getResources();
        TrackingLocationCallback callback = new TrackingLocationCallback();
        this.googleMap.setLocationSource(callback);

        this.locationService = new LocationService(
            this.mainActivity,
            this.googleMap,
            LocationRequest.create()
                    .setInterval(resources.getInteger(R.integer.locationInterval))
                    .setFastestInterval(resources.getInteger(R.integer.locationFastestInterval))
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY),
            callback);

        this.mainActivity.setLocationService(locationService);
        locationService.start();
        this.showRefreshing(true);

        //Set max brightness. Golfers are usually outdoors in extreme brightness.
        this.brightnessOverride(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL);
    }

    @Override
    public void stop(){
        super.stop();
        this.brightnessOverride(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE);
    }

    @Override
    public void restart(){
        super.restart();
        this.brightnessOverride(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL);
    }

    public void pause(){
        if(this.locationService != null){
            this.locationService.stop();
        }
    }

    public void resume(){
        if(this.locationService != null){
            this.locationService.start();
        }
    }

    /**
     * Handles the GPS updates as they come in.
     */
    protected class TrackingLocationCallback extends LocationCallback implements LocationSource{
        private LocationSource.OnLocationChangedListener locationSourceListener;

        @Override
        public void onLocationResult(LocationResult locationResult) {
            //If this is the first location update, set the listeners and hide the refresh
            //spinner when the map is done loading
            if(AbstractTrackingMapDriver.this.currentLocation == null){
                //If this is the first location, hide the spinner and set the listeners.
                AbstractTrackingMapDriver.this.googleMap.setOnMapLoadedCallback(() ->{
                    AbstractTrackingMapDriver.this.googleMap.setOnMapLoadedCallback(null);
                    AbstractTrackingMapDriver.this.onReady();
                });
            }

            Location newLocation = locationResult.getLastLocation();
            AbstractTrackingMapDriver.this.currentLocation = newLocation;
            AbstractTrackingMapDriver.this.currentPosition = new LatLng(
                    newLocation.getLatitude(),
                    newLocation.getLongitude());
            AbstractTrackingMapDriver.this.mainActivity.setLastPosition(AbstractTrackingMapDriver.this.currentPosition);

            AbstractTrackingMapDriver.this.updatePanel();
            if(!AbstractTrackingMapDriver.this.cameraOverride) {
                AbstractTrackingMapDriver.this.positionCamera(AbstractTrackingMapDriver.this.currentPosition);
            }

            if(this.locationSourceListener != null){
                this.locationSourceListener.onLocationChanged(newLocation);
            }
        }

        @Override
        public void activate(OnLocationChangedListener onLocationChangedListener) {
            this.locationSourceListener = onLocationChangedListener;
        }
        @Override public void deactivate() {}
    }

    protected void onReady(){
        this.showRefreshing(false);
        this.googleMap.setOnMarkerDragListener(new VerticalShiftMarkerDragListener(marker ->{
            this.waypoint.onDrag(marker);
        }));

        this.googleMap.setOnMarkerClickListener((marker) -> {
            if(this.waypoint != null && this.waypoint.equals(marker)){
                this.clearWayPoint();
            }
            if(!this.cameraOverride){
                this.hide(Button.CANCEL);
            }
            return true;
        });
        this.googleMap.setOnCameraMoveStartedListener((reason) -> {
            if(reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE){
                this.userAction();
            }
        });
        this.setClickListener(Button.CANCEL, view -> {
            this.cancelUserAction();
            this.positionCamera();
        });
        this.setClickListener(Button.WAYPOINT, view -> {
            this.userAction();
            this.positionCamera(this.waypoint.pos);
        });
    }

    protected void userAction(){
        this.cameraOverride = true;
        this.show(Button.CANCEL);
    }

    protected void cancelUserAction(){
        this.cameraOverride = false;
    }
    protected void clearWayPoint(){
        if(this.waypoint != null) {
            this.waypoint.clear();
            this.waypoint = null;
            this.hide(Button.WAYPOINT);
        }
    }
    protected void updatePanel(){
        if(this.currentPosition != null && currentLocation!= null && this.flag != null) {
            double distance = SphericalUtil.computeDistanceBetween(this.currentPosition, this.flag.getPosition());
            this.setGreenDistance(distance, (double)this.currentLocation.getAccuracy());
        }

        if (this.waypoint != null) {
            this.waypoint.updatePanel();
        }
    }

    protected void setGreenDistance(double distanceInM, double accuracyInM){
        int distance = Calculator.getYards(distanceInM);
        int accuracy = Calculator.getYards(accuracyInM);
        if(accuracy > 1){
            this.showText(Panel.DISTANCE_1, distance + "±" + accuracy + "y");
        }
        else{
            this.showText(Panel.DISTANCE_1, distance + "y");
        }
    }

    protected void positionCamera() {
        this.positionCamera(null);
    }

    protected void positionCamera(LatLng positionIn){
        if(this.currentPosition != null) {
            if (this.flag != null) {
                LatLng topPosition = this.flag.getPosition();
                LatLng bottomPosition = (positionIn == null) ? this.currentPosition : positionIn;
                double distanceToGreen = SphericalUtil.computeDistanceBetween(bottomPosition, topPosition);
                float zoomLevel = Calculator.getZoom(this.mainActivity.findViewById(R.id.courseMap).getMeasuredHeight(), distanceToGreen);

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

    protected class Flag{
        private final LatLng position;
        private final Marker marker;
        private final Circle red;
        private final Circle white;
        private final Circle blue;

        public Flag(GoogleMap googleMap, Marker marker) {
            this.marker = marker;
            this.marker.setDraggable(false);
            this.position = marker.getPosition();

            this.red = googleMap.addCircle(new CircleOptions()
                    .strokeColor(mainActivity.getColor(R.color.stroke100y))
                    .radius(Calculator.getMeters(100))
                    .strokeWidth(Calculator.getPixelsFromDp(mainActivity.getResources().getInteger(R.integer.strokeYardageDp)))
                    .center(position));

            this.white = googleMap.addCircle(new CircleOptions()
                    .strokeColor(mainActivity.getColor(R.color.stroke150y))
                    .radius(Calculator.getMeters(150))
                    .strokeWidth(Calculator.getPixelsFromDp(mainActivity.getResources().getInteger(R.integer.strokeYardageDp)))
                    .center(position));

            this.blue = googleMap.addCircle(new CircleOptions()
                    .strokeColor(mainActivity.getColor(R.color.stroke200y))
                    .radius(Calculator.getMeters(200))
                    .strokeWidth(Calculator.getPixelsFromDp(mainActivity.getResources().getInteger(R.integer.strokeYardageDp)))
                    .center(position));
        }

        public Flag(GoogleMap googleMap, LatLng position){
            this(googleMap, googleMap.addMarker(
                new MarkerOptions()
                    .position(position)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_golf_flag))
                    .anchor(AbstractTrackingMapDriver.this.anchorFlagIconX,
                            AbstractTrackingMapDriver.this.anchorFlagIconY)));
        }

        public Flag(GoogleMap googleMap, LatLng position, boolean yardageLinesVisible){
            this(googleMap, googleMap.addMarker(
                    new MarkerOptions()
                            .position(position)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_golf_flag))
                            .anchor(AbstractTrackingMapDriver.this.anchorFlagIconX,
                                    AbstractTrackingMapDriver.this.anchorFlagIconY)));
            this.red.setVisible(yardageLinesVisible);
            this.white.setVisible(yardageLinesVisible);
            this.blue.setVisible(yardageLinesVisible);
        }

        protected LatLng getPosition(){
            return this.position;
        }

        protected void remove(){
            this.marker.remove();
            this.red.remove();
            this.white.remove();
            this.blue.remove();
        }

        protected void show(boolean flagOnly){
            this.marker.setVisible(true);
            this.red.setVisible(!flagOnly);
            this.white.setVisible(!flagOnly);
            this.blue.setVisible(!flagOnly);
        }

        protected void show(){
            this.show(false);
        }

        protected void hide(){
            this.marker.setVisible(false);
            this.red.setVisible(false);
            this.white.setVisible(false);
            this.blue.setVisible(false);
        }
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
            this.marker = AbstractTrackingMapDriver.this.googleMap.addMarker(
                    new MarkerOptions()
                            .position(pos)
                            .icon(BitmapDescriptorFactory.fromBitmap(AbstractTrackingMapDriver.this.waypointIcon))
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
            AbstractTrackingMapDriver.this.hide(Panel.DISTANCE_2, Panel.DISTANCE_3);
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

            LatLng greenPosition = AbstractTrackingMapDriver.this.flag.getPosition();
            int waypointToGreen = Calculator.getYards(SphericalUtil.computeDistanceBetween(this.pos, greenPosition));
            int currentLocToWaypoint =  Calculator.getYards(SphericalUtil.computeDistanceBetween(
                    AbstractTrackingMapDriver.this.currentPosition, this.pos));

            AbstractTrackingMapDriver.this.showText(Panel.DISTANCE_2, "▲ " + waypointToGreen + "y");
            AbstractTrackingMapDriver.this.showText(Panel.DISTANCE_3, "▼ " + currentLocToWaypoint + "y");

            if(this.polyLine != null) {
                this.polyLine.setPoints(Arrays.asList(AbstractTrackingMapDriver.this.currentPosition,
                        this.pos, greenPosition));
            }
            else{
                this.polyLine = AbstractTrackingMapDriver.this.googleMap.addPolyline(
                        new PolylineOptions()
                                .add(AbstractTrackingMapDriver.this.currentPosition, pos, greenPosition)
                                .color(Color.WHITE)
                                .endCap(new RoundCap())
                                .width(AbstractTrackingMapDriver.this.waypointLinePx)
                );
            }
        }

        protected boolean equals(Marker marker){
            return marker.equals(this.marker);
        }
    }
}
