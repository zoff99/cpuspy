//-----------------------------------------------------------------------------
//
// (C) Brandon Valosek, 2011 <bvalosek@gmail.com>
//
//-----------------------------------------------------------------------------

package com.bvalosek.cpuspy;

// imports
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.os.SystemClock;
import android.util.Log;

/**
 * CpuStateMonitor is a class responsible for querying the system and getting
 * the time-in-state information, as well as allowing the user to set/reset
 * offsets to "restart" the state timers
 */
/** @noinspection SpellCheckingInspection*/
public class CpuStateMonitor {

    public static final String TIME_IN_STATE_PATH =
        "/sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state";

    private static final String TAG = "CpuStateMonitor";

    // Implausible frequency threshold: 100 GHz (100,000,000,000 Hz)
    private static final long MAX_FREQ = 100000000000L;

    private List<CpuState>      _states = new ArrayList<CpuState>();
    private Map<Long, Long>     _offsets = new HashMap<Long, Long>();

    /** exception class */
    public class CpuStateMonitorException extends Exception {
        public CpuStateMonitorException(String s) {
            super(s);
        }
    }

    /**
     * simple struct for states/time
     */
    public class CpuState implements Comparable<CpuState> {
        public CpuState(long a, long b) { freq = a; duration = b; }

        public long freq = 0;
        public long duration = 0;

        /** for sorting, compare the freqs */
        public int compareTo(CpuState state) {
            try {
                Long a = freq;
                Long b = state.freq;
                return a.compareTo(b);
            } catch (Exception e) {
                return 0;
            }
        }
    }

    /** @return List of CpuState with the offsets applied */
    public List<CpuState> getStates() {
        List<CpuState> states = new ArrayList<CpuState>();
        try {
            for (CpuState state : _states) {
                long duration = state.duration;
                if (_offsets.containsKey(state.freq)) {
                    long offset = _offsets.get(state.freq);
                    if (offset <= duration) {
                        duration -= offset;
                    } else {
                        _offsets.clear();
                        return getStates();
                    }
                }
                states.add(new CpuState(state.freq, duration));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in getStates", e);
        }

        return states;
    }

    /**
     * @return Sum of all state durations including deep sleep, accounting
     * for offsets
     */
    public long getTotalStateTime() {
        try {
            long sum = 0;
            long offset = 0;

            for (CpuState state : _states) {
                sum += state.duration;
            }

            for (Map.Entry<Long, Long> entry : _offsets.entrySet()) {
                offset += entry.getValue();
            }

            return sum - offset;
        } catch (Exception e) {
            Log.e(TAG, "Error in getTotalStateTime", e);
            return 0;
        }
    }

    /**
     * @return Map of freq->duration of all the offsets
     */
    public Map<Long, Long> getOffsets() {
        return _offsets;
    }

    public void setOffsets(Map<Long, Long> offsets) {
        try {
            _offsets = offsets;
        } catch (Exception e) {
            Log.e(TAG, "Error in setOffsets", e);
        }
    }

    /**
     * Updates the current time in states and then sets the offset map to the
     * current duration, effectively "zeroing out" the timers
     */
    public void setOffsets() throws CpuStateMonitorException {
        try {
            _offsets.clear();
            updateStates();

            for (CpuState state : _states) {
                _offsets.put(state.freq, state.duration);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in setOffsets", e);
            throw new CpuStateMonitorException("Error setting offsets");
        }
    }

    /** removes state offsets */
    public void removeOffsets() {
        try {
            _offsets.clear();
        } catch (Exception e) {
            Log.e(TAG, "Error in removeOffsets", e);
        }
    }

    /**
     * @return a list of all the CPU frequency states, which contains
     * both a frequency and a duration (time spent in that state
     */
    public List<CpuState> updateStates()
            throws CpuStateMonitorException {
        try {
            InputStream is = new FileInputStream(TIME_IN_STATE_PATH);
            InputStreamReader ir = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(ir);
            _states.clear();
            readInStates(br);
            is.close();
        } catch (Exception e) {
            Log.e(TAG, "Problem opening time-in-states file", e);
            throw new CpuStateMonitorException("Problem opening time-in-states file");
        }

        try {
            long sleepTime = (SystemClock.elapsedRealtime()
                              - SystemClock.uptimeMillis()) / 10;
            _states.add(new CpuState(0, sleepTime));

            Collections.sort(_states, new Comparator<CpuState>() {
                @Override
                public int compare(CpuState s1, CpuState s2) {
                    try {
                        if (s1.freq == 0 && s2.freq != 0) return -1;
                        if (s2.freq == 0 && s1.freq != 0) return 1;
                        return Long.compare(s1.freq, s2.freq);
                    } catch (Exception e) {
                        return 0;
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error sorting states", e);
        }

        return _states;
    }

    /** read from a provided BufferedReader the state lines into the
     * States member field
     */
    private void readInStates(BufferedReader br)
            throws CpuStateMonitorException {
        try {
            String line;
            while ((line = br.readLine()) != null) {
                try {
                    String[] nums = line.split(" ");
                    if (nums.length >= 2) {
                        long freq = Long.parseLong(nums[0].trim());
                        long duration = Long.parseLong(nums[1].trim());

                        // DISREGARD IMPLAUSIBLE VALUES
                        if (freq < 0 || freq > MAX_FREQ || duration < 0) {
                            continue;
                        }

                        _states.add(new CpuState(freq, duration));
                    }
                } catch (Exception e) {
                    // Skip this line if it's malformed or implausible
                    Log.w(TAG, "Skipping malformed line: " + line, e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Problem processing time-in-states file", e);
            throw new CpuStateMonitorException("Problem processing time-in-states file");
        }
    }
}
