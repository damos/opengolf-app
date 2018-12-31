package ca.dait.opengolf.app.activities.coursemap;

import android.graphics.Color;
import android.os.Bundle;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.maps.android.SphericalUtil;

import java.util.Arrays;

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.app.utlis.Calculator;

/**
 * Created by darinamos on 2018-12-29.
 */

public class FreeRoamMapActivity extends TrackingMapActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.layoutDriver.showSpinner();

        SupportMapFragment mapFragment = (SupportMapFragment)
                this.getSupportFragmentManager().findFragmentById(R.id.courseMap);
        mapFragment.getMapAsync(this);
    }

    public void onMapReady(GoogleMap googleMap) {
        super.onMapReady(googleMap);
        this.startGpsUpdates();
    }

    @Override
    protected void setInteractiveListeners() {
        super.setInteractiveListeners();

        this.googleMap.setOnMapClickListener((position) -> {
            if(this.waypoint != null){
                this.waypoint.updatePosition(position);
            }
            else if(this.flag == null){
                this.waypoint = new FlagWaypoint(position);
                this.layoutDriver.showGolfHoleButton();
            }
            else{
                this.waypoint = new Waypoint(position);
                this.layoutDriver.showWayPointAction();
            }
            this.layoutDriver.showCancelable();
        });

        //Set the flag as the current waypoint. Explicitly remove
        this.layoutDriver.setGolfHoleButtonListener((view) -> {
            UiSettings uiSettings = this.googleMap.getUiSettings();
            uiSettings.setScrollGesturesEnabled(false);
            uiSettings.setRotateGesturesEnabled(false);

            this.flag = this.waypoint.marker;
            this.flag.setDraggable(false);
            this.waypoint.polyLine.remove();
            this.waypoint = null;
            this.layoutDriver.hideGolfHoleButton();
            this.cameraOverride = false;
            this.positionCamera();
        });
    }

    @Override
    protected void userAction(){
        super.userAction();

        //Enable extra map controls
        UiSettings uiSettings = this.googleMap.getUiSettings();
        uiSettings.setScrollGesturesEnabled(true);
        uiSettings.setRotateGesturesEnabled(true);
    }

    @Override
    protected void cancelUserAction(){
        if(this.waypoint != null){
            this.clearWayPoint();
            this.layoutDriver.hideGolfHoleButton();
        }
        else if(this.flag != null && !this.cameraOverride) {
            this.flag.remove();
            this.flag = null;
            this.layoutDriver.setGreenDistance(-1);
        }

        UiSettings uiSettings = this.googleMap.getUiSettings();
        uiSettings.setScrollGesturesEnabled(this.flag == null);
        uiSettings.setRotateGesturesEnabled(this.flag == null);

        super.cancelUserAction();

        if(!this.cameraOverride && this.waypoint == null && this.flag == null){
            this.layoutDriver.clearCancelable();
        }
        this.positionCamera();
    }

    private class FlagWaypoint extends Waypoint{
        private FlagWaypoint(LatLng pos){
            super(pos);
        }

        @Override
        protected void init(){
            this.marker = FreeRoamMapActivity.this.googleMap.addMarker(
                    new MarkerOptions()
                            .position(pos)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_golf_flag))
                            .anchor(FreeRoamMapActivity.this.anchorFlagIconX, FreeRoamMapActivity.this.anchorFlagIconY)
            );
            this.marker.setDraggable(true);
            this.polyLine = FreeRoamMapActivity.this.googleMap.addPolyline(
                    new PolylineOptions()
                            .add(FreeRoamMapActivity.this.currentPosition, pos)
                            .color(Color.WHITE)
                            .endCap(new RoundCap())
                            .width(FreeRoamMapActivity.this.waypointLinePx)
            );
            this.updateDisplayText();
        }

        @Override
        protected void clear(){
            FreeRoamMapActivity.this.layoutDriver.setGreenDistance(-1);
            this.marker.remove();
            this.polyLine.remove();
        }

        private void updateDisplayText(){
            double distance = SphericalUtil.computeDistanceBetween(FreeRoamMapActivity.this.currentPosition, this.marker.getPosition());
            FreeRoamMapActivity.this.layoutDriver.setGreenDistance(Calculator.getYards(distance),
                    Calculator.getYards((double) FreeRoamMapActivity.this.currentLocation.getAccuracy()));
        }

        @Override
        protected void updatePanel() {
            this.updateDisplayText();

            if(this.polyLine != null) {
                this.polyLine.setPoints(
                        Arrays.asList(FreeRoamMapActivity.this.currentPosition, this.marker.getPosition()));
            }
            else{
                this.polyLine = FreeRoamMapActivity.this.googleMap.addPolyline(
                        new PolylineOptions()
                                .add(FreeRoamMapActivity.this.currentPosition, this.marker.getPosition())
                                .color(Color.WHITE)
                                .endCap(new RoundCap())
                                .width(FreeRoamMapActivity.this.waypointLinePx)
                );
            }
        }
    }
}
