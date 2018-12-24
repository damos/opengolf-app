package ca.dait.opengolf.app.activities.mainmenu;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.app.activities.coursemap.CourseMapActivity;
import ca.dait.opengolf.app.gps.LocationService;
import ca.dait.opengolf.app.http.EntityRequest;
import ca.dait.opengolf.app.http.OpenGolfRequestQueue;
import ca.dait.opengolf.entities.course.Course;
import ca.dait.opengolf.entities.course.CourseSearchResult;

public class MainMenuActivity extends AppCompatActivity {

    private LocationService locationService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //First thing the app does.
        OpenGolfRequestQueue.init(this.getApplicationContext());

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setNumUpdates(1);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        // TODO: User could take a while to respond to the promt.. might have to re-consider how the LocationService is coded
        //locationRequest.setExpirationDuration(30000);

        this.locationService = new LocationService(this, locationRequest, new LocationCallback(){
            public void onLocationResult(LocationResult locationResult) {
                MainMenuActivity.this.showNearByCourses(locationResult.getLastLocation());
            }
        });
        this.locationService.start();
    }

    private void showNearByCourses(Location location){
        String urlTemplate = MainMenuActivity.this.getString(R.string.url_proximitySearch);
        String url = String.format(urlTemplate, location.getLatitude(), location.getLongitude());

        EntityRequest<CourseSearchResult> request =
            new EntityRequest<>(CourseSearchResult.class, Request.Method.GET, url,

                new Response.Listener<CourseSearchResult>() {
                    @Override
                    public void onResponse(CourseSearchResult response) {
                        ListView listView = MainMenuActivity.this.findViewById(R.id.courseListView);
                        listView.setAdapter(MainMenuActivity.this.new CourseListAdapter(response));
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(MainMenuActivity.this, "Unable to find nearby courses.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            );

        OpenGolfRequestQueue.send(request);
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

    private class CourseListAdapter extends BaseAdapter{

        private final CourseSearchResult searchResults;

        private CourseListAdapter(CourseSearchResult searchResults){
            this.searchResults = searchResults;
        }

        @Override
        public int getCount() {
            return searchResults.getResults().length;
        }

        @Override
        public Course getItem(int i) {
            return this.searchResults.getResults()[i];
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(final int i, View view, ViewGroup viewGroup) {
            Course course = searchResults.getResults()[i];

            //TODO: investigate this suggestion...
            view = getLayoutInflater().inflate(R.layout.course_list_row, null);
            view.setTag(R.string.courseId, course.getId());

            TextView tv = view.findViewById(R.id.courseName);
            tv.setText(course.getDetails().getName());

            Double distanceInM = course.getDistance();
            String altText = (distanceInM == null) ? course.getDetails().getCountry() :
                    course.getDetails().getCountry() + " - " + MainMenuActivity.getDistanceInKm(distanceInM) + "km";

            tv = view.findViewById(R.id.courseCountry);
            tv.setText(altText);

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view){
                    Intent intent = new Intent(MainMenuActivity.this, CourseMapActivity.class);
                    intent.putExtra(view.getResources().getString(R.string.courseId), (String)view.getTag(R.string.courseId));
                    startActivity(intent);
                }
            });

            return view;
        }
    }

    //Converts double in Meters to Km to 1 decimal.
    private static double getDistanceInKm(double distanceInM){
        return Math.round(distanceInM / 100) / 10d;
    }
}
