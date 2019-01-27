package ca.dait.opengolf.app.drivers;

import android.content.Intent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import ca.dait.opengolf.app.R;
import ca.dait.opengolf.app.activities.MainActivity;

/**
 * Root map driver that abstracts the layout details from concrete classes.
 */
public abstract class AbstractLayoutDriver {

    protected final MainActivity mainActivity;

    private View buttonViews[];
    private TextView panelViews[];
    private int savedButtonState[];
    private int savedPanelState[];

    private View refreshSpinner;

    /**
     * Grabs the references to all relevant UI elements managed by this class and sets the initial
     * state. (Everything hidden)
     *
     * @param mainActivity
     */
    protected AbstractLayoutDriver(MainActivity mainActivity) {
        this.mainActivity = mainActivity;

        this.refreshSpinner = this.mainActivity.findViewById(R.id.spinner);

        this.buttonViews = new View[Button.values().length];
        this.savedButtonState = new int[this.buttonViews.length];
        for (Button button : Button.values()) {
            this.buttonViews[button.ordinal()] = this.mainActivity.findViewById(button.buttonId);
        }

        this.panelViews = new TextView[Panel.values().length];
        this.savedPanelState = new int[this.panelViews.length];
        for (Panel panel : Panel.values()) {
            this.panelViews[panel.ordinal()] = this.mainActivity.findViewById(panel.panelId);
        }
        this.hideAll();
    }

    /**
     * Defines all the display elements such as TextView's and EditText's
     */
    protected enum Panel {
        COURSE_TITLE(R.id.courseTitle),
        HOLE_NO(R.id.holeNo),
        DISTANCE_1(R.id.distancePanel1),
        DISTANCE_2(R.id.distancePanel2),
        DISTANCE_3(R.id.distancePanel3),
        NICK_NAME(R.id.courseNickname);
        private final int panelId;
        Panel(int panelId) {
            this.panelId = panelId;
        }
    }

    /**
     * Defines all interactive buttons
     */
    protected enum Button {
        START(R.id.startButton),
        PREVIOUS(R.id.prevButton),
        NEXT(R.id.nextButton),
        CANCEL(R.id.cancelButton),
        WAYPOINT(R.id.waypointButton),
        FLAG(R.id.flagButton),
        DONE(R.id.doneButton);

        private final int buttonId;
        Button(int buttonId) {
            this.buttonId = buttonId;
        }
    }

    /**
     * Called to see if the driver is in a state where it can be resumed before calling resume.
     *
     * @param intent The intent returned from the main menu. Implementations should determine if the
     *               user selected the same action (ex: same course) and if the driver should be
     *               resumed
     * @return true if the activity can and should be resumed, false otherwise.
     */
    public abstract boolean canRestart(Intent intent);

    /**
     * Sets and override value for screen brightness.
     *
     * @param brightness Screen brightness window attribute.
     */
    protected void brightnessOverride(float brightness){
        //Set max brightness. Golfers are usually outdoors in extreme brightness.
        Window window = this.mainActivity.getWindow();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.screenBrightness = brightness;
        window.setAttributes(layoutParams);
    }

    /**
     * Saves the display state of the UI elements so they can be hidden and restored when the main
     * menu shows
     */
    protected void saveState() {
        for (Panel panel : Panel.values()) {
            this.savedPanelState[panel.ordinal()] = this.panelViews[panel.ordinal()].getVisibility();
        }
        for (Button button : Button.values()) {
            this.savedButtonState[button.ordinal()] = this.buttonViews[button.ordinal()].getVisibility();
        }
    }

    /**
     * Restores the display state of the UI elements if the main menu results in the resumption of
     * the map driver.
     */
    public void restoreState() {
        for (Panel panel : Panel.values()) {
            this.panelViews[panel.ordinal()].setVisibility(this.savedPanelState[panel.ordinal()]);
        }
        for (Button button : Button.values()) {
            this.buttonViews[button.ordinal()].setVisibility(this.savedButtonState[button.ordinal()]);
        }
    }

    protected void show(Panel... panels) {
        for (Panel panel : panels) {
            this.panelViews[panel.ordinal()].setVisibility(View.VISIBLE);
        }
    }

    protected void hide(Panel... panels) {
        for (Panel panel : panels) {
            this.panelViews[panel.ordinal()].setVisibility(View.GONE);
        }
    }

    protected void showText(Panel panel, String text) {
        this.panelViews[panel.ordinal()].setText(text);
        this.show(panel);
    }

    @SuppressWarnings("unchecked")
    protected <T extends TextView> T getPanel(Panel panel){
        return (T)this.panelViews[panel.ordinal()];
    }

    protected void hide(Button... buttons) {
        for (Button button : buttons) {
            this.buttonViews[button.ordinal()].setVisibility(View.GONE);
        }
    }

    protected void show(Button... buttons) {
        for (Button button : buttons) {
            this.buttonViews[button.ordinal()].setVisibility(View.VISIBLE);
        }
    }

    /**
     * Shows only the provided buttons and hides all others.
     *
     * @param buttons
     */
    protected void showOnly(Button... buttons) {
        this.hideAllButtons();
        this.show(buttons);
    }

    protected void hideAllButtons() {
        for (Button button : Button.values()) {
            this.buttonViews[button.ordinal()].setVisibility(View.GONE);
        }
    }

    protected void hideAllPanels() {
        for (Panel panel : Panel.values()) {
            this.panelViews[panel.ordinal()].setVisibility(View.GONE);
        }
    }

    public void hideAll() {
        this.hideAllButtons();
        this.hideAllPanels();
        this.showRefreshing(false);
    }

    public void clear() {
        for (Button button : Button.values()) {
            this.setClickListener(button, null);
        }
        this.hideAll();
    }

    /**
     * Set's the display text for the provided hole number
     *
     * @param holeNo
     */
    protected void setHoleNo(int holeNo){
        this.showText(Panel.HOLE_NO,"#" + holeNo);
    }

    protected void setClickListener(Button button, View.OnClickListener listener) {
        this.buttonViews[button.ordinal()].setOnClickListener(listener);
    }

    protected void showRefreshing(boolean refreshing) {
        this.refreshSpinner.setVisibility((refreshing) ? View.VISIBLE : View.GONE);
    }

    public void onActivityResult(int resultCode, Intent data) {}
}
