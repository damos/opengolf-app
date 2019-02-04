package ca.dait.opengolf.app.drivers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.app.activities.MainActivity;
import ca.dait.opengolf.app.activities.MenuOverlayActivity;
import ca.dait.opengolf.app.utlis.JsonRepository;
import ca.dait.opengolf.entities.course.Course;

/**
 * Created by darinamos on 2019-01-16.
 */

public class CreateCourseMapDriver extends AbstractInteractiveMapDriver {
    private boolean canResume = false;
    private String placeId;
    private String facilityName;

    private int currentHole;
    private Marker currentFlag;
    private List<Marker> flags = new ArrayList<>();

    public CreateCourseMapDriver(MainActivity mainActivity, GoogleMap googleMap) {
        super(mainActivity, googleMap);

        try{
            Intent intent = new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_OVERLAY)
                    .setFilter(new AutocompleteFilter.Builder()
                            .setTypeFilter(AutocompleteFilter.TYPE_FILTER_ESTABLISHMENT).build())
                    .build(this.mainActivity);
            this.mainActivity.startActivityForResult(intent, MainActivity.ACTIVITY_RESULT_MAP_DRIVER);
        } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
            // TODO: Handle the error...
            this.mainActivity.showMainMenuOverlay();
        }
    }

    @Override
    public boolean canRestart(Intent intent){
        return intent.getIntExtra(MenuOverlayActivity.INTENT_EXTRA_RESULT, -1) == MenuOverlayActivity.INTENT_RESULT_CREATE_COURSE
                && this.canResume;
    }

    public void onActivityResult(int resultCode, Intent data) {
        if(resultCode == Activity.RESULT_OK){
            Place place = PlaceAutocomplete.getPlace(this.mainActivity, data);
            this.placeId = place.getId();
            this.facilityName = place.getName().toString();

            final EditText nickName = this.getPanel(Panel.NICK_NAME);
            nickName.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
                if(actionId == EditorInfo.IME_ACTION_DONE){
                    View view = this.mainActivity.getCurrentFocus();
                    if(view != null && view.equals(nickName)) {
                        view.clearFocus();
                        InputMethodManager inputManager = (InputMethodManager) this.mainActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if(inputManager != null) {
                            inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                        }
                    }
                }
                return false;
            });
            this.setClickListener(Button.START, view -> {
                this.startCourseDefinition();
                Toast.makeText(this.mainActivity, "Place the first flag!", Toast.LENGTH_SHORT).show();
                //TODO: Enable gestures and click listeners
            });

            LatLng latLng = place.getLatLng();
            LatLngBounds bounds = place.getViewport();

            this.googleMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.builder()
                        .target(place.getLatLng())
                        .zoom(Float.valueOf(this.mainActivity.getString(R.string.zoomDefineCourseFound)))
                        .build()
                )
            );

            this.googleMap.setOnMapLoadedCallback(() -> {
                this.googleMap.setOnMapLoadedCallback(null);
                this.showText(Panel.COURSE_TITLE, this.facilityName);
                this.show(Panel.NICK_NAME);
                this.show(Button.START);
                this.canResume = true;
            });
        }
        else{
            //Go back to main menu.
            this.mainActivity.showMainMenuOverlay();
        }
    }

    protected void startCourseDefinition(){
        this.currentHole = 0;
        if(this.flags.size() > 0){
            this.currentFlag = this.flags.get(0);
        }

        this.googleMap.setOnCameraMoveStartedListener((reason) -> {
            if(reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE && this.currentFlag != null){
                this.show(Button.FLAG);
            }
        });

        this.setClickListener(Button.DONE, view -> {
            LatLngBounds.Builder boundsBuilder = LatLngBounds.builder();
            this.flags.forEach(marker ->{
                boundsBuilder.include(marker.getPosition());
            });

            this.googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(
                            boundsBuilder.build(),
                            this.mainActivity.getResources().getInteger(R.integer.coursePreviewPadding))
            );

            this.setClickListener(Button.DONE, view2 ->{
                this.showRefreshing(true);
                List<Course.Hole> holes = this.flags.stream()
                        .map(flag ->{
                            LatLng pos = flag.getPosition();
                            return new Course.Hole(pos.latitude, pos.longitude);
                        }).collect(Collectors.toList());

                Course course = new Course();
                course.setGooglePlaceId(this.placeId);
                course.setHoles(holes.toArray(new Course.Hole[holes.size()]));

                JsonRepository repo = JsonRepository.getInstance(this.mainActivity.getApplicationContext());
                repo.insert(JsonRepository.TYPE_COURSE, course, ids ->{
                    this.canResume = false;
                    this.mainActivity.showMainMenuOverlay();
                });
            });

            this.setClickListener(Button.CANCEL, view2 ->{
                this.startCourseDefinition();
            });
            this.setMapGestures(false);
            this.show(Panel.NICK_NAME);
            this.showText(Panel.COURSE_TITLE, this.facilityName);
            this.showOnly(Button.DONE, Button.CANCEL);
        });

        this.setClickListener(Button.FLAG, view -> {
            this.positionCamera();
        });

        this.setClickListener(Button.CANCEL, view -> {
            if(this.currentFlag != null){
                this.flags.remove(this.currentFlag);
                this.currentFlag.remove();
                this.currentFlag = null;
                this.updateCoordinates();
                this.updateButtonPanel();
                this.positionCamera();
            }
        });

        this.setClickListener(Button.NEXT, view -> {
            this.currentHole++;
            if((this.currentHole + 1) > this.flags.size()){
                this.currentFlag.setDraggable(false);
                this.currentFlag = null;
            }
            else{
                this.currentFlag = this.flags.get(this.currentHole);
                this.currentFlag.setDraggable(true);
            }
            this.setHoleNo(this.currentHole + 1);
            this.updateButtonPanel();
            this.updateCoordinates();
            this.positionCamera();
        });

        this.setClickListener(Button.PREVIOUS, view -> {
            this.currentHole--;
            if(this.currentFlag != null){
                this.currentFlag.setDraggable(false);
            }
            this.currentFlag = this.flags.get(this.currentHole);
            this.currentFlag.setDraggable(true);
            this.setHoleNo(this.currentHole + 1);
            this.updateButtonPanel();
            this.updateCoordinates();
            this.positionCamera();
        });

        this.googleMap.setOnMapClickListener(position -> {
            if(this.currentFlag == null){
                this.currentFlag = this.googleMap.addMarker(
                        new MarkerOptions()
                                .position(position)
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_golf_flag))
                                .anchor(this.anchorFlagIconX, this.anchorFlagIconY));
                this.currentFlag.setDraggable(true);
                this.flags.add(this.currentHole, this.currentFlag);
                this.positionCamera(position);
                Toast.makeText(this.mainActivity, "Click, hold & drag to accurately place.",Toast.LENGTH_SHORT).show();
            }
            this.updateCoordinates();
            this.updateButtonPanel();
        });

        this.googleMap.setOnMarkerDragListener(new VerticalShiftMarkerDragListener(marker -> {
            this.updateCoordinates();
        }));

        //Disable marker click.
        this.googleMap.setOnMarkerClickListener(marker -> true);

        this.hide(Button.START);
        this.hide(Panel.COURSE_TITLE, Panel.NICK_NAME);
        this.setHoleNo(1);
        this.setMapGestures(true);
        this.updateButtonPanel();
        this.positionCamera();
    }

    protected void setMapGestures(boolean flag){
        UiSettings uiSettings = this.googleMap.getUiSettings();
        uiSettings.setScrollGesturesEnabled(flag);
        uiSettings.setRotateGesturesEnabled(flag);
        uiSettings.setZoomGesturesEnabled(flag);
    }

    protected void positionCamera(){
        if(this.currentFlag != null){
            this.positionCamera(this.currentFlag.getPosition());
        }
        else{
            this.positionCamera(null);
        }
    }

    protected void positionCamera(LatLng position){
        if(position != null){
            this.googleMap.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                            CameraPosition.builder()
                                    .target(position)
                                    .zoom(20.0f).build()
                    ));
        }
        else{
            this.googleMap.animateCamera(CameraUpdateFactory.zoomTo(17.5f));
        }
        this.hide(Button.FLAG);
    }

    protected void updateCoordinates(){
        if(this.currentFlag != null){
            LatLng position = this.currentFlag.getPosition();
            this.showText(Panel.COURSE_TITLE, position.latitude + ", " + position.longitude);
        }
        else{
            this.hide(Panel.COURSE_TITLE);
        }
    }

    protected void updateButtonPanel(){
        if(((this.currentHole + 1) == 18) ||
                (this.currentHole > 0 && this.currentFlag == null)){
            this.show(Button.PREVIOUS);
            this.hide(Button.NEXT);
        }
        else if(this.currentHole <= 0 && this.currentFlag == null) {
            this.hide(Button.PREVIOUS, Button.NEXT);
        }
        else if(this.currentHole <= 0 && this.currentFlag != null){
            this.hide(Button.PREVIOUS);
            this.show(Button.NEXT);
        }
        else if(this.currentHole > 0 && this.currentFlag != null){
            this.show(Button.PREVIOUS, Button.NEXT);
        }

        if((this.currentHole + 1) == this.flags.size() && this.currentFlag != null){
            this.show(Button.CANCEL);
        }
        else{
            this.hide(Button.CANCEL);
        }

        if(this.flags.size() > 0){
            this.show(Button.DONE);
        }
        else{
            this.hide(Button.DONE);
        }
    }

}
