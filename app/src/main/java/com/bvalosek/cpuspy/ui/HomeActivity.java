//-----------------------------------------------------------------------------
//
// (C) Brandon Valosek, 2011 <bvalosek@gmail.com>
//
//-----------------------------------------------------------------------------

package com.bvalosek.cpuspy.ui;

// imports
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bvalosek.cpuspy.*;
import com.bvalosek.cpuspy.CpuStateMonitor.CpuState;
import com.bvalosek.cpuspy.CpuStateMonitor.CpuStateMonitorException;
import android.util.Log;

/** main activity class */
public class HomeActivity extends Activity
{
    private static final String TAG = "CpuSpy";

    private CpuSpyApp _app = null;

    // the views
    private LinearLayout    _uiStatesView = null;
    private TextView        _uiAdditionalStates = null;
    private TextView        _uiTotalStateTime = null;
    private TextView        _uiHeaderAdditionalStates = null;
    private TextView        _uiHeaderTotalStateTime = null;
    private TextView        _uiStatesWarning = null;
    private TextView        _uiKernelString = null;

    /** whether or not we're updating the data in the background */
    private boolean     _updatingData = false;

    /** Initialize the Activity */
    @Override public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.home_layout);
            _app = (CpuSpyApp)getApplicationContext();
            findViews();

            setTitle(getString(R.string.app_name) + " v" + BuildConfig.VERSION_NAME);

            // see if we're updating data during a config change (rotate screen)
            if (savedInstanceState != null) {
                _updatingData = savedInstanceState.getBoolean("updatingData");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            finish(); // Close activity if we can't even initialize
        }
    }

    /** When the activity is about to change orientation */
    @Override public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            outState.putBoolean("updatingData", _updatingData);
        } catch (Exception e) {
            Log.e(TAG, "Error in onSaveInstanceState", e);
        }
    }


    /** Update the view when the application regains focus */
    @Override public void onResume () {
        super.onResume();
        try {
            refreshData();
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
        }
    }

    /** Map all of the UI elements to member variables */
    private void findViews() {
        try {
            _uiStatesView = (LinearLayout)findViewById(R.id.ui_states_view);
            _uiKernelString = (TextView)findViewById(R.id.ui_kernel_string);
            _uiAdditionalStates = (TextView)findViewById(
                    R.id.ui_additional_states);
            _uiHeaderAdditionalStates = (TextView)findViewById(
                    R.id.ui_header_additional_states);
            _uiHeaderTotalStateTime = (TextView)findViewById(
                    R.id.ui_header_total_state_time);
            _uiStatesWarning = (TextView)findViewById(R.id.ui_states_warning);
            _uiTotalStateTime = (TextView)findViewById(R.id.ui_total_state_time);
        } catch (Exception e) {
            Log.e(TAG, "Error finding views", e);
        }
    }

    /** called when we want to infalte the menu */
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        try {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.home_menu, menu);
        } catch (Exception e) {
            Log.e(TAG, "Error creating menu", e);
        }
        return true;
    }

    /** called to handle a menu event */
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        try {
            int itemId = item.getItemId();

            if (itemId == R.id.menu_refresh) {
                refreshData();
                return true;
            }
            else if (itemId == R.id.menu_reset) {
                try {
                    _app.getCpuStateMonitor().setOffsets();
                } catch (Exception e) {
                    Log.e(TAG, "Error resetting timers", e);
                }
                _app.saveOffsets();
                updateView();
                return true;
            }
            else if (itemId == R.id.menu_restore) {
                _app.getCpuStateMonitor().removeOffsets();
                _app.saveOffsets();
                updateView();
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling menu item", e);
        }
        return super.onOptionsItemSelected(item);
    }

    /** Generate and update all UI elements */
    public void updateView() {
        /** Get the CpuStateMonitor from the app, and iterate over all states,
         * creating a row if the duration is > 0 or otherwise marking it in
         * extraStates (missing) */
        try {
            CpuStateMonitor monitor = _app.getCpuStateMonitor();
            _uiStatesView.removeAllViews();
            List<String> extraStates = new ArrayList<String>();

            for (CpuState state : monitor.getStates()) {
                try {
                    if (state.duration > 0) {
                        generateStateRow(state, _uiStatesView);
                    } else {
                        if (state.freq == 0) {
                            extraStates.add("Deep Sleep");
                        } else {
                            extraStates.add(state.freq/1000 + " MHz");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing state row", e);
                }
            }

            if ( monitor.getStates().size() == 0) {
                _uiStatesWarning.setVisibility(View.VISIBLE);
                _uiHeaderTotalStateTime.setVisibility(View.GONE);
                _uiTotalStateTime.setVisibility(View.GONE);
                _uiStatesView.setVisibility(View.GONE);
            } else {
                _uiStatesWarning.setVisibility(View.GONE);
                _uiHeaderTotalStateTime.setVisibility(View.VISIBLE);
                _uiTotalStateTime.setVisibility(View.VISIBLE);
                _uiStatesView.setVisibility(View.VISIBLE);
            }

            long totTime = monitor.getTotalStateTime() / 100;
            _uiTotalStateTime.setText(sToString(totTime));

            if (extraStates.size() > 0) {
                int n = 0;
                String str = "";

                for (String s : extraStates) {
                    if (n++ > 0)
                        str += ", ";
                    str += s;
                }

                _uiAdditionalStates.setVisibility(View.VISIBLE);
                _uiHeaderAdditionalStates.setVisibility(View.VISIBLE);
                _uiAdditionalStates.setText(str);
            } else {
                _uiAdditionalStates.setVisibility(View.GONE);
                _uiHeaderAdditionalStates.setVisibility(View.GONE);
            }

            _uiKernelString.setText(_app.getKernelVersion());
        } catch (Exception e) {
            Log.e(TAG, "Error updating view", e);
        }
    }

    /** Attempt to update the time-in-state info */
    public void refreshData() {
        try {
            if (!_updatingData) {
                new RefreshStateDataTask().execute((Void)null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing data", e);
        }
    }

    /** @return A nicely formatted String representing tSec seconds */
    private static String sToString(long tSec) {
        try {
            long h = (long)Math.floor(tSec / (60*60));
            long m = (long)Math.floor((tSec - h*60*60) / 60);
            long s = tSec % 60;
            String sDur;
            sDur = h + ":";
            if (m < 10)
                sDur += "0";
            sDur += m + ":";
            if (s < 10)
                sDur += "0";
            sDur += s;

            return sDur;
        } catch (Exception e) {
            return "0:00:00";
        }
    }

    /**
     * @return a View that correpsonds to a CPU freq state row as specified
     * by the state parameter
     */
    private View generateStateRow(CpuState state, ViewGroup parent) {
        try {
            LayoutInflater inf = LayoutInflater.from((Context)_app);
            LinearLayout theRow = (LinearLayout)inf.inflate(
                    R.layout.state_row, parent, false);

            CpuStateMonitor monitor = _app.getCpuStateMonitor();
            float per = (float)state.duration * 100 /
                        monitor.getTotalStateTime();
            String sPer = (int)per + "%";

            String sFreq;
            if (state.freq == 0) {
                sFreq = "Deep Sleep";
            } else {
                sFreq = state.freq / 1000 + " MHz";
            }

            long tSec = state.duration / 100;
            String sDur = sToString(tSec);

            TextView freqText = (TextView)theRow.findViewById(R.id.ui_freq_text);
            TextView durText = (TextView)theRow.findViewById(
                    R.id.ui_duration_text);
            TextView perText = (TextView)theRow.findViewById(R.id.ui_percentage_text);
            ProgressBar bar = (ProgressBar)theRow.findViewById(R.id.ui_bar);

            freqText.setText(sFreq);
            perText.setText(sPer);
            durText.setText(sDur);
            bar.setProgress((int)per);

            parent.addView(theRow);
            return theRow;
        } catch (Exception e) {
            Log.e(TAG, "Error generating state row", e);
            return null;
        }
    }

    /** Keep updating the state data off the UI thread for slow devices */
    protected class RefreshStateDataTask extends AsyncTask<Void, Void, Void> {

        /** Stuff to do on a seperate thread */
        @Override protected Void doInBackground(Void... v) {
            try {
                CpuStateMonitor monitor = _app.getCpuStateMonitor();
                monitor.updateStates();
            } catch (Exception e) {
                Log.e(TAG, "Problem getting CPU states", e);
            }

            return null;
        }

        /** Executed on the UI thread right before starting the task */
        @Override protected void onPreExecute() {
            try {
                log("starting data update");
                _updatingData = true;
            } catch (Exception e) {
                Log.e(TAG, "Error in onPreExecute", e);
            }
        }

        /** Executed on UI thread after task */
        @Override protected void onPostExecute(Void v) {
            try {
                log("finished data update");
                _updatingData = false;
                updateView();
            } catch (Exception e) {
                Log.e(TAG, "Error in onPostExecute", e);
            }
        }
    }

    /** logging */
    private void log(String s) {
        try {
            Log.d(TAG, s);
        } catch (Exception e) {
            // Ignore logging errors
        }
    }
}
