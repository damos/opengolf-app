package ca.dait.opengolf.app.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.CycleInterpolator;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.google.android.gms.location.places.GeoDataClient;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBufferResponse;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;
import com.google.maps.android.SphericalUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.app.network.EntityRequest;
import ca.dait.opengolf.app.network.RequestQueueManager;
import ca.dait.opengolf.app.utlis.Calculator;
import ca.dait.opengolf.app.utlis.JsonRepository;
import ca.dait.opengolf.entities.course.Course;
import ca.dait.opengolf.entities.course.CourseSearchResult;

public class MenuOverlayActivity extends FragmentActivity {

    public final static String INTENT_EXTRA_RESULT = "result";
    public final static String INTENT_EXTRA_COURSE = "course";
    public final static String INTENT_EXTRA_COURSE_ID = "courseId";
    public final static String INTENT_EXTRA_COURSE_REMOTE_ID = "remoteCourseId";
    public final static String INTENT_EXTRA_LOCATION = "Location";

    public final static int INTENT_RESULT_FREE_ROAM = 1;
    public final static int INTENT_RESULT_CREATE_COURSE = 2;
    public final static int INTENT_RESULT_EDIT_COURSE = 3;
    public final static int INTENT_RESULT_PLAY_COURSE = 4;

    protected LatLng position;

    protected RequestQueue requestQueue;
    protected JsonRepository repo;

    protected EditText searchBox;
    protected View courseListHeader;
    protected View courseListEmptyView;
    protected ListView courseListView;
    protected SwipeRefreshLayout refreshLayout;

    protected RadioGroup radioGroup;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu_overlay);
        this.position = this.getIntent().getParcelableExtra(INTENT_EXTRA_LOCATION);

        this.requestQueue = RequestQueueManager.getInstance(this.getApplicationContext());
        this.repo = JsonRepository.getInstance(this.getApplicationContext());

        this.searchBox = this.findViewById(R.id.searchBox);
        this.searchBox.setOnFocusChangeListener((View view, boolean hasFocus) ->{
            if(!hasFocus){
                InputMethodManager inputManager = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
                if(inputManager != null) {
                    inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        this.searchBox.setOnEditorActionListener((view, actionId, event) -> {
            if(actionId == EditorInfo.IME_ACTION_SEARCH){
                if(this.searchBox.getText().toString().trim().length() > 0){
                    this.showSearchResults();
                    return false;
                }
                else{
                    TranslateAnimation shake = new TranslateAnimation(-5,5, 0, 0);
                    shake.setDuration(300);
                    shake.setInterpolator(new CycleInterpolator(3));
                    this.searchBox.startAnimation(shake);
                    return true;
                }
            }
            return false;
        });

        this.courseListView = this.findViewById(R.id.courseListView);
        this.courseListEmptyView = this.findViewById(R.id.noResults);

        this.radioGroup = this.findViewById(R.id.radioGroup);
        this.radioGroup.setOnCheckedChangeListener((group, checkedId) ->{
            switch(checkedId){
                case R.id.radioButtonMyCourses:
                    this.showSavedCourses();
                    break;
                case R.id.radioButtonNearMe:
                    this.showNearMe();
                    break;
            }
        });


        this.refreshLayout = this.findViewById(R.id.swipeRefreshLayout);
        this.refreshLayout.setColorSchemeColors(this.getColor(R.color.colorPrimary),
                                                this.getColor(R.color.colorPrimaryDark));
        this.refreshLayout.setOnRefreshListener(() ->{
            switch(this.radioGroup.getCheckedRadioButtonId()){
                case R.id.radioButtonMyCourses:
                    this.refreshLayout.setRefreshing(false); //No need to swipe to refresh local storage
                    break;
                case R.id.radioButtonNearMe:
                    this.showNearMe();
                    break;
                default:
                    this.showSearchResults();
            }
        });

        this.courseListHeader = this.findViewById(R.id.createCourseDefinition);
        this.courseListHeader.setOnClickListener(view -> {
            Intent output = new Intent();
            output.putExtra(INTENT_EXTRA_RESULT, INTENT_RESULT_CREATE_COURSE);
            this.setResult(RESULT_OK, output);
            this.finish();
        });

        this.findViewById(R.id.buttonFreeRoam).setOnClickListener(view -> {
            Intent output = new Intent();
            output.putExtra(INTENT_EXTRA_RESULT, INTENT_RESULT_FREE_ROAM);
            this.setResult(RESULT_OK, output);
            this.finish();
        });

        this.showSavedCourses();
    }

    protected void showSavedCourses(){
        if(this.searchBox.hasFocus()){
            this.searchBox.clearFocus();
        }
        this.refreshLayout.setRefreshing(true);

        this.courseListEmptyView.setVisibility(View.GONE);

        this.repo.getAll(Course.class, JsonRepository.TYPE_COURSE, results ->{

            if(results.size() == 0){
                this.refreshLayout.setRefreshing(false);
                this.courseListView.setAdapter(new CourseListAdapter(false, true));
                return;
            }

            //A single google place ID could have multiple golf courses.
            //Build on-to-many map of placeId to courses.
            Map<String, List<JsonRepository.JsonEntity<Course>>> placeIdToCourses = new HashMap<>();
            results.forEach(entity ->{
                placeIdToCourses.computeIfAbsent(entity.ref.getGooglePlaceId(),
                        (key) -> new ArrayList<>()).add(entity);
            });

            //Fetch Place data.
            //TODO: Leverage caching.
            GeoDataClient client = Places.getGeoDataClient(this);
            client.getPlaceById(placeIdToCourses.keySet().toArray(new String[placeIdToCourses.size()]))
                    .addOnCompleteListener(task ->{
                        PlaceBufferResponse response = task.getResult();
                        response.forEach((Place place) -> {
                            Double distance = (this.position == null) ? null :
                                    SphericalUtil.computeDistanceBetween(this.position, place.getLatLng());
                            String address[] = place.getAddress().toString().split(",");
                            List<JsonRepository.JsonEntity<Course>> entities = placeIdToCourses.get(place.getId());
                            entities.forEach(entity -> {
                                entity.ref.setMunicipality(address[1]);
                                entity.ref.setCountry(address[3]);
                                entity.ref.setFacilityName(place.getName().toString());
                                entity.ref.setState(address[2].substring(1,3));
                                entity.ref.setDistance(distance);
                            });
                        });

                        List<JsonRepository.JsonEntity<Course>> finalCourses = new ArrayList<>();
                        placeIdToCourses.values().forEach(finalCourses::addAll);
                        if(this.position == null){
                            finalCourses.sort(Comparator.comparing((entity) -> entity.ref.getFacilityName()));
                        }
                        else{
                            finalCourses.sort(Comparator.comparing((entity) -> entity.ref.getDistance()));
                        }

                        this.setSavedCourseAdapter(finalCourses);
                        this.refreshLayout.setRefreshing(false);
                    });
        });
    }

    protected void showSearchResults(){
        this.radioGroup.clearCheck();
        if(this.searchBox.hasFocus()){
            this.searchBox.clearFocus();
        }
        String searchText = this.searchBox.getText().toString();
        String url = (this.position == null) ?
                String.format(this.getString(R.string.url_searchTerm), searchText) :
                String.format(this.getString(R.string.url_searchTermWithLoc), searchText, this.position.latitude, this.position.longitude);
        this.searchRemoteCourses(url);
    }

    protected void showNearMe(){
        if(this.searchBox.hasFocus()){
            this.searchBox.clearFocus();
        }
        String url = (this.position == null) ?
                this.getString(R.string.url_search) :
                String.format(this.getString(R.string.url_searchByLoc), this.position.latitude, this.position.longitude);
        this.searchRemoteCourses(url);
    }

    protected void searchRemoteCourses(String url){
        this.refreshLayout.setRefreshing(true);

        EntityRequest<CourseSearchResult> request =
            new EntityRequest<>(CourseSearchResult.class, Request.Method.GET, url,
                response ->{
                    this.setRemoteCourseAdapter(response.getResults());
                    this.refreshLayout.setRefreshing(false);
                },
                error ->{
                    this.refreshLayout.setRefreshing(false);
                    Toast.makeText(this, "Unable to find nearby courses.", Toast.LENGTH_SHORT).show();
                }
            );

        this.requestQueue.add(request);
    }

    @Override
    public void onBackPressed(){
        super.onBackPressed();
        this.overridePendingTransition(0,0);
    }

    protected void setSavedCourseAdapter(List<JsonRepository.JsonEntity<Course>> entities){
        CourseListAdapter<JsonRepository.JsonEntity<Course>> adapter =
                new CourseListAdapter<>(entities, false, true,
                        entity -> entity.ref, entity -> entity.id);
        this.courseListView.setAdapter(adapter);
        this.courseListView.setOnItemClickListener(adapter::onItemClick);
        this.courseListView.setOnItemLongClickListener(adapter::onItemLongClick);
    }

    protected void setRemoteCourseAdapter(Course courses[]){
        CourseListAdapter<Course> adapter = new CourseListAdapter<>(courses,
                true, false, course -> course, course -> -1l);
        this.courseListView.setAdapter(adapter);
        this.courseListView.setOnItemClickListener(adapter::onItemClick);
        this.courseListView.setOnItemLongClickListener(null);
    }

    private class CourseListAdapter<T> extends BaseAdapter {
        private static final String LOCALE = "%s, %s, %s";
        private static final String LOCALE_WITH_DISTANCE = "%s, %s, %s - %s";

        private final List<T> items;
        private final Function<T, Course> courseExtractor;
        private final Function<T, Long> idExtractor;

        CourseListAdapter(List<T> items, boolean showEmptyView, boolean showHeader,
                          Function<T, Course> courseExtractor, Function<T, Long> idExtractor) {
            this.items = items;
            this.courseExtractor = courseExtractor;
            this.idExtractor = idExtractor;
            MenuOverlayActivity.this.courseListEmptyView.setVisibility(
                    (showEmptyView && this.items.size() == 0) ? View.VISIBLE : View.GONE);
            MenuOverlayActivity.this.courseListHeader.setVisibility(
                    showHeader ? View.VISIBLE : View.GONE);
        }

        CourseListAdapter(@NonNull T items[], boolean showEmptyView, boolean showHeader,
                          @NonNull Function<T, Course> courseExtractor, @NonNull Function<T, Long> idExtractor) {
            this(Arrays.asList(items), showEmptyView, showHeader, courseExtractor, idExtractor);
        }

        CourseListAdapter(boolean showEmptyView, boolean showHeader) {
            this(new ArrayList<>(), showEmptyView, showHeader, null, null);
        }

        private class ViewHolder{
            private final int position;
            private final View buttonPanel, cancelButton, deleteButton, editButton;
            private final TextView primaryTextView,altTextView;
            ViewHolder(int position, View view){
                this.position = position;
                this.primaryTextView = view.findViewById(R.id.courseName);
                this.altTextView = view.findViewById(R.id.courseAltText);
                this.buttonPanel = view.findViewById(R.id.buttonPanel);
                this.cancelButton = view.findViewById(R.id.cancelButton);
                this.deleteButton = view.findViewById(R.id.deleteButton);
                this.editButton = view.findViewById(R.id.editButton);

                view.setTag(this);
                this.cancelButton.setTag(this);
                this.deleteButton.setTag(this);
                this.editButton.setTag(this);
            }
        }

        protected void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Course course = this.getCourse(position);
            Intent output = new Intent();
            output.putExtra(INTENT_EXTRA_RESULT, INTENT_RESULT_PLAY_COURSE);
            output.putExtra(INTENT_EXTRA_COURSE_ID, this.getItemId(position));
            output.putExtra(INTENT_EXTRA_COURSE_REMOTE_ID, course.getRemoteId());
            output.putExtra(INTENT_EXTRA_COURSE, new Gson().toJson(course));
            MenuOverlayActivity.this.setResult(RESULT_OK, output);
            MenuOverlayActivity.this.finish();
        }

        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            @SuppressWarnings("unchecked")
            ViewHolder holder = (ViewHolder)view.getTag();
            if(holder.buttonPanel.getVisibility() == View.VISIBLE){
                return false;
            }
            else{
                holder.buttonPanel.setVisibility(View.VISIBLE);
                holder.altTextView.setVisibility(View.GONE);

                holder.cancelButton.setOnClickListener(v -> {
                    @SuppressWarnings("unchecked")
                    ViewHolder parentHolder = (ViewHolder)view.getTag();
                    parentHolder.buttonPanel.setVisibility(View.GONE);
                    parentHolder.altTextView.setVisibility(View.VISIBLE);
                });
                holder.editButton.setOnClickListener(v -> {
                    @SuppressWarnings("unchecked")
                    ViewHolder parentHolder = (ViewHolder)view.getTag();

                    Intent output = new Intent();
                    output.putExtra(MenuOverlayActivity.INTENT_EXTRA_RESULT, MenuOverlayActivity.INTENT_RESULT_EDIT_COURSE);
                    output.putExtra(MenuOverlayActivity.INTENT_EXTRA_COURSE_ID, this.getItemId(parentHolder.position));
                    output.putExtra(MenuOverlayActivity.INTENT_EXTRA_COURSE, new Gson().toJson(this.getCourse(parentHolder.position)));
                    MenuOverlayActivity.this.setResult(MenuOverlayActivity.RESULT_OK, output);
                    MenuOverlayActivity.this.finish();
                });
                holder.deleteButton.setOnClickListener(v ->{
                    @SuppressWarnings("unchecked")
                    ViewHolder parentHolder = (ViewHolder)view.getTag();

                    MenuOverlayActivity.this.repo.delete(
                        this.getItemId(parentHolder.position),
                        i -> {
                            this.items.remove(parentHolder.position);
                            this.notifyDataSetChanged();
                        }
                    );
                });
            }
            return true;
        }


        @Override
        public View getView(final int position, View convertView, ViewGroup viewGroup) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.list_row_course, viewGroup, false);
            }
            ViewHolder holder = new ViewHolder(position, convertView);

            Course course = this.getCourse(position);
            holder.primaryTextView.setText(course.getFullName());
            Double distanceInM = course.getDistance();
            holder.altTextView.setText(
                    (distanceInM == null) ?
                            String.format(LOCALE, course.getMunicipality(),
                                    course.getState(), course.getCountry()) :
                            String.format(LOCALE_WITH_DISTANCE, course.getMunicipality(),
                                    course.getState(), course.getCountry(),
                                    Calculator.getDistanceInKm(distanceInM) + "km"));

            return convertView;
        }

        @Override
        public long getItemId(int position) {
            return this.idExtractor.apply(this.items.get(position));
        }

        @Override
        public T getItem(int position) {
            return this.items.get(position);
        }

        protected Course getCourse(int position) {
            return this.courseExtractor.apply(this.getItem(position));
        }

        @Override
        public int getCount() {
            return this.items.size();
        }
    }


}
