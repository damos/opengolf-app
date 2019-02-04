package ca.dait.opengolf.app.drivers;

import android.content.Intent;
import android.graphics.Color;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.maps.android.SphericalUtil;

import java.util.Arrays;

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.app.activities.MainActivity;
import ca.dait.opengolf.app.activities.MenuOverlayActivity;

/**
 * Created by darinamos on 2019-01-15.
 */

public class FreeRoamMapDriver extends AbstractTrackingMapDriver {
    public FreeRoamMapDriver(MainActivity mainActivity, GoogleMap googleMap){
        super(mainActivity, googleMap);
        this.start();
    }

    public boolean canRestart(Intent intent){
        return intent.getIntExtra(MenuOverlayActivity.INTENT_EXTRA_RESULT, -1) == MenuOverlayActivity.INTENT_RESULT_FREE_ROAM;
    }

    @Override
    protected void onReady() {
        super.onReady();

        this.googleMap.setOnMapClickListener((position) -> {
            if(this.waypoint != null){
                this.waypoint.updatePosition(position);
            }
            else if(this.flag == null){
                this.waypoint = new FlagWaypoint(position);
                this.show(Button.FLAG);
            }
            else{
                this.waypoint = new FreeRoamMapDriver.Waypoint(position);
                this.show(Button.WAYPOINT);
            }
            this.show(Button.CANCEL);
        });

        //Set the flag as the current waypoint. Explicitly remove
        this.setClickListener(Button.FLAG, view -> {
            UiSettings uiSettings = this.googleMap.getUiSettings();
            uiSettings.setScrollGesturesEnabled(false);
            uiSettings.setRotateGesturesEnabled(false);

            this.flag = new Flag(this.googleMap, this.waypoint.marker);
            this.waypoint.polyLine.remove();
            this.waypoint = null;
            this.hide(Button.FLAG);
            this.cameraOverride = false;
            this.positionCamera();

        });

        //Finally enable map controls
        UiSettings uiSettings = googleMap.getUiSettings();
        uiSettings.setRotateGesturesEnabled(true);
        uiSettings.setScrollGesturesEnabled(true);
        uiSettings.setZoomGesturesEnabled(true);
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
        }
        else if(this.flag != null && !this.cameraOverride) {
            this.flag.remove();
            this.flag = null;
            this.hide(Panel.DISTANCE_1);
        }

        UiSettings uiSettings = this.googleMap.getUiSettings();
        uiSettings.setScrollGesturesEnabled(this.flag == null);
        uiSettings.setRotateGesturesEnabled(this.flag == null);

        super.cancelUserAction();

        if(!this.cameraOverride && this.waypoint == null && this.flag == null){
            this.hide(Button.CANCEL);
        }
        this.positionCamera();
    }

    private class FlagWaypoint extends Waypoint{
        private FlagWaypoint(LatLng pos){
            super(pos);
        }

        @Override
        protected void init(){
            this.marker = FreeRoamMapDriver.this.googleMap.addMarker(
                    new MarkerOptions()
                            .position(pos)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_golf_flag))
                            .anchor(FreeRoamMapDriver.this.anchorFlagIconX, FreeRoamMapDriver.this.anchorFlagIconY)
            );
            this.marker.setDraggable(true);
            this.polyLine = FreeRoamMapDriver.this.googleMap.addPolyline(
                    new PolylineOptions()
                            .add(FreeRoamMapDriver.this.currentPosition, pos)
                            .color(Color.WHITE)
                            .endCap(new RoundCap())
                            .width(FreeRoamMapDriver.this.waypointLinePx)
            );
            this.updateDisplayText();
        }

        @Override
        protected void clear(){
            FreeRoamMapDriver.this.hide(Panel.DISTANCE_1);
            FreeRoamMapDriver.this.hide(Button.FLAG);

            this.marker.remove();
            this.polyLine.remove();
        }

        private void updateDisplayText(){
            double distance = SphericalUtil.computeDistanceBetween(FreeRoamMapDriver.this.currentPosition, this.marker.getPosition());
            FreeRoamMapDriver.this.setGreenDistance(distance, (double)FreeRoamMapDriver.this.currentLocation.getAccuracy());
        }

        @Override
        protected void updatePanel() {
            this.updateDisplayText();

            if(this.polyLine != null) {
                this.polyLine.setPoints(
                        Arrays.asList(FreeRoamMapDriver.this.currentPosition, this.marker.getPosition()));
            }
            else{
                this.polyLine = FreeRoamMapDriver.this.googleMap.addPolyline(
                    new PolylineOptions()
                        .add(FreeRoamMapDriver.this.currentPosition, this.marker.getPosition())
                        .color(Color.WHITE)
                        .endCap(new RoundCap())
                        .width(FreeRoamMapDriver.this.waypointLinePx)
                );
            }
        }
    }
}
