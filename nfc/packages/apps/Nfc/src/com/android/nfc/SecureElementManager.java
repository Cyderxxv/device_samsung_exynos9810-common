package com.android.nfc;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.PhoneConstants;
import android.util.Log;
import android.os.UserHandle;
import java.util.Timer;
import java.util.TimerTask;
import android.database.ContentObserver;
import android.provider.Settings;
import android.net.Uri;

public class SecureElementManager {
    static private final String TAG = "SecureElementManager";
    static final boolean DBG = true;

    static final String NFC_SETTINGS_DEFAULT_SE = "nfc_default_se";
    static final String NFC_SETTINGS_PREFERRED_SE = "nfc_preferred_se";
    static final String NFC_SETTINGS_SE_LIST = "nfc_se_list";

    static final String SEID_STR_SIM = "SIM";
    static final String SEID_STR_SIM1 = "SIM1";
    static final String SEID_STR_SIM2 = "SIM2";
    static final String SEID_STR_ESE = "eSE";
    static final String SEID_STR_DELIMITER = ";";

    static final int SEID_HOST = 0;
    static final int SEID_ESE = 2;
    static final int SEID_SIM1 = 3;
    static final int SEID_SIM2 = 4;
    static final int UNDEFINED = -1;
    static final int SIM_SLOT1 = 0;
    static final int SIM_SLOT2 = 1;

    Context mContext;
    NfcService mNfcService;

    boolean mIsSupportDualSim = false;
    boolean mIsSupportAutoSwitch = true; /* SIM auto switch */
    boolean mIsSlotSwitched = false;

    Timer mSimDetectDebounceTimer;
    DebounceTimerTask mDebounceTimerTask;
    int mDebounceTime = 4000;

    public SecureElementManager(Context context) {
        Log.d(TAG, "SecureElementManager");
        mContext = context;
        mNfcService = NfcService.getInstance();
        mIsSupportDualSim = TelephonyManager.getDefault().getPhoneCount() > 1 ? true : false;
        if (!mIsSupportDualSim) {
            mDebounceTime = 500;
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiverAsUser(mReceiver, UserHandle.ALL, filter, null, null);
        mSimDetectDebounceTimer = new Timer();
        mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor(NFC_SETTINGS_DEFAULT_SE),
                                                              true, mUserValueObserver);
    }

    public void init() {
        updateSeListForSettings();
        updateDefaultsForSettings();
    }

    private void updateDefaultsForSettings() {
        int preferredSe;
        int defaultRoute;
        String preferredSeString;

        defaultRoute = mNfcService.getDefaultRoute();
        preferredSe = mNfcService.getPreferredSimSlot();

        preferredSeString = SEID_STR_SIM + (preferredSe + 1);
        Settings.Global.putString(mContext.getContentResolver(), NFC_SETTINGS_PREFERRED_SE, preferredSeString);

        if (defaultRoute > SEID_ESE) {
            defaultRoute += preferredSe;
        }

        Settings.Global.putInt(mContext.getContentResolver(), NFC_SETTINGS_DEFAULT_SE, defaultRoute);
        if (DBG) Log.d(TAG, "updateDefaultSeForSettings: pref: " + preferredSeString + ", defaultRoute: " + defaultRoute);
    }

    private void updateSeListForSettings() {
        int seList[];
        seList = mNfcService.getSecureElementList();

        boolean isSimAdded = false;
        String seListForSettings;
        seListForSettings = "";

        if (seList != null) {
            for (int i = 0; i < seList.length; i++) {
                if (SEID_SIM1 == seList[i]) {
                    if (mIsSupportDualSim == true) {
                        seListForSettings += SEID_STR_SIM1 + SEID_STR_DELIMITER;
                        seListForSettings += SEID_STR_SIM2 + SEID_STR_DELIMITER;
                    } else {
                        seListForSettings += SEID_STR_SIM + SEID_STR_DELIMITER;
                    }
                } else if (SEID_ESE == seList[i]) {
                    seListForSettings += SEID_STR_ESE + SEID_STR_DELIMITER;
                }
            }
        } else {
            if (DBG) Log.e(TAG, "Can't find SE");
        }

        if (DBG) Log.d(TAG, "seList: " + seListForSettings);
        Settings.Global.putString(mContext.getContentResolver(), NFC_SETTINGS_SE_LIST, seListForSettings);
    }

    public boolean isSupportAutoSwitch() {
        return mIsSupportAutoSwitch;
    }

    public boolean isSupportDualSim() {
        return mIsSupportDualSim;
    }

    private boolean isSimInsertedInOtherSlot(int curSlotId) {
        int checkSlot;
        int simState;

        if (UNDEFINED == curSlotId) {
            curSlotId = SIM_SLOT1;
        }

        checkSlot = (curSlotId == SIM_SLOT1) ? SIM_SLOT2 : SIM_SLOT1;
        simState = TelephonyManager.getDefault().getSimState(checkSlot);

        if (DBG) Log.d(TAG, "isSimInsertedInSecondSlot, simState: " + simState);
        if (simState == TelephonyManager.SIM_STATE_ABSENT ||
                simState == TelephonyManager.SIM_STATE_UNKNOWN) {
            return false;
        }
        return true;
    }

    public boolean simAutoSelectionCheck(int curSlotId) {
        boolean isNeedToRestart = false;

        // Normal boot and enable case
        if (!mIsSlotSwitched) {
            if (isSimInsertedInOtherSlot(curSlotId)) {
                if (DBG) Log.d(TAG, "SIM is inserted on other slot, switch it");
                int switchSlotId = (curSlotId == SIM_SLOT1) ? SIM_SLOT2 : SIM_SLOT1;
                int defaultRoute = mNfcService.getDefaultRoute();
                setPreferredSimSlot(switchSlotId);
                Settings.Global.putInt(mContext.getContentResolver(), NFC_SETTINGS_DEFAULT_SE, defaultRoute + switchSlotId);
                mIsSlotSwitched = true;
                isNeedToRestart = true;
            }
        }
        return isNeedToRestart;
    }

    private void setPreferredSimSlot(int slotId) {
        if (DBG) Log.d(TAG, "setPreferredSimSlot, slotId: " + slotId);
        mNfcService.setPreferredSimSlot(slotId);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!mNfcService.isNfcEnabled()) {
                if (DBG) Log.d(TAG, "NFC disabled, ignore SIM State change");
                return;
            }

            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                String iccState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);

                if (iccState.equals(IccCardConstants.INTENT_VALUE_ICC_ABSENT) ||
                        iccState.equals(IccCardConstants.INTENT_VALUE_ICC_NOT_READY)) {
                    if (DBG) Log.d(TAG, "SIM state changed, absent or inserted");
                    if (mDebounceTimerTask == null) {
                        mDebounceTimerTask = new DebounceTimerTask();
                    } else {
                        return;
                    }
                    mSimDetectDebounceTimer.schedule(mDebounceTimerTask, mDebounceTime);
                }
            }
        }
    };

    private class DebounceTimerTask extends TimerTask {
        @Override
        public void run() {
            int curSlotId = 0;
            int simCount = 0;

            mDebounceTimerTask = null;
            mNfcService.waitNfcEeDiscovery(true);

            for (int slotIndex = 0; slotIndex < 2; slotIndex++) {
                int simState = TelephonyManager.getDefault().getSimState(slotIndex);
                if (simState > TelephonyManager.SIM_STATE_ABSENT) {
                    simCount++;
                    curSlotId = slotIndex;
                }
            }

            if (DBG) Log.d(TAG, "Current simCount: " + simCount);
            mIsSlotSwitched = false;
            if (simCount == 0) {
                // SIM Removed
                mNfcService.restartNfcService();
            }

            if (simCount == 1) {
                // Single SIM inserted
                setPreferredSimSlot(curSlotId);
                mNfcService.restartNfcService();
            }

            if (simCount > 1) {
                // Dual SIM inserted
                mNfcService.restartNfcService();
            }
        }
    }

    private final ContentObserver mUserValueObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            int defaultSe = SEID_HOST,currentSe;

            try {
                defaultSe = Settings.Global.getInt(mContext.getContentResolver(), NFC_SETTINGS_DEFAULT_SE);
            } catch (Exception e) {
            }

            Log.d(TAG, "onChange; defaultSe: " + defaultSe);
            currentSe = mNfcService.getDefaultRoute();
            if (currentSe > SEID_ESE) {
                currentSe += mNfcService.getPreferredSimSlot();
                if (defaultSe == currentSe) {
                    Log.d(TAG, "onChange; not update same as current: " + currentSe);
                    return;
                }
            } else {
                if (defaultSe == currentSe) {
                    Log.d(TAG, "onChange; not update same as current: " + currentSe);
                    return;
                }
            }

            Log.d(TAG, "onChange; update route to: " + defaultSe);
            if (defaultSe > SEID_ESE) {
                mNfcService.setDefaultRoute(SEID_SIM1, true);
                mIsSlotSwitched = true; // Do not switch slot because it's intended behavior
                setPreferredSimSlot(defaultSe - SEID_SIM1);
                mNfcService.restartNfcService();
            } else {
                mNfcService.setDefaultRoute(defaultSe, true);
            }
        }
    }; 
}
