package ca.dait.opengolf.app.activities.coursemap;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.app.gps.LocationService;
import ca.dait.opengolf.app.http.EntityRequest;
import ca.dait.opengolf.app.http.OpenGolfRequestQueue;
import ca.dait.opengolf.entities.course.CourseDetails;

public class CourseMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private CourseDetails courseDetails;
    private MapDriver courseDriver;

    private LocationService locationService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course_map);

        String courseId = this.getIntent().getStringExtra(this.getString(R.string.courseId));
        String url = String.format(this.getString(R.string.url_getCourse), courseId);

        EntityRequest<CourseDetails> request =
            new EntityRequest<>(CourseDetails.class, Request.Method.GET, url,
                new Response.Listener<CourseDetails>() {
                    @Override
                    public void onResponse(CourseDetails courseDetails) {
                        CourseMapActivity.this.courseDetails = courseDetails;
                        TextView titleView = CourseMapActivity.this.findViewById(R.id.titleTextView);
                        titleView.setText(courseDetails.getName());

                        SupportMapFragment mapFragment = (SupportMapFragment)
                                CourseMapActivity.this.getSupportFragmentManager().findFragmentById(R.id.courseMap);
                        mapFragment.getMapAsync(CourseMapActivity.this);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(CourseMapActivity.this, "Unable to load course.",
                                Toast.LENGTH_SHORT).show();
                        CourseMapActivity.this.finish();
                    }
                });

        OpenGolfRequestQueue.send(request);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can send markers or lines, send listeners or move the camera. In this case,
     * we just send a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {

        LayoutDriver controlPanel = new LayoutDriver(this);

        this.courseDriver = new MapDriver(this, this.findViewById(R.id.courseMap),
                this.courseDetails, googleMap, controlPanel);

        this.courseDriver.drawPreview();

        FloatingActionButton startButton = this.findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                v.setVisibility(View.GONE);
                CourseMapActivity.this.start();
            }
        });
    }

    private void start(){
        //Set max brightness. Golfers are usually outdoors in extreme brightness.
        Window window = this.getWindow();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        window.setAttributes(layoutParams);

        this.courseDriver.start();

        Resources resources = this.getResources();

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(resources.getInteger(R.integer.locationInterval));
        locationRequest.setFastestInterval(resources.getInteger(R.integer.locationFastestInterval));
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        this.locationService = new LocationService(this, locationRequest, this.courseDriver);
        this.locationService.start();
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

    @Override
    public void onRequestPermissionsResult (int requestCode, String[] permissions, int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(LocationService.checkPermissions(requestCode, permissions, grantResults)){
            //Re-try start after user accepts permissions.
            this.locationService.start();
        }
        else{
            LocationService.noPermissionsFinish(this);
        }
    }

}
